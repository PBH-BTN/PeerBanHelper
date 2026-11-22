use std::net::IpAddr;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};

use axum::body::to_bytes;
use axum::extract::{Request, State};
use axum::http::{Method, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router};
use pbh_core::AddressPolicy;
use pbh_transmission::{
    BLOCKLIST_PATH, Config, Error, SpeedLimits, Transmission, render_p2p_blocklist,
    render_p2p_blocklist_networks_with_policy,
};
use serde_json::{Value, json};
use tokio::net::TcpListener;
use tokio::task::JoinHandle;

const RPC_PATH: &str = "/seedbox/rpc";
const SESSION_HEADER: &str = "x-transmission-session-id";
const BASIC_AUTH: &str = "Basic dHJhbnNtaXNzaW9uOnBhc3M=";

#[derive(Default)]
struct Fixture {
    session: AtomicUsize,
    handshakes: AtomicUsize,
    reject_blocklist: AtomicBool,
    reject_session: AtomicBool,
    calls: Mutex<Vec<Value>>,
    blocklist_url: Mutex<Option<String>>,
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
    async fn start() -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let endpoint = format!("http://{}", listener.local_addr().unwrap());
        let state = Arc::new(Fixture::default());
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
            endpoint: format!("{}/seedbox/", self.endpoint),
            rpc_url: "/rpc".into(),
            username: "transmission".into(),
            password: "pass".into(),
            ..Config::default()
        }
    }

    fn pbh_prefix(&self) -> String {
        format!("{}/pbh/", self.endpoint)
    }
}

async fn handle(State(mock): State<Arc<Fixture>>, request: Request) -> Response {
    if request.uri().path() == format!("/pbh{BLOCKLIST_PATH}") {
        let agent = request
            .headers()
            .get(header::USER_AGENT)
            .and_then(|it| it.to_str().ok());
        return render_p2p_blocklist(&[], agent).into_response();
    }
    assert_eq!(request.uri().path(), RPC_PATH);
    if request
        .headers()
        .get(header::AUTHORIZATION)
        .and_then(|it| it.to_str().ok())
        != Some(BASIC_AUTH)
    {
        return (
            StatusCode::UNAUTHORIZED,
            [(header::WWW_AUTHENTICATE, "Basic realm=rpc")],
        )
            .into_response();
    }
    let session = format!("fixture-{}", mock.session.load(Ordering::Relaxed));
    if request.method() == Method::GET {
        mock.handshakes.fetch_add(1, Ordering::Relaxed);
        return (StatusCode::CONFLICT, [(SESSION_HEADER, session)]).into_response();
    }
    assert_eq!(request.method(), Method::POST);
    if mock.reject_session.load(Ordering::Relaxed)
        || request
            .headers()
            .get(SESSION_HEADER)
            .and_then(|it| it.to_str().ok())
            != Some(&session)
    {
        return (StatusCode::CONFLICT, [(SESSION_HEADER, session)]).into_response();
    }
    let body = to_bytes(request.into_body(), 100_000).await.unwrap();
    let rpc: Value = serde_json::from_slice(&body).unwrap();
    assert!(rpc["arguments"].is_object());
    mock.calls.lock().unwrap().push(rpc.clone());
    let result = match rpc["method"].as_str().unwrap() {
        "session-get" => {
            let url = mock.blocklist_url.lock().unwrap().clone();
            json!({
                "version": "4.1.1 (test-build)",
                "blocklist-enabled": url.is_some(),
                "blocklist-url": url.unwrap_or_default(),
                "speed-limit-down": 5,
                "speed-limit-down-enabled": true,
                "speed-limit-up": 9,
                "speed-limit-up-enabled": false,
                "peer-port": 51413
            })
        }
        "session-set" => {
            if let Some(url) = rpc["arguments"]["blocklist-url"].as_str() {
                assert_eq!(rpc["arguments"]["blocklist-enabled"], true);
                *mock.blocklist_url.lock().unwrap() = Some(url.into());
            }
            json!({})
        }
        "blocklist-update" => {
            if mock.reject_blocklist.load(Ordering::Relaxed) {
                return Json(json!({"result": "blocklist server unavailable", "arguments": {}}))
                    .into_response();
            }
            let url = mock.blocklist_url.lock().unwrap().clone().unwrap();
            let body = reqwest::Client::new()
                .get(&url)
                .header(header::USER_AGENT, "Transmission/4.1.1")
                .send()
                .await
                .unwrap()
                .text()
                .await
                .unwrap();
            assert_eq!(
                body,
                "TransmissionWorkaround:127.127.127.127-127.127.127.127"
            );
            json!({"blocklist-size": 1})
        }
        "session-stats" => {
            json!({"cumulative-stats": {"uploadedBytes": 1000, "downloadedBytes": 2000}})
        }
        "torrent-get" => {
            let active = json!({
                "id": 3, "hashString": "0123abc", "name": "fixture", "totalSize": 100_001,
                "sizeWhenDone": 10_003, "percentDone": 0.5, "isPrivate": false,
                "peersConnected": 1, "rateDownload": 0, "rateUpload": 0, "peer-limit": 32,
                "trackerList": "udp://one.example\nudp://fallback.example\n\nhttps://two.example",
                "peers": [
                    {"address": "::ffff:203.0.113.2", "port": 1337, "clientName": "Fixture/1.0",
                     "peer_id": "Qf8", "flagStr": "I", "progress": 0.75, "rateToPeer": 20,
                     "bytes_to_client": 400, "bytes_to_peer": 500},
                    {"address": "64:ff9b::c633:6402", "port": 0, "progress": 0.0}
               ]
            });
            let mut inactive = active.clone();
            inactive["id"] = 4.into();
            inactive["hashString"] = "0123abd".into();
            inactive["peersConnected"] = 0.into();
            let mut private = active.clone();
            private["id"] = 5.into();
            private["hashString"] = "0123abe".into();
            private["isPrivate"] = true.into();
            json!({"torrents": [active, inactive, private]})
        }
        "torrent-set" => json!({}),
        method => panic!("unexpected RPC method: {method}"),
    };
    Json(json!({"result": "success", "arguments": result})).into_response()
}

