//! MySQL and MariaDB persistence for current bans and ban history.

mod schema;

use std::collections::BTreeSet;

use mysql_async::{Conn, Opts, Transaction, TxOpts, from_row_opt, params, prelude::Queryable};
use serde_json::Value;

use crate::{
    BanHistoryEntry, BanHistoryPage, BanHistoryQuery, BanRecord, StorageError, decode_ban,
    history::java_host_address, history_query, validate_ban_metadata,
};

pub struct MySqlStore {
    pub(crate) connection: Conn,
}

pub(crate) fn to_microseconds(milliseconds: i64) -> Result<i64, StorageError> {
    milliseconds
        .checked_mul(1000)
        .ok_or(StorageError::InvalidTimestamp(milliseconds))
}

impl MySqlStore {
    /// Authenticate without preparing the schema or writing any tables.
    pub async fn test_connection(options: impl Into<Opts>) -> Result<(), StorageError> {
        let mut connection = Conn::new(options.into()).await?;
        let result = connection.query_drop("SELECT 1").await;
        let _ = connection.disconnect().await;
        result?;
        Ok(())
    }

    pub async fn open(options: impl Into<Opts>) -> Result<Self, StorageError> {
        let mut connection = Conn::new(options.into()).await?;
        connection
            .query_drop("SET SESSION time_zone = '+00:00'")
            .await?;
        schema::prepare(&mut connection).await?;
        Ok(Self { connection })
    }

    pub async fn load_bans(&mut self) -> Result<Vec<BanRecord>, StorageError> {
        let rows: Vec<mysql_async::Row> = self
            .connection
            .query("SELECT address, metadata FROM banlist ORDER BY address")
            .await?;
        let mut addresses = BTreeSet::new();
        let mut bans = Vec::with_capacity(rows.len());
        for row in rows {
            let (address, metadata): (String, String) = from_row_opt(row).map_err(|_| {
                StorageError::UnsupportedSchema(
                    "invalid MySQL banlist column types or values".into(),
                )
            })?;
            let record = decode_ban(address, &metadata)?;
            if !addresses.insert(record.address) {
                return Err(StorageError::DuplicateBanAddress(record.address));
            }
            bans.push(record);
        }
        Ok(bans)
    }

    pub async fn ban_history(
        &mut self,
        query: &BanHistoryQuery,
    ) -> Result<BanHistoryPage, StorageError> {
        history_query::read_mysql(&mut self.connection, query).await
    }

    pub async fn replace_bans(&mut self, bans: &[BanRecord]) -> Result<(), StorageError> {
        self.commit_ban_wave(Some(bans), &[]).await
    }

    pub async fn commit_ban_wave(
        &mut self,
        bans: Option<&[BanRecord]>,
        history: &[BanHistoryEntry<'_>],
    ) -> Result<(), StorageError> {
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        if let Some(bans) = bans {
            transaction.query_drop("DELETE FROM banlist").await?;
            let insert = transaction
                .prep("INSERT INTO banlist (address, metadata) VALUES (?, ?)")
                .await?;
            for ban in bans {
                let address = ban.address.to_canonical();
                validate_ban_metadata(address, &ban.metadata)?;
                transaction
                    .exec_drop(&insert, (address.to_string(), ban.metadata.to_string()))
                    .await?;
            }
        }
        for entry in history {
            insert_history(&mut transaction, entry).await?;
        }
        transaction.commit().await?;
        Ok(())
    }
}

async fn insert_history(
    transaction: &mut Transaction<'_>,
    entry: &BanHistoryEntry<'_>,
) -> Result<(), StorageError> {
    let ban_at = entry
        .ban_at_ms
        .checked_mul(1_000)
        .ok_or(StorageError::InvalidTimestamp(entry.ban_at_ms))?;
    let unban_at = entry
        .unban_at_ms
        .checked_mul(1_000)
        .ok_or(StorageError::InvalidTimestamp(entry.unban_at_ms))?;
    let torrent = entry.torrent;
    transaction
        .exec_drop(
            crate::torrent::MYSQL_UPSERT,
            params! {
                "info_hash" => torrent.info_hash,
                "name" => torrent.name,
                "size" => torrent.size,
                "private_torrent" => torrent.private,
            },
        )
        .await?;
    transaction.exec_drop(
        "INSERT INTO `history` (
             ban_at, unban_at, ip, port, peer_id, peer_client_name, peer_uploaded,
             peer_downloaded, peer_progress, downloader_progress, torrent_id,
             module_name, rule_name, `description`, flags, downloader, structured_data, peer_geoip
         ) VALUES (
             TIMESTAMPADD(MICROSECOND, :ban_at, CAST('1970-01-01 00:00:00' AS DATETIME(3))),
             TIMESTAMPADD(MICROSECOND, :unban_at, CAST('1970-01-01 00:00:00' AS DATETIME(3))),
             :ip, :port, :peer_id, :peer_client_name, :peer_uploaded, :peer_downloaded, :peer_progress,
             :downloader_progress, (SELECT id FROM torrents WHERE info_hash = :info_hash),
             :module_name, :rule_name, :description, :flags, :downloader, :structured_data, :peer_geoip
         )",
        params! {
            "ban_at" => ban_at,
            "unban_at" => unban_at,
            "ip" => java_host_address(entry.address),
            "port" => entry.port,
            "peer_id" => entry.peer_id,
            "peer_client_name" => entry.peer_client_name,
            "peer_uploaded" => entry.peer_uploaded,
            "peer_downloaded" => entry.peer_downloaded,
            "peer_progress" => entry.peer_progress,
            "downloader_progress" => torrent.progress,
            "info_hash" => torrent.info_hash,
            "module_name" => entry.module_name,
            "rule_name" => entry.rule.to_string(),
            "description" => entry.description.to_string(),
            "flags" => entry.flags,
            "downloader" => entry.downloader,
            "structured_data" => entry.structured_data.map(Value::to_string),
            "peer_geoip" => entry.peer_geoip.map(Value::to_string),
        },
    ).await?;
    Ok(())
}
