use std::net::IpAddr;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};

use axum::extract::{Json, Request, State};
use axum::http::{Method, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::{Router, extract::FromRequest};
use base64::{Engine, engine::general_purpose::STANDARD};
use pbh_bitcomet::{BitComet, Config, Error, SpeedLimits, TrafficStatistics};
use serde_json::{Value, json};
use tokio::net::TcpListener;
use tokio::task::JoinHandle;

const CLIENT_ID: &str = "78ec56a2-8d0c-31ca-a843-f49fc1f20153";

struct Fixture {
    version: &'static str,
    reject_password: bool,
    expire_session: AtomicBool,
    reject_ip_upload: AtomicBool,
    filter_enabled: AtomicBool,
    token_generation: AtomicUsize,
    calls: Mutex<Vec<Call>>,
}

impl Default for Fixture {
    fn default() -> Self {
        Self {
            version: "2.23",
            reject_password: false,
            expire_session: AtomicBool::new(false),
            reject_ip_upload: AtomicBool::new(false),
            filter_enabled: AtomicBool::new(false),
            token_generation: AtomicUsize::new(0),
            calls: Mutex::new(Vec::new()),
        }
    }
}

#[derive(Debug)]
struct Call {
    route: String,
    payload: Value,
}

struct Server {
    endpoint: String,
    fixture: Arc<Fixture>,
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
        let endpoint = format!("http://{}/seedbox/", listener.local_addr().unwrap());
        let fixture = Arc::new(fixture);
        let router = Router::new()
            .fallback(handle)
            .with_state(Arc::clone(&fixture));
        let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
        Self {
            endpoint,
            fixture,
            task,
        }
    }

    fn config(&self) -> Config {
        Config {
            endpoint: self.endpoint.clone(),
            username: "fixture-user".into(),
            password: "fixture-password".into(),
            ..Config::default()
        }
    }
}

async fn handle(State(state): State<Arc<Fixture>>, request: Request) -> Response {
    let route = request
        .uri()
        .path()
        .strip_prefix("/seedbox/")
        .unwrap()
        .to_owned();
    assert_eq!(request.method(), Method::POST);
    assert_eq!(request.headers()["Client-Type"], "BitComet WebUI");
    assert_eq!(
        request.headers()[header::USER_AGENT],
        "PeerBanHelper BitComet Adapter"
    );
    assert_eq!(request.headers()[header::CONTENT_TYPE], "application/json");
    let authorization = request.headers().get(header::AUTHORIZATION).cloned();
    let payload = Json::<Value>::from_request(request, &()).await.unwrap().0;
    state.calls.lock().unwrap().push(Call {
        route: route.clone(),
        payload: payload.clone(),
    });
    if route == "api/webui/login" {
        assert!(authorization.is_none());
        assert_eq!(payload["client_id"], CLIENT_ID);
        let envelope = STANDARD
            .decode(payload["authentication"].as_str().unwrap())
            .unwrap();
        assert_eq!(&envelope[..2], [3, 1]);
        assert!(envelope.len() >= 82);
        assert_eq!((envelope.len() - 66) % 16, 0);
        if state.reject_password {
            return Json(
                json!({"error_code": "PASSWORD_ERROR", "error_message": "fixture-password"}),
            )
            .into_response();
        }
        return Json(json!({
            "error_code": "OK", "invite_token": "invite-secret", "version": state.version,
        }))
        .into_response();
    }
    if route == "api/device_token/get" {
        assert_eq!(authorization.unwrap(), "Bearer invite-secret");
        assert_eq!(payload["device_id"], CLIENT_ID);
        assert_eq!(payload["device_name"], "PeerBanHelper - BitComet Adapter");
        assert_eq!(payload["platform"], "webui");
        let generation = state.token_generation.fetch_add(1, Ordering::Relaxed) + 1;
        return Json(json!({
            "device_token": format!("device-secret-{generation}"),
            "server_id": "fixture-id", "version": state.version,
        }))
        .into_response();
    }
    let generation = state.token_generation.load(Ordering::Relaxed);
    assert_eq!(
        authorization.unwrap(),
        format!("Bearer device-secret-{generation}")
    );
    let response = match route.as_str() {
        "api/config/ipfilter/get" if state.expire_session.swap(false, Ordering::Relaxed) => {
            return StatusCode::UNAUTHORIZED.into_response();
        }
        "api/config/ipfilter/get" => json!({
            "error_code": "OK", "ip_filter_config": {
                "enable_ipfilter": state.filter_enabled.load(Ordering::Relaxed),
                "ipfilter_mode": "blacklist",
            }
        }),
        "api/config/ipfilter/set" => {
            assert_eq!(
                payload,
                json!({"ip_filter_config": {
                    "enable_ipfilter": true, "ipfilter_mode": "blacklist",
                }})
            );
            state.filter_enabled.store(true, Ordering::Relaxed);
            json!({"error_code": "ok"})
        }
        "api_v2/task_list/get" => {
            assert_eq!(payload["limit"], 50);
            assert_eq!(payload["query_mode"], "stateless");
            assert!(matches!(
                payload["state_group"].as_str(),
                Some("ALL" | "ACTIVE")
            ));
            if payload["start"] == 0 {
                json!({"tasks": (1..=50).map(|id| {
                    json!({"type": "HTTP", "task_id": id})
                }).collect::<Vec<_>>()})
            } else {
                assert_eq!(payload["start"], 50);
                json!({"tasks": [{"type": "BT", "task_id": 51}, {"type": "BT", "task_id": "52"}]})
            }
        }
        "api/task/summary/get" => {
            let id = payload["task_id"].as_str().unwrap();
            json!({
                "task": {"task_id": id, "task_name": "fixture", "selected_downloaded_size": 1024,
                         "download_rate": 16, "upload_rate": 24},
                "task_detail": {"infohash_v2": "v2-fixture", "total_size": 8192,
                                "torrent_private": id == "52"},
                "task_status": {"download_permillage": 125}
            })
        }
        "api/task/peers/get" => {
            assert_eq!(
                payload["groups"],
                json!(["peers_connected", "ltseeds_connected"])
            );
            assert_eq!(payload["max_count"], 0);
            json!({"peers": [
                {"group": "peers_connected", "ip": "[::ffff:192.0.2.113]:51413", "remote_port": 1234,
                 "peer_id": "2d5043303030312d", "client_type": "fixture-peer",
                 "dl_rate": 16, "up_rate": 24, "dl_size": 32, "up_size": 48,
                 "permillage": 750},
                {"group": "ltseeds_connected", "ip": "2001:db8::2", "remote_port": 1234,
                 "peer_id": "f", "dl_size": null},
                {"group": "peers_disconnected", "ip": "198.51.100.2"},
            ]})
        }
        "api/task/trackers/get" => json!({"trackers": [
            {"name": "udp://fixture"}, {"name": "DHT"}, {"name": "wss://fixture"},
        ]}),
        "api/config/connection/get" => json!({"error_code": "OK", "connection_config": {
            "listen_port_tcp": 51413, "max_download_speed": 2048, "max_upload_speed": 1024,
        }}),
        "api/config/connection/set" | "api/task/peers/unban_peers" => json!({"error_code": "ok"}),
        "api/config/ipfilter/upload" if state.reject_ip_upload.swap(false, Ordering::Relaxed) => {
            json!({"error_code": "FAILED", "error_message": "device-secret"})
        }
        "api/config/ipfilter/upload" => json!({"error_code": "OK"}),
        "api/statistics_list/get" => json!({"value_list": [
            {"token":"{G_TOTAL_UPLOAD_BYTE}", "value":"1111"},
            {"token":"{G_TOTAL_DOWNLOAD_BYTE}", "value":"2222"},
            {"token":"${G_TOTAL_UPLOAD_AUTO}", "value":"1.25 MiB"},
            {"token":"${G_TOTAL_DOWNLOAD_AUTO}", "value":"1.5 KB"},
        ]}),
        _ => panic!("unexpected BitComet request {route}"),
    };
    Json(response).into_response()
}

