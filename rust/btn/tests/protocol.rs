use std::collections::{BTreeMap, VecDeque};
use std::io::Read;
use std::net::{IpAddr, SocketAddr};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use axum::body::to_bytes;
use axum::extract::{ConnectInfo, Request, State};
use axum::http::{HeaderMap, Method, StatusCode, Uri};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router};
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use flate2::read::GzDecoder;
use pbh_btn::reports::{
    BanHistory, LegacyBan, LegacyBanEvent, LegacyPeer, PeerHistory, Swarm, TorrentIdentifier,
    peer_history_start,
};
use pbh_btn::{
    BtnClient, BtnRuleMatch, Config, CreateComment, Error, HeartbeatRoute, IpListCache,
    PeerRuleInput, ReconfigureResult, Refresh, RuleSource, UpdateComment,
};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use tokio::net::TcpListener;
use tokio::task::JoinHandle;

#[derive(Default)]
struct Mock {
    config: Option<Value>,
    require_authorization: bool,
    counts: VecDeque<(StatusCode, &'static str)>,
    rule_responses: VecDeque<RuleResponse>,
    report_statuses: VecDeque<StatusCode>,
    heartbeat_address: Option<&'static str>,
    requests: Vec<Observed>,
}

struct RuleResponse {
    status: StatusCode,
    revision: Option<&'static str>,
    body: &'static str,
}

struct Observed {
    method: Method,
    uri: Uri,
    headers: HeaderMap,
    payload: Value,
    source: SocketAddr,
}

struct Fixture {
    url: String,
    state: Arc<Mutex<Mock>>,
    task: JoinHandle<()>,
}

impl Fixture {
    async fn start(mock: Mock) -> Self {
        Self::from_listener(TcpListener::bind("127.0.0.1:0").await.unwrap(), mock)
    }

    fn from_listener(listener: TcpListener, mock: Mock) -> Self {
        let state = Arc::new(Mutex::new(mock));
        let app = Router::new().fallback(serve).with_state(state.clone());
        let url = format!(
            "http://{}/gateway/config?installation=fixture",
            listener.local_addr().unwrap()
        );
        let task = tokio::spawn(async move {
            axum::serve(
                listener,
                app.into_make_service_with_connect_info::<SocketAddr>(),
            )
            .await
            .unwrap()
        });
        Self { url, state, task }
    }

    fn config(&self) -> Config {
        Config {
            enabled: true,
            config_url: self.url.clone(),
            ..Config::default()
        }
    }

