//! Persistence adapters for PeerBanHelper.

mod alerts;
mod history;
mod history_query;
mod history_ranks;
mod legacy;
mod metadata;
mod metric_charts;
mod mysql;
mod pcb;
mod peer_charts;
mod peer_records;
mod peer_summary;
mod postgres;
mod schema;
mod sessions;
mod subscription_logs;
mod torrent;
mod torrent_search;
mod tracked_swarm;
mod traffic;

use std::{net::IpAddr, path::Path, time::Duration};

use rusqlite::{Connection, TransactionBehavior, params};
use serde_json::Value;
use thiserror::Error;

pub use alerts::{AlertDeduplication, AlertLevel, AlertRecord, NewAlert};
pub use history::{BanHistoryEntry, HistoryTorrent, java_host_address};
pub use history_query::{
    BanHistoryFilter, BanHistoryOrder, BanHistoryPage, BanHistoryQuery, BanHistoryRecord,
    BanHistoryTorrentRecord, SqliteHistoryReader,
};
pub use history_ranks::{BanRank, BanRankPage, BanRankQuery};
pub use metric_charts::{
    HistoryFieldMetric, HistoryMetricField, HistoryMetricOperation, HistoryTimestamp,
    HistoryTimestampCount,
};
pub use mysql::MySqlStore;
pub use mysql_async::{Opts as MySqlOptions, OptsBuilder as MySqlConfig};
pub use pcb::{PcbAddressKey, PcbRangeKey, PcbState};
pub use peer_charts::{
    ChartAddressSource, ChartRange, ClientAnalysisOrder, ClientAnalysisPage, ClientAnalysisRow,
    SessionSpan,
};
pub use peer_records::{
    PeerRecord, PeerRecordCursor, PeerRecordFilter, PeerRecordObservation, PeerRecordOrder,
    PeerRecordPage, PeerRecordQuery, PeerRecordTorrent,
};
pub use peer_summary::PeerHistorySummary;
pub use postgres::PostgresStore;
pub use sessions::{ConnectionMetrics, ConnectionMetricsBucket, PeerSessionObservation};
pub use subscription_logs::{SubscriptionLog, SubscriptionLogPage};
pub use tokio_postgres::Config as PostgresConfig;
pub use torrent::TorrentHistorySummary;
pub use torrent_search::{TorrentSearchPage, TorrentSearchQuery};
pub use tracked_swarm::{
    TrackedSwarmCursor, TrackedSwarmObservation, TrackedSwarmOrder, TrackedSwarmPage,
    TrackedSwarmQuery, TrackedSwarmRecord,
};
pub use traffic::{TrafficHour, TrafficSample, aggregate_traffic_days};

/// The SQLite database used by the current PeerBanHelper backend.
pub const SQLITE_FILENAME: &str = "peerbanhelper-nt.db";

