use std::net::IpAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use axum::extract::State;
use axum::http::{HeaderMap, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router, routing::post};
use pbh_aria2next::{Aria2Next, BlocklistRevision, Config, Error, SpeedLimits};
use serde_json::{Value, json};
use tokio::net::TcpListener;
use tokio::task::JoinHandle;

const FIXTURE_TOKEN: &str = "fixture-secret";

#[derive(Default)]
struct Fixture {
    vanilla_product: bool,
    no_secret: bool,
    reject_auth: bool,
    reject_blocklist: AtomicBool,
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
        let endpoint = format!("http://{}/seedbox/jsonrpc/", listener.local_addr().unwrap());
        let state = Arc::new(fixture);
        let router = Router::new()
            .route("/seedbox/jsonrpc", post(handle))
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
            token: if self.state.no_secret {
                String::new()
            } else {
                FIXTURE_TOKEN.into()
            },
            ..Config::default()
        }
    }
}

async fn handle(
    State(state): State<Arc<Fixture>>,
    headers: HeaderMap,
    Json(request): Json<Value>,
) -> Response {
    assert_eq!(request["jsonrpc"], "2.0");
    assert!(uuid::Uuid::parse_str(request["id"].as_str().unwrap()).is_ok());
    let method = request["method"].as_str().unwrap();
    let token = if state.no_secret { "" } else { FIXTURE_TOKEN };
    let authorization = format!("token:{token}");
    assert_eq!(headers[header::AUTHORIZATION], authorization);
    assert_eq!(headers[header::CONTENT_TYPE], "application/json");
    let params = request["params"].as_array().unwrap();
    let args = if state.no_secret {
        params.as_slice()
    } else {
        assert_eq!(params[0], authorization);
        &params[1..]
    };
    state.calls.lock().unwrap().push(request.clone());
    if state.reject_auth {
        return StatusCode::FORBIDDEN.into_response();
    }
    let result = match method {
        "aria2.getVersion" => {
            assert!(args.is_empty());
            if state.vanilla_product {
                json!({"version": "1.37.0"})
            } else {
                json!({"version": "3.0.8", "product": "aria2-next", "rpcVersion": "1.1.0"})
            }
        }
        "aria2.tellActive" => {
            assert_eq!(args.len(), 1);
            assert!(
                args[0]
                    .as_array()
                    .unwrap()
                    .iter()
                    .any(|key| key == "bittorrent")
            );
            json!([
                {
                    "gid": "0000000000000001", "infoHash": "aabbcc", "totalLength": "4096",
                    "completedLength": "1024", "uploadSpeed": "192", "downloadSpeed": 256,
                    "bittorrent": {"info": {"name": "Fixture"}, "announceList": [["udp://tracker.example"]],
                                   "privateTorrent": true}
                },
                {"gid": "0000000000000002", "infoHash": "bbccdd", "bittorrent": {},
                 "files": [{"path": "/fixture/magnet"}]},
                {"gid": "0000000000000003", "bittorrent": {}},
                {"gid": "0000000000000004", "files": null, "infoHash": null}
            ])
        }
        "aria2.tellWaiting" if args.len() == 1 => {
            return rpc_error(&request, 1, "expected an integer");
        }
        "aria2.tellWaiting" => {
            assert_eq!(args.len(), 3);
            assert_eq!(args[0], 0);
            assert_eq!(args[1], 100);
            assert!(args[2].is_array());
            json!([{"gid": "0000000000000005", "infoHash": "eeff", "bittorrent": {}}])
        }
        "aria2.tellStopped" => {
            assert_eq!(args.len(), 1);
            json!([{"gid": "0000000000000006", "infoHash": "ff11", "bittorrent": {}}])
        }
        "aria2.getPeers" => {
            assert_eq!(args, [json!("0000000000000001")]);
            json!([
                {"ip": "::ffff:192.0.2.113", "port": "51413",
                 "peerId": "foo+%FF%2B", "peerClientName": "Fixture",
                 "downloadSpeed": "50", "uploadSpeed": 100, "downloaded": "200", "uploaded": "400",
                 "amChoking": "true", "amInterested": true, "peerChoking": false,
                 "peerInterested": "true", "incoming": "true", "snubbed": true,
                 "handshaking": "true", "progress": "0.125"},
                {"ip": "64:ff9b::c000:271", "port": 0}
            ])
        }
        "aria2.setBtPeerBlocklist" if state.reject_blocklist.swap(false, Ordering::Relaxed) => {
            return rpc_error(&request, 1, FIXTURE_TOKEN);
        }
        "aria2.setBtPeerBlocklist" => {
            assert_eq!(args.len(), 1);
            json!({"ruleCount": args[0].as_array().unwrap().len(), "revision": "4"})
        }
        "aria2.getGlobalOption" => {
            json!({"listen-port": "51413", "max-overall-upload-limit": "1024",
                   "max-overall-download-limit": 2048})
        }
        "aria2.changeGlobalOption" => json!("OK"),
        _ => panic!("unexpected Aria2 Next method: {method}"),
    };
    Json(json!({"jsonrpc": "2.0", "id": request["id"], "result": result})).into_response()
}

fn rpc_error(request: &Value, code: i64, message: &str) -> Response {
    Json(json!({
        "jsonrpc": "2.0", "id": request["id"], "error": {"code": code, "message": message}
    }))
    .into_response()
}

