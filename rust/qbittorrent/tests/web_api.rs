use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};

use axum::extract::{Form, FromRequest, Request, State};
use axum::http::{HeaderMap, Method, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router};
use pbh_network::{ProxyConfig, ProxyPolicy};
use pbh_qbittorrent::{BasicAuth, Config, Error, Qbittorrent, SpeedLimits, TrackerTier};
use reqwest::Url;
use serde_json::{Value, json};
use tokio::net::TcpListener;
use tokio::task::JoinHandle;

#[derive(Default)]
struct Mock {
    api_key: bool,
    proxy_auth: bool,
    forward_proxy_authority: Option<String>,
    reject_login: bool,
    shadow_ban: bool,
    session_number: AtomicUsize,
    session_expires: AtomicBool,
    fail_next_ban: AtomicBool,
    calls: Mutex<Vec<Call>>,
}

#[derive(Debug)]
struct Call {
    method: Method,
    path: String,
    query: HashMap<String, String>,
    form: HashMap<String, String>,
}

struct Server {
    endpoint: String,
    state: Arc<Mock>,
    task: JoinHandle<()>,
}

impl Drop for Server {
    fn drop(&mut self) {
        self.task.abort();
    }
}

impl Server {
    async fn start(mock: Mock) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let endpoint = format!("http://{}/qbt/", listener.local_addr().unwrap());
        let state = Arc::new(mock);
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
            username: "pbh admin".into(),
            password: "a&b".into(),
            ..Config::default()
        }
    }
}

async fn handle(State(mock): State<Arc<Mock>>, request: Request) -> Response {
    let headers = request.headers().clone();
    let method = request.method().clone();
    let uri = request.uri();
    if let Some(authority) = &mock.forward_proxy_authority {
        assert_eq!(
            uri.authority().map(|value| value.as_str()),
            Some(authority.as_str())
        );
    }
    let url = Url::parse(&format!(
        "http://localhost{}",
        uri.path_and_query().unwrap()
    ))
    .unwrap();
    let path = url.path();
    let query: HashMap<String, String> = url.query_pairs().into_owned().collect();
    let form: HashMap<String, String> = if method == Method::POST {
        Form::from_request(request, &()).await.unwrap().0
    } else {
        HashMap::new()
    };
    mock.calls.lock().unwrap().push(Call {
        method,
        path: path.into(),
        query: query.clone(),
        form: form.clone(),
    });

    let authorization = value(&headers, header::AUTHORIZATION);
    if mock.proxy_auth && authorization != "Basic cHJveHk6cGFzcw==" {
        return (
            StatusCode::UNAUTHORIZED,
            [(header::WWW_AUTHENTICATE, "Basic realm=qbt")],
        )
            .into_response();
    }
    if path == "/qbt/api/v2/auth/login" {
        assert!(!mock.api_key);
        assert!(authorization.is_empty() || mock.proxy_auth);
        assert!(!value(&headers, header::REFERER).is_empty());
        assert_eq!(form.get("username").map(String::as_str), Some("pbh admin"));
        assert_eq!(form.get("password").map(String::as_str), Some("a&b"));
        if mock.reject_login {
            return "Fails.".into_response();
        }
        let session = mock.session_number.fetch_add(1, Ordering::Relaxed) + 1;
        mock.session_expires.store(false, Ordering::Relaxed);
        return (
            [(
                header::SET_COOKIE,
                format!("QBT_SID_9898={session}; Path=/qbt/"),
            )],
            "Ok.",
        )
            .into_response();
    }
    let session = mock.session_number.load(Ordering::Relaxed);
    let authenticated = if mock.api_key {
        authorization == "Bearer qbt_fixture"
    } else {
        session != 0
            && !mock.session_expires.load(Ordering::Relaxed)
            && value(&headers, header::COOKIE) == format!("QBT_SID_9898={session}")
    };
    if !authenticated {
        return StatusCode::FORBIDDEN.into_response();
    }
    match path {
        "/qbt/api/v2/app/buildInfo" => Json(json!({"libtorrent": "2.0.11"})).into_response(),
        "/qbt/api/v2/app/version" if mock.shadow_ban => "v5.3.0alpha1".into_response(),
        "/qbt/api/v2/app/version" if mock.api_key => "v5.2.0".into_response(),
        "/qbt/api/v2/app/version" => "v5.1.1.10".into_response(),
        "/qbt/api/v2/app/setPreferences" => StatusCode::OK.into_response(),
        "/qbt/api/v2/app/preferences" => Json(json!({
            "shadow_ban_enabled": mock.shadow_ban,
            "up_limit": 5000,
            "dl_limit": 9000,
            "listen_port": 6881,
        }))
        .into_response(),
        "/qbt/api/v2/sync/maindata" => Json(json!({
            "server_state": {"alltime_ul": 1800000, "alltime_dl": 2400000}
        }))
        .into_response(),
        "/qbt/api/v2/torrents/trackers" => Json(json!([
            {"tier": -1, "url": "** [DHT] **"},
            {"tier": 1, "url": "udp://b.example"},
            {"tier": 0, "url": "udp://a.example"},
            {"tier": 1, "url": "https://backup.example"},
        ]))
        .into_response(),
        "/qbt/api/v2/torrents/removeTrackers" | "/qbt/api/v2/torrents/addTrackers" => {
            StatusCode::OK.into_response()
        }
        "/qbt/api/v2/torrents/info" => {
            assert_eq!(query["limit"], "100");
            let public = json!({"hash": "public", "name": "Public", "total_size": 5000, "completed": 1024, "progress": 0.2});
            let private = json!({"hash": "private", "name": "Private", "total_size": 1000, "is_private": true});
            let torrents = if query["offset"] == "0" {
                vec![public; 100]
            } else {
                vec![public, private]
            };
            Json(torrents).into_response()
        }
        "/qbt/api/v2/torrents/properties" => {
            Json(json!({"piece_size": 512, "pieces_have": 2, "is_private": false})).into_response()
        }
        "/qbt/api/v2/sync/torrentPeers" => {
            assert_eq!(query["hash"], "public");
            (
                [(header::CONTENT_TYPE, "application/json")],
                r#"{"peers":{
                    "203.0.113.1:5001":{"ip":"::ffff:203.0.113.1","port":5001,"client":"Example 1.0","peer_id_client":"-EX1000-","connection":"BT","up_speed":12,"dl_speed":34,"uploaded":56,"downloaded":78,"progress":0.1,"flags":"I"},
                    "[64:ff9b::c633:6402]:5002":{"ip":"64:ff9b::c633:6402","port":5002},
                    "web":{"ip":"192.0.2.2","connection":"Web"},
                    "node.onion:1234":{"ip":"192.0.2.3"},
                    "node.i2p:1234":{"ip":"192.0.2.4"},
                    "missing":{"port":5003},
                    "192.0.2.5:5001":{"ip":"192.0.2.5","shadowbanned":true}
                }}"#,
            )
                .into_response()
        }
        "/qbt/api/v2/transfer/banPeers" if mock.fail_next_ban.swap(false, Ordering::Relaxed) => {
            StatusCode::SERVICE_UNAVAILABLE.into_response()
        }
        "/qbt/api/v2/transfer/banPeers" | "/qbt/api/v2/transfer/shadowbanPeers" => {
            StatusCode::OK.into_response()
        }
        _ => StatusCode::NOT_FOUND.into_response(),
    }
}

