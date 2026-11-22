//! Protected PBHPeerController routes backed by the negotiated BTN IP query ability.

use std::collections::{BTreeMap, BTreeSet};
use std::net::{IpAddr, SocketAddr};
use std::sync::{Arc, OnceLock};
use std::time::{Duration, Instant};

use axum::extract::{
    DefaultBodyLimit, Path, Query, State,
    rejection::{JsonRejection, QueryRejection},
};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post, put};
use axum::{Json, Router};
use pbh_btn::{
    BtnClient, CreateComment, DEFAULT_COMMENT_PAGE_SIZE, Error, IpQuery, MAX_COMMENT_COUNT_BATCH,
    UpdateComment,
};
use serde::Deserialize;
use serde_json::{Value, json};
use tokio::sync::Mutex;

use crate::ban_api::BanLanguage;
use crate::btn_runtime::{Ability, AbilityResult, BtnStatus, ConfigResult, list_details};

type ApiResult = Result<Json<Value>, ApiError>;
const CAPABILITY_TTL: Duration = Duration::from_secs(300);

struct ApiError(StatusCode, String);

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (
            self.0,
            Json(json!({"success": false, "message": self.1, "data": null})),
        )
            .into_response()
    }
}

impl From<Error> for ApiError {
    fn from(error: Error) -> Self {
        match error {
            Error::Disabled => Self(
                StatusCode::NOT_IMPLEMENTED,
                "BTN network is not enabled".into(),
            ),
            Error::EndpointUnavailable(_) => Self(
                StatusCode::NOT_IMPLEMENTED,
                "BTN IP Query capability is not available on this BTN instance".into(),
            ),
            Error::Upstream { status, message } => {
                let public = match status {
                    400 => StatusCode::BAD_REQUEST,
                    409 => StatusCode::CONFLICT,
                    429 => StatusCode::TOO_MANY_REQUESTS,
                    401 | 403 | 404 => StatusCode::UNPROCESSABLE_ENTITY,
                    _ => StatusCode::BAD_GATEWAY,
                };
                Self(
                    public,
                    message.unwrap_or_else(|| format!("BTN request failed: HTTP {status}")),
                )
            }
            Error::InvalidCommentCountBatch => {
                Self::bad_request("Expected between 1 and 50 IP addresses")
            }
            _ => Self(StatusCode::BAD_GATEWAY, "BTN request failed".into()),
        }
    }
}

impl ApiError {
    fn bad_request(message: &'static str) -> Self {
        Self(StatusCode::BAD_REQUEST, message.into())
    }
}

struct BtnState {
    client: Result<BtnClient, Error>,
    cache: Mutex<Option<(Instant, Option<IpQuery>)>>,
    display: BtnDisplay,
    status: BtnStatus,
}

struct BtnDisplay {
    app_id: String,
    masked_secret: String,
    config_url: String,
    language: BanLanguage,
    demo_mode: bool,
}

impl BtnState {
    async fn ability(&self) -> Result<IpQuery, ApiError> {
        if let Some(negotiated) = self.status.negotiated() {
            return negotiated
                .ip_query
                .clone()
                .ok_or_else(|| ApiError::from(Error::EndpointUnavailable("IP query")));
        }
        let client = self.client.as_ref().map_err(|error| match error {
            Error::Disabled => ApiError::from(Error::Disabled),
            _ => ApiError(
                StatusCode::BAD_GATEWAY,
                "BTN configuration is invalid".into(),
            ),
        })?;
        let mut cache = self.cache.lock().await;
        if let Some((checked, query)) = &*cache
            && checked.elapsed() < CAPABILITY_TTL
        {
            return query
                .clone()
                .ok_or_else(|| ApiError::from(Error::EndpointUnavailable("IP query")));
        }
        let query = client.negotiate().await?.ip_query;
        *cache = Some((Instant::now(), query.clone()));
        query.ok_or_else(|| ApiError::from(Error::EndpointUnavailable("IP query")))
    }
}

