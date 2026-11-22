use std::collections::BTreeSet;
use std::net::IpAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use axum::body::to_bytes;
use axum::extract::{Request, State};
use axum::http::{Method, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router};
use pbh_biglybt::{BiglyBt, Config, Error, SpeedLimits, TrafficStatistics};
use reqwest::Url;
use serde_json::{Value, json};
use tokio::net::TcpListener;
use tokio::sync::Notify;
use tokio::task::JoinHandle;

#[derive(Default)]
struct Fixture {
    legacy: bool,
    oversized_snapshot: bool,
    filter_disabled: AtomicBool,
    bans: Mutex<RemoteBans>,
    connector: Notify,
    calls: Mutex<Vec<Call>>,
}

struct RemoteBans {
    instance: &'static str,
    revision: u64,
    rules: BTreeSet<String>,
}

impl Default for RemoteBans {
    fn default() -> Self {
        Self {
            instance: "fixture-1",
            revision: 0,
            rules: BTreeSet::new(),
        }
    }
}

#[derive(Debug)]
struct Call {
    method: Method,
    path: String,
    query: Option<String>,
    content_type: String,
    body: Vec<u8>,
}

struct Server {
    endpoint: String,
    state: Arc<Fixture>,
    task: JoinHandle<()>,
}

impl Drop for Server {
    fn drop(&mut self) {
        self.task.abort();
    }
}

impl Server {
    async fn start(fixture: Fixture) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let endpoint = format!("http://{}/plugin/", listener.local_addr().unwrap());
        let state = Arc::new(fixture);
        let router = Router::new()
            .fallback(handle)
            .with_state(Arc::clone(&state));
        let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
        Self {
            endpoint,
            state,
            task,
        }
    }

    fn config(&self) -> Config {
        Config {
            endpoint: self.endpoint.clone(),
            token: "fixture-secret".into(),
            ..Config::default()
        }
    }
}

async fn handle(State(state): State<Arc<Fixture>>, request: Request) -> Response {
    if request.headers()[header::AUTHORIZATION] != "Bearer fixture-secret" {
        return StatusCode::UNAUTHORIZED.into_response();
    }
    let url = Url::parse(&format!("http://localhost{}", request.uri())).unwrap();
    let method = request.method().clone();
    let operation = url.path().strip_prefix("/plugin/").unwrap();
    let content_type = request.headers()[header::CONTENT_TYPE]
        .to_str()
        .unwrap()
        .to_owned();
    let body = to_bytes(request.into_body(), 65_536)
        .await
        .unwrap()
        .to_vec();
    state.calls.lock().unwrap().push(Call {
        method: method.clone(),
        path: operation.to_owned(),
        query: url.query().map(str::to_owned),
        content_type,
        body: body.clone(),
    });

    match (method, operation) {
        (Method::GET, "metadata") if state.legacy => {
            Json(json!({"pluginVersion": "1.9.9", "applicationName": "BiglyBT"})).into_response()
        }
        (Method::GET, "metadata") => {
            Json(json!({"pluginVersion": "2.1.0", "protocol": 2})).into_response()
        }
        (Method::POST, "setconnector") => {
            let connector: Value = serde_json::from_slice(&body).unwrap();
            assert_eq!(connector["software"], "PeerBanHelper");
            assert!(connector["version"].is_string());
            assert!(connector["abbrev"].is_string());
            state.connector.notify_one();
            StatusCode::OK.into_response()
        }
        (Method::GET, "v2/bans") => {
            let status = state.bans.lock().unwrap();
            Json(ban_status(&status, &state)).into_response()
        }
        (method @ (Method::PATCH | Method::PUT), "v2/bans") => {
            let update: Value = serde_json::from_slice(&body).unwrap();
            let mut remote = state.bans.lock().unwrap();
            if method == Method::PATCH
                && (update["instance"] != remote.instance || update["revision"] != remote.revision)
            {
                return StatusCode::CONFLICT.into_response();
            }
            let added: Vec<String> = serde_json::from_value(update["added"].clone()).unwrap();
            let removed: Vec<String> = serde_json::from_value(update["removed"].clone()).unwrap();
            let mut desired = if method == Method::PUT {
                assert!(update.get("instance").is_none() && update.get("revision").is_none());
                assert!(removed.is_empty());
                BTreeSet::new()
            } else {
                remote.rules.clone()
            };
            desired.extend(added);
            for address in removed {
                desired.remove(&address);
            }
            if desired != remote.rules {
                remote.revision += 1;
                remote.rules = desired;
            }
            Json(ban_status(&remote, &state)).into_response()
        }
        (Method::GET, "v2/snapshot") if state.oversized_snapshot => {
            StatusCode::INSUFFICIENT_STORAGE.into_response()
        }
        (Method::GET, "v2/snapshot") => Json(json!([{
            "download": download(false, "public"), "peers": peers(),
        }]))
        .into_response(),
        (Method::GET, "downloads") => Json(json!([
            download(false, "public"),
            download(true, "private")
        ]))
        .into_response(),
        (Method::GET, "download/public/peers") => Json(json!({"peers": peers()})).into_response(),
        (Method::GET, "download/gone/peers") => StatusCode::NOT_FOUND.into_response(),
        (Method::GET, "statistics") => {
            Json(json!({"overallDataBytesSent": 2400, "overallDataBytesReceived": 1800}))
                .into_response()
        }
        (Method::GET, "speedlimiter") => {
            Json(json!({"upload": 512, "download": 1024})).into_response()
        }
        (Method::GET, "listenport") => Json(json!({"port": 51413})).into_response(),
        (Method::POST, "speedlimiter" | "listenport")
        | (Method::PATCH, "download/public/trackers") => StatusCode::OK.into_response(),
        (other, path) => panic!("unexpected BiglyBT request {other} {path}"),
    }
}

