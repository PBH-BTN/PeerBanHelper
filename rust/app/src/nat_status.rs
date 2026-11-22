//! Refresh the cached UDP NAT classification used by AutoSTUN.

use std::collections::{BTreeMap, HashMap};
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use axum::extract::{Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::{
    Json, Router,
    routing::{get, post, put},
};
use pbh_traversal::{NatType, RetryPolicy, StunVersion, UdpStunClient};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use tokio::net::lookup_host;
use tokio::sync::{Mutex, mpsc, watch};
use tokio::task::JoinHandle;
use tokio::time::timeout;
use uuid::Uuid;

use crate::auto_tunnel::{AutoTunnelOptions, AutoTunnels, TunnelView};
use crate::ban_api::BanLanguage;
use crate::geo_runtime::GeoRuntime;
use pbh_traversal::forwarder::ForwarderMode;

const REFRESH_INTERVAL: Duration = Duration::from_secs(3_600);
const DNS_TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct StunServers {
    udp_servers: Vec<String>,
    tcp_servers: Vec<String>,
    available_test: bool,
    upnp: bool,
}

impl Default for StunServers {
    fn default() -> Self {
        Self {
            udp_servers: Vec::new(),
            tcp_servers: Vec::new(),
            available_test: true,
            upnp: true,
        }
    }
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct AutoStunConfig {
    enabled: bool,
    use_friendly_loopback_mapping: bool,
    downloaders: Vec<String>,
}

impl Default for AutoStunConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            use_friendly_loopback_mapping: true,
            downloaders: Vec::new(),
        }
    }
}

impl AutoStunConfig {
    pub(crate) fn tunnel_options(
        &self,
        servers: &StunServers,
        allow_network: bool,
    ) -> AutoTunnelOptions {
        let test_host =
            std::env::var("PBH_STUNTCPTUNNEL_TESTHOST").unwrap_or_else(|_| "qq.com".into());
        let heartbeat = if test_host.contains(':') {
            test_host
        } else {
            format!("{test_host}:80")
        };
        let local_ports = self
            .downloaders
            .iter()
            .filter_map(|id| {
                let key = format!("PBH_BTSTUN_LOCALPORT_{}", id.to_ascii_uppercase());
                let port = std::env::var(key).ok()?.parse::<u16>().ok()?;
                Some((id.clone(), port))
            })
            .collect::<BTreeMap<_, _>>();
        AutoTunnelOptions {
            enabled: self.enabled && allow_network,
            downloaders: self.downloaders.clone(),
            friendly_loopback_mapping: self.use_friendly_loopback_mapping,
            tcp_servers: servers.tcp_servers.clone(),
            heartbeat,
            verify_reachability: servers.available_test,
            upnp: servers.upnp,
            local_ports,
        }
    }
}

#[derive(Clone)]
pub(crate) struct AutoStunControl {
    settings: watch::Sender<AutoStunConfig>,
    tunnels: watch::Sender<AutoTunnels>,
    nat_type: watch::Sender<NatType>,
}

impl AutoStunControl {
    pub(crate) fn new(config: AutoStunConfig, tunnels: AutoTunnels) -> Self {
        Self {
            settings: watch::Sender::new(config),
            tunnels: watch::Sender::new(tunnels),
            nat_type: watch::Sender::new(NatType::Unknown),
        }
    }

    pub(crate) fn settings(&self) -> watch::Receiver<AutoStunConfig> {
        self.settings.subscribe()
    }

    pub(crate) fn replace_tunnels(&self, tunnels: AutoTunnels) {
        self.tunnels.send_replace(tunnels);
    }

    pub(crate) fn nat_type(&self) -> NatType {
        *self.nat_type.borrow()
    }

    fn tunnels(&self) -> AutoTunnels {
        self.tunnels.borrow().clone()
    }
}

#[derive(Clone)]
struct NatStatus {
    control: AutoStunControl,
    refresh: mpsc::Sender<()>,
    downloaders: Arc<HashMap<String, Value>>,
    geoip: GeoRuntime,
    demo_mode: bool,
    config_path: Arc<PathBuf>,
    edit: Arc<Mutex<()>>,
    _worker: Arc<ProbeTask>,
}

struct ProbeTask(JoinHandle<()>);

impl Drop for ProbeTask {
    fn drop(&mut self) {
        self.0.abort();
    }
}

