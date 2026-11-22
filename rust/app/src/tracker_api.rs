//! The legacy bulk tracker replacement endpoint.

use std::collections::BTreeMap;
use std::future::Future;
use std::sync::Arc;

use anyhow::Result;
use axum::extract::{State, rejection::JsonRejection};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router, routing::post};
use futures_util::{StreamExt, stream};
use serde::Deserialize;
use serde_json::json;
use tokio::sync::Mutex;

use crate::ban_api::BanLanguage;
use crate::downloader::Client;

const TRACKER_REQUESTS_IN_FLIGHT: usize = 8;

#[derive(Clone)]
struct Trackers {
    downloaders: Arc<BTreeMap<String, Arc<Client>>>,
    replacement: Arc<Mutex<()>>,
}

#[derive(Deserialize)]
struct ReplaceTracker {
    from: String,
    to: String,
    downloaders: Option<Vec<String>>,
}

pub(crate) fn router(downloaders: BTreeMap<String, Arc<Client>>) -> Router {
    Router::new()
        .route("/api/utilities/replaceTracker", post(replace))
        .with_state(Trackers {
            downloaders: Arc::new(downloaders),
            replacement: Arc::default(),
        })
}

async fn replace(
    State(state): State<Trackers>,
    headers: HeaderMap,
    body: Result<Json<ReplaceTracker>, JsonRejection>,
) -> Response {
    let Ok(Json(request)) = body else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if request.from.is_empty() || request.to.is_empty() {
        return StatusCode::BAD_REQUEST.into_response();
    }

    // Keep each read/replace pass ordered against other replacements made through PBH.
    let _guard = state.replacement.lock().await;
    let mut count = 0;
    for (id, client) in state.downloaders.iter() {
        if request
            .downloaders
            .as_ref()
            .is_some_and(|selected| !selected.is_empty() && !selected.contains(id))
        {
            continue;
        }
        match replace_in_client(client, &request, id).await {
            Ok(completed) => count += completed,
            Err(error) => crate::journal::log_warning!(
                "Unable to read tracker targets from downloader {id}: {error}"
            ),
        }
    }
    let language = BanLanguage::from_headers(&headers);
    let message = language.render(&json!({"key":"UTILITIES_TRACKER_REPLACED","params":[count]}));
    Json(json!({"success":true,"message":message,"data":count})).into_response()
}

async fn replace_in_client(client: &Client, request: &ReplaceTracker, id: &str) -> Result<usize> {
    let from = request.from.as_str();
    let to = request.to.as_str();
    Ok(match client {
        Client::Qbittorrent(client) => {
            client.login().await?;
            apply_to_torrents(id, client.all_torrents().await?, |torrent| async move {
                let mut tiers = client.trackers(&torrent.hash).await?;
                let changed =
                    replace_in_groups(tiers.iter_mut().map(|tier| &mut tier.urls), from, to);
                if changed {
                    client.replace_trackers(&torrent.hash, &tiers).await?;
                }
                Ok(changed)
            })
            .await
        }
        Client::Transmission(client) => {
            client.login().await?;
            apply_to_torrents(id, client.all_torrents().await?, |mut torrent| async move {
                let changed = replace_in_groups(&mut torrent.trackers, from, to);
                if changed {
                    client
                        .replace_trackers(torrent.id, &torrent.trackers)
                        .await?;
                }
                Ok(changed)
            })
            .await
        }
        Client::BiglyBt(client) => {
            client.login().await?;
            apply_to_torrents(id, client.all_torrents().await?, |mut torrent| async move {
                let changed = replace_in_groups(&mut torrent.trackers, from, to);
                if changed {
                    client
                        .replace_trackers(&torrent.id, &torrent.trackers)
                        .await?;
                }
                Ok(changed)
            })
            .await
        }
        Client::Aria2Next(client) => {
            client.login().await?;
            client
                .all_torrents()
                .await?
                .iter()
                .filter(|torrent| {
                    torrent
                        .trackers
                        .iter()
                        .flatten()
                        .any(|url| url.contains(from))
                })
                .count()
        }
        Client::BitComet(client) => {
            client.login().await?;
            apply_to_torrents(id, client.all_torrents().await?, |torrent| async move {
                let tiers = client.trackers(&torrent.id).await?;
                Ok(tiers.iter().flatten().any(|url| url.contains(from)))
            })
            .await
        }
        Client::Deluge(_) => 0,
    })
}

