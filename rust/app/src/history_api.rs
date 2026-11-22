//! Historical ban and peer data for the existing WebUI.

use std::net::{IpAddr, SocketAddr};

use axum::extract::{Path, RawQuery, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router, routing::get};
use pbh_storage::{
    BanHistoryFilter, BanHistoryOrder, BanHistoryQuery, BanRankQuery, PeerRecord, PeerRecordFilter,
    PeerRecordOrder, PeerRecordQuery, StorageError, TorrentHistorySummary, TorrentSearchQuery,
    java_host_address,
};
use serde_json::{Value, json};

use crate::{
    ban_api::{BanLanguage, history_json},
    storage::Database,
    web::ScanState,
};

pub(crate) fn router(state: ScanState) -> Router {
    Router::new()
        .route("/api/bans/ranks", get(ban_ranks))
        .route("/api/peer/info/{ip}", get(peer_info))
        .route("/api/torrent/query", get(torrent_search))
        .route("/api/torrent/{infoHash}", get(torrent_info))
        .with_state(state)
}

async fn peer_info(
    State(state): State<ScanState>,
    headers: HeaderMap,
    Path(host): Path<String>,
) -> Response {
    let language = BanLanguage::from_headers(&headers);
    let Some(ip) = peer_info_address(&host) else {
        return internal_error(language);
    };
    let summary = match &mut *state.database.lock().await {
        Database::Sqlite(store) => store.peer_history_summary(ip),
        Database::Postgres(store) => store.peer_history_summary(ip).await,
        Database::MySql(store) => store.peer_history_summary(ip).await,
    };
    let summary = match summary {
        Ok(summary) => summary,
        Err(error) => {
            eprintln!("Read peer information: {error}");
            return internal_error(language);
        }
    };
    let geo = state.geoip.current().map(|geoip| geoip.lookup(ip));
    let ptr_lookup = state.dashboard.dns.ptr_lookup(ip).await;
    let btn_query_available = state
        .btn_negotiated
        .borrow()
        .as_ref()
        .is_some_and(|cfg| cfg.ip_query.is_some());
    response(
        StatusCode::OK,
        None,
        json!({
            "found": summary.found(), "address": java_host_address(ip),
            "firstTimeSeen": summary.first_time_seen_ms, "lastTimeSeen": summary.last_time_seen_ms,
            "banCount": summary.ban_count, "torrentAccessCount": summary.torrent_access_count,
            "uploadedToPeer": summary.uploaded_to_peer, "downloadedFromPeer": summary.downloaded_from_peer,
            "geo": geo, "ptrLookup": ptr_lookup, "btnQueryAvailable": btn_query_available,
        }),
    )
}

fn peer_info_address(host: &str) -> Option<IpAddr> {
    let bare = host.strip_suffix(':').unwrap_or(host);
    host.parse::<IpAddr>()
        .ok()
        .or_else(|| bare.strip_prefix('[')?.strip_suffix(']')?.parse().ok())
        .or_else(|| bare.parse::<IpAddr>().ok())
        .or_else(|| host.parse::<SocketAddr>().ok().map(|socket| socket.ip()))
        .map(|ip| ip.to_canonical())
}

async fn torrent_search(
    State(state): State<ScanState>,
    headers: HeaderMap,
    RawQuery(raw): RawQuery,
) -> Response {
    let language = BanLanguage::from_headers(&headers);
    let Some(params) = HistoryParams::parse(raw.as_deref()) else {
        return internal_error(language);
    };
    let Ok(query) =
        TorrentSearchQuery::new(params.page, params.size, params.keyword, &params.order_by)
    else {
        return internal_error(language);
    };
    let result = match &mut *state.database.lock().await {
        Database::Sqlite(store) => store.search_torrents(&query),
        Database::Postgres(store) => store.search_torrents(&query).await,
        Database::MySql(store) => store.search_torrents(&query).await,
    };
    match result {
        Ok(page) => paginated(
            page.page,
            page.size,
            page.total,
            page.records.into_iter().map(torrent_json).collect(),
        ),
        Err(error) => {
            eprintln!("Search persisted torrents: {error}");
            internal_error(language)
        }
    }
}

