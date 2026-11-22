//! Authenticated downloader configuration and connection testing.

use std::path::{Path as FsPath, PathBuf};
use std::time::Duration;

use axum::body::Bytes;
use axum::extract::{Path, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::{patch, post, put};
use axum::{Json, Router};
use pbh_core::AddressPolicy;
use pbh_web::config_file::{self, EditError};
use serde::Deserialize;
use serde_json::{Map, Value, json};
use serde_yaml_ng::{Mapping, Value as Yaml};
use tokio::sync::watch;

use crate::ban_api::BanLanguage;
use crate::config::{DownloaderConfig, MainConfig, read_yaml};
use crate::downloader::Downloader;
use crate::web::ScanState;

#[derive(Clone)]
struct Downloaders {
    data_dir: PathBuf,
    reload: watch::Sender<bool>,
    demo_mode: bool,
}

impl Downloaders {
    fn config_path(&self) -> PathBuf {
        crate::config::config_dir(&self.data_dir).join("config.yml")
    }

    async fn edit_config(
        &self,
        edit: impl FnOnce(&mut Mapping) -> Result<(), Rejection> + Send + 'static,
    ) -> Result<(), EditError<Rejection>> {
        let path = self.config_path();
        let reload = self.reload.clone();
        tokio::task::spawn_blocking(move || {
            config_file::edit(&path, edit)?;
            // Keep the persisted file and the scan configuration in sync if the caller disconnects.
            reload.send_replace(true);
            Ok(())
        })
        .await
        .map_err(|error| EditError::Io(std::io::Error::other(error)))?
    }
}

#[derive(Deserialize)]
struct Draft {
    id: Option<String>,
    config: Map<String, Value>,
}

struct Prepared {
    yaml: Yaml,
    config: DownloaderConfig,
}

#[derive(Clone, Copy)]
enum Save {
    Create,
    Update,
}

enum Rejection {
    Invalid,
    Duplicate,
    Missing,
}

pub(crate) fn router(scan: ScanState, data_dir: &FsPath) -> Router {
    Router::new()
        .route("/api/downloaders", put(create))
        .route("/api/downloaders/{id}", patch(update).delete(remove))
        .route("/api/downloaders/test", post(test))
        .route("/api/downloaders/scan", post(discover))
        .with_state(Downloaders {
            data_dir: data_dir.to_owned(),
            reload: scan.reload,
            demo_mode: scan.demo_mode,
        })
}

async fn discover(State(state): State<Downloaders>, headers: HeaderMap) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if state.demo_mode {
        return demo_error(language);
    }
    let path = state.config_path();
    let endpoints = tokio::task::spawn_blocking(move || {
        let config: MainConfig = read_yaml(&path)?;
        Ok::<_, anyhow::Error>(
            config
                .client
                .into_values()
                .filter_map(|entry| {
                    entry
                        .settings
                        .get("endpoint")
                        .and_then(Yaml::as_str)
                        .map(str::to_owned)
                })
                .collect::<Vec<_>>(),
        )
    })
    .await;
    let Ok(Ok(endpoints)) = endpoints else {
        return result(
            language,
            StatusCode::INTERNAL_SERVER_ERROR,
            "WEBAPI_INTERNAL_ERROR",
            false,
        );
    };
    let found = pbh_network::discovery::discover(endpoints).await;
    (
        StatusCode::OK,
        Json(json!({"success": true, "message": null, "data": found})),
    )
        .into_response()
}

async fn create(State(state): State<Downloaders>, headers: HeaderMap, body: Bytes) -> Response {
    save(state, headers, None, body, Save::Create).await
}

async fn update(
    State(state): State<Downloaders>,
    Path(id): Path<String>,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    save(state, headers, Some(id), body, Save::Update).await
}

async fn save(
    state: Downloaders,
    headers: HeaderMap,
    path_id: Option<String>,
    body: Bytes,
    operation: Save,
) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if state.demo_mode {
        return demo_error(language);
    }
    let invalid = match operation {
        Save::Create => "DOWNLOADER_API_ADD_FAILURE",
        Save::Update => "DOWNLOADER_API_UPDATE_FAILURE",
    };
    let Ok((id, draft)) = prepare(path_id, &body) else {
        return result(language, StatusCode::BAD_REQUEST, invalid, false);
    };
    let outcome = state
        .edit_config(move |document| save_entry(document, &id, draft, operation))
        .await;
    match outcome {
        Ok(()) => {
            let (status, key) = match operation {
                Save::Create => (StatusCode::CREATED, "DOWNLOADER_API_CREATED"),
                Save::Update => (StatusCode::OK, "DOWNLOADER_API_UPDATED"),
            };
            result(language, status, key, true)
        }
        Err(EditError::Rejected(Rejection::Duplicate)) => {
            let key = match operation {
                Save::Create => "DOWNLOADER_API_CREATION_FAILED_ALREADY_EXISTS",
                Save::Update => "DOWNLOADER_API_UPDATE_FAILURE_ALREADY_EXISTS",
            };
            result(language, StatusCode::BAD_REQUEST, key, false)
        }
        Err(EditError::Rejected(Rejection::Invalid)) => {
            result(language, StatusCode::BAD_REQUEST, invalid, false)
        }
        _ => result(
            language,
            StatusCode::INTERNAL_SERVER_ERROR,
            "DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION",
            false,
        ),
    }
}