#[tokio::test]
async fn two_step_login_and_observer_preserve_settings() {
    let server = Server::start(Fixture::default()).await;
    let client = BitComet::read_only(Config {
        ignore_private: true,
        ..server.config()
    })
    .unwrap();
    client.login().await.unwrap();
    client.login().await.unwrap();
    let torrents = client.active_torrents().await.unwrap();
    assert_eq!(torrents.len(), 1);
    assert_eq!(torrents[0].id, "51");
    assert_eq!(torrents[0].hash, "v2-fixture");
    assert_eq!(torrents[0].progress, 0.125);
    assert_eq!(client.all_torrents().await.unwrap().len(), 2);

    let peers = client.peers(&torrents[0].id).await.unwrap();
    assert_eq!(peers.len(), 2);
    assert_eq!(peers[0].ip, "192.0.2.113".parse::<IpAddr>().unwrap());
    assert_eq!(peers[0].raw_address, "::ffff:192.0.2.113");
    assert_eq!(peers[0].port, 51413);
    assert_eq!(peers[0].peer_id.as_deref(), Some("-PC0001-"));
    assert_eq!(peers[0].progress, 0.75);
    assert!(peers[0].flags.is_none());
    assert_eq!(peers[1].port, 1234);
    assert_eq!(peers[1].peer_id.as_deref(), Some("\x0f"));
    assert_eq!((peers[1].downloaded, peers[1].uploaded), (-1, -1));
    assert!(peers[1].handshaking);
    assert_eq!(
        client.trackers("51").await.unwrap(),
        [["udp://fixture"], ["wss://fixture"]]
    );
    assert!(client.supports_traffic_statistics().await.unwrap());
    assert_eq!(
        client.traffic_statistics().await.unwrap(),
        TrafficStatistics {
            uploaded: 1111,
            downloaded: 2222
        }
    );
    assert!(matches!(
        client.set_listen_port(6881).await,
        Err(Error::ReadOnly)
    ));
    assert!(matches!(
        client.sync_bans(&[], &[]).await,
        Err(Error::ReadOnly)
    ));

    let calls = server.fixture.calls.lock().unwrap();
    assert_eq!(server.fixture.token_generation.load(Ordering::Relaxed), 1);
    assert!(
        calls
            .iter()
            .all(|call| !call.route.ends_with("/set") && !call.route.ends_with("/upload"))
    );
    let statistics = calls
        .iter()
        .find(|call| call.route.ends_with("statistics_list/get"))
        .unwrap();
    assert_eq!(
        statistics.payload["token_list"][0],
        "{G_TOTAL_DOWNLOAD_AUTO}"
    );
}

