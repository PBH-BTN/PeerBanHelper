use std::net::IpAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use axum::extract::State;
use axum::http::{HeaderMap, header};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router, routing::post};
use pbh_deluge::{Config, Deluge, Error, SpeedLimits, TrafficStatistics};
use serde_json::{Value, json};
use tokio::net::TcpListener;
use tokio::task::JoinHandle;

const METHODS: [&str; 4] = [
    "peerbanhelperadapter.replace_blocklist",
    "peerbanhelperadapter.unban_ips",
    "peerbanhelperadapter.get_active_torrents_info",
    "peerbanhelperadapter.ban_ips",
];

#[derive(Default)]
struct Fixture {
    omitted_method: Option<&'static str>,
    reject_increment: AtomicBool,
    calls: Mutex<Vec<Value>>,
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
        let endpoint = format!("http://{}/deluge/", listener.local_addr().unwrap());
        let state = Arc::new(fixture);
        let router = Router::new()
            .route("/deluge/api", post(handle))
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
            rpc_url: "/api".into(),
            password: "fixture-pass".into(),
            ..Config::default()
        }
    }
}

async fn handle(
    State(state): State<Arc<Fixture>>,
    headers: HeaderMap,
    Json(request): Json<Value>,
) -> Response {
    let id = request["id"].as_u64().unwrap();
    let method = request["method"].as_str().unwrap();
    assert!(request["params"].is_array());
    state.calls.lock().unwrap().push(request.clone());
    if method == "auth.login" {
        let accepted = request["params"] == json!(["fixture-pass"]);
        if accepted {
            return (
                [(header::SET_COOKIE, "_session_id=fixture; Path=/deluge")],
                Json(json!({"id": id, "result": true, "error": null})),
            )
                .into_response();
        }
        return Json(json!({"id": id, "result": false, "error": null})).into_response();
    }
    assert_eq!(headers[header::COOKIE], "_session_id=fixture");
    let result = match method {
        "system.listMethods" => json!(
            METHODS
                .into_iter()
                .filter(|name| Some(*name) != state.omitted_method)
                .collect::<Vec<_>>()
        ),
        "peerbanhelperadapter.get_active_torrents_info" => json!([{
            "id": "deluge-torrent-1",
            "info_hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "name": "Deluge fixture",
            "size": 123456,
            "completed_size": 1024,
            "progress": 80.0,
            "upload_payload_rate": 50,
            "download_payload_rate": 30,
            "priv": true,
            "peers": [
                {
                    "ip": "::ffff:192.0.2.113", "port": 51413, "client_name": "Fixture/1.0",
                    "peer_id": "2d464630303031ff2d3939", "flags": 13, "source": 6,
                    "payload_up_speed": 16, "payload_down_speed": 24,
                    "total_upload": 192, "total_download": 256, "progress": 75.0
                },
                {
                    "ip": "", "port": 0, "peer_id": "", "flags": 0, "source": 0,
                    "payload_up_speed": 0, "payload_down_speed": 0,
                    "total_upload": 0, "total_download": 0, "progress": 0.0
                },
            ]
        }]),
        "peerbanhelperadapter.ban_ips" if state.reject_increment.swap(false, Ordering::Relaxed) => {
            return Json(json!({
                "id": id, "result": null, "error": {"code": 12, "message": "fixture-pass must not be printed"}
            })).into_response();
        }
        "peerbanhelperadapter.ban_ips" | "peerbanhelperadapter.replace_blocklist" => json!({}),
        "peerbanhelperadapter.get_blocklist" => json!({"size": 1, "ips": ["192.0.2.113"]}),
        "peerbanhelperadapter.get_session_totals" => {
            json!({"total_payload_upload": 1200, "total_payload_download": 1800})
        }
        "core.get_config" => json!({"max_download_speed": 8.0, "max_upload_speed": -1.0}),
        "core.set_config" => Value::Null,
        _ => panic!("unexpected Deluge RPC: {method}"),
    };
    Json(json!({"id": id, "result": result, "error": null})).into_response()
}