pub(crate) fn plus_router(state: ScanState) -> Router {
    Router::new()
        .route("/api/peer/banHistory/{ip}", get(peer_ban_history))
        .route("/api/peer/accessHistory/{ip}", get(peer_access_history))
        .route(
            "/api/torrent/{infoHash}/banHistory",
            get(torrent_ban_history),
        )
        .route(
            "/api/torrent/{infoHash}/accessHistory",
            get(torrent_access_history),
        )
        .with_state(state)
}

async fn ban_ranks(
    State(state): State<ScanState>,
    headers: HeaderMap,
    RawQuery(raw): RawQuery,
) -> Response {
    let language = BanLanguage::from_headers(&headers);
    let Some(params) = HistoryParams::parse(raw.as_deref()) else {
        return internal_error(language);
    };
    let query = BanRankQuery {
        page: params.page,
        size: params.size,
        filter: params.filter,
    };
    let result = match &mut *state.database.lock().await {
        Database::Sqlite(store) => store.ban_ranks(&query),
        Database::Postgres(store) => store.ban_ranks(&query).await,
        Database::MySql(store) => store.ban_ranks(&query).await,
    };
    match result {
        Ok(page) => {
            let results = page
                .records
                .into_iter()
                .map(|rank| json!({"peerIp": rank.peer_ip, "count": rank.count}))
                .collect::<Vec<_>>();
            paginated(page.page, page.size, page.total, results)
        }
        Err(error) => {
            eprintln!("Read ban ranks: {error}");
            internal_error(language)
        }
    }
}

async fn torrent_info(
    State(state): State<ScanState>,
    headers: HeaderMap,
    Path(info_hash): Path<String>,
) -> Response {
    let language = BanLanguage::from_headers(&headers);
    let result = torrent_summary(&mut *state.database.lock().await, &info_hash).await;
    match result {
        Ok(Some(summary)) => response(StatusCode::OK, None, torrent_json(summary)),
        Ok(None) => torrent_not_found(language),
        Err(error) => {
            eprintln!("Read torrent information: {error}");
            internal_error(language)
        }
    }
}

fn torrent_json(summary: TorrentHistorySummary) -> Value {
    json!({
        "infoHash": summary.torrent.info_hash, "name": summary.torrent.name, "size": summary.torrent.size,
        "peerBanCount": summary.ban_count, "peerAccessCount": summary.access_count,
    })
}

async fn peer_ban_history(
    State(state): State<ScanState>,
    headers: HeaderMap,
    Path(ip): Path<String>,
    RawQuery(raw): RawQuery,
) -> Response {
    filtered_history(
        state,
        headers,
        raw.as_deref(),
        Subject::Peer(ip),
        HistoryKind::Ban,
    )
    .await
}

async fn peer_access_history(
    State(state): State<ScanState>,
    headers: HeaderMap,
    Path(ip): Path<String>,
    RawQuery(raw): RawQuery,
) -> Response {
    filtered_history(
        state,
        headers,
        raw.as_deref(),
        Subject::Peer(ip),
        HistoryKind::Access,
    )
    .await
}

async fn torrent_ban_history(
    State(state): State<ScanState>,
    headers: HeaderMap,
    Path(hash): Path<String>,
    RawQuery(raw): RawQuery,
) -> Response {
    filtered_history(
        state,
        headers,
        raw.as_deref(),
        Subject::Torrent(hash),
        HistoryKind::Ban,
    )
    .await
}

async fn torrent_access_history(
    State(state): State<ScanState>,
    headers: HeaderMap,
    Path(hash): Path<String>,
    RawQuery(raw): RawQuery,
) -> Response {
    filtered_history(
        state,
        headers,
        raw.as_deref(),
        Subject::Torrent(hash),
        HistoryKind::Access,
    )
    .await
}

