//! SwarmTrackingModule's read-only HTTP endpoints.

use std::net::IpAddr;
use std::sync::Arc;

use axum::extract::{RawQuery, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use pbh_storage::{
    StorageError, TrackedSwarmOrder, TrackedSwarmPage, TrackedSwarmQuery, TrackedSwarmRecord,
};
use serde_json::{Value, json};
use tokio::sync::Mutex;

use crate::ban_api::BanLanguage;
use crate::storage::Database;
use crate::web::ScanState;

pub(crate) fn router(state: ScanState) -> Router {
    routes(state.database)
}

fn routes(database: Arc<Mutex<Database>>) -> Router {
    Router::new()
        .route("/api/modules/swarm-tracking", get(count))
        .route("/api/modules/swarm-tracking/details", get(details))
        .with_state(database)
}

async fn count(State(database): State<Arc<Mutex<Database>>>, headers: HeaderMap) -> Response {
    let result = database.lock().await.swarm_count().await;
    match result {
        Ok(count) => Json(json!({"trackedSwarmSize": count})).into_response(),
        Err(error) => storage_error(error, &headers),
    }
}

async fn details(
    State(database): State<Arc<Mutex<Database>>>,
    headers: HeaderMap,
    RawQuery(raw): RawQuery,
) -> Response {
    let query = match query(raw.as_deref()) {
        Ok(query) => query,
        Err(QueryError::BadRequest(message)) => {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({"success":false,"message":message,"data":message})),
            )
                .into_response();
        }
        Err(QueryError::UnknownSort) => return internal_error(&headers),
    };
    let result = database.lock().await.swarm_details(&query).await;
    match result {
        Ok(page) => Json(page_json(&page)).into_response(),
        Err(error) => storage_error(error, &headers),
    }
}

fn storage_error(error: StorageError, headers: &HeaderMap) -> Response {
    eprintln!("Unable to read tracked swarm: {error}");
    internal_error(headers)
}

fn internal_error(headers: &HeaderMap) -> Response {
    let message =
        BanLanguage::from_headers(headers).render(&json!({"key":"WEBAPI_INTERNAL_ERROR"}));
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(json!({"success":false,"message":message,"data":null})),
    )
        .into_response()
}

enum QueryError {
    BadRequest(String),
    UnknownSort,
}

fn query(raw: Option<&str>) -> Result<TrackedSwarmQuery, QueryError> {
    let mut result = TrackedSwarmQuery::default();
    let mut page = None;
    let mut size = None;
    let mut orders = Vec::new();
    for (key, value) in form_urlencoded::parse(raw.unwrap_or_default().as_bytes()) {
        match key.as_ref() {
            "page" if page.is_none() => page = Some(value),
            "pageSize" if size.is_none() => size = Some(value),
            "orderBy" if !is_empty_order(&value) => orders.push(value),
            _ => {}
        }
    }
    result.page = page
        .as_deref()
        .map(parse_number)
        .transpose()?
        .unwrap_or(result.page);
    result.size = size
        .as_deref()
        .map(parse_number)
        .transpose()?
        .unwrap_or(result.size);
    result.orders = orders
        .iter()
        .map(|term| parse_order(term))
        .collect::<Result<_, _>>()?;
    Ok(result)
}

// Java String.split discards trailing empty pieces. A bar-only term is ignored.
fn is_empty_order(value: &str) -> bool {
    !value.is_empty() && value.bytes().all(|byte| byte == b'|')
}

fn parse_number(value: &str) -> Result<i64, QueryError> {
    value
        .parse()
        .map_err(|_| QueryError::BadRequest(format!("For input string: \"{value}\"")))
}

fn parse_order(term: &str) -> Result<TrackedSwarmOrder, QueryError> {
    let field = term.split('|').next().unwrap_or_default();
    let safe = field.split('.').all(|part| {
        !part.is_empty()
            && part
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
    });
    if !safe {
        return Err(QueryError::BadRequest(format!(
            "Detected non-valid field name pattern in input: {field}"
        )));
    }
    let unqualified = match term.split_once('.') {
        Some((table, suffix)) if table.eq_ignore_ascii_case("tracked_swarm") => suffix,
        _ => term,
    };
    // Java's SQL builder reports a legal identifier with no column as an internal error.
    // Keep the status while restricting emitted SQL to the storage allowlist.
    TrackedSwarmOrder::parse(unqualified).map_err(|_| QueryError::UnknownSort)
}

fn page_json(page: &TrackedSwarmPage) -> Value {
    let results = page.records.iter().map(row_json).collect::<Vec<_>>();
    json!({
        "success": true,
        "message": null,
        "data": {
            "page": page.page, "size": page.size, "total": page.total, "results": results,
        },
    })
}

fn row_json(row: &TrackedSwarmRecord) -> Value {
    let ip = match row.ip {
        IpAddr::V4(ip) => ip.to_string(),
        IpAddr::V6(ip) => ip.segments().map(|part| format!("{part:x}")).join(":"),
    };
    json!({
        "id": row.id, "ip": ip, "port": row.port, "infoHash": row.info_hash,
        "torrentIsPrivate": row.torrent_is_private, "torrentSize": row.torrent_size,
        "downloader": row.downloader, "downloaderProgress": row.downloader_progress,
        "peerId": row.peer_id, "clientName": row.client_name, "peerProgress": row.peer_progress,
        "uploaded": row.uploaded, "uploadedOffset": row.uploaded_offset,
        "uploadSpeed": row.upload_speed, "downloaded": row.downloaded,
        "downloadedOffset": row.downloaded_offset, "downloadSpeed": row.download_speed,
        "lastFlags": row.last_flags, "firstTimeSeen": row.first_time_seen_ms,
        "lastTimeSeen": row.last_time_seen_ms, "downloadSpeedMax": row.download_speed_max,
        "uploadSpeedMax": row.upload_speed_max,
    })
}