async fn remove(
    State(state): State<Downloaders>,
    Path(id): Path<String>,
    headers: HeaderMap,
) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if state.demo_mode {
        return demo_error(language);
    }
    let outcome = state
        .edit_config(move |document| {
            document
                .get_mut("client")
                .and_then(Yaml::as_mapping_mut)
                .and_then(|clients| clients.remove(id))
                .map(|_| ())
                .ok_or(Rejection::Missing)
        })
        .await;
    match outcome {
        Ok(()) => result(
            language,
            StatusCode::OK,
            "DOWNLOADER_API_REMOVE_SAVED",
            true,
        ),
        Err(EditError::Rejected(Rejection::Missing)) => result(
            language,
            StatusCode::NOT_FOUND,
            "DOWNLOADER_API_REMOVE_NOT_EXISTS",
            false,
        ),
        _ => result(
            language,
            StatusCode::INTERNAL_SERVER_ERROR,
            "WEBAPI_INTERNAL_ERROR",
            false,
        ),
    }
}

async fn test(State(state): State<Downloaders>, headers: HeaderMap, body: Bytes) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if state.demo_mode {
        return demo_error(language);
    }
    let Ok((id, draft)) = prepare(None, &body) else {
        return result(
            language,
            StatusCode::BAD_REQUEST,
            "DOWNLOADER_API_ADD_FAILURE",
            false,
        );
    };
    let path = state.config_path();
    let runtime = tokio::task::spawn_blocking(move || {
        let config: MainConfig = read_yaml(&path)?;
        let proxy = if draft.config.kind == "transmission" {
            config.proxy_policy()?
        } else {
            config.downloader_proxy_policy(&state.data_dir)?
        };
        Ok::<_, anyhow::Error>((id, draft, config, proxy))
    })
    .await;
    let Ok(Ok((id, draft, config, proxy))) = runtime else {
        return result(
            language,
            StatusCode::INTERNAL_SERVER_ERROR,
            "WEBAPI_INTERNAL_ERROR",
            false,
        );
    };
    let attempt = Downloader::configure_with_proxy(
        &draft.config,
        &id,
        Duration::from_secs(5),
        &config.server.prefix,
        false,
        &AddressPolicy::default(),
        &proxy,
    );
    let adapter = match attempt {
        Ok(Some(downloader)) => downloader,
        Ok(None) => {
            return result(
                language,
                StatusCode::OK,
                "DOWNLOADER_API_TEST_BYPASS_PAUSED",
                true,
            );
        }
        Err(_) => {
            return result(
                language,
                StatusCode::BAD_REQUEST,
                "DOWNLOADER_API_ADD_FAILURE",
                false,
            );
        }
    };
    match adapter.client.check_credentials().await {
        Ok(()) => result(language, StatusCode::OK, "DOWNLOADER_API_TEST_OK", true),
        Err(error) => {
            // Adapter display errors omit response bodies, tokens and request URLs. Do not unwrap
            // the source error chain into the HTTP response.
            let message = language.render(
                &json!({"key": "DOWNLOADER_LOGIN_EXCEPTION", "params": [error.to_string()]}),
            );
            (
                StatusCode::OK,
                Json(json!({"success": false, "message": message, "data": null})),
            )
                .into_response()
        }
    }
}

fn prepare(path_id: Option<String>, body: &[u8]) -> Result<(String, Prepared), Rejection> {
    let draft: Draft = serde_json::from_slice(body).map_err(|_| Rejection::Invalid)?;
    let id = path_id
        .or(draft.id)
        .filter(|id| !id.trim().is_empty())
        .ok_or(Rejection::Invalid)?;
    let mut yaml =
        pbh_web::downloader_settings_from_json(&draft.config).map_err(|_| Rejection::Invalid)?;
    let kind = yaml["type"]
        .as_str()
        .ok_or(Rejection::Invalid)?
        .to_ascii_lowercase();
    yaml["type"] = match kind.as_str() {
        "deluge" => "raccoonfink/deluge".into(),
        _ => kind.into(),
    };
    let config = serde_yaml_ng::from_value(yaml.clone()).map_err(|_| Rejection::Invalid)?;
    Ok((id, Prepared { yaml, config }))
}

fn save_entry(
    document: &mut Mapping,
    id: &str,
    draft: Prepared,
    operation: Save,
) -> Result<(), Rejection> {
    let default = MainConfig::default();
    let prefix = document
        .get("server")
        .and_then(|server| server.get("prefix"))
        .and_then(Yaml::as_str)
        .unwrap_or(&default.server.prefix);
    Downloader::configure(
        &draft.config,
        id,
        Duration::from_secs(5),
        prefix,
        false,
        &AddressPolicy::default(),
    )
    .map_err(|_| Rejection::Invalid)?;

    let clients = document
        .entry(Yaml::from("client"))
        .or_insert_with(|| Yaml::Mapping(Mapping::new()));
    if clients.is_null() {
        *clients = Yaml::Mapping(Mapping::new());
    }
    let clients = clients.as_mapping_mut().ok_or(Rejection::Invalid)?;
    let name = draft.yaml["name"].as_str().unwrap_or(id);
    let id_key = Yaml::from(id);
    if matches!(operation, Save::Create) && clients.contains_key(&id_key) {
        return Err(Rejection::Duplicate);
    }
    if clients.iter().any(|(other_id, entry)| {
        other_id.as_str().is_some_and(|other_id| {
            other_id != id && entry["name"].as_str().unwrap_or(other_id) == name
        })
    }) {
        return Err(Rejection::Duplicate);
    }
    clients.insert(id_key, draft.yaml);
    Ok(())
}

fn demo_error(language: BanLanguage) -> Response {
    result(
        language,
        StatusCode::BAD_REQUEST,
        "DEMO_MODE_OPERATION_NOT_PERMITTED",
        false,
    )
}

fn result(language: BanLanguage, status: StatusCode, key: &str, success: bool) -> Response {
    let message = language.render(&json!({"key": key}));
    (
        status,
        Json(json!({"success": success, "message": message, "data": null})),
    )
        .into_response()
}