#[tokio::test]
async fn reads_native_snapshots_and_refreshes_one_expired_session() {
    let server = Server::start().await;
    let client = Transmission::read_only(
        Config {
            ignore_private: true,
            ..server.config()
        },
        &server.pbh_prefix(),
    )
    .unwrap();
    client.login().await.unwrap();
    server.state.session.store(1, Ordering::Relaxed);
    let torrents = client.active_torrents().await.unwrap();
    assert_eq!(server.state.handshakes.load(Ordering::Relaxed), 2);
    assert_eq!(torrents.len(), 1);
    let torrent = &torrents[0];
    assert_eq!(
        (torrent.id, torrent.size, torrent.completed_size),
        (3, 100_001, 5001)
    );
    assert_eq!(
        torrent.trackers[0],
        ["udp://one.example", "udp://fallback.example"]
    );
    assert_eq!(torrent.peers[0].peer_id.as_deref(), Some("Aÿ"));
    assert_eq!(
        torrent.peers[0].ip,
        "203.0.113.2".parse::<IpAddr>().unwrap()
    );
    assert_eq!(torrent.peers[0].raw_address, "::ffff:203.0.113.2");
    assert_eq!(torrent.peers[0].download_speed, -1);
    assert_eq!(torrent.peers[0].uploaded, 500);
    assert_eq!(
        torrent.peers[1].ip,
        "198.51.100.2".parse::<IpAddr>().unwrap()
    );
    assert_eq!(torrent.peers[1].downloaded, -1);
    assert_eq!(torrent.peers[1].peer_id.as_deref(), Some(""));
    assert_eq!(client.all_torrents().await.unwrap().len(), 3);
    assert!(matches!(
        client.update_blocklist().await,
        Err(Error::ReadOnly)
    ));
    assert!(matches!(
        client.set_listen_port(5).await,
        Err(Error::ReadOnly)
    ));
    assert_eq!(server.state.calls.lock().unwrap().len(), 3);
}

#[tokio::test]
async fn applies_blocklist_using_pbh_prefix_and_cache_busting_timestamp() {
    let server = Server::start().await;
    let client = Transmission::new(server.config(), &server.pbh_prefix()).unwrap();
    client.login().await.unwrap();
    client.login().await.unwrap();
    assert_eq!(client.update_blocklist().await.unwrap(), 1);

    let calls = server.state.calls.lock().unwrap();
    let methods: Vec<_> = calls
        .iter()
        .map(|call| call["method"].as_str().unwrap())
        .collect();
    assert_eq!(
        methods,
        [
            "session-get",
            "session-set",
            "blocklist-update",
            "session-get",
            "blocklist-update"
        ]
    );
    let url = calls[1]["arguments"]["blocklist-url"].as_str().unwrap();
    let (base, timestamp) = url.split_once("?t=").unwrap();
    assert_eq!(base, format!("{}/pbh{BLOCKLIST_PATH}", server.endpoint));
    assert!(timestamp.parse::<u64>().unwrap() > 0);
}