impl NatStatus {
    fn new(
        control: AutoStunControl,
        servers: StunServers,
        downloaders: Arc<HashMap<String, Value>>,
        demo_mode: bool,
        geoip: GeoRuntime,
        config_path: PathBuf,
    ) -> Self {
        let cache = control.nat_type.clone();
        let (refresh, mut request) = mpsc::channel(1);
        let mut settings = control.settings();
        let worker = tokio::spawn(async move {
            let mut probe = settings.borrow().enabled;
            loop {
                if probe {
                    cache.send_replace(classify(&servers.udp_servers).await);
                }
                let enabled = settings.borrow().enabled;
                tokio::select! {
                    _ = tokio::time::sleep(REFRESH_INTERVAL), if enabled => probe = true,
                    result = request.recv() => {
                        if result.is_none() { return; }
                        probe = true;
                    },
                    result = settings.changed() => {
                        if result.is_err() { return; }
                        probe = settings.borrow().enabled;
                    },
                }
            }
        });
        Self {
            control,
            refresh,
            downloaders,
            demo_mode,
            geoip,
            config_path: Arc::new(config_path),
            edit: Arc::default(),
            _worker: Arc::new(ProbeTask(worker)),
        }
    }
}

pub(crate) fn router(
    control: AutoStunControl,
    servers: StunServers,
    downloaders: Arc<HashMap<String, Value>>,
    demo_mode: bool,
    geoip: GeoRuntime,
    config_path: PathBuf,
) -> Router {
    let state = NatStatus::new(control, servers, downloaders, demo_mode, geoip, config_path);
    Router::new()
        .route("/api/autostun/status", get(status))
        .route("/api/autostun/refreshNatType", post(refresh))
        .route("/api/general/refreshNatStatus", post(refresh))
        .route("/api/autostun/restart", post(restart))
        .route("/api/autostun/config", put(configure))
        .route("/api/autostun/tunnels", get(tunnels_list))
        .route("/api/autostun/tunnel/{downloader}/info", get(tunnel_info))
        .route(
            "/api/autostun/tunnel/{downloader}/connections",
            get(tunnel_connections),
        )
        .with_state(state)
}

async fn status(State(state): State<NatStatus>) -> Json<Value> {
    let settings = state.control.settings.borrow();
    let downloaders = settings
        .downloaders
        .iter()
        .filter_map(|id| state.downloaders.get(id))
        .collect::<Vec<_>>();
    Json(json!({"success": true, "message": null, "data": {
        "enabled": settings.enabled,
        "useFriendlyLoopbackMapping": settings.use_friendly_loopback_mapping,
        "selectedDownloaders": downloaders,
        "natType": state.control.nat_type(),
        "isBridgeNetDriver": bridge_network(),
    }}))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct AutoStunForm {
    enabled: bool,
    use_friendly_loopback_mapping: bool,
    downloaders: Vec<String>,
}

async fn configure(
    State(state): State<NatStatus>,
    headers: HeaderMap,
    Json(form): Json<AutoStunForm>,
) -> Response {
    if let Some(response) = demo_error(state.demo_mode, &headers) {
        return response;
    }
    let language = BanLanguage::from_headers(&headers);
    let guard = state.edit.clone().lock_owned().await;
    let next = AutoStunConfig {
        enabled: form.enabled,
        use_friendly_loopback_mapping: form.use_friendly_loopback_mapping,
        downloaders: form.downloaders,
    };
    let result = tokio::task::spawn_blocking(move || {
        let _guard = guard;
        pbh_web::config_file::edit(&state.config_path, |yaml| {
            yaml.insert("auto-stun".into(), serde_yaml_ng::to_value(&next)?);
            Ok::<(), serde_yaml_ng::Error>(())
        })?;
        state.control.settings.send_replace(next);
        Ok::<(), pbh_web::config_file::EditError<serde_yaml_ng::Error>>(())
    })
    .await;
    if !matches!(result, Ok(Ok(()))) {
        return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({
            "success": false, "message": language.render(&json!({"key": "WEBAPI_INTERNAL_ERROR"})), "data": null
        }))).into_response();
    }
    Json(json!({
        "success": true, "message": language.render(&json!({"key": "AUTOSTUN_CONFIG_REAPPLIED"})), "data": null
    })).into_response()
}