async fn apply_to_torrents<T, F, Work>(id: &str, torrents: Vec<T>, update: F) -> usize
where
    F: FnMut(T) -> Work,
    Work: Future<Output = Result<bool>>,
{
    stream::iter(torrents)
        .map(update)
        .buffer_unordered(TRACKER_REQUESTS_IN_FLIGHT)
        .fold(0, |count, result| async move {
            match result {
                Ok(changed) => count + usize::from(changed),
                Err(error) => {
                    crate::journal::log_warning!(
                        "Trackers could not be updated on downloader {id}: {error}"
                    );
                    count
                }
            }
        })
        .await
}

fn replace_in_groups<'a>(
    groups: impl IntoIterator<Item = &'a mut Vec<String>>,
    from: &str,
    to: &str,
) -> bool {
    let mut changed = false;
    for url in groups.into_iter().flatten() {
        if url.contains(from) {
            *url = url.replace(from, to);
            changed = true;
        }
    }
    changed
}

#[cfg(test)]
mod tests {
    use axum::extract::{OriginalUri, Query};
    use axum::routing::get;
    use serde_json::Value;
    use tokio::net::TcpListener;

    use super::*;

    const ORIGINAL: &str = "https://old.example/announce?fallback=old.example";
    const REPLACED: &str = "https://new.example/announce?fallback=new.example";
    const UNCHANGED: &str = "udp://keep.example/announce";

    #[derive(Clone, Default)]
    struct Fixture {
        qbit_writes: Arc<std::sync::Mutex<Vec<(String, String, String)>>>,
        aria_calls: Arc<std::sync::Mutex<Vec<String>>>,
    }

    #[tokio::test]
    async fn endpoint_replaces_real_tiers_and_preserves_legacy_noop_counts() {
        let fixture = Fixture::default();
        let app = Router::new()
            .route(
                "/api/v2/app/buildInfo",
                get(|| async { Json(json!({"libtorrent":"2.0.11"})) }),
            )
            .route("/api/v2/app/version", get(|| async { "v5.2.1" }))
            .route("/api/v2/app/setPreferences", post(|| async { "Ok." }))
            .route(
                "/api/v2/torrents/properties",
                get(|| async { Json(json!({"is_private":false})) }),
            )
            .route("/api/v2/torrents/info", get(torrents))
            .route("/api/v2/torrents/trackers", get(trackers))
            .route("/api/v2/torrents/removeTrackers", post(track_write))
            .route("/api/v2/torrents/addTrackers", post(track_write))
            .route("/aria", post(aria_rpc))
            .with_state(fixture.clone());
        let (backend, backend_task) = serve(app).await;
        let qbittorrent = pbh_qbittorrent::Qbittorrent::new(pbh_qbittorrent::Config {
            endpoint: backend.clone(),
            ..Default::default()
        })
        .unwrap();
        let aria = pbh_aria2next::Aria2Next::new(pbh_aria2next::Config {
            endpoint: format!("{backend}/aria"),
            ..Default::default()
        })
        .unwrap();
        let (api, api_task) = serve(router(BTreeMap::from([
            (
                "qb".into(),
                Arc::new(Client::Qbittorrent(Box::new(qbittorrent))),
            ),
            ("aria".into(), Arc::new(Client::Aria2Next(Box::new(aria)))),
        ])))
        .await;
        let http = reqwest::Client::builder()
            .no_proxy()
            .timeout(std::time::Duration::from_secs(3))
            .build()
            .unwrap();
        let endpoint = format!("{api}/api/utilities/replaceTracker");

        let invalid = http
            .post(&endpoint)
            .json(&json!({"from": "old.example", "to": ""}))
            .send()
            .await
            .unwrap();
        assert_eq!(invalid.status(), StatusCode::BAD_REQUEST);
        let unknown: Value = http
            .post(&endpoint)
            .json(&json!({"from": "old.example", "to": "new.example", "downloaders":["absent"]}))
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(unknown["data"], 0);
        assert!(fixture.aria_calls.lock().unwrap().is_empty());
        assert!(fixture.qbit_writes.lock().unwrap().is_empty());

        let result: Value = http
            .post(&endpoint)
            .header("Accept-Language", "en-US")
            .json(&json!({"from": "old.example", "to": "new.example", "downloaders":[]}))
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(result["success"], true);
        assert_eq!(result["data"], 2);
        assert_eq!(
            result["message"],
            "Successfully replaced 2 torrent's tracker"
        );
        assert_eq!(
            *fixture.qbit_writes.lock().unwrap(),
            [
                (
                    "removeTrackers".into(),
                    "matched".into(),
                    format!("{ORIGINAL}|{UNCHANGED}")
                ),
                (
                    "addTrackers".into(),
                    "matched".into(),
                    format!("{REPLACED}\n{UNCHANGED}")
                ),
            ]
        );
        assert_eq!(
            *fixture.aria_calls.lock().unwrap(),
            [
                "aria2.getVersion",
                "aria2.tellActive",
                "aria2.tellWaiting",
                "aria2.tellStopped"
            ]
        );
        backend_task.abort();
        api_task.abort();
    }