fn value(headers: &HeaderMap, name: header::HeaderName) -> &str {
    headers
        .get(name)
        .and_then(|value| value.to_str().ok())
        .unwrap_or_default()
}

#[tokio::test]
async fn explicit_forward_proxy_carries_read_only_login_and_session_cookies() {
    let origin = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let authority = origin.local_addr().unwrap().to_string();
    let proxy = Server::start(Mock {
        forward_proxy_authority: Some(authority.clone()),
        ..Mock::default()
    })
    .await;
    let policy = ProxyPolicy::new(&ProxyConfig {
        setting: 1,
        port: i32::from(Url::parse(&proxy.endpoint).unwrap().port().unwrap()),
        ..ProxyConfig::default()
    })
    .unwrap();
    let adapter = Qbittorrent::connect_with_proxy(
        Config {
            endpoint: format!("http://{authority}/qbt/"),
            ..proxy.config()
        },
        false,
        true,
        &policy,
    )
    .unwrap();
    adapter.login().await.unwrap();
    let calls = proxy.state.calls.lock().unwrap();
    assert!(calls.iter().any(|call| call.path.ends_with("/auth/login")));
    assert!(
        calls
            .iter()
            .any(|call| call.path.ends_with("/app/buildInfo"))
    );
    assert!(
        !calls
            .iter()
            .any(|call| call.path.ends_with("/app/setPreferences"))
    );
}

