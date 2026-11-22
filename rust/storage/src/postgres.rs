//! PostgreSQL persistence for current bans and ban history.

mod schema;

use std::collections::BTreeSet;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use native_tls::TlsConnector;
use postgres_native_tls::MakeTlsConnector;
use serde_json::Value;
use tokio_postgres::{Client, Config, Transaction};

use crate::{
    BanHistoryEntry, BanHistoryPage, BanHistoryQuery, BanRecord, StorageError, decode_ban,
    history_query, validate_ban_metadata,
};

pub struct PostgresStore {
    pub(crate) client: Client,
}

impl PostgresStore {
    /// Authenticate without preparing the schema or writing any tables.
    pub async fn test_connection(config: Config) -> Result<(), StorageError> {
        let (client, connection) = config
            .connect(MakeTlsConnector::new(TlsConnector::new()?))
            .await?;
        let task = tokio::spawn(connection);
        let result = client.simple_query("SELECT 1").await;
        drop(client);
        let _ = task.await;
        result?;
        Ok(())
    }

    /// Open the database using the active PostgreSQL schema. TLS connections
    /// use the platform trust store unless the caller specifies a connector.
    pub async fn open(config: Config) -> Result<Self, StorageError> {
        Self::open_with_tls(config, TlsConnector::new()?).await
    }

    pub async fn open_with_tls(config: Config, tls: TlsConnector) -> Result<Self, StorageError> {
        let (mut client, connection) = config.connect(MakeTlsConnector::new(tls)).await?;
        tokio::spawn(connection);
        schema::prepare(&mut client).await?;
        Ok(Self { client })
    }

    pub async fn load_bans(&self) -> Result<Vec<BanRecord>, StorageError> {
        let rows = self
            .client
            .query(
                "SELECT address, metadata FROM banlist ORDER BY address",
                &[],
            )
            .await?;
        let mut addresses = BTreeSet::new();
        let mut bans = Vec::with_capacity(rows.len());
        for row in rows {
            let record = decode_ban(row.try_get(0)?, row.try_get(1)?)?;
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
        history_query::read_postgres(&mut self.client, query).await
    }

    pub async fn replace_bans(&mut self, bans: &[BanRecord]) -> Result<(), StorageError> {
        self.commit_ban_wave(Some(bans), &[]).await
    }

    pub async fn commit_ban_wave(
        &mut self,
        bans: Option<&[BanRecord]>,
        history: &[BanHistoryEntry<'_>],
    ) -> Result<(), StorageError> {
        let transaction = self.client.transaction().await?;
        if let Some(bans) = bans {
            transaction
                .batch_execute(
                    "LOCK TABLE banlist IN SHARE ROW EXCLUSIVE MODE; DELETE FROM banlist;",
                )
                .await?;
            let insert = transaction
                .prepare("INSERT INTO banlist (address, metadata) VALUES ($1, $2)")
                .await?;
            for ban in bans {
                let address = ban.address.to_canonical();
                validate_ban_metadata(address, &ban.metadata)?;
                transaction
                    .execute(&insert, &[&address.to_string(), &ban.metadata.to_string()])
                    .await?;
            }
        }
        for entry in history {
            insert_history(&transaction, entry).await?;
        }
        transaction.commit().await?;
        Ok(())
    }
}

async fn insert_history(
    transaction: &Transaction<'_>,
    entry: &BanHistoryEntry<'_>,
) -> Result<(), StorageError> {
    let ban_at = to_timestamp(entry.ban_at_ms)?;
    let unban_at = to_timestamp(entry.unban_at_ms)?;
    let torrent = entry.torrent;
    transaction
        .execute(
            crate::torrent::POSTGRES_UPSERT,
            &[
                &torrent.info_hash,
                &torrent.name,
                &torrent.size,
                &torrent.private,
            ],
        )
        .await?;
    transaction
        .execute(
            "INSERT INTO history (
                 ban_at, unban_at, ip, port, peer_id, peer_client_name, peer_uploaded,
                 peer_downloaded, peer_progress, downloader_progress, torrent_id,
                 module_name, rule_name, description, flags, downloader,
                 structured_data, peer_geoip
             ) VALUES (
                 $1, $2, $3, $4, $5, $6, $7, $8, $9, $10,
                 (SELECT id FROM torrents WHERE info_hash = $11), $12, $13, $14,
                 $15, $16, $17, $18
             )",
            &[
                &ban_at,
                &unban_at,
                &entry.address.to_canonical(),
                &i32::from(entry.port),
                &entry.peer_id,
                &entry.peer_client_name,
                &entry.peer_uploaded,
                &entry.peer_downloaded,
                &entry.peer_progress,
                &torrent.progress,
                &torrent.info_hash,
                &entry.module_name,
                &entry.rule.to_string(),
                &entry.description.to_string(),
                &entry.flags,
                &entry.downloader,
                &entry.structured_data.map(Value::to_string),
                &entry.peer_geoip.map(Value::to_string),
            ],
        )
        .await?;
    Ok(())
}

pub(crate) fn to_timestamp(milliseconds: i64) -> Result<SystemTime, StorageError> {
    let elapsed = Duration::from_millis(milliseconds.unsigned_abs());
    let result = if milliseconds < 0 {
        UNIX_EPOCH.checked_sub(elapsed)
    } else {
        UNIX_EPOCH.checked_add(elapsed)
    };
    result.ok_or(StorageError::InvalidTimestamp(milliseconds))
}