#[tokio::test]
async fn blocklist_error_sets_legacy_help_url_and_double_conflict_is_bounded() {
    let server = Server::start().await;
    server.state.reject_blocklist.store(true, Ordering::Relaxed);
    let client = Transmission::new(server.config(), &server.pbh_prefix()).unwrap();
    assert!(matches!(
        client.login().await,
        Err(Error::Rejected {
            method: "blocklist-update"
        })
    ));
    assert!(server.state.blocklist_url.lock().unwrap().as_deref().unwrap().starts_with(
        "http://peerbanhelper-blocklist-update-failed.com/check-peerbanhelper-webui-prefix-settings?t="
    ));
    server.state.reject_session.store(true, Ordering::Relaxed);
    assert!(matches!(client.login().await, Err(Error::SessionExpired)));
    assert_eq!(server.state.handshakes.load(Ordering::Relaxed), 2);
}

#[tokio::test]
async fn manages_traffic_limits_port_and_trackers() {
    let server = Server::start().await;
    let client = Transmission::new(server.config(), &server.pbh_prefix()).unwrap();
    let statistics = client.traffic_statistics().await.unwrap();
    assert_eq!(
        (statistics.uploaded_bytes, statistics.downloaded_bytes),
        (1000, 2000)
    );
    assert_eq!(
        client.speed_limits().await.unwrap(),
        SpeedLimits {
            upload: 0,
            download: 5120
        }
    );
    client
        .set_speed_limits(SpeedLimits {
            upload: -1,
            download: 2000,
        })
        .await
        .unwrap();
    assert_eq!(client.listen_port().await.unwrap(), 51413);
    client.set_listen_port(5050).await.unwrap();
    client
        .replace_trackers(
            3,
            &[
                vec!["udp://one.example".into()],
                vec!["https://two.example".into()],
            ],
        )
        .await
        .unwrap();

    let calls = server.state.calls.lock().unwrap();
    assert_eq!(
        calls[2]["arguments"],
        json!({
            "alt-speed-enabled": false, "alt-speed-time-enabled": false,
            "speed-limit-up": 1, "speed-limit-up-enabled": false,
            "speed-limit-down": 1, "speed-limit-down-enabled": true
        })
    );
    assert_eq!(
        calls[4]["arguments"],
        json!({"peer-port-random-on-start": false, "peer-port": 5050})
    );
    assert_eq!(
        calls[5]["arguments"],
        json!({"ids": [3], "trackerList": "udp://one.example\n\nhttps://two.example"})
    );
}

#[test]
fn blocklist_format_preserves_dual_stack_ranges_and_empty_workaround() {
    let addresses = [
        "203.0.113.2".parse().unwrap(),
        "::ffff:203.0.113.2".parse().unwrap(),
        "2001:db8:0:1:2:3:4:5".parse().unwrap(),
    ];
    let rendered = render_p2p_blocklist(&addresses, None);
    let ranges: Vec<_> = rendered
        .lines()
        .map(|line| {
            let (id, range) = line.split_once(':').unwrap();
            assert_eq!(id.len(), 32);
            assert!(id.chars().all(|ch| ch.is_ascii_hexdigit()));
            range
        })
        .collect();
    assert_eq!(
        ranges,
        [
            "203.0.113.2-203.0.113.2",
            "::ffff:cb00:7102-::ffff:cb00:7102",
            "2001:db8:0:1:2:3:4:5-2001:db8:0:1:2:3:4:5",
            "2001:db8::-2001:db8:0:fff:ffff:ffff:ffff:ffff",
        ]
    );
    assert_eq!(render_p2p_blocklist(&[], Some("curl")), "");
}

#[test]
fn blocklist_format_uses_configured_nat64_and_ipv4_ranges() {
    let policy = AddressPolicy {
        nat64_prefixes: vec!["2001:db8:64::/96".parse().unwrap()],
        ipv4_ban_prefix: Some(30),
        ipv6_ban_prefix: None,
        ..AddressPolicy::default()
    };
    let networks = [
        "2001:db8:64::c000:237/128".parse().unwrap(),
        "2001:db8:42::1/128".parse().unwrap(),
    ];
    let rendered = render_p2p_blocklist_networks_with_policy(&networks, None, &policy);
    let ranges: Vec<_> = rendered
        .lines()
        .map(|line| line.split_once(':').unwrap().1)
        .collect();
    assert_eq!(
        ranges,
        [
            "2001:db8:64::c000:237-2001:db8:64::c000:237",
            "192.0.2.55-192.0.2.55",
            "192.0.2.52-192.0.2.55",
            "::ffff:c000:234-::ffff:c000:237",
            "2001:db8:42::1-2001:db8:42::1",
        ]
    );
}