#[derive(Debug, Error)]
pub enum StorageError {
    #[error("{0}")]
    Sqlite(#[from] rusqlite::Error),
    #[error("PostgreSQL: {0}")]
    Postgres(#[from] tokio_postgres::Error),
    #[error("PostgreSQL TLS: {0}")]
    PostgresTls(#[from] native_tls::Error),
    #[error("MySQL: {0}")]
    MySql(#[from] mysql_async::Error),
    #[error("{0}")]
    Io(#[from] std::io::Error),
    #[error("cannot open unsupported database schema: {0}")]
    UnsupportedSchema(String),
    #[error("cannot import legacy ORMLite database: {0}")]
    LegacyDatabase(String),
    #[error("unsupported ban address in storage: {0}")]
    InvalidBanAddress(String),
    #[error("invalid ban metadata for {address}: {reason}")]
    InvalidBanMetadata { address: IpAddr, reason: String },
    #[error("duplicate ban address: {0}")]
    DuplicateBanAddress(IpAddr),
    #[error("timestamp outside the supported range: {0} ms")]
    InvalidTimestamp(i64),
    #[error("invalid ban history query: {0}")]
    InvalidHistoryQuery(String),
    #[error("invalid field {field} in ban history row {id}: {reason}")]
    InvalidHistoryRow {
        id: i64,
        field: &'static str,
        reason: String,
    },
    #[error("invalid field {field} in peer record {id}: {reason}")]
    InvalidPeerRecord {
        id: i64,
        field: &'static str,
        reason: String,
    },
    #[error("invalid tracked swarm sort field: {0}")]
    InvalidTrackedSwarmSort(String),
    #[error("invalid field {field} in tracked swarm record {id}: {reason}")]
    InvalidTrackedSwarmRecord {
        id: i64,
        field: &'static str,
        reason: String,
    },
    #[error("invalid field {field} in alert {id}: {reason}")]
    InvalidAlertRecord {
        id: i64,
        field: &'static str,
        reason: String,
    },
    #[error("could not acquire MySQL alert publication lock")]
    AlertPublicationLock,
}

/// `metadata` is the Java `BanMetadata` JSON object. Preserve unknown fields
/// when updating a ban loaded from SQLite.
#[derive(Debug, Clone, PartialEq)]
pub struct BanRecord {
    pub address: IpAddr,
    pub metadata: Value,
}

pub struct SqliteStore {
    connection: Connection,
}

impl SqliteStore {
    /// Open or initialize a SQLite database. Pass the existing `persist`
    /// directory's `peerbanhelper-nt.db` path for an in-place migration.
    pub fn open(path: impl AsRef<Path>) -> Result<Self, StorageError> {
        let path = path.as_ref();
        let mut connection = Connection::open(path)?;
        connection.busy_timeout(Duration::from_secs(5))?;
        schema::prepare(&mut connection)?;
        if path.file_name().is_some_and(|name| name == SQLITE_FILENAME) {
            legacy::import_pending(&mut connection, path)?;
        }
        Ok(Self { connection })
    }

    /// Return the persisted snapshot, including expired records. The scan
    /// scheduler decides when to expire or reconcile them with the downloader.
    pub fn load_bans(&self) -> Result<Vec<BanRecord>, StorageError> {
        let mut statement = self
            .connection
            .prepare("SELECT address, metadata FROM banlist ORDER BY address")?;
        let mut rows = statement.query([])?;
        let mut bans = Vec::new();
        let mut addresses = std::collections::BTreeSet::new();
        while let Some(row) = rows.next()? {
            let record = decode_ban(row.get(0)?, &row.get::<_, String>(1)?)?;
            if !addresses.insert(record.address) {
                return Err(StorageError::DuplicateBanAddress(record.address));
            }
            bans.push(record);
        }
        Ok(bans)
    }

    pub fn ban_history(&self, query: &BanHistoryQuery) -> Result<BanHistoryPage, StorageError> {
        history_query::read_sqlite(&self.connection, query)
    }

    /// Replace the whole persisted list as one transaction, including clearing
    /// it when `bans` is empty.
    pub fn replace_bans(&mut self, bans: &[BanRecord]) -> Result<(), StorageError> {
        self.commit_ban_wave(Some(bans), &[])
    }

    /// Commit the new ban snapshot and newly recorded history together.
    /// Pass `None` for `bans` to honor `persist.banlist: false`.
    pub fn commit_ban_wave(
        &mut self,
        bans: Option<&[BanRecord]>,
        history: &[BanHistoryEntry<'_>],
    ) -> Result<(), StorageError> {
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        if let Some(bans) = bans {
            transaction.execute("DELETE FROM banlist", [])?;
            let mut insert =
                transaction.prepare("INSERT INTO banlist (address, metadata) VALUES (?1, ?2)")?;
            for ban in bans {
                let address = ban.address.to_canonical();
                validate_ban_metadata(address, &ban.metadata)?;
                insert.execute(params![address.to_string(), ban.metadata.to_string()])?;
            }
        }
        for entry in history {
            history::insert(&transaction, entry)?;
        }
        transaction.commit()?;
        Ok(())
    }
}

fn validate_ban_metadata(address: IpAddr, metadata: &Value) -> Result<(), StorageError> {
    if !metadata.is_object() {
        return Err(StorageError::InvalidBanMetadata {
            address,
            reason: "expected a JSON object".to_string(),
        });
    }
    Ok(())
}

fn decode_ban(address: String, metadata: &str) -> Result<BanRecord, StorageError> {
    let address = address
        .parse::<IpAddr>()
        .map_err(|_| StorageError::InvalidBanAddress(address))?
        .to_canonical();
    let metadata =
        serde_json::from_str(metadata).map_err(|error| StorageError::InvalidBanMetadata {
            address,
            reason: error.to_string(),
        })?;
    validate_ban_metadata(address, &metadata)?;
    Ok(BanRecord { address, metadata })
}
