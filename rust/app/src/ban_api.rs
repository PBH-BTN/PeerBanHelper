//! Public JSON shapes for PBHBanController's current-list query.

use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::OnceLock;

use axum::extract::{Query, RawQuery, State, rejection::JsonRejection};
use axum::http::{HeaderMap, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router};
use futures_util::{StreamExt, stream};
use pbh_core::AddressRule;
use pbh_geoip::GeoIp;
use pbh_storage::{BanHistoryOrder, BanHistoryQuery, BanHistoryRecord, BanRecord};
use percent_encoding::percent_decode_str;
use serde::Deserialize;
use serde_json::{Value, json};

use crate::web::ScanState;

pub(crate) enum ManualBans {
    Add(Vec<(IpAddr, String)>),
    Remove { addresses: Vec<IpAddr>, full: bool },
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Pager {
    page: Option<usize>,
    page_size: Option<usize>,
    ignore_ban_for_disconnect: Option<String>,
    search: Option<String>,
}

pub(crate) fn router(state: ScanState) -> Router {
    Router::new()
        .route(
            "/api/bans",
            axum::routing::get(current_bans)
                .put(add_bans)
                .delete(remove_bans),
        )
        .route("/api/bans/logs", axum::routing::get(ban_history))
        .with_state(state)
}

async fn add_bans(
    State(state): State<ScanState>,
    headers: HeaderMap,
    body: Result<Json<Vec<String>>, JsonRejection>,
) -> Response {
    update_bans(state, headers, body, true).await
}

async fn remove_bans(
    State(state): State<ScanState>,
    headers: HeaderMap,
    body: Result<Json<Vec<String>>, JsonRejection>,
) -> Response {
    update_bans(state, headers, body, false).await
}

async fn update_bans(
    state: ScanState,
    headers: HeaderMap,
    body: Result<Json<Vec<String>>, JsonRejection>,
    add: bool,
) -> Response {
    if state.demo_mode {
        let language = BanLanguage::from_headers(&headers);
        let message = language.render(&json!({"key": "DEMO_MODE_OPERATION_NOT_PERMITTED"}));
        return operation_response(StatusCode::BAD_REQUEST, Some(&message), None);
    }
    let Ok(Json(values)) = body else {
        return operation_response(
            StatusCode::BAD_REQUEST,
            Some("Expected an array of IP addresses"),
            None,
        );
    };
    let (command, count) = if !add && values.iter().any(|value| value == "*") {
        let addresses = state
            .bans
            .read()
            .await
            .iter()
            .map(|ban| ban.address)
            .collect::<Vec<_>>();
        let count = addresses.len();
        (
            ManualBans::Remove {
                addresses,
                full: true,
            },
            count,
        )
    } else {
        let addresses = values
            .into_iter()
            .map(|value| match value.parse::<AddressRule>() {
                Ok(AddressRule::Host(address)) => Some((address, value)),
                _ => None,
            })
            .collect::<Option<Vec<_>>>();
        let Some(addresses) = addresses else {
            return operation_response(StatusCode::BAD_REQUEST, Some("Invalid IP address"), None);
        };
        let count = addresses.len();
        if add {
            if !state.can_add_manual_bans && count > 0 {
                return operation_response(
                    StatusCode::BAD_REQUEST,
                    Some("No active downloader"),
                    None,
                );
            }
            (ManualBans::Add(addresses), count)
        } else {
            let addresses = addresses.into_iter().map(|(address, _)| address).collect();
            (
                ManualBans::Remove {
                    addresses,
                    full: false,
                },
                count,
            )
        }
    };
    if state.manual_bans.try_send(command).is_err() {
        return operation_response(
            StatusCode::SERVICE_UNAVAILABLE,
            Some("Ban processing is busy"),
            None,
        );
    }
    operation_response(StatusCode::OK, None, Some(count))
}

fn operation_response(status: StatusCode, message: Option<&str>, count: Option<usize>) -> Response {
    (status, Json(json!({"success": count.is_some(), "message": message, "data": count.map(|count| json!({"count": count}))}))).into_response()
}

async fn current_bans(
    State(state): State<ScanState>,
    headers: HeaderMap,
    pager: Result<Query<Pager>, axum::extract::rejection::QueryRejection>,
) -> Response {
    let Ok(Query(pager)) = pager else {
        return invalid_pagination();
    };
    let page = pager.page.unwrap_or(1);
    let size = pager.page_size.unwrap_or(10);
    let Some(offset) = page.checked_sub(1).and_then(|page| page.checked_mul(size)) else {
        return invalid_pagination();
    };
    let language = BanLanguage::from_headers(&headers);
    let ignore_disconnect = pager
        .ignore_ban_for_disconnect
        .as_ref()
        .is_none_or(|value| value.eq_ignore_ascii_case("true"));
    let search = pager.search.map(|value| {
        percent_decode_str(&value.replace('+', " "))
            .decode_utf8_lossy()
            .to_lowercase()
    });
    let geoip = state.geoip.current();
    let list = state.bans.read().await;
    let mut results = list
        .iter()
        .filter(|ban| included(ban, ignore_disconnect, search.as_deref()))
        .collect::<Vec<_>>();
    results
        .sort_by_key(|ban| std::cmp::Reverse(ban.metadata["banAt"].as_i64().unwrap_or_default()));
    let total = results.len();
    let mut results = results
        .into_iter()
        .skip(offset)
        .take(size)
        .map(|ban| bake(ban, language, geoip.as_deref()))
        .collect::<Vec<_>>();
    drop(list);
    if state.dashboard.reverse_dns {
        let _ = tokio::time::timeout(
            crate::dns::TIMEOUT,
            stream::iter(results.iter_mut()).for_each_concurrent(12, |row| async {
                if row["banMetadata"]["reverseLookup"] == "N/A"
                    && let Some(ip) = row["address"].as_str().and_then(|value| value.parse().ok())
                    && let Some(host) = state.dashboard.dns.ptr_lookup(ip).await
                {
                    row["banMetadata"]["reverseLookup"] = json!(host);
                }
            }),
        )
        .await;
    }
    Json(json!({"success": true, "message": null, "data": {"page": page, "size": size, "total": total, "results": results}})).into_response()
}

async fn ban_history(
    State(state): State<ScanState>,
    headers: HeaderMap,
    RawQuery(raw): RawQuery,
) -> Response {
    let Some(query) = history_query(raw.as_deref()) else {
        return invalid_pagination();
    };
    let outcome = state.database.lock().await.ban_history(&query).await;
    let page = match outcome {
        Ok(page) => page,
        Err(error) => {
            crate::journal::log_warning!("Failed to load ban history: {error}");
            return (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"success": false, "message": "Unable to read ban history", "data": null}))).into_response();
        }
    };
    let language = BanLanguage::from_headers(&headers);
    let results = page
        .records
        .iter()
        .map(|record| history_json(record, language, &state.downloader_info))
        .collect::<Vec<_>>();
    Json(json!({"success": true, "message": null, "data": {"page": page.page, "size": page.size, "total": page.total, "results": results}})).into_response()
}

