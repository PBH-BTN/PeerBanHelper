//! The live downloader and peer views populated by completed scan waves.

use std::collections::BTreeMap;
use std::sync::Arc;

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use futures_util::{StreamExt, stream};
use pbh_geoip::GeoIp;
use serde_json::{Value, json};
use tokio::sync::RwLock;

use crate::config::MainConfig;
use crate::downloader::{Peer, Snapshot, Torrent};
use crate::web::ScanState;

#[derive(Clone)]
pub(crate) struct Dashboard {
    pub(crate) live: Arc<RwLock<BTreeMap<String, LiveDownloader>>>,
    pub(crate) counters: Arc<RwLock<Counters>>,
    pub(crate) reverse_dns: bool,
    pub(crate) dns: crate::dns::Resolver,
    configured: Arc<BTreeMap<String, ConfiguredDownloader>>,
}

#[derive(Clone, Copy, Default)]
pub(crate) struct Counters {
    pub(crate) checks: u64,
    pub(crate) bans: u64,
    pub(crate) unbans: u64,
    pub(crate) wasted_traffic: i64,
}

struct ConfiguredDownloader {
    summary: Value,
    config: Value,
    paused: bool,
}

#[derive(Default)]
pub(crate) struct LiveDownloader {
    torrents: Vec<Value>,
    peers: BTreeMap<String, Vec<Value>>,
    issue: Option<String>,
}

impl Dashboard {
    pub(crate) fn new(
        config: &MainConfig,
        dns: crate::dns::Resolver,
        counters: Arc<RwLock<Counters>>,
    ) -> Self {
        let demo =
            std::env::var("PBH_DEMOMODE").is_ok_and(|value| value.eq_ignore_ascii_case("true"));
        let configured = config.client.iter().map(|(id, downloader)| {
            let mut config = camelize(serde_json::to_value(&downloader.settings).unwrap_or(Value::Null));
            let name = config["name"].as_str().unwrap_or(id).to_owned();
            let mut endpoint = config["endpoint"].as_str().unwrap_or_default().to_owned();
            let paused = config["paused"].as_bool().unwrap_or(false);
            let kind = if downloader.kind == "deluge" { "raccoonfink/deluge" } else { &downloader.kind };
            config["type"] = json!(kind);
            if demo {
                endpoint = "REDACTED_IN_DEMO_MODE".into();
                for field in ["endpoint", "username", "password", "token", "apiKey"] {
                    if let Some(setting) = config.get_mut(field) { *setting = json!(endpoint); }
                }
                if let Some(auth) = config.get_mut("basicAuth").and_then(Value::as_object_mut) {
                    for field in ["user", "pass"] {
                        if let Some(setting) = auth.get_mut(field) { *setting = json!(endpoint); }
                    }
                }
            }
            (id.clone(), ConfiguredDownloader {
                summary: json!({"id": id, "name": name, "endpoint": endpoint, "type": kind, "paused": paused}),
                config,
                paused,
            })
        }).collect();
        Self {
            live: Arc::default(),
            counters,
            reverse_dns: config.lookup.dns_reverse_lookup,
            dns,
            configured: Arc::new(configured),
        }
    }
}

impl LiveDownloader {
    pub(crate) fn from_snapshot(snapshot: &Snapshot, geoip: Option<&GeoIp>) -> Self {
        let mut active = snapshot
            .torrents
            .iter()
            .filter(|(_, peers)| !peers.is_empty())
            .collect::<Vec<_>>();
        active.sort_by_key(|(torrent, _)| {
            std::cmp::Reverse((torrent.upload_speed, torrent.download_speed))
        });
        let torrents = active
            .iter()
            .map(|(torrent, _)| torrent_json(torrent))
            .collect();
        let peers = active
            .into_iter()
            .map(|(torrent, peers)| {
                let mut peers = peers.iter().collect::<Vec<_>>();
                peers.sort_by_key(|peer| std::cmp::Reverse(peer.upload_speed));
                (
                    torrent.id.clone(),
                    peers
                        .into_iter()
                        .map(|peer| json!({"peer": peer_json(peer), "geo": geoip.map(|lookup| lookup.lookup(peer.ip)), "ptrRecord": null}))
                        .collect(),
                )
            })
            .collect();
        Self {
            torrents,
            peers,
            issue: snapshot.failures.first().cloned(),
        }
    }

    pub(crate) fn fail(&mut self, error: String) {
        self.issue = Some(error);
    }
}

pub(crate) fn router(state: ScanState) -> Router {
    Router::new()
        .route("/api/downloaders", get(downloaders))
        .route("/api/downloaders/{id}/status", get(status))
        .route("/api/downloaders/{id}/torrents", get(torrents))
        .route("/api/downloaders/{id}/torrent/{torrent}/peers", get(peers))
        .with_state(state.dashboard.clone())
        .route("/api/statistic/counter", get(counters))
        .with_state(state)
}

async fn counters(State(state): State<ScanState>) -> Response {
    let now = time::OffsetDateTime::now_utc();
    let start = start_of_day(now - time::Duration::days(7));
    let end = start_of_day(now);
    let (tracked, weekly) = {
        let mut database = state.database.lock().await;
        (
            database.swarm_count().await,
            database.global_total_connections_count(start, end).await,
        )
    };
    let weekly = weekly
        .inspect_err(|error| crate::journal::log_warning!("Read weekly sessions: {error:#}"))
        .unwrap_or_default();
    let counters = *state.dashboard.counters.read().await;
    let bans = state.bans.read().await.len();
    let mut result = json!({
        "checkCounter": counters.checks,
        "peerBanCounter": counters.bans,
        "peerUnbanCounter": counters.unbans,
        "banlistCounter": bans,
        "bannedIpCounter": bans,
        "wastedTraffic": counters.wasted_traffic,
        "peersBlockRate": 0.0,
        "weeklySessions": weekly,
    });
    match tracked {
        Ok(tracked) => {
            result["trackedSwarmCount"] = json!(tracked);
            if tracked > 0 {
                result["peersBlockRate"] = json!(counters.bans as f64 / tracked as f64);
            }
        }
        Err(error) => crate::journal::log_warning!("Read Swarm count: {error:#}"),
    }
    success(result).into_response()
}