#[tokio::test]
async fn reads_paginated_snapshots_and_reuses_the_session() {
    let server = Server::start(Mock::default()).await;
    let adapter = Qbittorrent::new(Config {
        ignore_private: true,
        ..server.config()
    })
    .unwrap();
    adapter.login().await.unwrap();
    adapter.login().await.unwrap();
    let torrents = adapter.active_torrents().await.unwrap();
    assert_eq!(torrents.len(), 1);
    assert_eq!(torrents[0].name, "Public");
    assert_eq!(torrents[0].size, 5000);
    assert_eq!(torrents[0].completed_size, 1024);
    assert_eq!(adapter.all_torrents().await.unwrap().len(), 2);

    let peers = adapter.peers(&torrents[0].hash).await.unwrap();
    assert_eq!(peers.len(), 3);
    assert_eq!(peers[0].ip, "203.0.113.1".parse::<IpAddr>().unwrap());
    assert_eq!(peers[0].port, 5001);
    assert_eq!(peers[0].raw_address, "203.0.113.1:5001");
    assert_eq!(peers[0].peer_id.as_deref(), Some("-EX1000-"));
    assert_eq!(peers[0].client_name.as_deref(), Some("Example 1.0"));
    assert_eq!(peers[0].download_speed, 34);
    assert_eq!(peers[0].uploaded, 56);
    assert!(peers[1].is_handshaking());
    assert_eq!(peers[1].ip, "198.51.100.2".parse::<IpAddr>().unwrap());

    let calls = server.state.calls.lock().unwrap();
    assert_eq!(server.state.session_number.load(Ordering::Relaxed), 1);
    let active_calls: Vec<_> = calls
        .iter()
        .filter(|call| {
            call.query
                .get("filter")
                .is_some_and(|filter| filter == "active")
        })
        .collect();
    assert_eq!(active_calls.len(), 2);
    assert_eq!(active_calls[1].query["offset"], "100");
    assert_eq!(
        calls
            .iter()
            .filter(|call| call.path.ends_with("/properties"))
            .count(),
        2,
        "properties are reused when completed bytes are unchanged"
    );
}

#[tokio::test]
async fn reauthenticates_after_expiration_and_reconciles_failed_ban() {
    let server = Server::start(Mock {
        fail_next_ban: AtomicBool::new(true),
        ..Mock::default()
    })
    .await;
    let adapter = Qbittorrent::new(Config {
        increment_ban: true,
        ..server.config()
    })
    .unwrap();
    adapter.login().await.unwrap();
    server.state.session_expires.store(true, Ordering::Relaxed);
    adapter.login().await.unwrap();
    assert_eq!(server.state.session_number.load(Ordering::Relaxed), 2);

    let addresses = [
        "203.0.113.1".parse().unwrap(),
        "2001:db8::1".parse().unwrap(),
    ];
    let added = ["203.0.113.1:1000", "[2001:db8::1]:7000", "198.51.100.2"];
    assert!(matches!(
        adapter.sync_bans(&addresses, &added, false, false).await,
        Err(Error::Status {
            status: StatusCode::SERVICE_UNAVAILABLE,
            ..
        })
    ));
    adapter
        .sync_bans(&addresses, &added, false, true)
        .await
        .unwrap();
    adapter.sync_bans(&[], &[], true, false).await.unwrap();

    let calls = server.state.calls.lock().unwrap();
    let writes: Vec<_> = calls
        .iter()
        .filter(|call| call.method == Method::POST && !call.path.ends_with("/login"))
        .collect();
    assert_eq!(writes.len(), 5);
    assert_eq!(
        writes[2].form["peers"],
        "203.0.113.1:1000|[2001:db8::1]:7000|198.51.100.2"
    );
    assert_eq!(
        serde_json::from_str::<Value>(&writes[3].form["json"]).unwrap(),
        json!({"banned_IPs": "203.0.113.1\n::ffff:cb00:7101\n2001:db8::1"})
    );
    assert_eq!(
        serde_json::from_str::<Value>(&writes[4].form["json"]).unwrap(),
        json!({"banned_IPs": ""})
    );
}

#[tokio::test]
async fn bearer_observer_never_writes_preferences_or_bans() {
    let server = Server::start(Mock {
        api_key: true,
        ..Mock::default()
    })
    .await;
    let adapter = Qbittorrent::read_only(Config {
        api_key: "qbt_fixture".into(),
        ..server.config()
    })
    .unwrap();
    adapter.login().await.unwrap();
    assert_eq!(adapter.peers("public").await.unwrap().len(), 3);
    assert!(matches!(
        adapter.replace_bans(&[]).await,
        Err(Error::ReadOnly)
    ));
    assert!(matches!(
        adapter.add_bans(&["203.0.113.1:9000"]).await,
        Err(Error::ReadOnly)
    ));
    assert!(
        server
            .state
            .calls
            .lock()
            .unwrap()
            .iter()
            .all(|call| call.method == Method::GET)
    );
}