pub(crate) fn router(
    config: pbh_btn::Config,
    installation_id: Option<&str>,
    proxy: &pbh_network::ProxyPolicy,
    status: BtnStatus,
    locale: &str,
    demo_mode: bool,
) -> Router {
    let display = BtnDisplay {
        app_id: config.app_id.clone(),
        masked_secret: mask_secret(&config.app_secret),
        config_url: config.config_url.clone(),
        language: BanLanguage::from_locale(locale),
        demo_mode,
    };
    let state = Arc::new(BtnState {
        client: create_client(config, installation_id, proxy),
        cache: Mutex::new(None),
        display,
        status,
    });
    Router::new()
        .route("/api/modules/btn", get(status_view))
        .route("/api/peer/btnLookup", get(lookup))
        .route("/api/peer/btnComments", get(comments).post(create))
        .route("/api/peer/btnComments/{id}", put(update).delete(delete))
        .route("/api/peer/btnCommentCounts", post(counts))
        .route("/api/peer/btnQuery/{ip}", get(query))
        .route("/api/peer/btnQueryIframe/{ip}", get(iframe))
        .layer(DefaultBodyLimit::max(64 * 1024))
        .with_state(state)
}

pub(crate) fn create_client(
    config: pbh_btn::Config,
    installation_id: Option<&str>,
    proxy: &pbh_network::ProxyPolicy,
) -> Result<BtnClient, Error> {
    static HARDWARE_UUID: OnceLock<Option<String>> = OnceLock::new();
    let hardware = config
        .enabled
        .then(|| HARDWARE_UUID.get_or_init(pbh_plus::hardware_uuid))
        .and_then(Option::as_deref);
    BtnClient::with_proxy_identity(config, installation_id, hardware, proxy)
}

fn mask_secret(value: &str) -> String {
    if value.encode_utf16().count() <= 5 {
        return "******".into();
    }
    let mut units = 0;
    let prefix: String = value
        .chars()
        .take_while(|character| {
            units += character.len_utf16();
            units <= 5
        })
        .collect();
    format!("{prefix}*******")
}

async fn status_view(State(state): State<Arc<BtnState>>, headers: HeaderMap) -> Json<Value> {
    let display = &state.display;
    let language = BanLanguage::from_headers_or(&headers, display.language);
    let status = state.status.snapshot();
    let config_success = matches!(status.config, Some(ConfigResult::Connected { .. }));
    let config_result = status.config.as_ref().map(|status| {
        let message = match status {
            ConfigResult::Connected { submit: true } => json!({"key":"BTN_CONFIG_STATUS_SUCCESSFUL"}),
            ConfigResult::Connected { submit: false } => json!({"key":"BTN_CONFIG_STATUS_SUCCESSFUL_READ_ONLY"}),
            ConfigResult::HttpError(code) => json!({"key":"BTN_CONFIG_STATUS_UNSUCCESSFUL_HTTP_REQUEST","params":[display.config_url,code,""]}),
            ConfigResult::Incompatible { min, actual, .. } if actual < min => json!({
                "key":"BTN_CONFIG_STATUS_EXCEPTION",
                "params":["java.lang.IllegalStateException", {"key":"BTN_CONFIG_STATUS_UNSUCCESSFUL_INCOMPATIBLE_BTN_PROTOCOL_VERSION_CLIENT","params":[actual,min]}]
            }),
            ConfigResult::Incompatible { max, actual, .. } => json!({
                "key":"BTN_CONFIG_STATUS_EXCEPTION",
                "params":["java.lang.IllegalStateException", {"key":"BTN_CONFIG_STATUS_UNSUCCESSFUL_INCOMPATIBLE_BTN_PROTOCOL_VERSION_SERVER","params":[max,actual]}]
            }),
            ConfigResult::Failed(message) => json!({"key":"BTN_CONFIG_STATUS_EXCEPTION","params":[message,message]}),
        };
        language.render(&message)
    });
    let rules = status
        .negotiated
        .as_ref()
        .map(|negotiated| negotiated.rule_snapshot())
        .unwrap_or_default();
    let abilities = status
        .abilities
        .iter()
        .map(|(ability, result)| {
            ability_status(*ability, result, &rules, display.demo_mode, language)
        })
        .collect::<Vec<_>>();
    success(json!({
        "enabled":true,
        "configSuccess":config_success,
        "configResult":config_result,
        "abilities":abilities,
        "appId":display.app_id,
        "appSecret":display.masked_secret,
        "configUrl":display.config_url,
    }))
}