async fn refresh(State(state): State<NatStatus>, headers: HeaderMap) -> Response {
    if let Some(response) = demo_error(state.demo_mode, &headers) {
        return response;
    }
    if let Err(mpsc::error::TrySendError::Closed(_)) = state.refresh.try_send(()) {
        return (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(json!({"success": false, "message": "NAT probe is unavailable", "data": null})),
        )
            .into_response();
    }
    Json(json!({"success": true, "message": "Refreshing NAT Status", "data": null})).into_response()
}

async fn restart(State(state): State<NatStatus>, headers: HeaderMap) -> Response {
    if let Some(response) = demo_error(state.demo_mode, &headers) {
        return response;
    }
    let language = BanLanguage::from_headers(&headers);
    state.control.tunnels().restart();
    Json(json!({"success": true, "message": language.render(&json!({"key": "AUTOSTUN_RESTARTED"})), "data": null})).into_response()
}

fn demo_error(demo_mode: bool, headers: &HeaderMap) -> Option<Response> {
    demo_mode.then(|| {
        let language = BanLanguage::from_headers(headers);
        let message = language.render(&json!({"key": "DEMO_MODE_OPERATION_NOT_PERMITTED"}));
        (
            StatusCode::BAD_REQUEST,
            Json(json!({"success": false, "message": message, "data": null})),
        )
            .into_response()
    })
}

async fn tunnels_list(State(state): State<NatStatus>) -> Json<Value> {
    let data = state
        .control
        .tunnels()
        .snapshots()
        .iter()
        .map(|view| {
            json!({
                "downloader": {"id": view.id, "name": view.name, "type": view.kind},
                "tunnel": tunnel_json(view),
            })
        })
        .collect::<Vec<_>>();
    Json(json!({"success": true, "message": null, "data": data}))
}