fn history_query(query: Option<&str>) -> Option<BanHistoryQuery> {
    let mut result = BanHistoryQuery::default();
    let mut page_set = false;
    let mut size_set = false;
    for (key, value) in form_urlencoded::parse(query.unwrap_or_default().as_bytes()) {
        match key.as_ref() {
            "page" if !page_set => {
                result.page = value.parse().ok()?;
                page_set = true;
            }
            "pageSize" if !size_set => {
                result.size = value.parse().ok()?;
                size_set = true;
            }
            "orderBy" => result.orders.push(BanHistoryOrder::parse(&value).ok()?),
            _ => {}
        }
    }
    Some(result)
}

pub(crate) fn history_json(
    record: &BanHistoryRecord,
    language: BanLanguage,
    downloaders: &HashMap<String, Value>,
) -> Value {
    let unknown = || json!({"id": record.downloader_id, "name": "Unknown", "type": "Unknown"});
    let torrent = record.torrent.as_ref();
    json!({
        "banAt": record.ban_at_ms,
        "unbanAt": record.unban_at_ms,
        "peerIp": record.address,
        "peerPort": record.port,
        "peerId": record.peer_id,
        "peerClientName": record.peer_client_name,
        "peerUploaded": record.peer_uploaded.unwrap_or_default(),
        "peerDownloaded": record.peer_downloaded.unwrap_or_default(),
        "peerProgress": record.peer_progress,
        "torrentInfoHash": torrent.map(|torrent| torrent.info_hash.as_str()),
        "torrentName": torrent.map(|torrent| torrent.name.as_str()),
        "torrentSize": torrent.map(|torrent| torrent.size).unwrap_or_default(),
        "module": record.module_name,
        "rule": language.render(&record.rule),
        "description": language.render(&record.description),
        "downloader": downloaders.get(&record.downloader_id).cloned().unwrap_or_else(unknown),
    })
}

fn invalid_pagination() -> Response {
    (
        StatusCode::BAD_REQUEST,
        Json(json!({"success": false, "message": "Invalid pagination", "data": null})),
    )
        .into_response()
}

fn included(ban: &BanRecord, ignore_disconnect: bool, search: Option<&str>) -> bool {
    let metadata = &ban.metadata;
    if metadata["excludeFromDisplay"].as_bool() == Some(true)
        || (ignore_disconnect && metadata["banForDisconnect"].as_bool() == Some(true))
    {
        return false;
    }
    search.is_none_or(|search| {
        ban.address.to_string().contains(search)
            || metadata.to_string().to_lowercase().contains(search)
    })
}

fn bake(ban: &BanRecord, language: BanLanguage, geoip: Option<&GeoIp>) -> Value {
    let metadata = &ban.metadata;
    json!({
        "address": ban.address,
        "banMetadata": {
            "downloader": metadata["downloader"],
            "torrent": metadata["torrent"],
            "peer": metadata["peer"],
            "reverseLookup": metadata["reverseLookup"].as_str().unwrap_or("N/A"),
            "context": metadata["context"],
            "banAt": metadata["banAt"],
            "unbanAt": metadata["unbanAt"],
            "rule": language.render(&metadata["rule"]),
            "description": language.render(&metadata["description"]),
        },
        "ipGeoData": geoip.map(|lookup| lookup.lookup(ban.address)),
    })
}