    async fn serve(router: Router) -> (String, tokio::task::JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let endpoint = format!("http://{}", listener.local_addr().unwrap());
        let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
        (endpoint, task)
    }

    async fn torrents(Query(query): Query<BTreeMap<String, String>>) -> Json<Value> {
        assert!(!query.contains_key("filter"));
        Json(json!([
            {"hash":"matched", "name":"Stopped torrent", "state":"stoppedUP", "has_metadata":true},
            {"hash":"unchanged", "name":"Other torrent", "state":"uploading", "has_metadata":true},
        ]))
    }

    async fn trackers(Query(query): Query<BTreeMap<String, String>>) -> Json<Value> {
        let mut result = vec![json!({"tier":-1,"url":"** [DHT] **"})];
        if query["hash"] == "matched" {
            result.push(json!({"tier":0,"url":ORIGINAL}));
        }
        result.push(json!({"tier":1,"url":UNCHANGED}));
        Json(json!(result))
    }

    async fn track_write(
        State(fixture): State<Fixture>,
        OriginalUri(uri): OriginalUri,
        body: String,
    ) -> &'static str {
        let form = form_urlencoded::parse(body.as_bytes())
            .into_owned()
            .collect::<BTreeMap<_, _>>();
        let operation = uri.path().rsplit('/').next().unwrap().to_owned();
        fixture.qbit_writes.lock().unwrap().push((
            operation,
            form["hash"].clone(),
            form["urls"].clone(),
        ));
        "Ok."
    }

    async fn aria_rpc(State(fixture): State<Fixture>, Json(request): Json<Value>) -> Json<Value> {
        let method = request["method"].as_str().unwrap();
        fixture.aria_calls.lock().unwrap().push(method.into());
        let payload = match method {
            "aria2.getVersion" => {
                json!({"version":"3.0.8","product":"aria2-next","rpcVersion":"1.1.0"})
            }
            "aria2.tellActive" => {
                json!([{"gid":"1","bittorrent":{"info":{"name":"Torrent"},"announceList":[[ORIGINAL]]}}])
            }
            "aria2.tellWaiting" | "aria2.tellStopped" => json!([]),
            other => panic!("unexpected tracker write or other RPC: {other}"),
        };
        Json(json!({"jsonrpc":"2.0","id":request["id"],"result":payload}))
    }
}