#[tokio::test]
async fn renews_device_session_enables_filter_and_uploads_replacement() {
    let server = Server::start(Fixture {
        reject_ip_upload: AtomicBool::new(true),
        ..Fixture::default()
    })
    .await;
    let client = BitComet::new(server.config()).unwrap();
    client.login().await.unwrap();
    assert!(server.fixture.filter_enabled.load(Ordering::Relaxed));
    server.fixture.expire_session.store(true, Ordering::Relaxed);
    client.login().await.unwrap();
    assert_eq!(server.fixture.token_generation.load(Ordering::Relaxed), 2);
    let addresses = [
        "192.0.2.113".parse().unwrap(),
        "2001:db8:0:1::2".parse().unwrap(),
    ];
    let error = client.sync_bans(&addresses, &[]).await.unwrap_err();
    assert!(matches!(
        error,
        Error::Rejected {
            operation: "api/config/ipfilter/upload"
        }
    ));
    assert!(!format!("{error:?}").contains("device-secret"));
    client.sync_bans(&addresses, &[]).await.unwrap();
    client.sync_bans(&[], &addresses).await.unwrap();
    assert_eq!(client.listen_port().await.unwrap(), 51413);
    assert_eq!(
        client.speed_limits().await.unwrap(),
        SpeedLimits {
            upload: 1024,
            download: 2048
        }
    );
    client.set_listen_port(6882).await.unwrap();
    client
        .set_speed_limits(SpeedLimits {
            upload: -1,
            download: 3030,
        })
        .await
        .unwrap();

    let calls = server.fixture.calls.lock().unwrap();
    let operations: Vec<_> = calls
        .iter()
        .filter(|call| call.route.ends_with("/upload") || call.route.ends_with("/unban_peers"))
        .collect();
    let uploaded = STANDARD
        .decode(operations[1].payload["content_base64"].as_str().unwrap())
        .unwrap();
    assert_eq!(
        uploaded,
        b"192.0.2.113\n::ffff:c000:271\n2001:db8:0:1::2\n2001:db8::/52"
    );
    assert_eq!(operations[1].payload["import_type"], "replace");
    assert_eq!(operations[1].payload["data_type"], "data_file");
    assert_eq!(
        operations[2].payload,
        json!({
            "ip_list": ["192.0.2.113", "2001:db8:0:1::2"], "unban_range": "unban_peers_in_all_tasks",
        })
    );
    assert_eq!(operations[3].payload["content_base64"], "");
    assert_eq!(
        calls[calls.len() - 2].payload,
        json!({"connection_config": {
            "enable_listen_tcp": true, "listen_port_tcp": 6882,
        }})
    );
    assert_eq!(
        calls.last().unwrap().payload,
        json!({"connection_config": {
            "max_upload_speed": 0, "max_download_speed": 3030,
        }})
    );
}

#[tokio::test]
async fn respects_old_version_and_human_readable_statistics_profiles() {
    let obsolete = Server::start(Fixture {
        version: "2.17",
        ..Fixture::default()
    })
    .await;
    let client = BitComet::new(obsolete.config()).unwrap();
    assert!(matches!(
        client.login().await,
        Err(Error::UnsupportedVersion)
    ));
    assert_eq!(obsolete.fixture.calls.lock().unwrap().len(), 1);

    let legacy = Server::start(Fixture {
        version: "2.19",
        ..Fixture::default()
    })
    .await;
    let client = BitComet::read_only(legacy.config()).unwrap();
    client.login().await.unwrap();
    assert!(!client.supports_traffic_statistics().await.unwrap());
    assert_eq!(
        client.traffic_statistics().await.unwrap(),
        TrafficStatistics {
            uploaded: 0,
            downloaded: 1536
        }
    );
    let calls = legacy.fixture.calls.lock().unwrap();
    assert_eq!(
        calls.last().unwrap().payload["token_list"][0],
        "${G_TOTAL_DOWNLOAD_AUTO}"
    );
}

#[tokio::test]
async fn does_not_exchange_device_token_after_password_error() {
    let server = Server::start(Fixture {
        reject_password: true,
        ..Fixture::default()
    })
    .await;
    let client = BitComet::read_only(server.config()).unwrap();
    let error = client.login().await.unwrap_err();
    assert!(matches!(error, Error::Authentication));
    assert!(!format!("{error:?}").contains("fixture-password"));
    assert_eq!(server.fixture.calls.lock().unwrap().len(), 1);
}