pub(crate) fn start_of_day(now: time::OffsetDateTime) -> i64 {
    let offset = time::UtcOffset::local_offset_at(now).unwrap_or(time::UtcOffset::UTC);
    now.to_offset(offset)
        .replace_time(time::Time::MIDNIGHT)
        .unix_timestamp()
        * 1_000
}

async fn downloaders(State(state): State<Dashboard>) -> Json<Value> {
    let summaries = state
        .configured
        .values()
        .map(|downloader| &downloader.summary)
        .collect::<Vec<_>>();
    success(json!(summaries))
}

async fn status(State(state): State<Dashboard>, Path(id): Path<String>) -> Response {
    let Some(configured) = state.configured.get(&id) else {
        return missing();
    };
    let live = state.live.read().await;
    let snapshot = live.get(&id);
    let (status, message) = match (configured.paused, snapshot) {
        (true, _) => ("PAUSED", "Paused"),
        (_, Some(snapshot)) => snapshot
            .issue
            .as_deref()
            .map_or(("HEALTHY", "Healthy"), |error| ("ERROR", error)),
        _ => ("UNKNOWN", "Unknown"),
    };
    success(json!({
        "lastStatus": status,
        "lastStatusMessage": message,
        "activeTorrents": snapshot.map(|value| value.torrents.len()).unwrap_or_default(),
        "activePeers": snapshot.map(|value| value.peers.values().map(Vec::len).sum::<usize>()).unwrap_or_default(),
        "config": configured.config,
        "paused": configured.paused,
    })).into_response()
}

async fn torrents(State(state): State<Dashboard>, Path(id): Path<String>) -> Response {
    if !state.configured.contains_key(&id) {
        return missing();
    }
    let live = state.live.read().await;
    let rows = live
        .get(&id)
        .map(|snapshot| snapshot.torrents.as_slice())
        .unwrap_or_default();
    success(json!(rows)).into_response()
}

async fn peers(
    State(state): State<Dashboard>,
    Path((id, torrent)): Path<(String, String)>,
) -> Response {
    if !state.configured.contains_key(&id) {
        return missing();
    }
    let mut rows = state
        .live
        .read()
        .await
        .get(&id)
        .and_then(|snapshot| snapshot.peers.get(&torrent))
        .cloned()
        .unwrap_or_default();
    if state.reverse_dns {
        let _ = tokio::time::timeout(
            crate::dns::TIMEOUT,
            stream::iter(rows.iter_mut()).for_each_concurrent(12, |row| async {
                if let Some(ip) = row["peer"]["address"]["ip"]
                    .as_str()
                    .and_then(|value| value.parse().ok())
                    && let Some(host) = state.dns.ptr_lookup(ip).await
                {
                    row["ptrRecord"] = json!(host);
                }
            }),
        )
        .await;
    }
    success(json!(rows)).into_response()
}

fn success(data: Value) -> Json<Value> {
    Json(json!({"success": true, "message": null, "data": data}))
}

fn missing() -> Response {
    (
        StatusCode::NOT_FOUND,
        Json(json!({"success": false, "message": "Downloader does not exist", "data": null})),
    )
        .into_response()
}

pub(crate) fn torrent_json(torrent: &Torrent) -> Value {
    json!({
        "id": torrent.id, "hash": torrent.hash, "name": torrent.name,
        "size": torrent.size, "completedSize": torrent.completed_size,
        "privateTorrent": torrent.private, "progress": torrent.progress,
        "rtDownloadSpeed": torrent.download_speed, "rtUploadSpeed": torrent.upload_speed,
    })
}

pub(crate) fn peer_json(peer: &Peer) -> Value {
    json!({
        "address": {
            "ip": peer.ip.to_canonical(), "port": peer.port, "downloaderRawIp": peer.raw_address,
            "downloaderRawPort": peer.forwarded_from.map_or(peer.port, |address| address.port()),
            "teredoClientUdpPort": 0,
            "nattedClientIp": peer.forwarded_from.map(|_| peer.ip),
            "nattedClientPort": peer.forwarded_from.map_or(0, |_| peer.port),
            "natTranslated": peer.forwarded_from.is_some(), "teredoTranslated": false,
        },
        "rawIp": peer.raw_address, "id": peer.peer_id, "clientName": peer.client_name,
        "downloaded": peer.downloaded, "downloadSpeed": peer.download_speed,
        "uploaded": peer.uploaded, "uploadSpeed": peer.upload_speed,
        "progress": peer.progress, "flags": peer.flags,
    })
}

fn camelize(value: Value) -> Value {
    let Value::Object(map) = value else {
        return value;
    };
    Value::Object(
        map.into_iter()
            .map(|(key, value)| {
                let mut parts = key.split('-');
                let mut camel = parts.next().unwrap_or_default().to_owned();
                for part in parts {
                    let mut characters = part.chars();
                    if let Some(first) = characters.next() {
                        camel.extend(first.to_uppercase());
                        camel.push_str(characters.as_str());
                    }
                }
                (camel, camelize(value))
            })
            .collect(),
    )
}