fn ban_status(remote: &RemoteBans, fixture: &Fixture) -> Value {
    json!({
        "instance": remote.instance,
        "revision": remote.revision,
        "filterEnabled": !fixture.filter_disabled.load(Ordering::Relaxed),
    })
}

fn download(private: bool, hash: &str) -> Value {
    json!({
        "name": "BiglyBT fixture",
        "torrent": {"infoHash": hash, "size": 4096, "privateTorrent": private},
        "stats": {"remainingBytes": 1024, "completedInThousandNotation": 750,
                  "rtDownloadSpeed": 0, "rtUploadSpeed": 0},
        "trackers": [["udp://one.example", "udp://backup.example"], ["https://two.example"]]
    })
}

fn peers() -> Value {
    json!([
        {"peerId": "2d464600ff", "ip": "/::ffff:192.0.2.113", "port": 51413, "client": "Fixture",
         "state": 30, "percentDoneInThousandNotation": 250, "interesting": true, "interested": true,
         "choked": true, "incoming": true, "peerSource": "PeerExchange", "protocol": "uTP",
         "useCrypto": true, "stats": {"rtDownloadSpeed": 80, "rtUploadSpeed": 100,
                                     "totalReceived": 120, "totalSent": 140}},
        {"ip": "64:ff9b::c000:271", "port": 0, "state": 30, "stats": {}},
        {"peerId": "malformed", "ip": "192.0.2.5", "stats": {}},
        {"peerId": "0000", "ip": "", "stats": {}},
        {"peerId": "0000", "ip": "192.0.2.6", "stats": null},
        null,
    ])
}

#[tokio::test]
async fn reads_protocol_two_snapshot_including_idle_peer_without_connector_write() {
    let server = Server::start(Fixture::default()).await;
    let client = BiglyBt::read_only(Config {
        ignore_private: true,
        ..server.config()
    })
    .unwrap();
    client.login().await.unwrap();
    let snapshots = client.active_torrents().await.unwrap();
    assert_eq!(snapshots.len(), 1);
    let torrent = &snapshots[0];
    assert_eq!((torrent.size, torrent.completed_size), (4096, 3072));
    assert_eq!(torrent.progress, 0.75);
    assert_eq!(torrent.upload_speed, 0);
    assert_eq!(torrent.trackers.len(), 2);
    let peers = torrent.peers.as_ref().unwrap();
    assert_eq!(peers.len(), 2);
    assert_eq!(peers[0].raw_address, "::ffff:192.0.2.113");
    assert_eq!(peers[0].ip, "192.0.2.113".parse::<IpAddr>().unwrap());
    assert_eq!(peers[0].peer_id.as_deref(), Some("-FF\0ÿ"));
    assert_eq!(peers[0].flags.as_deref(), Some("d U I X E P"));
    assert_eq!(peers[0].protocol.as_deref(), Some("uTP"));
    assert_eq!(peers[0].progress, 0.25);
    assert!(!peers[0].handshaking);
    assert_eq!(peers[1].ip, peers[0].ip);
    assert!(peers[1].handshaking);
    assert!(matches!(
        client.sync_bans(&[], true).await,
        Err(Error::ReadOnly)
    ));
    assert!(matches!(
        client.set_listen_port(9000).await,
        Err(Error::ReadOnly)
    ));
    assert_eq!(client.listen_port().await.unwrap(), 51413);

    let calls = server.state.calls.lock().unwrap();
    assert!(calls.iter().all(|call| call.method == Method::GET));
    assert_eq!(calls[2].query.as_deref(), Some("includePrivate=false"));
}