#[tokio::test]
async fn observes_snapshots_and_protocol_metadata_without_mutation() {
    let server = Server::start(Fixture::default()).await;
    let client = Deluge::read_only(Config {
        ignore_private: true,
        ..server.config()
    })
    .unwrap();
    client.login().await.unwrap();
    let torrents = client.active_torrents().await.unwrap();
    assert_eq!(torrents.len(), 1);
    let torrent = &torrents[0];
    assert_eq!(torrent.id, "deluge-torrent-1");
    assert_eq!(torrent.name, "Deluge fixture");
    assert_eq!((torrent.size, torrent.completed_size), (123456, 1024));
    assert_eq!(torrent.progress, 0.8);
    assert!(torrent.private);
    assert_eq!(torrent.peers.len(), 1);
    let peer = &torrent.peers[0];
    assert_eq!(peer.ip, "192.0.2.113".parse::<IpAddr>().unwrap());
    assert_eq!(peer.raw_address, "::ffff:192.0.2.113");
    assert_eq!(peer.peer_id.as_deref(), Some("-FF0001ÿ"));
    assert_eq!(peer.flags.as_deref(), Some("d U I H X"));
    assert_eq!(peer.progress, 0.75);
    assert_eq!((peer.uploaded, peer.downloaded), (192, 256));
    assert_eq!(client.all_torrents().await.unwrap(), torrents);
    assert_eq!(client.banned_ips().await.unwrap(), ["192.0.2.113"]);
    assert_eq!(
        client.traffic_statistics().await.unwrap(),
        TrafficStatistics {
            uploaded: 1200,
            downloaded: 1800
        }
    );
    assert_eq!(
        client.speed_limits().await.unwrap(),
        SpeedLimits {
            upload: -1024,
            download: 8192
        }
    );
    assert!(matches!(
        client.replace_bans(&[]).await,
        Err(Error::ReadOnly)
    ));
    assert!(matches!(client.add_bans(&[]).await, Err(Error::ReadOnly)));
    assert!(matches!(
        client
            .set_speed_limits(SpeedLimits {
                upload: 0,
                download: 0
            })
            .await,
        Err(Error::ReadOnly)
    ));
    let calls = server.state.calls.lock().unwrap();
    for (id, call) in calls.iter().enumerate() {
        assert_eq!(call["id"], id);
    }
    assert_eq!(calls.len(), 7);
}

#[tokio::test]
async fn requires_plugin_methods_including_unban() {
    let server = Server::start(Fixture {
        omitted_method: Some("peerbanhelperadapter.unban_ips"),
        ..Fixture::default()
    })
    .await;
    let client = Deluge::new(server.config()).unwrap();
    assert!(matches!(
        client.login().await,
        Err(Error::MissingMethod {
            method: "peerbanhelperadapter.unban_ips"
        })
    ));
}

#[tokio::test]
async fn reconciles_failed_increment_and_full_unban() {
    let server = Server::start(Fixture {
        reject_increment: AtomicBool::new(true),
        ..Fixture::default()
    })
    .await;
    let client = Deluge::new(server.config()).unwrap();
    client.login().await.unwrap();
    let addresses = [
        "192.0.2.113".parse().unwrap(),
        "2001:db8:0:1::2".parse().unwrap(),
    ];
    let err = client
        .sync_bans(&addresses, &addresses[..1], false, false)
        .await
        .unwrap_err();
    assert!(matches!(err, Error::Rejected { code: Some(12), .. }));
    assert!(!format!("{err:?}").contains("fixture-pass"));
    client
        .sync_bans(&addresses, &addresses[..1], false, true)
        .await
        .unwrap();
    client.sync_bans(&[], &[], true, false).await.unwrap();
    client
        .set_speed_limits(SpeedLimits {
            upload: -1,
            download: 3072,
        })
        .await
        .unwrap();

    let calls = server.state.calls.lock().unwrap();
    assert_eq!(calls[2]["method"], "peerbanhelperadapter.ban_ips");
    assert_eq!(
        calls[2]["params"],
        json!([["192.0.2.113", "::ffff:c000:271"]])
    );
    assert_eq!(calls[3]["method"], "peerbanhelperadapter.replace_blocklist");
    assert_eq!(
        calls[3]["params"],
        json!([[
            "192.0.2.113",
            "::ffff:c000:271",
            "2001:db8:0:1::2",
            "2001:db8::/52"
        ]])
    );
    assert_eq!(calls[4]["method"], "peerbanhelperadapter.replace_blocklist");
    assert_eq!(calls[4]["params"], json!([[]]));
    assert_eq!(calls[5]["method"], "core.set_config");
    assert_eq!(
        calls[5]["params"],
        json!([{"max_upload_speed": 0, "max_download_speed": 3}])
    );
}

#[tokio::test]
async fn backs_off_after_repeated_password_rejections() {
    let server = Server::start(Fixture::default()).await;
    let client = Deluge::new(Config {
        password: "wrong".into(),
        ..server.config()
    })
    .unwrap();
    for _ in 0..15 {
        assert!(matches!(client.login().await, Err(Error::Authentication)));
    }
    assert!(matches!(client.login().await, Err(Error::LoginBackoff)));
    assert_eq!(server.state.calls.lock().unwrap().len(), 15);
}