fn ability_status(
    ability: Ability,
    result: &AbilityResult,
    rules: &pbh_btn::RuleSnapshot,
    demo_mode: bool,
    language: BanLanguage,
) -> Value {
    let (name, title, description, params) = match ability {
        Ability::Allowlist => (
            "BtnAbilityIPAllowList",
            "BTN_ABILITY_IP_ALLOWLIST_TITLE",
            "BTN_ABILITY_IP_ALLOWLIST_DESCRIPTION",
            rules
                .ip_allowlist
                .as_deref()
                .map_or_else(|| json!(["initial", 0]), list_details),
        ),
        Ability::Denylist => (
            "BtnAbilityIPDenyList",
            "BTN_ABILITY_IP_DENYLIST_TITLE",
            "BTN_ABILITY_IP_DENYLIST_DESCRIPTION",
            rules
                .ip_denylist
                .as_deref()
                .map_or_else(|| json!(["initial", 0]), list_details),
        ),
        Ability::Rules => {
            let (version, [ips, peers, clients, ports]) =
                rules.rules.as_ref().map_or((Some("N/A"), [0; 4]), |rules| {
                    (rules.version(), rules.counts())
                });
            (
                "BtnAbilityRuleset (Legacy)",
                "BTN_ABILITY_RULES",
                "BTN_ABILITY_RULES_DESCRIPTION",
                json!([
                    version,
                    ips + peers + clients + ports,
                    ips,
                    peers,
                    clients,
                    ports,
                    0
                ]),
            )
        }
        Ability::Heartbeat => (
            "BtnAbilityHeartBeat",
            "BTN_HEARTBEAT_TITLE",
            "BTN_HEARTBEAT_DESCRIPTION",
            json!([result.heartbeat_description(demo_mode)]),
        ),
        Ability::Reconfigure => (
            "BtnAbilityReconfigure",
            "BTN_ABILITY_RECONFIGURE",
            "BTN_ABILITY_RECONFIGURE_DESCRIPTION",
            Value::Null,
        ),
        Ability::IpQuery => (
            "BtnAbilityIpQuery",
            "BTN_ABILITY_IP_QUERY_TITLE",
            "BTN_ABILITY_IP_QUERY_DESCRIPTION",
            Value::Null,
        ),
        Ability::BanHistory => (
            "BtnAbilitySubmitBans",
            "BTN_ABILITY_SUBMIT_BANS",
            "BTN_ABILITY_SUBMIT_BANS_DESCRIPTION",
            Value::Null,
        ),
        Ability::PeerHistory => (
            "BtnAbilitySubmitHistory (Legacy)",
            "BTN_ABILITY_SUBMIT_HISTORY",
            "BTN_ABILITY_SUBMIT_HISTORY_DESCRIPTION",
            Value::Null,
        ),
        Ability::Swarm => (
            "BtnAbilitySubmitSwarm",
            "BTN_ABILITY_SUBMIT_SWARM",
            "BTN_ABILITY_SUBMIT_SWARM_DESCRIPTION",
            Value::Null,
        ),
        Ability::LegacyPeers => (
            "BtnAbilitySubmitPeers (Legacy)",
            "BTN_ABILITY_SUBMIT_SNAPSHOT",
            "BTN_ABILITY_SUBMIT_SNAPSHOT_DESCRIPTION",
            Value::Null,
        ),
        Ability::LegacyBans => (
            "BtnAbilitySubmitBans (Legacy)",
            "BTN_ABILITY_SUBMIT_BANS",
            "BTN_ABILITY_SUBMIT_BANS_DESCRIPTION",
            Value::Null,
        ),
    };
    json!({
        "name":name,
        "displayName":language.render(&json!({"key":title})),
        "description":language.render(&json!({"key":description,"params":params})),
        "lastSuccess":result.success,
        "lastMessage":language.render(&result.message),
        "lastUpdateAt":result.at,
    })
}

