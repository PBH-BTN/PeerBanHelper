use rusqlite::{ToSql, TransactionBehavior, params};

use super::{
    CLEAR_TRACKS, CONFLICT_KEEP, ConnectionMetrics, ConnectionMetricsBucket,
    PeerSessionObservation, TRACK_COLUMNS, TRACK_CONFLICT, aggregate_tracks, conflict_add,
    downloader_filter, insert_metrics, select_metrics,
};
use crate::{SqliteStore, StorageError, history::java_host_address, torrent::SQLITE_UPSERT};

impl SqliteStore {
    pub fn global_total_connections_count(
        &self,
        start_ms: i64,
        end_ms: i64,
    ) -> Result<i64, StorageError> {
        Ok(self.connection.query_row(
            "SELECT COALESCE(SUM(total_connections), 0) FROM peer_connection_metrics
             WHERE timeframe_at BETWEEN ? AND ?",
            [start_ms, end_ms],
            |row| row.get(0),
        )?)
    }

    pub fn connection_metrics(
        &self,
        start_ms: i64,
        end_ms: i64,
        downloader: Option<&str>,
    ) -> Result<Vec<ConnectionMetricsBucket>, StorageError> {
        let downloader = downloader_filter(downloader);
        let sql = select_metrics(
            "timeframe_at",
            "timeframe_at BETWEEN ?1 AND ?2",
            downloader.map(|_| "?3"),
            |column| format!("SUM({column})"),
        );
        let mut parameters: Vec<&dyn ToSql> = vec![&start_ms, &end_ms];
        if downloader.is_some() {
            parameters.push(&downloader);
        }
        let mut statement = self.connection.prepare_cached(&sql)?;
        let mut rows = statement.query(parameters.as_slice())?;
        let mut result = Vec::new();
        while let Some(row) = rows.next()? {
            result.push(ConnectionMetricsBucket {
                key: row.get(0)?,
                metrics: ConnectionMetrics::read(|index| Ok(row.get(index)?))?,
            });
        }
        Ok(result)
    }

    pub fn upsert_peer_sessions(
        &mut self,
        sessions: &[PeerSessionObservation<'_>],
    ) -> Result<(), StorageError> {
        if sessions.is_empty() {
            return Ok(());
        }
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        {
            let mut torrent = transaction.prepare_cached(SQLITE_UPSERT)?;
            let mut track = transaction.prepare_cached(&format!(
                "INSERT INTO peer_connection_metrics_track ({TRACK_COLUMNS}) VALUES (
                 ?1, ?2, (SELECT id FROM torrents WHERE info_hash = ?3), ?4, ?5, ?6, ?7, ?8) {TRACK_CONFLICT}"
            ))?;
            for session in sessions {
                torrent.execute(params![
                    session.torrent.info_hash,
                    session.torrent.name,
                    session.torrent.size,
                    session.torrent.private,
                ])?;
                track.execute(params![
                    session.timeframe_at_ms,
                    session.downloader,
                    session.torrent.info_hash,
                    java_host_address(session.address),
                    session.port,
                    session.peer_id,
                    session.client_name,
                    session.last_flags,
                ])?;
            }
        }
        transaction.commit()?;
        Ok(())
    }

    pub fn aggregate_peer_sessions(&mut self, today_start_ms: i64) -> Result<u64, StorageError> {
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        {
            let mut rows = transaction.prepare(
                "SELECT timeframe_at, downloader, last_flags FROM peer_connection_metrics_track",
            )?;
            let tracks = rows
                .query_map([], |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)))?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            if tracks.is_empty() {
                return Ok(0);
            }
            let mut add = transaction.prepare_cached(&insert_metrics(
                |index| format!("?{index}"),
                &conflict_add(),
            ))?;
            let mut keep = transaction
                .prepare_cached(&insert_metrics(|index| format!("?{index}"), CONFLICT_KEEP))?;
            for ((timestamp, downloader), metrics) in aggregate_tracks(tracks) {
                let values = metrics.values();
                let mut parameters: Vec<&dyn ToSql> = vec![&timestamp, &downloader];
                parameters.extend(values.iter().map(|value| value as &dyn ToSql));
                let statement = if timestamp == today_start_ms {
                    &mut add
                } else {
                    &mut keep
                };
                statement.execute(parameters.as_slice())?;
            }
        }
        let consumed = transaction.execute(CLEAR_TRACKS, [])? as u64;
        transaction.commit()?;
        Ok(consumed)
    }

    pub fn cleanup_connection_metrics(&mut self, before_ms: i64) -> Result<u64, StorageError> {
        Ok(self.connection.execute(
            "DELETE FROM peer_connection_metrics WHERE timeframe_at <= ?",
            [before_ms],
        )? as u64)
    }
}