async fn tunnel_info(
    State(state): State<NatStatus>,
    Path(id): Path<String>,
    headers: HeaderMap,
) -> Json<Value> {
    match tunnel_by_id(&state, &id) {
        Ok(view) => Json(json!({"success": true, "message": null, "data": tunnel_json(&view)})),
        Err((key, value)) => tunnel_error(&headers, key, value, Value::Null),
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Page {
    page: Option<usize>,
    page_size: Option<usize>,
}

async fn tunnel_connections(
    State(state): State<NatStatus>,
    Path(id): Path<String>,
    headers: HeaderMap,
    Query(query): Query<Page>,
) -> Json<Value> {
    let view = match tunnel_by_id(&state, &id) {
        Ok(view) => view,
        Err((key, value)) => return tunnel_error(&headers, key, value, Value::Null),
    };
    let Some(mut forwarder) = view.forwarder else {
        return tunnel_error(
            &headers,
            "AUTOSTUN_DOWNLOADER_TUNNEL_FORWARDER_NOT_EXISTS",
            view.name,
            json!([]),
        );
    };
    let page = query.page.unwrap_or(1).max(1);
    let size = query.page_size.unwrap_or(10).clamp(1, 1_000);
    let total = forwarder.connections.len();
    forwarder
        .connections
        .sort_by_key(|row| std::cmp::Reverse((row.to_downstream_bytes, row.to_upstream_bytes)));
    let geoip = state.geoip.current();
    let results = forwarder
        .connections
        .into_iter()
        .skip((page - 1).saturating_mul(size))
        .take(size)
        .map(|row| {
            json!({
                "ipGeoData": geoip.as_ref().map(|geoip| geoip.lookup(row.downstream.ip())),
                "downstreamHost": row.downstream.ip(),
                "downstreamPort": row.downstream.port(),
                "proxyHost": forwarder.listener.ip(),
                "proxyPort": forwarder.listener.port(),
                "proxyOutgoingHost": row.upstream_local.ip(),
                "proxyOutgoingPort": row.upstream_local.port(),
                "upstreamHost": forwarder.upstream.ip(),
                "upstreamPort": forwarder.upstream.port(),
                "establishedAt": row.established_at_ms,
                "lastActivityAt": row.last_activity_at_ms,
                "toDownstreamBytes": row.to_downstream_bytes,
                "toUpstreamBytes": row.to_upstream_bytes,
            })
        })
        .collect::<Vec<_>>();
    Json(
        json!({"success": true, "message": null, "data": {"page": page, "size": size, "total": total, "results": results}}),
    )
}

fn tunnel_by_id(state: &NatStatus, id: &str) -> Result<TunnelView, (&'static str, String)> {
    let Some(downloader) = state.downloaders.get(id) else {
        return Err(("AUTOSTUN_DOWNLOADER_NOT_EXISTS", id.to_owned()));
    };
    state.control.tunnels().snapshot(id).ok_or_else(|| {
        (
            "AUTOSTUN_DOWNLOADER_TUNNEL_NOT_EXISTS",
            downloader["name"].as_str().unwrap_or(id).to_owned(),
        )
    })
}

fn tunnel_error(headers: &HeaderMap, key: &str, value: String, data: Value) -> Json<Value> {
    let message =
        BanLanguage::from_headers(headers).render(&json!({"key": key, "params": [value]}));
    Json(json!({"success": false, "message": message, "data": data}))
}

fn tunnel_json(view: &TunnelView) -> Value {
    let tunnel = view.tunnel.as_ref();
    let forwarder = view.forwarder.as_ref();
    json!({
        "valid": tunnel.is_some_and(|value| value.valid),
        "startedAt": tunnel.map_or(0, |value| value.started_at_ms),
        "lastSuccessHeartbeatAt": tunnel.map_or(0, |value| value.last_success_at_ms),
        "connectionsHandled": forwarder.map_or(0, |value| value.connection_handled),
        "connectionsFailed": forwarder.map_or(0, |value| value.connection_failed),
        "connectionsBlocked": forwarder.map_or(0, |value| value.connection_blocked),
        "connectionsRejected": forwarder.map_or(0, |value| value.connection_rejected),
        "totalToDownstreamBytes": forwarder.map_or(0, |value| value.total_to_downstream),
        "totalToUpstreamBytes": forwarder.map_or(0, |value| value.total_to_upstream),
        "establishedConnections": forwarder.map_or(0, |value| value.established_connections),
        "proxyHost": forwarder.map_or_else(|| "???".to_owned(), |value| value.listener.ip().to_string()),
        "proxyPort": forwarder.map_or(0, |value| value.listener.port()),
        "upstreamHost": forwarder.map_or_else(|| "???".to_owned(), |value| value.upstream.ip().to_string()),
        "upstreamPort": forwarder.map_or(0, |value| value.upstream.port()),
        "forwarderIOHandlerType": forwarder.map_or(ForwarderMode::Default, |value| value.mode),
        "shutdownReason": view.last_error.as_deref().unwrap_or_default(),
    })
}

async fn classify(servers: &[String]) -> NatType {
    let mut servers = servers.iter().collect::<Vec<_>>();
    for index in (1..servers.len()).rev() {
        let other = (Uuid::new_v4().as_u128() % (index + 1) as u128) as usize;
        servers.swap(index, other);
    }
    let local = SocketAddr::new(Ipv4Addr::UNSPECIFIED.into(), 0);
    for server in servers {
        let Ok(Ok(addresses)) = timeout(DNS_TIMEOUT, lookup_host(server)).await else {
            continue;
        };
        let mut addresses = addresses.filter(SocketAddr::is_ipv4);
        if let Some(address) = addresses.next()
            && let Ok(mut client) = UdpStunClient::bind(local, RetryPolicy::default()).await
            && let Ok(result) = client
                .probe(
                    address,
                    IpAddr::V4(Ipv4Addr::UNSPECIFIED),
                    StunVersion::Legacy,
                )
                .await
            && !matches!(result.nat_type, NatType::Unknown | NatType::UdpBlocked)
        {
            return result.nat_type;
        }
    }
    NatType::UdpBlocked
}

#[cfg(target_os = "linux")]
fn bridge_network() -> bool {
    let docker = std::env::var("PBH_RELEASE")
        .is_ok_and(|release| release.to_ascii_lowercase().contains("docker"));
    let skip = std::env::var("PBH_AUTOSTUN_CHECKDOCKERBRIDGE")
        .is_ok_and(|flag| flag.eq_ignore_ascii_case("false"));
    if !docker || skip {
        return false;
    }
    let routes = std::fs::read_to_string("/proc/net/route").unwrap_or_default();
    for row in routes.lines().skip(1) {
        let mut fields = row.split_whitespace();
        if let (Some("00000000"), Some(gateway)) = (fields.nth(1), fields.next())
            && let Ok(gateway) = u32::from_str_radix(gateway, 16)
        {
            return gateway.to_le_bytes()[0] == 172;
        }
    }
    false
}

#[cfg(not(target_os = "linux"))]
fn bridge_network() -> bool {
    false
}