enum Subject {
    Peer(String),
    Torrent(String),
}

#[derive(Clone, Copy)]
enum HistoryKind {
    Ban,
    Access,
}

enum HistoryFilter {
    Peer(IpAddr),
    Torrent(i64),
}

async fn filtered_history(
    state: ScanState,
    headers: HeaderMap,
    raw: Option<&str>,
    subject: Subject,
    kind: HistoryKind,
) -> Response {
    let language = BanLanguage::from_headers(&headers);
    let mut database = state.database.lock().await;
    let filter = match subject {
        Subject::Peer(ip) => match ip.parse() {
            Ok(ip) => HistoryFilter::Peer(ip),
            Err(_) => return internal_error(language),
        },
        Subject::Torrent(hash) => match torrent_summary(&mut database, &hash).await {
            Ok(Some(summary)) => HistoryFilter::Torrent(summary.torrent.id),
            Ok(None) => return torrent_not_found(language),
            Err(error) => {
                eprintln!("Look up history torrent: {error}");
                return internal_error(language);
            }
        },
    };
    let Some(params) = HistoryParams::parse(raw) else {
        return internal_error(language);
    };
    match kind {
        HistoryKind::Ban => ban_history(&mut database, &state, language, params, filter).await,
        HistoryKind::Access => {
            access_history(&mut database, &state, language, params, filter).await
        }
    }
}

async fn torrent_summary(
    database: &mut Database,
    info_hash: &str,
) -> Result<Option<TorrentHistorySummary>, StorageError> {
    match database {
        Database::Sqlite(store) => store.torrent_history_summary(info_hash),
        Database::Postgres(store) => store.torrent_history_summary(info_hash).await,
        Database::MySql(store) => store.torrent_history_summary(info_hash).await,
    }
}

async fn ban_history(
    database: &mut Database,
    state: &ScanState,
    language: BanLanguage,
    params: HistoryParams,
    filter: HistoryFilter,
) -> Response {
    let by_peer = matches!(&filter, HistoryFilter::Peer(_));
    let filter = match filter {
        HistoryFilter::Peer(ip) => BanHistoryFilter::Address(ip),
        HistoryFilter::Torrent(id) => BanHistoryFilter::TorrentId(id),
    };
    let Ok(orders) = params
        .order_by
        .iter()
        .map(|term| BanHistoryOrder::parse(term))
        .collect::<Result<_, _>>()
    else {
        return internal_error(language);
    };
    let query = BanHistoryQuery {
        page: params.page,
        size: params.size,
        orders,
        filter: Some(filter),
    };
    match database.ban_history(&query).await {
        Ok(page) => {
            let results = page
                .records
                .iter()
                .map(|record| {
                    let mut row = history_json(record, language, &state.downloader_info);
                    row["peerIp"] = json!(java_host_address(record.address));
                    if by_peer {
                        row["peerUploaded"] = json!(record.peer_uploaded);
                        row["peerDownloaded"] = json!(record.peer_downloaded);
                        row["torrentSize"] =
                            json!(record.torrent.as_ref().map(|torrent| torrent.size));
                    }
                    row
                })
                .collect::<Vec<_>>();
            paginated(page.page as i64, page.size as i64, page.total, results)
        }
        Err(error) => {
            eprintln!("Read filtered ban history: {error}");
            internal_error(language)
        }
    }
}

