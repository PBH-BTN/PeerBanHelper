//! Protected chart data formatted for the existing WebUI.

use std::cmp::Reverse;
use std::collections::BTreeMap;
use std::net::IpAddr;

use axum::extract::{Query, RawQuery, State, rejection::QueryRejection};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router, routing::get};
use ipnet::IpNet;
use pbh_geoip::{GeoData, GeoIp};
use pbh_storage::{
    ChartAddressSource, ChartRange, ClientAnalysisOrder, ClientAnalysisPage,
    ConnectionMetricsBucket, HistoryTimestamp, HistoryTimestampCount, SessionSpan, StorageError,
    aggregate_traffic_days,
};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use time::OffsetDateTime;

use crate::ban_api::BanLanguage;
use crate::dashboard::start_of_day;
use crate::metric_charts::{group_timestamps, trend_day};
use crate::storage::Database;
use crate::web::ScanState;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ChartQuery {
    start_at: i64,
    end_at: i64,
    downloader: Option<String>,
}

impl ChartQuery {
    fn range(&self) -> ChartRange<'_> {
        ChartRange {
            start_ms: self.start_at,
            end_ms: self.end_at,
            downloader: self.downloader.as_deref(),
        }
    }
}

pub(crate) fn router(state: ScanState) -> Router {
    Router::new()
        .route("/api/chart/geoIpInfo", get(geo_ip_info))
        .route("/api/chart/trend", get(peer_trends))
        .route("/api/chart/traffic", get(traffic))
        .route("/api/chart/sessionBetween", get(session_between))
        .route("/api/chart/sessionDayBucket", get(session_day_bucket))
        .route("/api/chart/sessionAnalyse", get(session_analysis))
        .route("/api/chart/clientAnalyse", get(client_analysis))
        .with_state(state)
}

async fn peer_trends(
    State(state): State<ScanState>,
    headers: HeaderMap,
    query: Result<Query<ChartQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let mut range = query.range();
    range.downloader = range.downloader.filter(|name| !name.trim().is_empty());
    let trends = {
        let mut store = state.database.lock().await;
        read_peer_trends(&mut store, range).await
    };
    let Ok((connected, banned, postgres)) = trends
        .inspect_err(|error| crate::journal::log_warning!("Read peer trend chart: {error:#}"))
    else {
        return internal_error(&headers);
    };
    let (Some(connected), Some(banned)) = (
        group_timestamps(connected, |event| trend_day(event, postgres)),
        group_timestamps(banned, |event| trend_day(event, postgres)),
    ) else {
        return internal_error(&headers);
    };
    success(json!({
        "connectedPeersTrend": key_value_rows(connected),
        "bannedPeersTrend": key_value_rows(banned),
    }))
}

async fn read_peer_trends(
    database: &mut Database,
    range: ChartRange<'_>,
) -> Result<(Vec<HistoryTimestampCount>, Vec<HistoryTimestampCount>, bool), StorageError> {
    let (field, start, end, downloader) = (
        HistoryTimestamp::BanAt,
        range.start_ms,
        range.end_ms,
        range.downloader,
    );
    match database {
        Database::Sqlite(store) => Ok((
            store.peer_chart_timestamps(range)?,
            store.history_timestamp_counts(field, start, end, downloader)?,
            false,
        )),
        Database::Postgres(store) => Ok((
            store.peer_chart_timestamps(range).await?,
            store
                .history_timestamp_counts(field, start, end, downloader)
                .await?,
            true,
        )),
        Database::MySql(store) => Ok((
            store.peer_chart_timestamps(range).await?,
            store
                .history_timestamp_counts(field, start, end, downloader)
                .await?,
            false,
        )),
    }
}

async fn session_between(
    State(state): State<ScanState>,
    headers: HeaderMap,
    query: Result<Query<ChartQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let mut range = query.range();
    range.downloader = range.downloader.filter(|name| !name.trim().is_empty());
    let count = {
        let mut store = state.database.lock().await;
        match &mut *store {
            Database::Sqlite(store) => store.session_address_count(range),
            Database::Postgres(store) => store.session_address_count(range).await,
            Database::MySql(store) => store.session_address_count(range).await,
        }
    };
    match count {
        Ok(count) => success(count),
        Err(error) => chart_error("session count", &headers, error),
    }
}