fn success(data: Value) -> Json<Value> {
    Json(json!({"success": true, "message": null, "data": data}))
}

fn validate_scope(value: &str) -> Result<(), ApiError> {
    if value.trim().is_empty() || value.encode_utf16().count() > 128 {
        return Err(ApiError::bad_request("An IP address or CIDR is required"));
    }
    Ok(())
}

fn body<T>(body: Result<Json<T>, JsonRejection>) -> Result<T, ApiError> {
    body.map(|Json(value)| value)
        .map_err(|_| ApiError::bad_request("Invalid JSON request body"))
}

fn valid_id(id: i64) -> Result<i64, ApiError> {
    if id <= 0 {
        return Err(ApiError::bad_request("Invalid comment ID"));
    }
    Ok(id)
}

fn address(value: &str, allow_port: bool) -> Result<String, ApiError> {
    let unbracketed = value
        .strip_prefix('[')
        .and_then(|ip| ip.strip_suffix(']'))
        .unwrap_or(value);
    let ip = unbracketed.parse::<IpAddr>().ok().or_else(|| {
        if allow_port {
            value.parse::<SocketAddr>().ok().map(|socket| socket.ip())
        } else {
            None
        }
    });
    ip.map(|ip| ip.to_canonical().to_string())
        .ok_or_else(|| ApiError::bad_request("Expected an IP address"))
}

async fn iframe(
    State(state): State<Arc<BtnState>>,
    Path(ip): Path<String>,
) -> Result<Response, ApiError> {
    let address = address(&ip, true)?;
    let ability = match state.ability().await {
        Ok(ability) => ability,
        Err(error) if error.0 == StatusCode::NOT_IMPLEMENTED => {
            return Ok(
                Json(json!({"success": false, "message": error.1, "data": null})).into_response(),
            );
        }
        Err(error) => return Err(error),
    };
    let url = ability.iframe_url(&address)?;
    Ok((
        [(axum::http::header::CACHE_CONTROL, "no-store")],
        success(json!(url.as_str())),
    )
        .into_response())
}

async fn query(State(state): State<Arc<BtnState>>, Path(ip): Path<String>) -> ApiResult {
    let ip = address(&ip, true)?;
    let ability = match state.ability().await {
        Ok(ability) => ability,
        Err(error) if error.0 == StatusCode::NOT_IMPLEMENTED => {
            return Ok(Json(
                json!({"success": false, "message": error.1, "data": null}),
            ));
        }
        Err(error) => return Err(error),
    };
    match ability.query(&ip).await {
        Ok(result) => {
            state.status.observe(
                Ability::IpQuery,
                AbilityResult::new(true, "BTN_ABILITY_IP_QUERY_STATUS_OK", json!([ip])),
            );
            Ok(success(result.unwrap_or(Value::Null)))
        }
        Err(error) => {
            state.status.observe(
                Ability::IpQuery,
                AbilityResult::failure(Ability::IpQuery, Some(&error)),
            );
            Err(error.into())
        }
    }
}

#[derive(Deserialize)]
struct Lookup {
    query: String,
}