async fn access_history(
    database: &mut Database,
    state: &ScanState,
    language: BanLanguage,
    params: HistoryParams,
    filter: HistoryFilter,
) -> Response {
    let filter = match filter {
        HistoryFilter::Peer(ip) => PeerRecordFilter::Address(ip),
        HistoryFilter::Torrent(id) => PeerRecordFilter::TorrentId(id),
    };
    let Ok(orders) = params
        .order_by
        .iter()
        .map(|term| PeerRecordOrder::parse(term))
        .collect::<Result<_, _>>()
    else {
        return internal_error(language);
    };
    let query = PeerRecordQuery {
        page: params.page,
        size: params.size,
        orders,
        filter,
    };
    let result = match database {
        Database::Sqlite(store) => store.peer_history(&query),
        Database::Postgres(store) => store.peer_history(&query).await,
        Database::MySql(store) => store.peer_history(&query).await,
    };
    match result {
        Ok(page) => {
            let results = page
                .records
                .iter()
                .map(|record| access_json(record, state))
                .collect();
            paginated(page.page as i64, page.size as i64, page.total, results)
        }
        Err(error) => {
            eprintln!("Read peer access history: {error}");
            internal_error(language)
        }
    }
}

fn access_json(record: &PeerRecord, state: &ScanState) -> Value {
    let torrent = record.torrent.as_ref().map(|torrent| {
        json!({
            "id": torrent.id, "infoHash": torrent.info_hash, "name": torrent.name,
            "size": torrent.size, "privateTorrent": torrent.private,
        })
    });
    let downloader = state
        .downloader_info
        .get(&record.downloader_id)
        .cloned()
        .unwrap_or_else(
            || json!({"id": record.downloader_id, "name": "Unknown", "type": "Unknown"}),
        );
    json!({
        "id": record.id, "address": java_host_address(record.address), "port": record.port,
        "torrent": torrent, "downloader": downloader, "peerId": record.peer_id, "clientName": record.client_name,
        "uploaded": record.uploaded, "uploadedOffset": record.uploaded_offset, "uploadSpeed": record.upload_speed,
        "downloaded": record.downloaded, "downloadedOffset": record.downloaded_offset, "downloadSpeed": record.download_speed,
        "lastFlags": record.last_flags, "firstTimeSeen": record.first_time_seen_ms, "lastTimeSeen": record.last_time_seen_ms,
    })
}

struct HistoryParams {
    page: i64,
    size: i64,
    filter: Option<String>,
    keyword: Option<String>,
    order_by: Vec<String>,
}

impl HistoryParams {
    fn parse(raw: Option<&str>) -> Option<Self> {
        let mut result = Self {
            page: 1,
            size: 10,
            filter: None,
            keyword: None,
            order_by: Vec::new(),
        };
        let (mut page_seen, mut size_seen) = (false, false);
        for (key, value) in form_urlencoded::parse(raw.unwrap_or_default().as_bytes()) {
            match key.as_ref() {
                "page" if !page_seen => {
                    result.page = value.parse().ok()?;
                    page_seen = true;
                }
                "pageSize" if !size_seen => {
                    result.size = value.parse().ok()?;
                    size_seen = true;
                }
                "filter" if result.filter.is_none() => result.filter = Some(value.into_owned()),
                "keyword" if result.keyword.is_none() => result.keyword = Some(value.into_owned()),
                "orderBy" => result.order_by.push(value.into_owned()),
                _ => {}
            }
        }
        Some(result)
    }
}

fn torrent_not_found(language: BanLanguage) -> Response {
    let message = language.render(&json!({"key": "TORRENT_NOT_FOUND"}));
    response(StatusCode::NOT_FOUND, Some(&message), Value::Null)
}

fn paginated(page: i64, size: i64, total: u64, results: Vec<Value>) -> Response {
    response(
        StatusCode::OK,
        None,
        json!({ "page": page, "size": size, "total": total, "results": results }),
    )
}

fn internal_error(language: BanLanguage) -> Response {
    let message = language.render(&json!({"key": "WEBAPI_INTERNAL_ERROR"}));
    response(
        StatusCode::INTERNAL_SERVER_ERROR,
        Some(&message),
        Value::Null,
    )
}

fn response(status: StatusCode, message: Option<&str>, data: Value) -> Response {
    (
        status,
        Json(json!({"success": status.is_success(), "message": message, "data": data})),
    )
        .into_response()
}
