//! WebUI alert listing, dismissal, and deletion.

use axum::extract::{Path, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::{delete, get, patch, post};
use axum::{Json, Router};
use serde_json::{Value, json};
use time::OffsetDateTime;

use crate::{ban_api::BanLanguage, web::ScanState};

pub(crate) fn router(state: ScanState) -> Router {
    Router::new()
        .route("/api/alerts", get(list))
        .route("/api/alert/{id}/dismiss", patch(dismiss))
        .route("/api/alert/dismissAll", post(dismiss_all))
        .route("/api/alert/{id}", delete(remove))
        .with_state(state)
}

async fn list(State(state): State<ScanState>, headers: HeaderMap) -> Response {
    let language = BanLanguage::from_headers(&headers);
    match state.database.lock().await.unread_alerts().await {
        Ok(alerts) => {
            let data: Vec<_> = alerts
                .into_iter()
                .map(|alert| {
                    json!({
                        "id": alert.id,
                        "createAt": alert.create_at_ms,
                        "readAt": alert.read_at_ms,
                        "level": alert.level.as_str(),
                        "identifier": alert.identifier,
                        "title": language.render(&alert.title),
                        "content": language.render(&alert.content),
                    })
                })
                .collect();
            response(StatusCode::OK, None, json!(data))
        }
        Err(error) => {
            eprintln!("Could not list alerts: {error:#}");
            internal_error(language)
        }
    }
}

async fn dismiss(
    State(state): State<ScanState>,
    headers: HeaderMap,
    Path(id): Path<String>,
) -> Response {
    mutate(state, headers, Mutation::Dismiss(id)).await
}

async fn dismiss_all(State(state): State<ScanState>, headers: HeaderMap) -> Response {
    mutate(state, headers, Mutation::DismissAll).await
}

async fn remove(
    State(state): State<ScanState>,
    headers: HeaderMap,
    Path(id): Path<String>,
) -> Response {
    mutate(state, headers, Mutation::Delete(id)).await
}

enum Mutation {
    Dismiss(String),
    DismissAll,
    Delete(String),
}

async fn mutate(state: ScanState, headers: HeaderMap, mutation: Mutation) -> Response {
    let language = BanLanguage::from_headers(&headers);
    if state.demo_mode {
        let message = language.render(&json!({"key": "DEMO_MODE_OPERATION_NOT_PERMITTED"}));
        return response(StatusCode::BAD_REQUEST, Some(&message), Value::Null);
    }
    let now_ms = (OffsetDateTime::now_utc().unix_timestamp_nanos() / 1_000_000) as i64;
    let mut database = state.database.lock().await;
    let result = match mutation {
        Mutation::Dismiss(id) => {
            let Ok(id) = id.parse::<i64>() else {
                return internal_error(language);
            };
            database.mark_alert_read(id, now_ms).await.map(|exists| {
                if exists {
                    (StatusCode::OK, "OK")
                } else {
                    (StatusCode::NOT_FOUND, "Alert not exists or expired")
                }
            })
        }
        Mutation::DismissAll => database
            .mark_all_alerts_read(now_ms)
            .await
            .map(|_| (StatusCode::OK, "OK!")),
        Mutation::Delete(id) => {
            let Ok(id) = id.parse::<i64>() else {
                return internal_error(language);
            };
            database
                .delete_alert(id)
                .await
                .map(|_| (StatusCode::OK, "OK"))
        }
    };
    match result {
        Ok((status, message)) => response(status, Some(message), Value::Null),
        Err(error) => {
            eprintln!("Could not update alerts: {error:#}");
            internal_error(language)
        }
    }
}

fn internal_error(language: BanLanguage) -> Response {
    let message = language.render(&json!({"key": "WEBAPI_INTERNAL_ERROR"}));
    response(
        StatusCode::INTERNAL_SERVER_ERROR,
        Some(&message),
        Value::Null,
    )
}

fn response(status: StatusCode, message: Option<&str>, data: Value) -> Response {
    (
        status,
        Json(json!({ "success": status.is_success(), "message": message, "data": data })),
    )
        .into_response()
}