    fn client(&self) -> BtnClient {
        BtnClient::new(self.config(), "fixture-id").unwrap()
    }
}

impl Drop for Fixture {
    fn drop(&mut self) {
        self.task.abort();
    }
}

fn default_config() -> Value {
    json!({"min_protocol_version": 20, "max_protocol_version": 22,
        "proof_of_work_captcha": {"endpoint": "proof?tag=keep"},
        "ability": {
            "ip_query": {
                "endpoint": "queryIp?tag=keep", "lookup_endpoint": "queryIp/lookup?tag=keep",
                "comments_endpoint": "queryIp/comments/", "comment_counts_endpoint": "queryIp/commentCounts",
                "iframe_endpoint": "/iframe?mode=legacy", "pow_captcha": true, "comment_counts_pow_captcha": false,
            },
            "heartbeat": {"endpoint": "heartbeat", "interval": 10000, "random_initial_delay": 2000,
                          "multi_if": true, "pow_captcha": true},
            "reconfigure": {"interval": 5000, "random_initial_delay": 1000, "version": "1"}
        }
    })
}

#[tokio::test]
async fn installation_identity_preserves_absent_ids_and_hashes_the_original_hardware_text() {
    let fixture = Fixture::start(Mock::default()).await;
    let policy = pbh_network::ProxyPolicy::default();
    let cases = [
        (
            None,
            None,
            "",
            "e42d9cfa692c649db15779b45db58fa5544582a4142e0fc8089e6c8a9cf969b9",
        ),
        (
            Some(""),
            None,
            "",
            "13f64e8f9c2289fa7171e589f65ff18d55656e3b0e2fb1ec6428c0acc9fac5e3",
        ),
        (
            Some("fixture-id"),
            None,
            "fixture-id",
            "6291705dc5d7080051c3b558642768f0082318795b62004602595ec43a2e97ec",
        ),
        (
            Some("fixture-id"),
            Some("04EC1234-ABCD-4321-8765-1234ABCDEF99"),
            "fixture-id",
            "8f9881da31379efff9e7f41ed38c0ffe52363180c922ad087759e5bc37e1b2cb",
        ),
    ];
    for (installation, hardware, expected_id, expected_hardware) in cases {
        let plan =
            BtnClient::with_proxy_identity(fixture.config(), installation, hardware, &policy)
                .unwrap()
                .negotiate()
                .await
                .unwrap();
        let url = plan.ip_query.unwrap().iframe_url("192.0.2.1").unwrap();
        let query: BTreeMap<_, _> = url.query_pairs().collect();
        assert_eq!(query["installationId"], expected_id);
        assert_eq!(query["hardwareId"], expected_hardware);
        assert_eq!(
            fixture
                .state
                .lock()
                .unwrap()
                .requests
                .last()
                .unwrap()
                .headers["x-btn-installationid"],
            expected_id
        );
    }
}

async fn serve(
    ConnectInfo(source): ConnectInfo<SocketAddr>,
    State(state): State<Arc<Mutex<Mock>>>,
    request: Request,
) -> Response {
    let (parts, body) = request.into_parts();
    let payload = to_bytes(body, 16 * 1024).await.unwrap();
    let payload = if parts
        .headers
        .get("content-encoding")
        .is_some_and(|header| header == "gzip")
    {
        let mut decoded = Vec::new();
        GzDecoder::new(&payload[..])
            .read_to_end(&mut decoded)
            .unwrap();
        serde_json::from_slice(&decoded).unwrap()
    } else {
        serde_json::from_slice(&payload).unwrap_or(Value::Null)
    };
    let path = parts.uri.path().to_owned();
    if path == "/slow" {
        tokio::time::sleep(Duration::from_millis(350)).await;
    }
    let mut mock = state.lock().unwrap();
    let is_authorized = !mock.require_authorization || parts.headers.contains_key("authorization");
    mock.requests.push(Observed {
        uri: parts.uri,
        method: parts.method.clone(),
        headers: parts.headers,
        payload,
        source,
    });
    if !is_authorized {
        return StatusCode::UNAUTHORIZED.into_response();
    }
    match path.as_str() {
        "/gateway/config" => {
            Json(mock.config.clone().unwrap_or_else(default_config)).into_response()
        }
        "/gateway/proof" => Json(json!({"id":"proof-id", "challengeBase64":"Y2hhbGxlbmdl",
                                          "algorithm":"SHA-256", "difficultyBits":8}))
        .into_response(),
        "/gateway/queryIp/commentCounts" => {
            let (status, body) = mock.counts.pop_front().unwrap();
            (status, [("content-type", "application/json")], body).into_response()
        }
        "/gateway/queryIp/comments/42" if parts.method == Method::DELETE => {
            StatusCode::NO_CONTENT.into_response()
        }
        "/gateway/queryIp" => Json(json!({"color":"green"})).into_response(),
        "/gateway/heartbeat" => {
            Json(json!({"external_ip":mock.heartbeat_address.unwrap_or("192.0.2.55")}))
                .into_response()
        }
        path if path.starts_with("/gateway/report/") => {
            let status = mock
                .report_statuses
                .pop_front()
                .unwrap_or(StatusCode::NO_CONTENT);
            (status, [("location", "/should-not-follow")]).into_response()
        }
        "/gateway/allow" | "/gateway/deny" | "/gateway/rules" => {
            let reply = mock.rule_responses.pop_front().unwrap();
            let mut response = (reply.status, reply.body).into_response();
            if let Some(revision) = reply.revision {
                response.headers_mut().insert(
                    "x-btn-contentversion",
                    axum::http::HeaderValue::from_static(revision),
                );
            }
            response
        }
        _ => Json(json!({})).into_response(),
    }
}

fn rule_config() -> Value {
    let mut config = default_config();
    for (capability, endpoint) in [
        ("ip_allowlist", "allow?tag=keep"),
        ("ip_denylist", "deny"),
        ("rule_peer_identity", "rules"),
    ] {
        config["ability"][capability] =
            json!({"endpoint":endpoint,"interval":3000,"random_initial_delay":200});
    }
    config["ability"]["ip_allowlist"]["pow_captcha"] = Value::Bool(true);
    config
}

fn report_config(minimum: u32) -> Value {
    let mut config = default_config();
    config["min_protocol_version"] = json!(minimum);
    for (name, endpoint) in [
        ("submit_bans", "report/bans?tag=keep"),
        ("submit_swarm", "report/swarm"),
        ("submit_histories", "report/histories"),
        ("submit_peers", "report/peers"),
    ] {
        config["ability"][name] = json!({
            "endpoint":endpoint, "interval":3000,"random_initial_delay":200,"pow_captcha":true,
        });
    }
    config
}

const INFO_HASH: &str = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";
const TORRENT_IDENTIFIER: &str = "50f1fc3e79cc6646fb905960314786bd49645764dc80377fcd2d39fb540b2830";

#[tokio::test]
async fn submits_modern_reports_with_gzip_authentication_and_distinct_proofs() {
    let fixture = Fixture::start(Mock {
        config: Some(report_config(20)),
        require_authorization: true,
        report_statuses: VecDeque::from([
            StatusCode::BAD_REQUEST,
            StatusCode::NO_CONTENT,
            StatusCode::FOUND,
            StatusCode::NO_CONTENT,
        ]),
        ..Mock::default()
    })
    .await;
    let config = Config {
        submit: true,
        ..fixture.config()
    };
    let reports = BtnClient::new(config, "fixture-id")
        .unwrap()
        .negotiate()
        .await
        .unwrap()
        .reports;
    assert!(reports.legacy_bans.is_none());
    assert!(reports.legacy_peers.is_none());

    let torrent_identifier = TorrentIdentifier::from_info_hash(INFO_HASH);
    assert_eq!(torrent_identifier.as_str(), TORRENT_IDENTIFIER);
    assert_eq!(
        TorrentIdentifier::from_info_hash(&INFO_HASH.to_lowercase()),
        torrent_identifier
    );
    let address = "2001:db8::1".parse().unwrap();
    let (bans, swarm, histories) = (
        reports.ban_history.unwrap(),
        reports.swarm.unwrap(),
        reports.peer_history.unwrap(),
    );
    assert_eq!(bans.schedule.interval, Duration::from_secs(3));
    assert_eq!(
        bans.schedule.random_initial_delay,
        Duration::from_millis(200)
    );
    assert_eq!(bans.initial_delay_floor(), Duration::ZERO);
    let ban_row = BanHistory {
        ban_at: 1_700_000_030_001,
        peer_ip: address,
        peer_port: 6881,
        peer_id: None,
        peer_client_name: Some("Test torrent client".into()),
        peer_progress: 0.5,
        peer_flag: None,
        torrent_identifier: torrent_identifier.clone(),
        torrent_is_private: true,
        torrent_size: 1200,
        from_peer_traffic: 100,
        to_peer_traffic: 200,
        downloader_progress: 0.25,
        module: Some("tst".into()),
        rule: "mock-rule".into(),
        description: "a rule matched".into(),
        structured_data: json!({"sample":null}),
    };
    assert!(matches!(
        bans.send(std::slice::from_ref(&ban_row)).await,
        Err(Error::Upstream { status: 400, .. })
    ));
    assert_eq!(bans.send(&[]).await.unwrap(), 0);
    let mut invalid = ban_row;
    invalid.peer_progress = f64::NAN;
    assert!(matches!(
        bans.send(&[invalid]).await,
        Err(Error::ReportEncoding("submit_bans"))
    ));

    assert_eq!(
        swarm
            .send(&[Swarm {
                torrent_identifier: torrent_identifier.clone(),
                torrent_is_private: None,
                torrent_size: 1200,
                downloader: Some("local".into()),
                downloader_progress: 0.5,
                peer_ip: address,
                peer_port: 6881,
                peer_id: None,
                peer_client_name: None,
                peer_progress: 0.1,
                to_peer_traffic: 250,
                to_peer_traffic_offset: -1,
                from_peer_traffic: 300,
                from_peer_traffic_offset: 150,
                first_time_seen: 1_700_000_010_521,
                last_time_seen: 1_700_000_030_001,
                peer_last_flags: Some("U".into()),
                upload_speed: 222,
                download_speed: 111,
                download_speed_max: 777,
                upload_speed_max: 555,
            }])
            .await
            .unwrap(),
        1
    );
    assert_eq!(
        histories
            .send(
                1_700_000_032_000,
                &[PeerHistory {
                    ip_address: address,
                    peer_id: None,
                    client_name: None,
                    torrent_identifier,
                    torrent_size: 1200,
                    downloaded: 91,
                    downloaded_offset: 34,
                    uploaded: 161,
                    uploaded_offset: 42,
                    first_time_seen: 1_700_000_010_521,
                    last_time_seen: 1_700_000_030_001,
                    peer_flag: None,
                }]
            )
            .await
            .unwrap(),
        1
    );
    let state = fixture.state.lock().unwrap();
    let submissions = state
        .requests
        .iter()
        .filter(|request| request.method == Method::POST)
        .collect::<Vec<_>>();
    assert_eq!(submissions.len(), 8);
    for pair in submissions.chunks_exact(2) {
        assert_eq!(pair[0].payload, pair[1].payload);
        assert!(!pair[0].headers.contains_key("authorization"));
        assert!(pair[1].headers.contains_key("authorization"));
        for request in pair {
            assert_eq!(request.headers["content-encoding"], "gzip");
            assert_eq!(request.headers["content-type"], "application/json");
            assert_eq!(request.headers["x-btn-powid"], "proof-id");
        }
    }
    assert_eq!(submissions[0].uri.query(), Some("tag=keep"));
    assert_eq!(
        submissions[0].payload,
        json!({"bans":[{
            "ban_at":1_700_000_030_001_i64, "peer_ip":"2001:db8:0:0:0:0:0:1", "peer_port":6881,
            "peer_id":null,"peer_client_name":"Test torrent client", "peer_progress":0.5,
            "peer_flag":null,"torrent_identifier":TORRENT_IDENTIFIER,"torrent_is_private":true,
            "torrent_size":1200,"from_peer_traffic":100,"to_peer_traffic":200,"downloader_progress":0.25,
            "module":"tst","rule":"mock-rule","description":"a rule matched","structured_data":"{\"sample\":null}"
        }]})
    );
    assert_eq!(submissions[2].payload, json!({"bans":[]}));
    assert_eq!(
        submissions[4].payload,
        json!({"swarms":[{
            "torrent_identifier":TORRENT_IDENTIFIER,"torrent_is_private":null,"torrent_size":1200,
            "downloader":"local","downloader_progress":0.5,"peer_ip":"2001:db8:0:0:0:0:0:1",
            "peer_port":6881,"peer_id":null,"peer_client_name":null,"peer_progress":0.1,
            "to_peer_traffic":250,"to_peer_traffic_offset":-1,"from_peer_traffic":300,
            "from_peer_traffic_offset":150,"first_time_seen":1_700_000_010_521_i64,
            "last_time_seen":1_700_000_030_001_i64,"peer_last_flags":"U","upload_speed":222,
            "download_speed":111,"download_speed_max":777,"upload_speed_max":555
        }]})
    );
    assert_eq!(
        submissions[6].payload,
        json!({"populate_time":1_700_000_032_000_i64,"peers":[{
            "ip_address":"2001:db8:0:0:0:0:0:1","port":0,"peer_id":null,"client_name":null,
            "torrent_identifier":TORRENT_IDENTIFIER,"torrent_is_private":false,"torrent_size":1200,
            "downloaded":91,"downloaded_offset":34,"uploaded":161,"uploaded_offset":42,
            "first_time_seen":1_700_000_010_521_i64,"last_time_seen":1_700_000_030_001_i64,"peer_flag":null
        }]})
    );
    let proof_queries = state
        .requests
        .iter()
        .filter(|r| r.uri.path() == "/gateway/proof" && r.headers.contains_key("authorization"))
        .map(|r| r.uri.query().unwrap())
        .collect::<Vec<_>>();
    assert_eq!(
        proof_queries,
        [
            "tag=keep&type=submit_bans",
            "tag=keep&type=submit_bans",
            "tag=keep&type=submit_swarm",
            "tag=keep&type=submit_history"
        ]
    );
    assert!(
        state
            .requests
            .iter()
            .all(|request| request.uri.path() != "/should-not-follow")
    );
}

#[tokio::test]
async fn submits_legacy_snapshots_with_selected_bans_and_no_proof() {
    let fixture = Fixture::start(Mock {
        config: Some(report_config(19)),
        ..Mock::default()
    })
    .await;
    let config = Config {
        submit: true,
        ..fixture.config()
    };
    let reports = BtnClient::new(config, "fixture-id")
        .unwrap()
        .negotiate()
        .await
        .unwrap()
        .reports;
    assert!(reports.ban_history.is_none());
    assert!(reports.swarm.is_none());
    assert!(reports.peer_history.is_some());
    let peers = reports.legacy_peers.unwrap();
    assert_eq!(peers.initial_delay_floor(), peers.schedule.interval);
    let peer = LegacyPeer {
        ip_address: "192.0.2.1".into(),
        peer_port: 3000,
        peer_id: Some("peer".into()),
        client_name: None,
        torrent_identifier: TorrentIdentifier::from_info_hash(INFO_HASH),
        torrent_is_private: false,
        torrent_size: 1200,
        downloaded: 732,
        rt_download_speed: 47,
        uploaded: 122,
        rt_upload_speed: 32,
        peer_progress: 0.2,
        downloader_progress: 0.7,
        peer_flag: Some("I".into()),
    };
    assert_eq!(peers.send(6000, &[]).await.unwrap(), 0);
    assert_eq!(
        peers.send(6000, std::slice::from_ref(&peer)).await.unwrap(),
        1
    );
    let ban = LegacyBan {
        btn_ban: false,
        ban_unique_id: LegacyBan::id_from_seed("ban"),
        module: "test".into(),
        rule: "threshold matched".into(),
        peer,
        structured_data: json!({"fixture":true}),
    };
    let mut event = LegacyBanEvent {
        ban_at: 5010,
        disconnect: false,
        exclude_from_report: false,
        ban,
    };
    let mut duplicate = event.clone();
    duplicate.ban.ban_unique_id = LegacyBan::id_from_seed("later");
    let mut excluded = event.clone();
    excluded.exclude_from_report = true;
    excluded.ban.peer.peer_port += 1;
    let mut disconnect = event.clone();
    disconnect.disconnect = true;
    disconnect.ban.peer.peer_port += 2;
    let mut old = event.clone();
    old.ban_at = 5000;
    old.ban.peer.peer_port += 3;
    let final_id = duplicate.ban.ban_unique_id.clone();
    let bans = reports.legacy_bans.unwrap();
    assert_eq!(bans.initial_delay_floor(), bans.schedule.interval);
    let candidates = [event.clone(), duplicate, excluded, disconnect, old];
    assert_eq!(
        bans.send_acknowledged(6000, 5000, &candidates)
            .await
            .unwrap(),
        vec![final_id.as_str()]
    );
    event.exclude_from_report = true;
    assert_eq!(bans.send(6100, 5000, &[event]).await.unwrap(), 0);
    let state = fixture.state.lock().unwrap();
    assert_eq!(state.requests.len(), 3);
    let peer_payload = &state.requests[1].payload;
    assert_eq!(
        *peer_payload,
        json!({"populate_time":6000,"peers":[{
            "ip_address":"192.0.2.1","peer_port":3000,"peer_id":"peer","client_name":null,"torrent_identifier":TORRENT_IDENTIFIER,
            "torrent_is_private":false,"torrent_size":1200,"downloaded":732,"rt_download_speed":47,"uploaded":122,
            "rt_upload_speed":32,"peer_progress":0.2,"downloader_progress":0.7,"peer_flag":"I"
        }]})
    );
    assert_eq!(
        state.requests[2].payload,
        json!({"populate_time":6000,"bans":[{
            "btn_ban":false,"ban_unique_id":final_id,"module":"test","rule":"threshold matched",
            "peer":peer_payload["peers"][0],"structured_data":{"fixture":true}
        }]})
    );
    for request in &state.requests[1..] {
        assert_eq!(request.headers["content-encoding"], "gzip");
        assert!(!request.headers.contains_key("x-btn-powid"));
    }
}

#[tokio::test]
async fn submission_opt_out_ignores_advertised_report_endpoints() {
    let mut configuration = report_config(20);
    configuration["ability"]["submit_bans"]["endpoint"] = json!("invalid endpoint");
    configuration["ability"]["submit_swarm"]["interval"] = json!(0);
    let fixture = Fixture::start(Mock {
        config: Some(configuration),
        ..Mock::default()
    })
    .await;
    let config = Config {
        submit: false,
        ..fixture.config()
    };
    let negotiated = BtnClient::new(config, "fixture-id")
        .unwrap()
        .negotiate()
        .await
        .unwrap();
    assert!(!negotiated.submissions_allowed);
    assert!(negotiated.advertised_abilities.contains("submit_bans"));
    assert!(negotiated.reports.ban_history.is_none());
    assert!(negotiated.reports.swarm.is_none());
    assert!(negotiated.reports.peer_history.is_none());
    assert!(negotiated.reports.legacy_bans.is_none());
    assert!(negotiated.reports.legacy_peers.is_none());
    assert_eq!(fixture.state.lock().unwrap().requests.len(), 1);
}

#[test]
fn accepts_recent_peer_history_cutoffs_and_resets_unusable_ones() {
    let now = 1_700_000_000_000;
    let boundary = (now - 30_i64 * 24 * 60 * 60 * 1000).to_string();
    assert_eq!(
        peer_history_start(Some(&boundary), now),
        boundary.parse::<i64>().unwrap()
    );
    for unset in [None, Some("invalid"), Some("42")] {
        assert_eq!(peer_history_start(unset, now), now);
    }
}

#[tokio::test]
async fn polls_versioned_ip_lists_and_preserves_old_snapshots_on_unchanged_and_error() {
    let fixture = Fixture::start(Mock {
        config: Some(rule_config()),
        rule_responses: VecDeque::from([
            RuleResponse { status: StatusCode::OK, revision: Some("new"), body: "# broad\n192.0.2.0/24\n192.0.2.9 #narrow\n198.051.100.001,198.051.100.004,001,covered\n2001:db8::1 #six\n203.0.113.0,203.0.113.255,200,disabled\n" },
            RuleResponse { status: StatusCode::OK, revision: None, body: "192.0.2.9 #denied\n203.0.113.1 #blocked\n" },
            RuleResponse { status: StatusCode::NO_CONTENT, revision: None, body: "" },
            RuleResponse { status: StatusCode::SERVICE_UNAVAILABLE, revision: None, body: "temporary" },
        ]),
        ..Mock::default()
    }).await;
    let negotiated = fixture.client().negotiate().await.unwrap();
    let allow = negotiated.ip_allowlist.as_ref().unwrap();
    let deny = negotiated.ip_denylist.as_ref().unwrap();
    assert_eq!(
        allow.cache_keys(),
        (
            "btn.ability.ip_allowlist.cache.version",
            "btn.ability.ip_allowlist.cache.value"
        )
    );
    assert_eq!(allow.schedule.interval, Duration::from_secs(3));
    let cache = IpListCache {
        revision: Some("restore".into()),
        body: "192.0.2.9 #cached\n".into(),
    };
    assert!(allow.restore_cache(cache.clone()));
    assert!(!allow.restore_cache(cache));
    let cached = allow.snapshot().unwrap();
    assert_eq!(
        cached.matching_comment("192.0.2.9".parse().unwrap()),
        Some("cached")
    );

    assert_eq!(allow.refresh().await.unwrap(), Refresh::Updated);
    assert_eq!(deny.refresh().await.unwrap(), Refresh::Updated);
    assert_eq!(
        deny.snapshot().unwrap().cache().revision.as_deref(),
        Some("unknown")
    );
    assert_eq!(allow.refresh().await.unwrap(), Refresh::Unchanged);
    assert!(matches!(
        allow.refresh().await,
        Err(Error::Upstream { status: 503, .. })
    ));
    let active = allow.snapshot().unwrap();
    assert_eq!(active.cache().revision.as_deref(), Some("new"));
    assert_eq!(active.len(), 4);
    assert_eq!(
        active.matching_comment("198.41.100.7".parse().unwrap()),
        Some("covered")
    );
    assert_eq!(
        active.matching_comment("2001:db8::1".parse().unwrap()),
        Some("six")
    );
    assert_eq!(
        cached.matching_comment("192.0.2.9".parse().unwrap()),
        Some("cached")
    );

    let mut peer = PeerRuleInput {
        address: "::ffff:192.0.2.9".parse().unwrap(),
        port: 1000,
        peer_id: None,
        client_name: None,
        handshaking: true,
    };
    assert_eq!(
        negotiated.check_peer(peer).unwrap(),
        Some(BtnRuleMatch::AllowIp {
            comment: " broad".into()
        })
    );
    peer.address = "203.0.113.1".parse().unwrap();
    assert_eq!(
        negotiated.check_peer(peer).unwrap(),
        Some(BtnRuleMatch::DenyIp {
            comment: "blocked".into()
        })
    );
    let state = fixture.state.lock().unwrap();
    assert_eq!(
        state.requests[1].uri.query(),
        Some("tag=keep&type=ip_allowlist")
    );
    assert_eq!(state.requests[2].uri.query(), Some("tag=keep&rev=restore"));
    assert_eq!(state.requests[3].uri.query(), Some("rev=initial"));
    assert_eq!(state.requests[5].uri.query(), Some("tag=keep&rev=new"));
    assert_eq!(state.requests[7].uri.query(), Some("tag=keep&rev=new"));
}

#[tokio::test]
async fn online_identity_rules_ignore_script_fields_and_retain_cache_on_invalid_updates() {
    const RULES: &str = r#"{
        "version":"fresh",
        "peer_id":{"ids":["{\"method\":\"STARTS_WITH\",\"content\":\"-pbh\"}"]},
        "client_name":{"names":["{\"method\":\"EQUALS\",\"content\":\"Example\"}"]},
        "ip":{"range":["192.0.2.0/24"]},
        "port":{"ports":[12345]},
        "script":{"unused":"unrecognized script text"}
    }"#;
    let mut config = rule_config();
    config["min_protocol_version"] = json!(19);
    config["ability"]["rules"] = config["ability"]["rule_peer_identity"].clone();
    let fixture = Fixture::start(Mock {
        config: Some(config),
        rule_responses: VecDeque::from([
            RuleResponse {
                status: StatusCode::OK,
                revision: None,
                body: RULES,
            },
            RuleResponse {
                status: StatusCode::OK,
                revision: None,
                body: r#"{"version":"invalid","ip":{"bad":["not-an-ip"]}}"#,
            },
        ]),
        ..Mock::default()
    })
    .await;
    let negotiated = fixture.client().negotiate().await.unwrap();
    assert!(negotiated.uses_legacy_protocol);
    assert!(negotiated.ip_allowlist.is_none());
    assert!(negotiated.ip_denylist.is_none());
    let identity = negotiated.rules.as_ref().unwrap();
    assert!(
        identity
            .restore_cache(r#"{"version":"old","script":{"ignored":"ignored"}}"#)
            .unwrap()
    );
    assert_eq!(identity.refresh().await.unwrap(), Refresh::Updated);
    assert!(matches!(
        identity.refresh().await,
        Err(Error::InvalidResponse(_))
    ));
    let active = identity.snapshot().unwrap();
    assert_eq!(active.version(), Some("fresh"));
    let cached: Value = serde_json::from_str(active.cache_json()).unwrap();
    assert_eq!(cached["script"], json!({}));
    assert!(!active.cache_json().contains("unrecognized script text"));
    let mut peer = PeerRuleInput {
        address: "192.0.2.1".parse().unwrap(),
        port: 12345,
        peer_id: Some("-PBH123"),
        client_name: Some("Example"),
        handshaking: false,
    };
    for expected in [
        RuleSource::Port,
        RuleSource::Ip,
        RuleSource::ClientName,
        RuleSource::PeerId,
    ] {
        let Some(BtnRuleMatch::Identity(actual)) = negotiated.check_peer(peer).unwrap() else {
            panic!("missing identity match")
        };
        assert_eq!(actual.source, expected);
        match expected {
            RuleSource::Port => peer.port = 1,
            RuleSource::Ip => peer.address = "198.51.100.1".parse().unwrap(),
            RuleSource::ClientName => peer.client_name = None,
            RuleSource::PeerId => peer.handshaking = true,
        }
    }
    assert!(negotiated.check_peer(peer).unwrap().is_none());
    let state = fixture.state.lock().unwrap();
    assert_eq!(state.requests[1].uri.query(), Some("rev=old"));
    assert_eq!(state.requests[2].uri.query(), Some("rev=fresh"));
}

#[tokio::test]
async fn negotiates_legacy_headers_and_retries_user_authentication_once() {
    let fixture = Fixture::start(Mock {
        config: Some(json!({"min_protocol_version":19,"max_protocol_version":21,"ability":{
            "ip_query": {"endpoint":"/query", "lookup_endpoint":42, "comments_endpoint":"ftp://wrong"}
        }})),
        require_authorization: true,
        ..Mock::default()
    }).await;
    let mut config = fixture.config();
    config.submit = false;
    config.app_id = "pbh".into();
    config.app_secret = "fixture-key".into();
    let result = BtnClient::new(config, "fixture-id")
        .unwrap()
        .negotiate()
        .await
        .unwrap();
    assert!(result.uses_legacy_protocol);
    assert!(!result.submissions_allowed);
    let query = result.ip_query.unwrap();
    assert!(query.lookup_endpoint().is_none());
    assert!(query.comments_endpoint().is_none());
    assert!(query.comment_counts_endpoint().is_none());
    assert!(matches!(
        query.lookup("2001:db8::/48").await,
        Err(Error::EndpointUnavailable(_))
    ));
    let state = fixture.state.lock().unwrap();
    assert_eq!(state.requests.len(), 2);
    let first = &state.requests[0];
    assert_eq!(first.uri.query(), Some("installation=fixture"));
    assert_eq!(first.headers["authentication"], "Bearer pbh@fixture-key");
    assert_eq!(first.headers["btn-appid"], "pbh");
    assert_eq!(first.headers["btn-appsecret"], "fixture-key");
    assert_eq!(first.headers["x-btn-appid"], "pbh");
    assert_eq!(first.headers["x-btn-appsecret"], "fixture-key");
    assert_eq!(first.headers["content-type"], "application/json");
    assert!(
        first.headers["user-agent"]
            .to_str()
            .unwrap()
            .ends_with("BTN-Protocol-Version/21")
    );
    assert!(!first.headers.contains_key("authorization"));
    assert!(!first.headers.contains_key("x-btn-installationid"));
    assert_eq!(
        state.requests[1].headers["authorization"],
        "Bearer pbh@fixture-key"
    );
}

#[tokio::test]
async fn negotiates_capabilities_and_proves_the_expected_queries() {
    let fixture = Fixture::start(Mock::default()).await;
    let negotiated = fixture.client().negotiate().await.unwrap();
    assert!(negotiated.advertised_abilities.contains("heartbeat"));
    assert!(!negotiated.uses_legacy_protocol);
    let query = negotiated.ip_query.unwrap();
    assert_eq!(query.iframe_endpoint().unwrap().path(), "/iframe");
    let iframe = query.iframe_url("2001:db8::1").unwrap();
    let frame: BTreeMap<_, _> = iframe.query_pairs().into_owned().collect();
    assert_eq!(frame["mode"], "legacy");
    assert_eq!(frame["ip"], "2001:db8::1");
    assert_eq!(frame["appId"], "example-app-id");
    assert_eq!(frame["appSecret"], "example-app-secret");
    assert_eq!(frame["installationId"], "fixture-id");
    assert_eq!(
        frame["hardwareId"],
        format!("{:x}", Sha256::digest(b"IID-fixture-id"))
    );
    let scope = "2001:db8::/48";
    assert_eq!(
        query.query("2001:db8::1").await.unwrap().unwrap()["labels"],
        json!([])
    );
    query.lookup(scope).await.unwrap();
    query.list_comments(scope, 2, 200).await.unwrap();
    query
        .create_comment(&CreateComment {
            scope: scope.into(),
            parent_id: None,
            content: "comment".into(),
        })
        .await
        .unwrap();
    query
        .update_comment(
            42,
            &UpdateComment {
                content: "edited".into(),
                revision: Some(1),
            },
        )
        .await
        .unwrap();
    query.delete_comment(42).await.unwrap();
    let state = fixture.state.lock().unwrap();
    assert_eq!(
        state.requests[0].headers["x-btn-installationid"],
        "fixture-id"
    );
    let actions: Vec<_> = state.requests[1..]
        .iter()
        .filter(|request| request.uri.path() != "/gateway/proof")
        .collect();
    assert_eq!(state.requests.len(), 1 + actions.len() * 2);
    assert_eq!(
        actions
            .iter()
            .map(|action| action.method.clone())
            .collect::<Vec<_>>(),
        [
            Method::GET,
            Method::GET,
            Method::GET,
            Method::POST,
            Method::PUT,
            Method::DELETE
        ]
    );
    assert_eq!(
        actions[0].uri.query(),
        Some("tag=keep&ip=2001%3Adb8%3A%3A1")
    );
    assert_eq!(
        actions[1].uri.query(),
        Some("tag=keep&query=2001%3Adb8%3A%3A%2F48")
    );
    assert_eq!(
        actions[2].uri.query(),
        Some("scope=2001%3Adb8%3A%3A%2F48&page=2&size=50")
    );
    assert_eq!(
        actions[3].payload,
        json!({"scope":scope,"parentId":null,"content":"comment"})
    );
    assert_eq!(actions[4].payload, json!({"content":"edited","revision":1}));
    assert_eq!(actions[5].uri.path(), "/gateway/queryIp/comments/42");
    for action in actions {
        assert_eq!(action.headers["x-btn-powid"], "proof-id");
        let solution = STANDARD
            .decode(action.headers["x-btn-powsolution"].to_str().unwrap())
            .unwrap();
        assert_eq!(solution.len(), 8);
        assert_eq!(
            Sha256::new()
                .chain_update(b"challenge")
                .chain_update(solution)
                .finalize()[0],
            0
        );
    }
}

#[tokio::test]
async fn comment_count_batches_cache_zeros_and_never_cache_partial_or_failed_responses() {
    let fixture = Fixture::start(Mock {
        counts: VecDeque::from([
            (StatusCode::OK, r#"{"192.0.2.1":0}"#),
            (StatusCode::TOO_MANY_REQUESTS, r#"{"message":"Slow down"}"#),
            (
                StatusCode::OK,
                r#"{"2001:db8::1":3,"192.0.2.2":1e9999999999}"#,
            ),
            (StatusCode::OK, r#"{"2001:db8::1":4e0,"192.0.2.2":2}"#),
        ]),
        ..Mock::default()
    })
    .await;
    let q = fixture
        .client()
        .negotiate()
        .await
        .unwrap()
        .ip_query
        .unwrap();
    let a = String::from("192.0.2.1");
    let b = String::from("2001:db8::1");
    let c = String::from("192.0.2.2");
    assert_eq!(
        q.query_comment_counts(std::slice::from_ref(&a))
            .await
            .unwrap(),
        BTreeMap::from([(a.clone(), 0)])
    );
    let batch = [a.clone(), b.clone(), b.clone(), c.clone()];
    let error = q.query_comment_counts(&batch).await.unwrap_err();
    assert!(
        matches!(error, Error::Upstream {status: 429, message: Some(message)} if message == "Slow down")
    );
    assert!(matches!(
        q.query_comment_counts(&batch).await,
        Err(Error::InvalidResponse("IP comment counts"))
    ));
    let expected = BTreeMap::from([(a, 0), (b, 4), (c, 2)]);
    assert_eq!(q.query_comment_counts(&batch).await.unwrap(), expected);
    assert_eq!(q.query_comment_counts(&batch).await.unwrap(), expected);
    let state = fixture.state.lock().unwrap();
    assert_eq!(state.requests.len(), 5);
    assert_eq!(state.requests[1].payload, json!(["192.0.2.1"]));
    for request in &state.requests[2..] {
        assert_eq!(request.payload, json!(["2001:db8::1", "192.0.2.2"]));
        assert!(!request.headers.contains_key("x-btn-powid"));
    }
}

#[tokio::test]
async fn count_proof_is_independent_of_legacy_query_proof() {
    let mut config = default_config();
    config["ability"]["ip_query"]["pow_captcha"] = Value::Bool(false);
    config["ability"]["ip_query"]["comment_counts_pow_captcha"] = Value::Bool(true);
    let fixture = Fixture::start(Mock {
        config: Some(config),
        counts: VecDeque::from([(StatusCode::OK, r#"{"192.0.2.1":0}"#)]),
        ..Mock::default()
    })
    .await;
    let q = fixture
        .client()
        .negotiate()
        .await
        .unwrap()
        .ip_query
        .unwrap();
    q.query("192.0.2.1").await.unwrap();
    let ips = [String::from("192.0.2.1")];
    q.query_comment_counts(&ips).await.unwrap();
    q.query_comment_counts(&ips).await.unwrap();
    let state = fixture.state.lock().unwrap();
    assert_eq!(state.requests.len(), 4);
    assert!(!state.requests[1].headers.contains_key("x-btn-powid"));
    assert_eq!(
        state.requests[2].uri.query(),
        Some("tag=keep&type=ip_query")
    );
    assert_eq!(state.requests[3].headers["x-btn-powid"], "proof-id");
}

#[tokio::test]
async fn heartbeat_distinguishes_credentials_and_reconfigure_detects_changes() {
    let fixture = Fixture::start(Mock::default()).await;
    let abilities = fixture.client().negotiate().await.unwrap();
    let heartbeat = abilities.heartbeat.unwrap();
    assert!(heartbeat.multi_interface);
    assert_eq!(heartbeat.schedule.interval, Duration::from_secs(10));
    assert_eq!(
        heartbeat.schedule.random_initial_delay,
        Duration::from_secs(2)
    );
    assert_eq!(
        heartbeat.send_default().await.unwrap().as_deref(),
        Some("192.0.2.55")
    );
    assert_eq!(
        heartbeat
            .send_for_interface("127.0.0.1".parse().unwrap())
            .await
            .unwrap()
            .as_deref(),
        Some("192.0.2.55")
    );
    {
        let observed = fixture.state.lock().unwrap();
        assert_eq!(
            observed.requests[1].uri.query(),
            Some("tag=keep&type=heartbeat")
        );
        assert_eq!(observed.requests[2].payload, json!({"ifaddr":"default"}));
        assert!(!observed.requests[2].headers.contains_key("x-btn-appid"));
        assert_eq!(observed.requests[2].headers["x-btn-powid"], "proof-id");
        assert_eq!(observed.requests[4].payload, json!({"ifaddr":"127.0.0.1"}));
        assert_eq!(
            observed.requests[4].headers["x-btn-appid"],
            "example-app-id"
        );
    }

    let reconfigure = abilities.reconfigure.unwrap();
    assert_eq!(reconfigure.version, "1");
    assert_eq!(
        reconfigure.check().await.unwrap(),
        ReconfigureResult::Unchanged
    );
    let mut changed = default_config();
    changed["ability"]["reconfigure"]["version"] = json!("2");
    fixture.state.lock().unwrap().config = Some(changed);
    assert_eq!(
        reconfigure.check().await.unwrap(),
        ReconfigureResult::Changed {
            version: "2".into()
        }
    );
    fixture.state.lock().unwrap().config = Some(json!({"ability":{}}));
    assert_eq!(
        reconfigure.check().await.unwrap(),
        ReconfigureResult::DisabledByServer
    );
}

#[tokio::test]
async fn multi_interface_heartbeat_binds_direct_and_explicit_proxy_requests() {
    let origin = Fixture::start(Mock::default()).await;
    let proxy = Fixture::start(Mock {
        heartbeat_address: Some("192.0.2.56"),
        ..Mock::default()
    })
    .await;
    let proxy_endpoint = reqwest::Url::parse(&proxy.url).unwrap();
    let policy = pbh_network::ProxyPolicy::new(&pbh_network::ProxyConfig {
        setting: 1,
        host: "127.0.0.1".into(),
        port: proxy_endpoint.port().unwrap().into(),
        ..pbh_network::ProxyConfig::default()
    })
    .unwrap();
    let heartbeat = BtnClient::with_proxy(origin.config(), "fixture-id", &policy)
        .unwrap()
        .negotiate()
        .await
        .unwrap()
        .heartbeat
        .unwrap();
    let source = if TcpListener::bind("127.0.0.2:0").await.is_ok() {
        "127.0.0.2"
    } else {
        "127.0.0.1"
    };
    let mapped = format!("::ffff:{source}");
    let candidates = [
        source,
        mapped.as_str(),
        "::1",
        "0.0.0.0",
        "::",
        "ff02::1",
        "224.0.0.1",
        "fe80::1",
        "255.255.255.255",
    ]
    .into_iter()
    .map(|address| address.parse::<IpAddr>().unwrap());
    let attempts = heartbeat.send_for_interfaces(candidates).await;
    assert_eq!(attempts.len(), 4);
    for attempt in attempts {
        if attempt.address != source {
            assert_eq!(attempt.address, "0:0:0:0:0:0:0:1");
            assert!(matches!(attempt.reply, Err(Error::HeartbeatAddressFamily)));
            continue;
        }
        let expected = match attempt.route {
            HeartbeatRoute::Direct => "192.0.2.55",
            HeartbeatRoute::ConfiguredProxy => "192.0.2.56",
        };
        assert_eq!(attempt.reply.unwrap().as_deref(), Some(expected));
    }
    assert_eq!(
        heartbeat.send_default().await.unwrap().as_deref(),
        Some("192.0.2.56")
    );

    let origin_requests = origin.state.lock().unwrap();
    let proxy_requests = proxy.state.lock().unwrap();
    assert_eq!(origin_requests.requests.len(), 1);
    let direct = &origin_requests.requests[0];
    let forwarded = proxy_requests
        .requests
        .iter()
        .filter(|call| call.uri.path() == "/gateway/heartbeat")
        .collect::<Vec<_>>();
    assert_eq!(forwarded.len(), 2);
    for call in [direct, forwarded[0]] {
        assert_eq!(call.method, Method::POST);
        assert_eq!(call.payload, json!({"ifaddr":source}));
        assert_eq!(call.source.ip(), source.parse::<IpAddr>().unwrap());
        assert!(call.headers.contains_key("authentication"));
        assert!(call.headers.contains_key("x-btn-appsecret"));
        assert!(call.headers.contains_key("x-btn-powid"));
    }
    assert_eq!(
        forwarded[0].uri.authority().unwrap().port_u16(),
        reqwest::Url::parse(&origin.url).unwrap().port()
    );
    assert_eq!(forwarded[1].payload, json!({"ifaddr":"default"}));
    assert!(!forwarded[1].headers.contains_key("x-btn-appsecret"));
    assert!(!forwarded[1].headers.contains_key("authentication"));
    assert!(forwarded[1].headers.contains_key("x-btn-powid"));
}

#[tokio::test]
async fn bound_ipv6_heartbeat_uses_java_address_text() {
    let Ok(listener) = TcpListener::bind("[::1]:0").await else {
        return;
    };
    let fixture = Fixture::from_listener(listener, Mock::default());
    let heartbeat = fixture
        .client()
        .negotiate()
        .await
        .unwrap()
        .heartbeat
        .unwrap();
    assert_eq!(
        heartbeat
            .send_for_interface(IpAddr::V6(std::net::Ipv6Addr::LOCALHOST))
            .await
            .unwrap()
            .as_deref(),
        Some("192.0.2.55")
    );
    let requests = fixture.state.lock().unwrap();
    let call = requests.requests.last().unwrap();
    assert_eq!(call.source.ip(), IpAddr::V6(std::net::Ipv6Addr::LOCALHOST));
    assert_eq!(call.payload, json!({"ifaddr":"0:0:0:0:0:0:0:1"}));
}

#[tokio::test]
async fn distinguishes_protocol_range_and_transport_timeout() {
    let fixture = Fixture::start(Mock {
        config: Some(json!({"min_protocol_version":22,"max_protocol_version":23,"ability":{}})),
        ..Mock::default()
    })
    .await;
    assert!(matches!(
        fixture.client().negotiate().await,
        Err(Error::IncompatibleProtocol {
            min: 22,
            max: 23,
            actual: 21
        })
    ));
    fixture.state.lock().unwrap().config =
        Some(json!({"min_protocol_version":19,"max_protocol_version":22,
        "ability":{"ip_query":{"endpoint":"/slow"}}}));
    let q = BtnClient::with_timeout(fixture.config(), "fixture-id", Duration::from_millis(150))
        .unwrap()
        .negotiate()
        .await
        .unwrap()
        .ip_query
        .unwrap();
    assert!(
        matches!(q.query("192.0.2.1").await, Err(Error::Transport { source, .. }) if source.is_timeout())
    );
    assert!(matches!(
        BtnClient::new(Config::default(), "fixture-id"),
        Err(Error::Disabled)
    ));
}
