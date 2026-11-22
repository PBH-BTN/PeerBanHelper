//! Authenticated WebUI configuration and provider validation.

use std::path::PathBuf;
use std::sync::Arc;

use axum::extract::{Path, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::{
    Json, Router,
    routing::{delete, get, post},
};
use pbh_storage::AlertLevel;
use pbh_web::config_file::{self, EditError};
use serde_json::{Value, json};
use serde_yaml_ng::{Mapping, Value as Yaml};

use crate::ban_api::BanLanguage;
use crate::push::{Provider, read_providers};

#[derive(Clone)]
struct PushState {
    path: Arc<PathBuf>,
    demo_mode: bool,
    proxy: pbh_network::ProxyPolicy,
}

pub(crate) fn router(path: PathBuf, demo_mode: bool, proxy: pbh_network::ProxyPolicy) -> Router {
    Router::new()
        .route("/api/push", get(list).put(add))
        .route("/api/push/test", post(test))
        .route("/api/push/{name}", delete(remove).patch(update))
        .with_state(PushState {
            path: Arc::new(path),
            demo_mode,
            proxy,
        })
}

async fn list(State(state): State<PushState>, headers: HeaderMap) -> Response {
    let language = BanLanguage::from_headers(&headers);
    let providers = tokio::task::spawn_blocking(move || read_providers(&state.path)).await;
    let Ok(Ok(providers)) = providers else {
        return message(
            StatusCode::INTERNAL_SERVER_ERROR,
            language,
            "WEBAPI_INTERNAL_ERROR",
        );
    };
    let mut data = providers
        .into_values()
        .map(|provider| serde_json::to_value(provider).expect("provider is serializable"))
        .collect::<Vec<_>>();
    if state.demo_mode {
        for provider in &mut data {
            provider["config"] = json!({});
        }
    }
    Json(json!({"success": true, "message": null, "data": data})).into_response()
}

async fn add(
    State(state): State<PushState>,
    headers: HeaderMap,
    Json(provider): Json<Provider>,
) -> Response {
    update_config(state, headers, Change::Add(provider)).await
}

async fn update(
    State(state): State<PushState>,
    headers: HeaderMap,
    Path(name): Path<String>,
    Json(provider): Json<Provider>,
) -> Response {
    update_config(state, headers, Change::Update(name, provider)).await
}

async fn remove(
    State(state): State<PushState>,
    headers: HeaderMap,
    Path(name): Path<String>,
) -> Response {
    update_config(state, headers, Change::Delete(name)).await
}

async fn test(
    State(state): State<PushState>,
    headers: HeaderMap,
    Json(mut provider): Json<Provider>,
) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if state.demo_mode {
        return message(
            StatusCode::BAD_REQUEST,
            language,
            "DEMO_MODE_OPERATION_NOT_PERMITTED",
        );
    }
    if provider.validate().is_err() {
        return message(
            StatusCode::BAD_REQUEST,
            language,
            "PUSH_PROVIDER_API_ADD_FAILURE",
        );
    }
    let title = language.render(&json!({"key": "PUSH_PROVIDER_TEST_TITLE"}));
    let params = json!([provider.name, provider.kind]);
    let content =
        language.render(&json!({"key": "PUSH_PROVIDER_TEST_DESCRIPTION", "params": params}));
    match provider
        .send(&title, &content, AlertLevel::Info, &state.proxy)
        .await
    {
        Ok(()) => message(StatusCode::OK, language, "PUSH_PROVIDER_API_TEST_OK"),
        Err(_) => {
            let detail =
                language.render(&json!({"key": "PUSH_PROVIDER_TEST_FAILED", "params": params}));
            Json(json!({"success": false, "message": detail, "data": null})).into_response()
        }
    }
}

enum Change {
    Add(Provider),
    Update(String, Provider),
    Delete(String),
}

type ResponseCode = (StatusCode, &'static str);

async fn update_config(state: PushState, headers: HeaderMap, change: Change) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if state.demo_mode {
        return message(
            StatusCode::BAD_REQUEST,
            language,
            "DEMO_MODE_OPERATION_NOT_PERMITTED",
        );
    }
    let result = tokio::task::spawn_blocking(move || {
        config_file::edit(&state.path, |yaml| {
            let section = yaml
                .entry("push-notification".into())
                .or_insert_with(|| Yaml::Mapping(Mapping::new()));
            if section.is_null() {
                *section = Yaml::Mapping(Mapping::new());
            }
            let section = section
                .as_mapping_mut()
                .ok_or((StatusCode::INTERNAL_SERVER_ERROR, "WEBAPI_INTERNAL_ERROR"))?;
            apply_change(section, change)
        })
    })
    .await;
    let (status, key) = match result {
        Ok(Ok(result) | Err(EditError::Rejected(result))) => result,
        _ => (
            StatusCode::INTERNAL_SERVER_ERROR,
            "PUSH_PROVIDER_API_CREATION_FAILED_IO_EXCEPTION",
        ),
    };
    message(status, language, key)
}

fn apply_change(section: &mut Mapping, change: Change) -> Result<ResponseCode, ResponseCode> {
    match change {
        Change::Add(mut provider) => {
            provider
                .validate()
                .map_err(|_| (StatusCode::BAD_REQUEST, "PUSH_PROVIDER_API_ADD_FAILURE"))?;
            let settings = encode(&provider)?;
            section.insert(provider.name.into(), settings);
            Ok((StatusCode::CREATED, "PUSH_PROVIDER_API_CREATED"))
        }
        Change::Update(previous, mut provider) => {
            provider
                .validate()
                .map_err(|_| (StatusCode::BAD_REQUEST, "PUSH_PROVIDER_API_UPDATE_FAILURE"))?;
            let settings = encode(&provider)?;
            section.remove(previous);
            section.insert(provider.name.into(), settings);
            Ok((StatusCode::OK, "PUSH_PROVIDER_API_UPDATED"))
        }
        Change::Delete(name) => {
            if section.remove(name).is_none() {
                return Err((StatusCode::NOT_FOUND, "PUSH_PROVIDER_API_REMOVE_NOT_EXISTS"));
            }
            Ok((StatusCode::OK, "PUSH_PROVIDER_API_REMOVE_SAVED"))
        }
    }
}

fn encode(provider: &Provider) -> Result<Yaml, ResponseCode> {
    provider.yaml().map_err(|_| {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            "PUSH_PROVIDER_API_CREATION_FAILED_IO_EXCEPTION",
        )
    })
}

fn message(status: StatusCode, language: BanLanguage, key: &str) -> Response {
    let detail = language.render(&json!({"key": key}));
    (
        status,
        Json(json!({"success": status.is_success(), "message": detail, "data": Value::Null})),
    )
        .into_response()
}