#[derive(Clone, Copy)]
pub(crate) enum BanLanguage {
    En,
    SimplifiedChinese,
    TraditionalChinese,
}

impl BanLanguage {
    pub(crate) fn from_locale(locale: &str) -> Self {
        if locale.trim().is_empty() || locale.eq_ignore_ascii_case("default") {
            let system = std::env::var("PBH_USERLOCALE")
                .ok()
                .or_else(|| std::env::var("PBH_USER_LOCALE").ok())
                .or_else(sys_locale::get_locale);
            return system
                .as_deref()
                .and_then(Self::parse_locale)
                .unwrap_or(Self::En);
        }
        Self::parse_locale(locale).unwrap_or(Self::En)
    }

    fn parse_locale(locale: &str) -> Option<Self> {
        match locale
            .trim()
            .to_ascii_lowercase()
            .replace('_', "-")
            .as_str()
        {
            "en-us" | "en" => Some(Self::En),
            "zh-cn" | "zh-hans-cn" => Some(Self::SimplifiedChinese),
            "zh-tw" | "zh-hant-tw" => Some(Self::TraditionalChinese),
            _ => None,
        }
    }

    pub(crate) fn from_headers(headers: &HeaderMap) -> Self {
        Self::from_headers_or(headers, Self::En)
    }

    pub(crate) fn from_headers_or(headers: &HeaderMap, fallback: Self) -> Self {
        headers
            .get(header::ACCEPT_LANGUAGE)
            .and_then(|value| value.to_str().ok())
            .unwrap_or_default()
            .split(',')
            .filter_map(|locale| {
                let (name, q) = locale.split_once(';').unwrap_or((locale, "q=1"));
                let language = Self::parse_locale(name)?;
                let q: f32 = q.trim().strip_prefix("q=")?.parse().ok()?;
                (q > 0.0).then_some((language, q))
            })
            .rev()
            .max_by(|(_, one), (_, two)| one.total_cmp(two))
            .map(|(locale, _)| locale)
            .unwrap_or(fallback)
    }

    fn messages(self) -> &'static HashMap<String, Value> {
        type Messages = OnceLock<HashMap<String, Value>>;
        static EN: Messages = OnceLock::new();
        static ZH_CN: Messages = OnceLock::new();
        static ZH_TW: Messages = OnceLock::new();
        let (storage, source) = match self {
            Self::En => (
                &EN,
                include_str!("../../../src/main/resources/lang/en_us/messages.yml"),
            ),
            Self::SimplifiedChinese => (
                &ZH_CN,
                include_str!("../../../src/main/resources/lang/zh_cn/messages.yml"),
            ),
            Self::TraditionalChinese => (
                &ZH_TW,
                include_str!("../../../src/main/resources/lang/zh_tw/messages.yml"),
            ),
        };
        storage.get_or_init(|| serde_yaml_ng::from_str(source).unwrap_or_default())
    }

    pub(crate) fn render(self, component: &Value) -> String {
        static FALLBACK: OnceLock<HashMap<String, Value>> = OnceLock::new();
        let Some(key) = component["key"].as_str() else {
            return component.as_str().unwrap_or_default().to_owned();
        };
        let fallback = FALLBACK.get_or_init(|| {
            serde_yaml_ng::from_str(include_str!(
                "../../../src/main/resources/lang/messages_fallback.yml"
            ))
            .unwrap_or_default()
        });
        let template = self
            .messages()
            .get(key)
            .or_else(|| fallback.get(key))
            .and_then(Value::as_str)
            .unwrap_or(key);
        let mut text = String::new();
        let mut parts = template.split("{}");
        text.push_str(parts.next().unwrap_or_default());
        let values = component["params"].as_array().into_iter().flatten();
        let mut values = values.map(|argument| match argument {
            Value::Object(_) => self.render(argument),
            Value::String(value) => value.clone(),
            argument => argument.to_string(),
        });
        for part in parts {
            text.push_str(&values.next().unwrap_or_else(|| "{}".to_string()));
            text.push_str(part);
        }
        text
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bakes_localized_reason_from_distribution_messages() {
        let ban = BanRecord {
            address: "192.0.2.1".parse().unwrap(),
            metadata: json!({"rule": {"key": "IP_BLACKLIST_CIDR_RULE", "params": ["192.0.2.0/24"]}, "description": {"key": "MODULE_IBL_MATCH_IP", "params": ["192.0.2.0/24"]}}),
        };
        assert_eq!(
            bake(&ban, BanLanguage::En, None)["banMetadata"]["description"],
            "Match IP rule: 192.0.2.0/24"
        );
        assert_eq!(
            bake(&ban, BanLanguage::SimplifiedChinese, None)["banMetadata"]["description"],
            "匹配 IP 规则: 192.0.2.0/24"
        );
    }
}