async fn session_day_bucket(
    State(state): State<ScanState>,
    headers: HeaderMap,
    query: Result<Query<ChartQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let range = query.range();
    let (spans, postgres) = {
        let mut store = state.database.lock().await;
        match &mut *store {
            Database::Sqlite(store) => (store.session_spans(range), false),
            Database::Postgres(store) => (store.session_spans(range).await, true),
            Database::MySql(store) => (store.session_spans(range).await, false),
        }
    };
    let spans = match spans {
        Ok(spans) => spans,
        Err(error) => return chart_error("session day buckets", &headers, error),
    };
    match session_days(spans, range, postgres) {
        Some(days) => success(days),
        None => internal_error(&headers),
    }
}

#[derive(Debug, PartialEq, Eq, Serialize)]
struct SessionDay {
    key: i64,
    total: i64,
    incoming: i64,
}

fn session_days(
    spans: Vec<SessionSpan>,
    range: ChartRange<'_>,
    postgres: bool,
) -> Option<Vec<SessionDay>> {
    const DAY_MS: i64 = 86_400_000;
    let mut buckets = BTreeMap::new();
    for session in spans {
        let mut day = chart_day(session.first_seen_ms, postgres)?;
        let last = chart_day(session.last_seen_ms, postgres)?.min(range.end_ms);
        let incoming = i64::from(session.flags.is_some_and(|flags| flags.contains('I')));
        while day <= last {
            if day >= range.start_ms {
                let bucket = buckets.entry(day).or_insert(SessionDay {
                    key: day,
                    total: 0,
                    incoming: 0,
                });
                bucket.total += 1;
                bucket.incoming += incoming;
            }
            day += DAY_MS;
        }
    }
    Some(buckets.into_values().collect())
}

fn chart_day(timestamp_ms: i64, postgres: bool) -> Option<i64> {
    let date =
        OffsetDateTime::from_unix_timestamp_nanos(i128::from(timestamp_ms) * 1_000_000).ok()?;
    trend_day(date, postgres)
}

async fn geo_ip_info(
    State(state): State<ScanState>,
    headers: HeaderMap,
    RawQuery(raw): RawQuery,
    query: Result<Query<ChartQuery>, QueryRejection>,
) -> Response {
    let Some(geoip) = state.geoip.current() else {
        let language = BanLanguage::from_headers(&headers);
        let message = language.render(&json!({"key": "CHARTS_IPDB_NEED_INIT"}));
        return Json(json!({"success": false, "message": message, "data": null})).into_response();
    };
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let banned_only = form_urlencoded::parse(raw.as_deref().unwrap_or_default().as_bytes())
        .find(|(key, _)| key == "bannedOnly")
        .is_some_and(|(_, value)| value.eq_ignore_ascii_case("true"));
    let source = if banned_only {
        ChartAddressSource::BanHistory
    } else {
        ChartAddressSource::PeerHistory
    };
    let mut range = query.range();
    range.downloader = range.downloader.filter(|name| !name.is_empty());
    let addresses = {
        let mut store = state.database.lock().await;
        match &mut *store {
            Database::Sqlite(store) => store.chart_addresses(range, source),
            Database::Postgres(store) => store.chart_addresses(range, source).await,
            Database::MySql(store) => store.chart_addresses(range, source).await,
        }
    };
    let addresses = match addresses {
        Ok(addresses) => addresses,
        Err(error) => return chart_error("GeoIP", &headers, error),
    };
    // Database lookups use a consistent in-memory snapshot for the whole chart.
    match tokio::task::spawn_blocking(move || geo_breakdown(&geoip, &addresses)).await {
        Ok(data) => success(data),
        Err(error) => chart_error("GeoIP", &headers, error),
    }
}

fn geo_breakdown(geoip: &GeoIp, addresses: &[String]) -> Value {
    let mut groups = GeoGroups::default();
    for raw in addresses {
        let address = raw
            .parse::<IpAddr>()
            .ok()
            .or_else(|| raw.parse::<IpNet>().ok().map(|net| net.network()));
        if let Some(address) = address {
            groups.add(geoip.lookup(address.to_canonical()));
        }
    }
    groups.into_json()
}