#[tokio::test]
async fn handles_basic_auth_and_backs_off_after_rejected_logins() {
    let server = Server::start(Mock {
        proxy_auth: true,
        reject_login: true,
        ..Mock::default()
    })
    .await;
    let adapter = Qbittorrent::read_only(Config {
        basic_auth: BasicAuth {
            user: "proxy".into(),
            pass: "pass".into(),
        },
        ..server.config()
    })
    .unwrap();
    for _ in 0..15 {
        assert!(matches!(adapter.login().await, Err(Error::Authentication)));
    }
    let call_count = server.state.calls.lock().unwrap().len();
    assert!(matches!(adapter.login().await, Err(Error::LoginBackoff)));
    assert_eq!(server.state.calls.lock().unwrap().len(), call_count);
}

#[tokio::test]
async fn enhanced_shadow_bans_use_distinct_preference_and_peer_filter() {
    let enabled = Server::start(Mock {
        shadow_ban: true,
        ..Mock::default()
    })
    .await;
    let client = Qbittorrent::enhanced(Config {
        use_shadow_ban: true,
        increment_ban: true,
        ..enabled.config()
    })
    .unwrap();
    client.login().await.unwrap();
    assert!(client.supports_range_bans().await);
    assert_eq!(client.peers("public").await.unwrap().len(), 2);
    client.add_bans(&["203.0.113.1:9000"]).await.unwrap();
    client
        .replace_bans(&["2001:db8:0:1:2:3:4:5".parse().unwrap()])
        .await
        .unwrap();
    {
        let calls = enabled.state.calls.lock().unwrap();
        let incremental = calls
            .iter()
            .find(|call| call.path.ends_with("/shadowbanPeers"))
            .unwrap();
        assert_eq!(incremental.form["peers"], "203.0.113.1:9000");
        let full: Value = serde_json::from_str(&calls.last().unwrap().form["json"]).unwrap();
        assert_eq!(
            full,
            json!({"shadow_banned_IPs": "2001:db8:0:1:2:3:4:5\n2001:db8::/52"})
        );
    }

    let disabled = Server::start(Mock::default()).await;
    let observer = Qbittorrent::enhanced_read_only(Config {
        use_shadow_ban: true,
        ..disabled.config()
    })
    .unwrap();
    assert!(matches!(
        observer.login().await,
        Err(Error::ShadowBanDisabled)
    ));
    assert!(
        disabled
            .state
            .calls
            .lock()
            .unwrap()
            .iter()
            .all(|call| { call.method == Method::GET || call.path.ends_with("/auth/login") })
    );
}

#[tokio::test]
async fn traffic_limits_listen_port_and_tracker_updates_match_wire_contract() {
    let server = Server::start(Mock::default()).await;
    let client = Qbittorrent::new(server.config()).unwrap();
    client.login().await.unwrap();
    let traffic = client.traffic_statistics().await.unwrap();
    assert_eq!((traffic.uploaded, traffic.downloaded), (1800000, 2400000));
    let limits = client.speed_limits().await.unwrap();
    assert_eq!((limits.upload, limits.download), (5000, 9000));
    assert_eq!(client.listen_port().await.unwrap(), 6881);
    client
        .set_speed_limits(SpeedLimits {
            upload: -1,
            download: 12000,
        })
        .await
        .unwrap();
    client.set_listen_port(6882).await.unwrap();

    let trackers = client.trackers("public").await.unwrap();
    assert_eq!(trackers[0].urls, ["udp://a.example"]);
    assert_eq!(
        trackers[1].urls,
        ["udp://b.example", "https://backup.example"]
    );
    let desired = [TrackerTier {
        tier: 0,
        urls: vec!["udp://new.example".into(), "https://new.example".into()],
    }];
    client.replace_trackers("public", &desired).await.unwrap();

    let calls = server.state.calls.lock().unwrap();
    let writes: Vec<_> = calls
        .iter()
        .filter(|call| call.method == Method::POST && !call.path.ends_with("/login"))
        .collect();
    assert_eq!(writes.len(), 5);
    assert_eq!(
        serde_json::from_str::<Value>(&writes[1].form["json"]).unwrap(),
        json!({
            "up_limit": 0,
            "dl_limit": 12000,
            "alt_up_limit": 0,
            "alt_dl_limit": 12000,
            "limit_utp_rate": true,
            "limit_lan_peers": true,
            "scheduler_enabled": false
        })
    );
    assert_eq!(
        serde_json::from_str::<Value>(&writes[2].form["json"]).unwrap(),
        json!({"listen_port": 6882})
    );
    assert!(writes[3].path.ends_with("/removeTrackers"));
    assert_eq!(
        writes[3].form["urls"],
        "udp://a.example|udp://b.example|https://backup.example"
    );
    assert!(writes[4].path.ends_with("/addTrackers"));
    assert_eq!(
        writes[4].form["urls"],
        "udp://new.example\nhttps://new.example"
    );
}