#[tokio::test]
async fn observes_torrents_peers_and_legacy_queue_shapes_read_only() {
    let server = Server::start(Fixture::default()).await;
    let client = Aria2Next::read_only(Config {
        ignore_private: true,
        ..server.config()
    })
    .unwrap();
    client.login().await.unwrap();
    let active = client.active_torrents().await.unwrap();
    assert_eq!(active.len(), 3);
    assert_eq!(active[0].name, "Fixture");
    assert_eq!(active[0].hash, "aabbcc");
    assert_eq!(active[0].progress, 0.25);
    assert_eq!(
        (active[0].download_speed, active[0].upload_speed),
        (256, 192)
    );
    assert_eq!(active[0].trackers[0], ["udp://tracker.example"]);
    assert!(!active[0].private);
    assert_eq!(active[1].name, "/fixture/magnet");
    assert_eq!(active[2].name, "<METADATA> 0000000000000003");
    assert_eq!(active[2].progress, 0.0);
    assert_eq!(client.all_torrents().await.unwrap().len(), 5);

    let peers = client.peers(&active[0].id).await.unwrap();
    let peer = &peers[0];
    assert_eq!(peer.ip, "192.0.2.113".parse::<IpAddr>().unwrap());
    assert_eq!(peer.raw_address, "::ffff:192.0.2.113");
    assert_eq!(peer.peer_id.as_deref(), Some("foo ÿ+"));
    assert_eq!(peer.flags.as_deref(), Some("D u S I"));
    assert_eq!((peer.downloaded, peer.uploaded), (200, 400));
    assert!(peer.handshaking);
    assert_eq!(peers[1].ip, peer.ip);
    assert_eq!(peers[1].peer_id.as_deref(), Some(""));
    assert_eq!(peers[1].client_name.as_deref(), Some(""));
    assert!(!peers[1].handshaking);
    assert_eq!(client.listen_port().await.unwrap(), 51413);
    assert!(matches!(
        client.replace_bans(&[]).await,
        Err(Error::ReadOnly)
    ));
    assert!(matches!(
        client.set_listen_port(5000).await,
        Err(Error::ReadOnly)
    ));
    assert!(
        server
            .state
            .calls
            .lock()
            .unwrap()
            .iter()
            .all(|call| !matches!(
                call["method"].as_str().unwrap(),
                "aria2.setBtPeerBlocklist" | "aria2.changeGlobalOption",
            ))
    );
}

#[tokio::test]
async fn uses_full_blocklist_and_numeric_global_options() {
    let server = Server::start(Fixture {
        reject_blocklist: AtomicBool::new(true),
        ..Fixture::default()
    })
    .await;
    let client = Aria2Next::new(server.config()).unwrap();
    client.login().await.unwrap();
    let full = [
        "192.0.2.113".parse().unwrap(),
        "2001:db8:0:1::2".parse().unwrap(),
    ];
    let err = client.replace_bans(&full).await.unwrap_err();
    assert!(matches!(err, Error::Rejected { code: 1, .. }));
    assert!(!format!("{err:?}").contains(FIXTURE_TOKEN));
    assert_eq!(
        client.replace_bans(&full).await.unwrap(),
        BlocklistRevision {
            rule_count: 4,
            revision: 4
        },
    );
    assert_eq!(client.replace_bans(&[]).await.unwrap().rule_count, 0);
    assert_eq!(
        client.speed_limits().await.unwrap(),
        SpeedLimits {
            upload: 1024,
            download: 2048
        }
    );
    client
        .set_speed_limits(SpeedLimits {
            upload: -1,
            download: 3072,
        })
        .await
        .unwrap();
    client.set_listen_port(6881).await.unwrap();

    let calls = server.state.calls.lock().unwrap();
    assert_eq!(
        calls[2]["params"][1],
        json!([
            "192.0.2.113",
            "::ffff:c000:271",
            "2001:db8:0:1::2",
            "2001:db8::/52"
        ])
    );
    assert!(calls[3]["params"][1].as_array().unwrap().is_empty());
    assert_eq!(
        calls[5]["params"][1],
        json!({
            "max-overall-upload-limit": 0, "max-overall-download-limit": 3072
        })
    );
    assert_eq!(calls[6]["params"][1], json!({"listen-port": 6881}));
}

#[tokio::test]
async fn requires_aria_next_product_and_omits_empty_rpc_secret_parameter() {
    let server = Server::start(Fixture {
        vanilla_product: true,
        no_secret: true,
        ..Fixture::default()
    })
    .await;
    let client = Aria2Next::new(server.config()).unwrap();
    assert!(matches!(
        client.login().await,
        Err(Error::UnsupportedProduct)
    ));
    assert_eq!(server.state.calls.lock().unwrap()[0]["params"], json!([]));
}

#[tokio::test]
async fn throttles_repeated_wrong_credentials() {
    let server = Server::start(Fixture {
        reject_auth: true,
        ..Fixture::default()
    })
    .await;
    let client = Aria2Next::new(server.config()).unwrap();
    for _ in 0..15 {
        assert!(matches!(client.login().await, Err(Error::Authentication)));
    }
    assert!(matches!(client.login().await, Err(Error::LoginBackoff)));
    assert_eq!(server.state.calls.lock().unwrap().len(), 15);
}
