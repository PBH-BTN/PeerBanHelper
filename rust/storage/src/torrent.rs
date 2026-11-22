//! Torrent upserts shared by the ban and access histories.

use mysql_async::prelude::Queryable;
use rusqlite::OptionalExtension;

use crate::{BanHistoryTorrentRecord, MySqlStore, PostgresStore, SqliteStore, StorageError};

pub(crate) const SQLITE_UPSERT: &str =
    "INSERT INTO torrents (info_hash, name, size, private_torrent)
    VALUES (?1, ?2, ?3, ?4)
    ON CONFLICT (info_hash) DO UPDATE SET
        size = CASE WHEN torrents.size <= 0 THEN excluded.size ELSE torrents.size END,
        private_torrent = COALESCE(torrents.private_torrent, excluded.private_torrent)
    WHERE (torrents.size <= 0 OR torrents.private_torrent IS NULL)
      AND (excluded.size > 0 OR excluded.private_torrent IS NOT NULL)";

pub(crate) const POSTGRES_UPSERT: &str =
    "INSERT INTO torrents (info_hash, name, size, private_torrent)
    VALUES ($1, $2, $3, $4)
    ON CONFLICT (info_hash) DO UPDATE SET
        size = CASE WHEN torrents.size <= 0 THEN excluded.size ELSE torrents.size END,
        private_torrent = COALESCE(torrents.private_torrent, excluded.private_torrent)
    WHERE (torrents.size <= 0 OR torrents.private_torrent IS NULL)
      AND (excluded.size > 0 OR excluded.private_torrent IS NOT NULL)";

pub(crate) const MYSQL_UPSERT: &str = "INSERT INTO torrents (info_hash, name, size, private_torrent)
    VALUES (:info_hash, :name, :size, :private_torrent)
    ON DUPLICATE KEY UPDATE
        size = IF(torrents.size <= 0 AND (:size > 0 OR :private_torrent IS NOT NULL), :size, torrents.size),
        private_torrent = COALESCE(torrents.private_torrent, :private_torrent)";

pub(super) const SUMMARY_COLUMNS: &str =
    "t.id, t.info_hash, t.name, COALESCE(t.size, 0), t.private_torrent,
    (SELECT COUNT(*) FROM history h WHERE h.torrent_id = t.id) AS peer_ban_count,
    (SELECT COUNT(*) FROM peer_records p WHERE p.torrent_id = t.id) AS peer_access_count";

pub(super) type SummaryRow = (i64, String, String, i64, Option<bool>, i64, i64);

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TorrentHistorySummary {
    pub torrent: BanHistoryTorrentRecord,
    pub ban_count: i64,
    pub access_count: i64,
}

impl From<SummaryRow> for TorrentHistorySummary {
    fn from(row: SummaryRow) -> Self {
        let (id, info_hash, name, size, private, ban_count, access_count) = row;
        Self {
            torrent: BanHistoryTorrentRecord {
                id,
                info_hash,
                name,
                size,
                private,
            },
            ban_count,
            access_count,
        }
    }
}

impl TorrentHistorySummary {
    pub(super) fn read_sqlite(row: &rusqlite::Row<'_>) -> rusqlite::Result<Self> {
        Ok(Self::from((
            row.get(0)?,
            row.get(1)?,
            row.get(2)?,
            row.get(3)?,
            row.get(4)?,
            row.get(5)?,
            row.get(6)?,
        )))
    }

    pub(super) fn read_postgres(row: &tokio_postgres::Row) -> Result<Self, StorageError> {
        Ok(Self::from((
            row.try_get(0)?,
            row.try_get(1)?,
            row.try_get(2)?,
            row.try_get(3)?,
            row.try_get(4)?,
            row.try_get(5)?,
            row.try_get(6)?,
        )))
    }
}

impl SqliteStore {
    pub fn torrent_history_summary(
        &self,
        info_hash: &str,
    ) -> Result<Option<TorrentHistorySummary>, StorageError> {
        Ok(self
            .connection
            .query_row(
                &format!("SELECT {SUMMARY_COLUMNS} FROM torrents t WHERE t.info_hash = ?"),
                [info_hash],
                TorrentHistorySummary::read_sqlite,
            )
            .optional()?)
    }
}

impl PostgresStore {
    pub async fn torrent_history_summary(
        &self,
        info_hash: &str,
    ) -> Result<Option<TorrentHistorySummary>, StorageError> {
        self.client
            .query_opt(
                &format!("SELECT {SUMMARY_COLUMNS} FROM torrents t WHERE t.info_hash = $1"),
                &[&info_hash],
            )
            .await?
            .as_ref()
            .map(TorrentHistorySummary::read_postgres)
            .transpose()
    }
}

impl MySqlStore {
    pub async fn torrent_history_summary(
        &mut self,
        info_hash: &str,
    ) -> Result<Option<TorrentHistorySummary>, StorageError> {
        let row: Option<SummaryRow> = self
            .connection
            .exec_first(
                format!("SELECT {SUMMARY_COLUMNS} FROM torrents t WHERE t.info_hash = ?"),
                (info_hash,),
            )
            .await?;
        Ok(row.map(Into::into))
    }
}