#[tokio::test]
async fn falls_back_to_small_requests_after_oversized_batch() {
    let server = Server::start(Fixture {
        oversized_snapshot: true,
        ..Fixture::default()
    })
    .await;
    let client = BiglyBt::read_only(Config {
        ignore_private: true,
        ..server.config()
    })
    .unwrap();
    client.login().await.unwrap();
    let torrents = client.active_torrents().await.unwrap();
    assert_eq!(torrents.len(), 1);
    assert!(torrents[0].peers.is_none());
    assert_eq!(client.peers(&torrents[0].hash).await.unwrap().len(), 2);
    assert!(client.peers("gone").await.unwrap().is_empty());
    assert_eq!(client.all_torrents().await.unwrap().len(), 2);

    let calls = server.state.calls.lock().unwrap();
    assert_eq!(
        calls[3].query.as_deref(),
        Some("filter=4&filter=5&filter=8")
    );
}

#[tokio::test]
async fn synchronizes_deltas_and_recovers_conflict_and_remote_drift() {
    let server = Server::start(Fixture::default()).await;
    let client = BiglyBt::new(server.config()).unwrap();
    client.login().await.unwrap();
    tokio::time::timeout(Duration::from_secs(3), server.state.connector.notified())
        .await
        .unwrap();
    let v4 = "192.0.2.113".parse().unwrap();
    let v6 = "2001:db8:0:1::2".parse().unwrap();
    client.sync_bans(&[v4], true).await.unwrap();
    client.sync_bans(&[v6], false).await.unwrap();
    {
        let mut remote = server.state.bans.lock().unwrap();
        remote.instance = "fixture-restarted";
        remote.revision = 0;
        remote.rules.clear();
    }
    client.sync_bans(&[v4], false).await.unwrap();
    {
        let mut remote = server.state.bans.lock().unwrap();
        remote.revision += 1;
        remote.rules.insert("203.0.113.55".into());
    }
    client.login().await.unwrap();
    assert_eq!(
        server.state.bans.lock().unwrap().rules,
        BTreeSet::from(["192.0.2.113".into(), "::ffff:c000:271".into()]),
    );
    let calls = server.state.calls.lock().unwrap();
    let writes: Vec<_> = calls
        .iter()
        .filter(|call| call.path == "v2/bans" && call.method != Method::GET)
        .collect();
    assert_eq!(
        writes
            .iter()
            .map(|call| call.method.clone())
            .collect::<Vec<_>>(),
        [
            Method::PUT,
            Method::PATCH,
            Method::PATCH,
            Method::PUT,
            Method::PUT
        ]
    );
    let patch = serde_json::from_slice::<Value>(&writes[1].body).unwrap();
    assert_eq!(
        patch,
        json!({
            "instance": "fixture-1", "revision": 1,
            "added": ["2001:db8:0:1::2", "2001:db8::/52"],
            "removed": ["192.0.2.113", "::ffff:c000:271"],
        })
    );
}

#[tokio::test]
async fn rejects_legacy_and_surfaces_disabled_filter() {
    let legacy = Server::start(Fixture {
        legacy: true,
        ..Fixture::default()
    })
    .await;
    let client = BiglyBt::new(legacy.config()).unwrap();
    assert!(matches!(
        client.login().await,
        Err(Error::UnsupportedProtocol)
    ));
    assert_eq!(legacy.state.calls.lock().unwrap().len(), 1);

    let disabled = Server::start(Fixture {
        filter_disabled: AtomicBool::new(true),
        ..Fixture::default()
    })
    .await;
    let client = BiglyBt::new(disabled.config()).unwrap();
    client.login().await.unwrap();
    assert!(!client.ban_status().await.unwrap().filter_enabled);
    assert!(matches!(
        client.sync_bans(&[], true).await,
        Err(Error::FilterDisabled)
    ));
}

#[tokio::test]
async fn uses_existing_statistics_settings_and_tracker_routes() {
    let server = Server::start(Fixture::default()).await;
    let client = BiglyBt::new(server.config()).unwrap();
    assert_eq!(
        client.traffic_statistics().await.unwrap(),
        TrafficStatistics {
            uploaded: 2400,
            downloaded: 1800
        }
    );
    assert_eq!(
        client.speed_limits().await.unwrap(),
        SpeedLimits {
            upload: 512,
            download: 1024
        }
    );
    client
        .set_speed_limits(SpeedLimits {
            upload: 100,
            download: -1,
        })
        .await
        .unwrap();
    client.set_listen_port(6882).await.unwrap();
    client
        .replace_trackers(
            "public",
            &[
                vec!["udp://one.example".into()],
                vec!["https://two.example".into()],
            ],
        )
        .await
        .unwrap();
    let calls = server.state.calls.lock().unwrap();
    assert_eq!(
        serde_json::from_slice::<Value>(&calls[2].body).unwrap(),
        json!({"upload": 100, "download": -1})
    );
    assert_eq!(
        serde_json::from_slice::<Value>(&calls[3].body).unwrap(),
        json!({"port": 6882})
    );
    assert_eq!(calls[4].content_type, "text/plain");
    assert_eq!(calls[4].body, b"udp://one.example\n\nhttps://two.example");
}
