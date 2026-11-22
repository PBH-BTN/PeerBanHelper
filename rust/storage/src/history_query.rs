//! Queries shared by the persisted-ban HTTP views.

use std::{net::IpAddr, path::Path, time::Duration};

use mysql_async::{IsolationLevel as MySqlIsolation, TxOpts, prelude::Queryable};
use rusqlite::{Connection, OpenFlags, Row, params_from_iter, types::Value as SqliteValue};
use serde_json::Value;
use tokio_postgres::{IsolationLevel as PostgresIsolation, types::ToSql};

use crate::{StorageError, history::java_host_address};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BanHistoryOrder {
    column: &'static str,
    descending: bool,
}

impl BanHistoryOrder {
    /// Parse one `orderBy=field|asc` or `orderBy=field|desc` term.
    pub fn parse(term: &str) -> Result<Self, StorageError> {
        let mut fields = term.split('|');
        let field = fields.next().unwrap_or_default();
        let column = match field {
            "id" => "h.id",
            "banAt" | "ban_at" => "h.ban_at",
            "unbanAt" | "unban_at" => "h.unban_at",
            "peerIp" | "ip" => "h.ip",
            "peerPort" | "port" => "h.port",
            "peerId" | "peer_id" => "h.peer_id",
            "peerClientName" | "peer_client_name" => "h.peer_client_name",
            "peerUploaded" | "peer_uploaded" => "h.peer_uploaded",
            "peerDownloaded" | "peer_downloaded" => "h.peer_downloaded",
            "peerProgress" | "peer_progress" => "h.peer_progress",
            "downloader_progress" => "h.downloader_progress",
            "torrent_id" => "h.torrent_id",
            "torrentInfoHash" | "torrent_info_hash" => "t.info_hash",
            "module" | "module_name" => "h.module_name",
            "rule" | "rule_name" => "h.rule_name",
            "description" => "h.description",
            "flags" => "h.flags",
            "downloader" => "h.downloader",
            "structured_data" => "h.structured_data",
            "peer_geoip" => "h.peer_geoip",
            _ => {
                return Err(StorageError::InvalidHistoryQuery(format!(
                    "unsupported sort field {field:?}"
                )));
            }
        };
        let descending = fields.next().is_some_and(|direction| {
            direction.eq_ignore_ascii_case("desc") || direction.eq_ignore_ascii_case("descend")
        });
        Ok(Self { column, descending })
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BanHistoryFilter {
    Address(IpAddr),
    TorrentId(i64),
    AfterId(i64),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BanHistoryQuery {
    pub page: i64,
    pub size: i64,
    pub orders: Vec<BanHistoryOrder>,
    pub filter: Option<BanHistoryFilter>,
}

impl Default for BanHistoryQuery {
    fn default() -> Self {
        Self {
            page: 1,
            size: 10,
            orders: Vec::new(),
            filter: None,
        }
    }
}

impl BanHistoryQuery {
    fn window(&self, total: u64) -> Result<Option<(i64, i64)>, StorageError> {
        page_window(self.page, self.size, total)
    }

    fn result(
        &self,
        window: Option<(i64, i64)>,
        total: u64,
        records: Vec<BanHistoryRecord>,
    ) -> BanHistoryPage {
        BanHistoryPage {
            page: self.page.max(1) as u64,
            size: window.map_or(self.size, |(size, _)| size) as u64,
            total,
            records,
        }
    }

    fn order_sql(&self) -> String {
        let mut orders: Vec<BanHistoryOrder> = Vec::with_capacity(self.orders.len() + 2);
        for order in &self.orders {
            if let Some(previous) = orders
                .iter_mut()
                .find(|previous| previous.column == order.column)
            {
                *previous = *order;
            } else {
                orders.push(*order);
            }
        }
        for column in ["h.ban_at", "h.id"] {
            if !orders.iter().any(|order| order.column == column) {
                orders.push(BanHistoryOrder {
                    column,
                    descending: true,
                });
            }
        }
        orders
            .into_iter()
            .map(|order| {
                let direction = if order.descending { "DESC" } else { "ASC" };
                format!("{} {direction}", order.column)
            })
            .collect::<Vec<_>>()
            .join(", ")
    }
}

pub(crate) fn page_window(
    page: i64,
    requested_size: i64,
    total: u64,
) -> Result<Option<(i64, i64)>, StorageError> {
    const MAX_PAGE_SIZE: i64 = 1_000;
    let page = page.max(1);
    // MyBatis-Plus decides whether the requested page exists before applying
    // its maximum. A negative size opts out of total counting.
    if requested_size == 0
        || (requested_size > 0 && (page as u64) > total.div_ceil(requested_size as u64))
    {
        return Ok(None);
    }
    let size = if requested_size < 0 {
        MAX_PAGE_SIZE
    } else {
        requested_size.min(MAX_PAGE_SIZE)
    };
    let offset = (page - 1).checked_mul(size).ok_or_else(|| {
        StorageError::InvalidHistoryQuery("page offset exceeds database range".into())
    })?;
    Ok(Some((size, offset)))
}

#[derive(Debug, Clone, PartialEq)]
pub struct BanHistoryPage {
    pub page: u64,
    pub size: u64,
    pub total: u64,
    pub records: Vec<BanHistoryRecord>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct BanHistoryRecord {
    pub id: i64,
    pub ban_at_ms: i64,
    pub unban_at_ms: i64,
    pub address: IpAddr,
    pub port: u16,
    pub peer_id: Option<String>,
    pub peer_client_name: Option<String>,
    pub peer_uploaded: Option<i64>,
    pub peer_downloaded: Option<i64>,
    pub peer_progress: f64,
    pub downloader_progress: f64,
    pub torrent_id: i64,
    pub module_name: String,
    pub rule: Value,
    pub description: Value,
    pub flags: Option<String>,
    pub downloader_id: String,
    pub structured_data: Option<Value>,
    pub peer_geoip: Option<Value>,
    pub torrent: Option<BanHistoryTorrentRecord>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BanHistoryTorrentRecord {
    pub id: i64,
    pub info_hash: String,
    pub name: String,
    pub size: i64,
    pub private: Option<bool>,
}

/// Reads existing SQLite databases without creating files or running migrations.
pub struct SqliteHistoryReader {
    connection: Connection,
}

impl SqliteHistoryReader {
    pub fn open(path: impl AsRef<Path>) -> Result<Self, StorageError> {
        let connection = Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY)?;
        connection.busy_timeout(Duration::from_secs(5))?;
        Ok(Self { connection })
    }

    pub fn ban_history(&self, query: &BanHistoryQuery) -> Result<BanHistoryPage, StorageError> {
        read_sqlite(&self.connection, query)
    }
}

const RECORD_COLUMNS: &str =
    "h.port, h.peer_id, h.peer_client_name, h.peer_uploaded, h.peer_downloaded,
       h.peer_progress, h.downloader_progress, h.torrent_id, h.module_name, h.rule_name,
       h.description, h.flags, h.downloader, h.structured_data, h.peer_geoip,
       t.id, t.info_hash, t.name, t.size, t.private_torrent";

pub(crate) fn read_sqlite(
    connection: &Connection,
    query: &BanHistoryQuery,
) -> Result<BanHistoryPage, StorageError> {
    let (condition, mut args) = match query.filter {
        Some(BanHistoryFilter::Address(address)) => (
            "WHERE h.ip = ?",
            vec![SqliteValue::Text(java_host_address(address))],
        ),
        Some(BanHistoryFilter::TorrentId(id)) => {
            ("WHERE h.torrent_id = ?", vec![SqliteValue::Integer(id)])
        }
        Some(BanHistoryFilter::AfterId(id)) => ("WHERE h.id > ?", vec![SqliteValue::Integer(id)]),
        None => ("", Vec::new()),
    };
    // Keep the count and page on the same database snapshot when scans insert
    // rows between the two statements.
    let transaction = connection.unchecked_transaction()?;
    let total = if query.size < 0 {
        0
    } else {
        transaction.query_row(
            &format!("SELECT COUNT(*) FROM history h {condition}"),
            params_from_iter(&args),
            |row| row.get::<_, i64>(0),
        )? as u64
    };
    let window = query.window(total)?;
    let mut records = Vec::new();
    if let Some((size, offset)) = window {
        args.extend([SqliteValue::Integer(size), SqliteValue::Integer(offset)]);
        let mut statement = transaction.prepare(&format!(
            "SELECT h.id, h.ban_at, h.unban_at, h.ip, {RECORD_COLUMNS}
             FROM history h LEFT JOIN torrents t ON h.torrent_id = t.id
             {condition} ORDER BY {} LIMIT ? OFFSET ?",
            query.order_sql(),
        ))?;
        let mut rows = statement.query(params_from_iter(&args))?;
        while let Some(row) = rows.next()? {
            records.push(decode_sqlite(row)?);
        }
    }
    transaction.commit()?;
    Ok(query.result(window, total, records))
}

pub(crate) async fn read_postgres(
    connection: &mut tokio_postgres::Client,
    query: &BanHistoryQuery,
) -> Result<BanHistoryPage, StorageError> {
    let normalized = match query.filter {
        Some(BanHistoryFilter::Address(address)) => Some(address.to_canonical()),
        _ => None,
    };
    let mut args: Vec<&(dyn ToSql + Sync)> = Vec::new();
    let condition = match (&query.filter, &normalized) {
        (Some(BanHistoryFilter::Address(_)), Some(address)) => {
            args.push(address);
            "WHERE h.ip = $1"
        }
        (Some(BanHistoryFilter::TorrentId(id)), _) => {
            args.push(id);
            "WHERE h.torrent_id = $1"
        }
        (Some(BanHistoryFilter::AfterId(id)), _) => {
            args.push(id);
            "WHERE h.id > $1"
        }
        _ => "",
    };
    let transaction = connection
        .build_transaction()
        .isolation_level(PostgresIsolation::RepeatableRead)
        .read_only(true)
        .start()
        .await?;
    let total = if query.size < 0 {
        0
    } else {
        transaction
            .query_one(
                &format!("SELECT COUNT(*) FROM history h {condition}"),
                &args,
            )
            .await?
            .try_get::<_, i64>(0)? as u64
    };
    let window = query.window(total)?;
    let mut records = Vec::new();
    if let Some((size, offset)) = window {
        let limit_arg = args.len() + 1;
        let offset_arg = limit_arg + 1;
        args.extend([&size as &(dyn ToSql + Sync), &offset]);
        let rows = transaction
            .query(
                &format!(
                    "SELECT h.id, FLOOR(EXTRACT(EPOCH FROM h.ban_at) * 1000)::bigint,
                          FLOOR(EXTRACT(EPOCH FROM h.unban_at) * 1000)::bigint,
                          host(h.ip), {RECORD_COLUMNS}
             FROM history h LEFT JOIN torrents t ON h.torrent_id = t.id
             {condition} ORDER BY {} LIMIT ${limit_arg} OFFSET ${offset_arg}",
                    query.order_sql(),
                ),
                &args,
            )
            .await?;
        for row in rows {
            records.push(decode_postgres(&row)?);
        }
    }
    transaction.commit().await?;
    Ok(query.result(window, total, records))
}

pub(crate) async fn read_mysql(
    connection: &mut mysql_async::Conn,
    query: &BanHistoryQuery,
) -> Result<BanHistoryPage, StorageError> {
    let (condition, mut args) = match query.filter {
        Some(BanHistoryFilter::Address(address)) => {
            ("WHERE h.ip = ?", vec![java_host_address(address).into()])
        }
        Some(BanHistoryFilter::TorrentId(id)) => ("WHERE h.torrent_id = ?", vec![id.into()]),
        Some(BanHistoryFilter::AfterId(id)) => ("WHERE h.id > ?", vec![id.into()]),
        None => ("", Vec::new()),
    };
    let mut options = TxOpts::new();
    options
        .with_isolation_level(MySqlIsolation::RepeatableRead)
        .with_readonly(true);
    let mut transaction = connection.start_transaction(options).await?;
    let total = if query.size < 0 {
        0
    } else {
        transaction
            .exec_first::<u64, _, _>(
                format!("SELECT COUNT(*) FROM `history` h {condition}"),
                args.clone(),
            )
            .await?
            .unwrap_or(0)
    };
    let window = query.window(total)?;
    let mut records = Vec::new();
    if let Some((size, offset)) = window {
        args.extend([mysql_async::Value::from(size), offset.into()]);
        let rows: Vec<mysql_async::Row> = transaction.exec(format!(
            "SELECT h.id,
                    CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', h.ban_at) / 1000) AS SIGNED),
                    CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', h.unban_at) / 1000) AS SIGNED),
                    h.ip, {RECORD_COLUMNS}
             FROM `history` h LEFT JOIN torrents t ON h.torrent_id = t.id
             {condition} ORDER BY {} LIMIT ? OFFSET ?",
            query.order_sql(),
        ), args).await?;
        for row in rows {
            records.push(decode_mysql(&row)?);
        }
    }
    transaction.commit().await?;
    Ok(query.result(window, total, records))
}

// The adapters select the same positions and normalize timestamps to epoch
// milliseconds. Only the column accessor differs between driver row types.
macro_rules! decode_record {
    ($get:ident) => {{
        let id = $get!(0, i64);
        let address = $get!(3, String)
            .parse::<IpAddr>()
            .map_err(|error| malformed(id, "ip", error))?
            .to_canonical();
        let port = u16::try_from($get!(4, i32)).map_err(|error| malformed(id, "port", error))?;
        let torrent = match $get!(19, Option<i64>) {
            Some(torrent_id) => Some(BanHistoryTorrentRecord {
                id: torrent_id,
                info_hash: $get!(20, String),
                name: $get!(21, String),
                size: $get!(22, i64),
                private: $get!(23, Option<bool>),
            }),
            None => None,
        };
        Ok(BanHistoryRecord {
            id,
            ban_at_ms: $get!(1, i64),
            unban_at_ms: $get!(2, i64),
            address,
            port,
            peer_id: $get!(5, Option<String>),
            peer_client_name: $get!(6, Option<String>),
            peer_uploaded: $get!(7, Option<i64>),
            peer_downloaded: $get!(8, Option<i64>),
            peer_progress: $get!(9, f64),
            downloader_progress: $get!(10, f64),
            torrent_id: $get!(11, i64),
            module_name: $get!(12, String),
            rule: decode_json(id, "rule_name", &$get!(13, String))?,
            description: decode_json(id, "description", &$get!(14, String))?,
            flags: $get!(15, Option<String>),
            downloader_id: $get!(16, String),
            structured_data: decode_optional_json(
                id,
                "structured_data",
                $get!(17, Option<String>),
            )?,
            peer_geoip: decode_optional_json(id, "peer_geoip", $get!(18, Option<String>))?,
            torrent,
        })
    }};
}

fn decode_sqlite(row: &Row<'_>) -> Result<BanHistoryRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.get::<_, $ty>($index)?
        };
    }
    decode_record!(get)
}

fn decode_postgres(row: &tokio_postgres::Row) -> Result<BanHistoryRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.try_get::<_, $ty>($index)?
        };
    }
    decode_record!(get)
}

fn decode_mysql(row: &mysql_async::Row) -> Result<BanHistoryRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.get_opt::<$ty, _>($index)
                .and_then(Result::ok)
                .ok_or_else(|| {
                    StorageError::UnsupportedSchema(format!(
                        "invalid MySQL history column {}",
                        $index
                    ))
                })?
        };
    }
    decode_record!(get)
}

fn malformed(id: i64, field: &'static str, error: impl std::fmt::Display) -> StorageError {
    StorageError::InvalidHistoryRow {
        id,
        field,
        reason: error.to_string(),
    }
}

fn decode_json(id: i64, field: &'static str, text: &str) -> Result<Value, StorageError> {
    serde_json::from_str(text).map_err(|error| malformed(id, field, error))
}

fn decode_optional_json(
    id: i64,
    field: &'static str,
    text: Option<String>,
) -> Result<Option<Value>, StorageError> {
    text.map(|text| decode_json(id, field, &text)).transpose()
}