async fn lookup(
    State(state): State<Arc<BtnState>>,
    params: Result<Query<Lookup>, QueryRejection>,
) -> ApiResult {
    let Query(params) =
        params.map_err(|_| ApiError::bad_request("An IP address or CIDR is required"))?;
    validate_scope(&params.query)?;
    Ok(success(state.ability().await?.lookup(&params.query).await?))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Comments {
    scope: String,
    page: Option<i32>,
    page_size: Option<i32>,
}

async fn comments(
    State(state): State<Arc<BtnState>>,
    params: Result<Query<Comments>, QueryRejection>,
) -> ApiResult {
    let Query(params) = params.map_err(|_| ApiError::bad_request("Invalid comment query"))?;
    validate_scope(&params.scope)?;
    let page = params.page.unwrap_or(1).max(1);
    let size = params.page_size.unwrap_or(DEFAULT_COMMENT_PAGE_SIZE);
    Ok(success(
        state
            .ability()
            .await?
            .list_comments(&params.scope, page, size)
            .await?,
    ))
}

async fn create(
    State(state): State<Arc<BtnState>>,
    input: Result<Json<CreateComment>, JsonRejection>,
) -> ApiResult {
    Ok(success(
        state.ability().await?.create_comment(&body(input)?).await?,
    ))
}

async fn update(
    State(state): State<Arc<BtnState>>,
    Path(id): Path<i64>,
    input: Result<Json<UpdateComment>, JsonRejection>,
) -> ApiResult {
    Ok(success(
        state
            .ability()
            .await?
            .update_comment(valid_id(id)?, &body(input)?)
            .await?,
    ))
}

async fn delete(State(state): State<Arc<BtnState>>, Path(id): Path<i64>) -> ApiResult {
    state.ability().await?.delete_comment(valid_id(id)?).await?;
    Ok(success(Value::Null))
}

async fn counts(
    State(state): State<Arc<BtnState>>,
    input: Result<Json<Vec<String>>, JsonRejection>,
) -> ApiResult {
    let inputs = body(input)?;
    if inputs.is_empty() || inputs.len() > MAX_COMMENT_COUNT_BATCH {
        return Err(ApiError::from(Error::InvalidCommentCountBatch));
    }
    let canonical: BTreeMap<_, _> = inputs
        .into_iter()
        .map(|input| address(&input, false).map(|host| (input, host)))
        .collect::<Result<_, _>>()?;
    let unique: BTreeSet<_> = canonical.values().cloned().collect();
    let responses = state
        .ability()
        .await?
        .query_comment_counts(&unique.into_iter().collect::<Vec<_>>())
        .await?;
    let output: BTreeMap<_, _> = canonical
        .into_iter()
        .map(|(input, host)| (input, responses.get(&host).copied()))
        .collect();
    Ok(success(json!(output)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::background_tasks::BackgroundTasks;
    use crate::btn_runtime::{BtnResources, BtnRuntime};
    use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
    use tokio::net::TcpListener;
    use tokio::sync::watch;

    fn ability<'a>(body: &'a Value, name: &str) -> Option<&'a Value> {
        body["data"]["abilities"]
            .as_array()?
            .iter()
            .find(|entry| entry["name"] == name)
    }

    async fn wait_until(
        client: &reqwest::Client,
        url: &str,
        predicate: impl Fn(&Value) -> bool,
    ) -> Value {
        tokio::time::timeout(Duration::from_secs(5), async {
            loop {
                let body: Value = client
                    .get(url)
                    .bearer_auth("api-fixture")
                    .send()
                    .await
                    .unwrap()
                    .json()
                    .await
                    .unwrap();
                if predicate(&body) {
                    return body;
                }
                tokio::time::sleep(Duration::from_millis(12)).await;
            }
        })
        .await
        .expect("BTN loopback status changed")
    }

    async fn completed_rule_tasks(client: &reqwest::Client, base: &str) {
        let mut response = client
            .get(format!("{base}/api/tasks/live"))
            .bearer_auth("api-fixture")
            .header("accept-language", "zh-TW")
            .send()
            .await
            .unwrap()
            .error_for_status()
            .unwrap();
        let mut pending = BTreeSet::from([
            "[BTN 網路] 同步 IP 允許列表",
            "[BTN 網路] 同步 IP 拒絕列表",
            "[BTN 網路] 同步定向特徵規則",
        ]);
        tokio::time::timeout(Duration::from_secs(5), async {
            let mut buffer = Vec::new();
            while !pending.is_empty() {
                buffer.extend_from_slice(
                    &response
                        .chunk()
                        .await
                        .unwrap()
                        .expect("task stream remains open"),
                );
                while let Some(end) = buffer.iter().position(|byte| *byte == b'\n') {
                    let line: Vec<_> = buffer.drain(..=end).collect();
                    if let Some(data) = line.strip_prefix(b"data:") {
                        let event: Value = serde_json::from_slice(data).unwrap();
                        let task = &event["task"];
                        if task["status"] == "COMPLETED" {
                            pending.remove(task["title"].as_str().unwrap());
                        }
                    }
                }
            }
        })
        .await
        .expect("all BTN rule refresh tasks completed");
    }

    #[tokio::test]
    async fn status_uses_live_negotiation_and_ability_results() {
        let reject_config = Arc::new(AtomicBool::new(true));
        let reject_list = Arc::new(AtomicBool::new(true));
        let config_requests = Arc::new(AtomicUsize::new(0));
        let fixture = Router::new()
            .route("/config", get({
                let reject = reject_config.clone();
                let requests = config_requests.clone();
                move || {
                    requests.fetch_add(1, Ordering::Relaxed);
                    let reject = reject.load(Ordering::Relaxed);
                    async move {
                        if reject { return (StatusCode::SERVICE_UNAVAILABLE, "upstream-private-fixture").into_response(); }
                        Json(json!({"min_protocol_version":20,"max_protocol_version":21,"ability":{
                            "ip_query":{"endpoint":"/query"},
                            "ip_allowlist":{"endpoint":"/allow","interval":10000,"random_initial_delay":0},
                            "ip_denylist":{"endpoint":"/deny","interval":40,"random_initial_delay":0},
                            "rule_peer_identity":{"endpoint":"/rules","interval":10000,"random_initial_delay":0},
                            "submit_bans":{},
                            "unknown_fixture_ability":{}
                        }})).into_response()
                    }
                }
            }))
            .route("/allow", get(|| async { StatusCode::NO_CONTENT }))
            .route("/deny", get({
                let reject = reject_list.clone();
                move || {
                    let reject = reject.load(Ordering::Relaxed);
                    async move {
                        if reject {
                            (StatusCode::SERVICE_UNAVAILABLE, "upstream-private-fixture").into_response()
                        } else {
                            ([("x-btn-contentversion", "fixture-2")], "192.0.2.0/24\n").into_response()
                        }
                    }
                }
            }))
            .route("/rules", get(|| async { Json(json!({"version":"rule-fixture","ip":{"fixture":["192.0.2.0/24"]}, "script":{"ignored":"not executed"}})) }))
            .route("/query", get(|| async { Json(json!({"labels":[]})) }));
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let config_url = format!("http://{}/config", listener.local_addr().unwrap());
        let upstream = tokio::spawn(async move { axum::serve(listener, fixture).await.unwrap() });
        let config = pbh_btn::Config {
            enabled: true,
            submit: false,
            config_url: config_url.clone(),
            app_id: "fixture-id".into(),
            app_secret: "12345-fixture-private".into(),
        };
        let proxy = pbh_network::ProxyPolicy::default();
        let status = BtnStatus::default();
        let background_tasks = BackgroundTasks::default();
        let app = pbh_web::router_with_protected_routes(
            pbh_web::WebConfig {
                token: "api-fixture".into(),
                manifest: pbh_web::Manifest {
                    version: pbh_web::BuildVersion {
                        version: String::new(),
                        os: String::new(),
                        branch: String::new(),
                        commit: String::new(),
                        abbrev: String::new(),
                    },
                    modules: Vec::new(),
                    installation_id: "fixture".into(),
                    analytics: false,
                },
                static_dir: None,
                allow_cors: false,
            },
            router(
                config.clone(),
                Some("fixture"),
                &proxy,
                status.clone(),
                "en-US",
                false,
            )
            .merge(crate::background_tasks::router(background_tasks.clone())),
        );
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let base = format!("http://{}", listener.local_addr().unwrap());
        let api = tokio::spawn(pbh_web::serve(listener, app, std::future::pending()));
        let url = format!("{base}/api/modules/btn");
        let http = reqwest::Client::builder().no_proxy().build().unwrap();
        assert_eq!(
            http.get(&url).send().await.unwrap().status(),
            StatusCode::FORBIDDEN
        );
        let initial = wait_until(&http, &url, |body| body["data"]["configSuccess"] == false).await;
        assert_eq!(initial["data"]["configResult"], Value::Null);
        assert_eq!(initial["data"]["abilities"], json!([]));
        assert_eq!(initial["data"]["appSecret"], "12345*******");

        let start = || {
            let client = BtnClient::with_proxy(config.clone(), "fixture", &proxy).unwrap();
            let (tx, _) = watch::channel(None);
            BtnRuntime::start(
                Some(client),
                false,
                BtnResources {
                    database: None,
                    swarm: None,
                    language: BanLanguage::En,
                    background_tasks: background_tasks.clone(),
                },
                tx,
                status.clone(),
            )
            .unwrap()
        };
        let runtime = start();
        let failed = wait_until(&http, &url, |body| body["data"]["configResult"].is_string()).await;
        let message = failed["data"]["configResult"].as_str().unwrap();
        assert!(message.contains("503"));
        assert!(!message.contains("upstream-private-fixture"));
        runtime.shutdown().await;
        reject_config.store(false, Ordering::Relaxed);
        let runtime = start();
        let name = "BtnAbilityIPDenyList";
        let denied = wait_until(&http, &url, |body| {
            ability(body, name).is_some_and(|entry| entry["lastSuccess"] == false)
        })
        .await;
        assert_eq!(denied["data"]["configSuccess"], true);
        assert_eq!(
            denied["data"]["configResult"],
            "Successfully connected to the BTN server (read-only mode)"
        );
        assert_eq!(denied["data"]["abilities"].as_array().unwrap().len(), 4);
        assert_eq!(
            ability(&denied, name).unwrap()["lastMessage"],
            "HTTP Error: 503 - "
        );
        completed_rule_tasks(&http, &base).await;
        reject_list.store(false, Ordering::Relaxed);
        let refreshed = wait_until(&http, &url, |body| {
            ability(body, name).is_some_and(|entry| {
                entry["lastSuccess"] == true
                    && entry["description"]
                        .as_str()
                        .unwrap_or_default()
                        .contains("fixture-2")
            })
        })
        .await;
        assert!(
            ability(&refreshed, name).unwrap()["lastUpdateAt"]
                .as_i64()
                .unwrap()
                > 0
        );
        let cloud = ability(&refreshed, "BtnAbilityRuleset (Legacy)").unwrap();
        assert!(
            cloud["description"]
                .as_str()
                .unwrap()
                .contains("loaded **1** rules")
        );

        http.get(format!("{base}/api/peer/btnQuery/192.0.2.8"))
            .bearer_auth("api-fixture")
            .send()
            .await
            .unwrap()
            .error_for_status()
            .unwrap();
        let queried = wait_until(&http, &url, |body| {
            ability(body, "BtnAbilityIpQuery").is_some_and(|entry| {
                entry["lastMessage"] == "Query processed successfully: 192.0.2.8"
            })
        })
        .await;
        assert_eq!(queried["data"]["configUrl"], config_url);
        assert_eq!(config_requests.load(Ordering::Relaxed), 2);
        runtime.shutdown().await;
        api.abort();
        upstream.abort();
    }
}
