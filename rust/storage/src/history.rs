use std::net::IpAddr;

use mysql_async::prelude::Queryable;
use rusqlite::{Transaction, params};
use serde_json::Value;

use crate::{MySqlStore, PostgresStore, SqliteStore, StorageError};
use crate::{mysql::to_microseconds, postgres::to_timestamp};

#[derive(Debug, Clone, Copy)]
pub struct HistoryTorrent<'a> {
    pub info_hash: &'a str,
    pub name: &'a str,
    pub size: i64,
    pub private: Option<bool>,
    pub progress: f64,
}

/// One full ban event. The caller excludes actions that only disconnect.
/// `rule` and `description` use PBH's `TranslationComponent` JSON schema.
#[derive(Debug, Clone, Copy)]
pub struct BanHistoryEntry<'a> {
    pub address: IpAddr,
    pub ban_at_ms: i64,
    pub unban_at_ms: i64,
    pub port: u16,
    pub peer_id: Option<&'a str>,
    pub peer_client_name: Option<&'a str>,
    pub peer_uploaded: Option<i64>,
    pub peer_downloaded: Option<i64>,
    pub peer_progress: f64,
    pub torrent: HistoryTorrent<'a>,
    pub module_name: &'a str,
    pub rule: &'a Value,
    pub description: &'a Value,
    pub flags: Option<&'a str>,
    pub downloader: &'a str,
    pub structured_data: Option<&'a Value>,
    pub peer_geoip: Option<&'a Value>,
}

pub(crate) fn insert(
    transaction: &Transaction<'_>,
    entry: &BanHistoryEntry<'_>,
) -> rusqlite::Result<()> {
    let torrent = entry.torrent;
    transaction.execute(
        crate::torrent::SQLITE_UPSERT,
        params![
            torrent.info_hash,
            torrent.name,
            torrent.size,
            torrent.private
        ],
    )?;
    transaction.execute(
        "INSERT INTO history (
             ban_at, unban_at, ip, port, peer_id, peer_client_name, peer_uploaded,
             peer_downloaded, peer_progress, downloader_progress, torrent_id,
             module_name, rule_name, description, flags, downloader,
             structured_data, peer_geoip
         ) VALUES (
             ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10,
             (SELECT id FROM torrents WHERE info_hash = ?11), ?12, ?13, ?14,
             ?15, ?16, ?17, ?18
         )",
        params![
            entry.ban_at_ms,
            entry.unban_at_ms,
            java_host_address(entry.address),
            entry.port,
            entry.peer_id,
            entry.peer_client_name,
            entry.peer_uploaded,
            entry.peer_downloaded,
            entry.peer_progress,
            torrent.progress,
            torrent.info_hash,
            entry.module_name,
            entry.rule.to_string(),
            entry.description.to_string(),
            entry.flags,
            entry.downloader,
            entry.structured_data.map(Value::to_string),
            entry.peer_geoip.map(Value::to_string),
        ],
    )?;
    Ok(())
}

/// Format a host as Java's InetAddress does in historical API responses.
pub fn java_host_address(address: IpAddr) -> String {
    match address.to_canonical() {
        IpAddr::V4(address) => address.to_string(),
        IpAddr::V6(address) => address
            .segments()
            .map(|segment| format!("{segment:x}"))
            .join(":"),
    }
}

const RETENTION_BATCH: u64 = 200;

impl SqliteStore {
    pub fn cleanup_ban_history(&mut self, before_ms: i64) -> Result<u64, StorageError> {
        let mut statement = self.connection.prepare_cached(
            "DELETE FROM history WHERE id IN (
                SELECT id FROM history WHERE ban_at <= ? ORDER BY ban_at, id LIMIT ?
            )",
        )?;
        let mut total = 0;
        loop {
            let deleted = statement.execute(params![before_ms, RETENTION_BATCH as i64])? as u64;
            total += deleted;
            if deleted < RETENTION_BATCH {
                return Ok(total);
            }
        }
    }
}

impl PostgresStore {
    pub async fn cleanup_ban_history(&mut self, before_ms: i64) -> Result<u64, StorageError> {
        let threshold = to_timestamp(before_ms)?;
        let statement = self
            .client
            .prepare(
                "DELETE FROM history WHERE id IN (
                SELECT id FROM history WHERE ban_at <= $1 ORDER BY ban_at, id LIMIT $2
            )",
            )
            .await?;
        let mut total = 0;
        loop {
            let deleted = self
                .client
                .execute(&statement, &[&threshold, &(RETENTION_BATCH as i64)])
                .await?;
            total += deleted;
            if deleted < RETENTION_BATCH {
                return Ok(total);
            }
        }
    }
}

impl MySqlStore {
    pub async fn cleanup_ban_history(&mut self, before_ms: i64) -> Result<u64, StorageError> {
        let threshold = to_microseconds(before_ms)?;
        let statement = self.connection.prep(
            "DELETE FROM history
             WHERE ban_at <= TIMESTAMPADD(MICROSECOND, ?, CAST('1970-01-01 00:00:00' AS DATETIME(3)))
             ORDER BY ban_at, id LIMIT ?",
        ).await?;
        let mut total = 0;
        loop {
            self.connection
                .exec_drop(&statement, (threshold, RETENTION_BATCH))
                .await?;
            let deleted = self.connection.affected_rows();
            total += deleted;
            if deleted < RETENTION_BATCH {
                return Ok(total);
            }
        }
    }
}
