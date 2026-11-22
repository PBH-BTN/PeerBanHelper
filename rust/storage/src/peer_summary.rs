//! Read-only overview of a peer's persisted ban and access history.

use std::net::IpAddr;

use mysql_async::prelude::Queryable;

use crate::{MySqlStore, PostgresStore, SqliteStore, StorageError, java_host_address};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PeerHistorySummary {
    pub ban_count: i64,
    pub torrent_access_count: i64,
    pub uploaded_to_peer: i64,
    pub downloaded_from_peer: i64,
    pub first_time_seen_ms: i64,
    pub last_time_seen_ms: i64,
}

impl PeerHistorySummary {
    pub fn found(self) -> bool {
        self.ban_count > 0 || self.torrent_access_count > 0
    }
}

type SummaryRow = (i64, i64, i64, i64, i64, i64);

impl From<SummaryRow> for PeerHistorySummary {
    fn from(row: SummaryRow) -> Self {
        let (
            ban_count,
            torrent_access_count,
            uploaded_to_peer,
            downloaded_from_peer,
            first_time_seen_ms,
            last_time_seen_ms,
        ) = row;
        Self {
            ban_count,
            torrent_access_count,
            uploaded_to_peer,
            downloaded_from_peer,
            first_time_seen_ms,
            last_time_seen_ms,
        }
    }
}

impl SqliteStore {
    pub fn peer_history_summary(&self, ip: IpAddr) -> Result<PeerHistorySummary, StorageError> {
        Ok(self.connection.query_row(
            "SELECT (SELECT COUNT(*) FROM history WHERE ip = ?1), COUNT(*),
                    COALESCE(SUM(uploaded), -1), COALESCE(SUM(downloaded), -1),
                    COALESCE(MIN(first_time_seen), 0), COALESCE(MAX(last_time_seen), 0)
             FROM peer_records WHERE address = ?1",
            [java_host_address(ip)],
            |row| {
                Ok(PeerHistorySummary::from((
                    row.get(0)?,
                    row.get(1)?,
                    row.get(2)?,
                    row.get(3)?,
                    row.get(4)?,
                    row.get(5)?,
                )))
            },
        )?)
    }
}

impl PostgresStore {
    pub async fn peer_history_summary(
        &self,
        ip: IpAddr,
    ) -> Result<PeerHistorySummary, StorageError> {
        let row = self
            .client
            .query_one(
                "SELECT (SELECT COUNT(*) FROM history WHERE ip = $1), COUNT(*),
                    COALESCE(SUM(uploaded), -1)::bigint, COALESCE(SUM(downloaded), -1)::bigint,
                    COALESCE(FLOOR(EXTRACT(EPOCH FROM MIN(first_time_seen)) * 1000)::bigint, 0),
                    COALESCE(FLOOR(EXTRACT(EPOCH FROM MAX(last_time_seen)) * 1000)::bigint, 0)
             FROM peer_records WHERE address = $1",
                &[&ip.to_canonical()],
            )
            .await?;
        Ok((
            row.try_get(0)?,
            row.try_get(1)?,
            row.try_get(2)?,
            row.try_get(3)?,
            row.try_get(4)?,
            row.try_get(5)?,
        )
            .into())
    }
}

impl MySqlStore {
    pub async fn peer_history_summary(
        &mut self,
        ip: IpAddr,
    ) -> Result<PeerHistorySummary, StorageError> {
        let ip = java_host_address(ip);
        let row: Option<SummaryRow> = self.connection.exec_first(
            "SELECT (SELECT COUNT(*) FROM history WHERE ip = ?), COUNT(*),
                    CAST(COALESCE(SUM(uploaded), -1) AS SIGNED), CAST(COALESCE(SUM(downloaded), -1) AS SIGNED),
                    CAST(COALESCE(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', MIN(first_time_seen)) / 1000), 0) AS SIGNED),
                    CAST(COALESCE(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', MAX(last_time_seen)) / 1000), 0) AS SIGNED)
             FROM peer_records WHERE address = ?",
            (&ip, &ip),
        ).await?;
        let row = row
            .ok_or_else(|| StorageError::UnsupportedSchema("missing MySQL peer summary".into()))?;
        Ok(row.into())
    }
}