impl Database {
    pub(crate) async fn swarm_count(&mut self) -> Result<i64, StorageError> {
        match self {
            Self::Sqlite(database) => database.tracked_swarm_count(),
            Self::Postgres(database) => database.tracked_swarm_count().await,
            Self::MySql(database) => database.tracked_swarm_count().await,
        }
    }

    async fn swarm_details(
        &mut self,
        query: &TrackedSwarmQuery,
    ) -> Result<TrackedSwarmPage, StorageError> {
        match self {
            Self::Sqlite(database) => database.tracked_swarm(query),
            Self::Postgres(database) => database.tracked_swarm(query).await,
            Self::MySql(database) => database.tracked_swarm(query).await,
        }
    }
}

#[cfg(test)]
mod tests {
    use pbh_storage::{SqliteStore, TrackedSwarmObservation};

    use super::*;

    #[tokio::test]
    async fn swarm_routes_keep_java_shapes_and_paginate_with_repeated_orders() {
        let directory = tempfile::tempdir().unwrap();
        let mut database =
            Database::Sqlite(SqliteStore::open(directory.path().join("swarm.db")).unwrap());
        let v6 = TrackedSwarmObservation {
            ip: "2001:db8::42".parse().unwrap(),
            port: 6881,
            info_hash: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            torrent_is_private: false,
            torrent_size: 1024,
            downloader: "fixture",
            downloader_progress: 1.0,
            peer_id: None,
            client_name: Some("Fixture peer"),
            peer_progress: 0.5,
            uploaded: 100,
            upload_speed: 9,
            downloaded: 50,
            download_speed: 19,
            last_flags: Some("U"),
            seen_at_ms: 1_700_000_010_521,
        };
        let v4 = TrackedSwarmObservation {
            ip: "192.0.2.1".parse().unwrap(),
            port: 6880,
            last_flags: None,
            ..v6
        };
        database.upsert_tracked_swarm(&[v4, v6]).await.unwrap();
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let base = format!(
            "http://{}/api/modules/swarm-tracking",
            listener.local_addr().unwrap()
        );
        let app = routes(Arc::new(Mutex::new(database)));
        let server = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
        let client = reqwest::Client::builder().no_proxy().build().unwrap();
        let get = |url: String| {
            let client = client.clone();
            async move {
                let response = client.get(url).send().await.unwrap();
                let status = response.status();
                let payload: Value =
                    serde_json::from_slice(&response.bytes().await.unwrap()).unwrap();
                (status, payload)
            }
        };
        let (status, count) = get(base.clone()).await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(count, json!({"trackedSwarmSize":2}));

        let detail = format!("{base}/details");
        let query = "?page=1&page=9&pageSize=1&orderBy=port%7Casc&orderBy=tracked_swarm.port%7Cdescend&orderBy=id";
        let (status, page) = get(format!("{detail}{query}")).await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(
            page,
            json!({
                "success":true,"message":null,
                "data":{"page":1,"size":1,"total":2,"results":[{
                    "id":2,"ip":"2001:db8:0:0:0:0:0:42","port":6881,
                    "infoHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","torrentIsPrivate":false,
                    "torrentSize":1024,"downloader":"fixture","downloaderProgress":1.0,
                    "peerId":null,"clientName":"Fixture peer","peerProgress":0.5,
                    "uploaded":100,"uploadedOffset":100,"uploadSpeed":9,
                    "downloaded":50,"downloadedOffset":50,"downloadSpeed":19,
                    "lastFlags":"U","firstTimeSeen":1_700_000_010_521_i64,"lastTimeSeen":1_700_000_010_521_i64,
                    "downloadSpeedMax":19,"uploadSpeedMax":9
                }]}
            })
        );
        let (_, empty) = get(format!("{detail}?page=3&pageSize=1")).await;
        assert_eq!(
            empty["data"],
            json!({"page":3,"size":1,"total":2,"results":[]})
        );
        let (_, limited) = get(format!("{detail}?pageSize=1100")).await;
        assert_eq!(limited["data"]["size"], 1000);
        let (_, without_count) = get(format!("{detail}?pageSize=-1")).await;
        assert_eq!(without_count["data"]["size"], 1000);
        assert_eq!(without_count["data"]["total"], 0);
        assert_eq!(
            without_count["data"]["results"].as_array().unwrap().len(),
            2
        );
        let (status, invalid) = get(format!("{detail}?page=z")).await;
        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(
            invalid,
            json!({"success":false,"message":"For input string: \"z\"","data":"For input string: \"z\""})
        );
        let (status, bad_page) =
            get(format!("{detail}?orderBy=firstTimeSeen&pageSize=no&page=z")).await;
        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(bad_page, invalid);
        let (status, invalid) = get(format!("{detail}?orderBy=firstTimeSeen")).await;
        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
        assert_eq!(
            invalid,
            json!({"success":false,"message":"An internal server error occurred while processing the WebAPI request, please check the console logs","data":null})
        );
        let (status, invalid) = get(format!("{detail}?orderBy=invalid%20field")).await;
        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(
            invalid["data"],
            "Detected non-valid field name pattern in input: invalid field"
        );
        server.abort();
    }
}