#[derive(Default)]
struct GeoGroups {
    isp: BTreeMap<String, i64>,
    province: BTreeMap<String, i64>,
    region: BTreeMap<String, i64>,
    city: BTreeMap<String, i64>,
}

impl GeoGroups {
    fn add(&mut self, geo: GeoData) {
        const UNKNOWN: &str = "N/A";
        let isp = match geo.network {
            Some(network) => network.isp,
            None => geo.asn.and_then(|asn| asn.organization),
        };
        let region = geo.country.and_then(|country| country.name);
        let (province, city) = geo.city.map_or((None, None), |city| {
            let name = if let Some(local) = city.cn_city {
                Some(format!(
                    "{} {local}",
                    city.cn_province.as_deref().unwrap_or("null")
                ))
            } else {
                city.name
            };
            (city.cn_province, name)
        });
        for (group, name) in [
            (&mut self.isp, isp),
            (&mut self.province, province),
            (&mut self.region, region),
            (&mut self.city, city),
        ] {
            *group
                .entry(name.unwrap_or_else(|| UNKNOWN.to_owned()))
                .or_default() += 1;
        }
    }

    fn into_json(self) -> Value {
        fn sorted(values: BTreeMap<String, i64>) -> Vec<Value> {
            let mut values = values.into_iter().collect::<Vec<_>>();
            values.sort_by_key(|(_, count)| Reverse(*count));
            key_value_rows(values)
        }
        json!({"isp": sorted(self.isp), "province": sorted(self.province), "region": sorted(self.region), "city": sorted(self.city)})
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ClientQuery {
    start_at: i64,
    end_at: i64,
    downloader: Option<String>,
    page: Option<i64>,
    page_size: Option<i64>,
}

async fn client_analysis(
    State(state): State<ScanState>,
    headers: HeaderMap,
    RawQuery(raw): RawQuery,
    query: Result<Query<ClientQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let orders = form_urlencoded::parse(raw.as_deref().unwrap_or_default().as_bytes())
        .filter(|(key, _)| key == "orderBy")
        .map(|(_, order)| ClientAnalysisOrder::parse(&order))
        .collect::<Result<Vec<_>, _>>();
    let orders = match orders {
        Ok(orders) => orders,
        Err(error) => return chart_error("client analysis", &headers, error),
    };
    let (page, size) = (query.page.unwrap_or(1), query.page_size.unwrap_or(10));
    let range = ChartRange {
        start_ms: query.start_at,
        end_ms: query.end_at,
        downloader: query.downloader.as_deref(),
    };
    let clients: Result<ClientAnalysisPage, _> = {
        let mut store = state.database.lock().await;
        match &mut *store {
            Database::Sqlite(store) => store.client_analysis(range, page, size, &orders),
            Database::Postgres(store) => store.client_analysis(range, page, size, &orders).await,
            Database::MySql(store) => store.client_analysis(range, page, size, &orders).await,
        }
    };
    let clients = match clients {
        Ok(clients) => clients,
        Err(error) => return chart_error("client analysis", &headers, error),
    };
    let results: Vec<_> = clients
        .rows
        .into_iter()
        .map(|row| {
            json!({
                "peerId": row.peer_id, "clientName": row.client_name, "uploaded": row.uploaded,
                "downloaded": row.downloaded, "count": row.count,
            })
        })
        .collect();
    success(json!({"page": page, "size": size, "total": clients.total, "results": results}))
}

fn key_value_rows<T: Serialize>(rows: impl IntoIterator<Item = (T, i64)>) -> Vec<Value> {
    rows.into_iter()
        .map(|(key, value)| json!({"key": key, "value": value}))
        .collect()
}

fn success(data: impl Serialize) -> Response {
    Json(json!({"success": true, "message": null, "data": data})).into_response()
}

fn chart_error(name: &str, headers: &HeaderMap, error: impl std::fmt::Display) -> Response {
    crate::journal::log_warning!("Read {name} chart: {error:#}");
    internal_error(headers)
}

async fn session_analysis(
    State(state): State<ScanState>,
    headers: HeaderMap,
    query: Result<Query<ChartQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let rows = state
        .database
        .lock()
        .await
        .connection_metrics(query.start_at, query.end_at, query.downloader.as_deref())
        .await;
    let Ok(rows) = rows.inspect_err(|error| {
        crate::journal::log_warning!("Read session analysis chart: {error:#}")
    }) else {
        return internal_error(&headers);
    };
    let data = rows.into_iter().map(session_json).collect::<Vec<_>>();
    Json(json!({"success": true, "message": null, "data": data})).into_response()
}

fn session_json(bucket: ConnectionMetricsBucket) -> Value {
    let metrics = bucket.metrics;
    json!({
        "key": bucket.key,
        "totalConnections": metrics.total_connections,
        "incomingConnections": metrics.incoming_connections,
        "remoteRefuseTransferToClient": metrics.remote_refuse_transfer_to_client,
        "remoteAcceptTransferToClient": metrics.remote_accept_transfer_to_client,
        "localRefuseTransferToPeer": metrics.local_refuse_transfer_to_peer,
        "localAcceptTransferToPeer": metrics.local_accept_transfer_to_peer,
        "localNotInterested": metrics.local_not_interested,
        "questionStatus": metrics.question_status,
        "optimisticUnchoke": metrics.optimistic_unchoke,
        "fromDHT": metrics.from_dht,
        "fromPEX": metrics.from_pex,
        "fromLSD": metrics.from_lsd,
        "fromTrackerOrOther": metrics.from_tracker_or_other,
        "rc4Encrypted": metrics.rc4_encrypted,
        "plainTextEncrypted": metrics.plain_text_encrypted,
        "utpSocket": metrics.utp_socket,
        "tcpSocket": metrics.tcp_socket,
    })
}

async fn traffic(
    State(state): State<ScanState>,
    headers: HeaderMap,
    query: Result<Query<ChartQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let hours = state
        .database
        .lock()
        .await
        .traffic_hours(query.downloader.as_deref(), query.start_at, query.end_at)
        .await;
    let Ok(hours) =
        hours.inspect_err(|error| crate::journal::log_warning!("Read traffic chart: {error:#}"))
    else {
        return internal_error(&headers);
    };
    let days = aggregate_traffic_days(hours, |ms| {
        OffsetDateTime::from_unix_timestamp_nanos(i128::from(ms) * 1_000_000)
            .map_or(ms, start_of_day)
    });
    let data = days
        .into_iter()
        .map(|day| {
            json!({
                "timestamp": day.timestamp_ms,
                "dataOverallUploaded": day.uploaded,
                "dataOverallDownloaded": day.downloaded,
            })
        })
        .collect::<Vec<_>>();
    Json(json!({"success": true, "message": null, "data": data})).into_response()
}

fn internal_error(headers: &HeaderMap) -> Response {
    let language = BanLanguage::from_headers(headers);
    let message = language.render(&json!({"key": "WEBAPI_INTERNAL_ERROR"}));
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(json!({"success": false, "message": message, "data": null})),
    )
        .into_response()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn session_buckets_clip_partial_days_and_count_incoming_flags() {
        const DAY: i64 = 86_400_000;
        let day = chart_day(1_701_000_000_000, false).unwrap();
        let range = ChartRange {
            start_ms: day + 1,
            end_ms: day + DAY * 2,
            downloader: None,
        };
        let spans = vec![
            SessionSpan {
                first_seen_ms: day + 1_000,
                last_seen_ms: day + DAY * 2,
                flags: Some("DI".into()),
            },
            SessionSpan {
                first_seen_ms: day + DAY + 1_000,
                last_seen_ms: day + DAY + 2_000,
                flags: Some("L".into()),
            },
        ];
        assert_eq!(
            session_days(spans, range, false).unwrap(),
            [
                SessionDay {
                    key: day + DAY,
                    total: 2,
                    incoming: 1
                },
                SessionDay {
                    key: day + DAY * 2,
                    total: 1,
                    incoming: 1
                },
            ]
        );
    }
}
