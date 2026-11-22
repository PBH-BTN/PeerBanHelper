use tokio_postgres::{IsolationLevel, types::ToSql};

use super::{
    CLEAR_TRACKS, CONFLICT_KEEP, ConnectionMetrics, ConnectionMetricsBucket,
    PeerSessionObservation, SessionTrackRow, TRACK_COLUMNS, TRACK_CONFLICT, aggregate_tracks,
    conflict_add, downloader_filter, insert_metrics, select_metrics,
};
use crate::{PostgresStore, StorageError, postgres::to_timestamp, torrent::POSTGRES_UPSERT};

const TIMEFRAME_MS: &str = "FLOOR(EXTRACT(EPOCH FROM timeframe_at) * 1000)::bigint";

impl PostgresStore {
    pub async fn global_total_connections_count(
        &self,
        start_ms: i64,
        end_ms: i64,
    ) -> Result<i64, StorageError> {
        Ok(self
            .client
            .query_one(
                "SELECT COALESCE(SUM(total_connections), 0)::bigint FROM peer_connection_metrics
             WHERE timeframe_at BETWEEN $1 AND $2",
                &[&to_timestamp(start_ms)?, &to_timestamp(end_ms)?],
            )
            .await?
            .try_get(0)?)
    }

    pub async fn connection_metrics(
        &self,
        start_ms: i64,
        end_ms: i64,
        downloader: Option<&str>,
    ) -> Result<Vec<ConnectionMetricsBucket>, StorageError> {
        let downloader = downloader_filter(downloader);
        let sql = select_metrics(
            TIMEFRAME_MS,
            "timeframe_at BETWEEN $1 AND $2",
            downloader.map(|_| "$3"),
            |column| format!("SUM({column})::bigint"),
        );
        let start = to_timestamp(start_ms)?;
        let end = to_timestamp(end_ms)?;
        let mut parameters: Vec<&(dyn ToSql + Sync)> = vec![&start, &end];
        if downloader.is_some() {
            parameters.push(&downloader);
        }
        self.client
            .query(&sql, &parameters)
            .await?
            .iter()
            .map(|row| {
                Ok(ConnectionMetricsBucket {
                    key: row.try_get(0)?,
                    metrics: ConnectionMetrics::read(|index| Ok(row.try_get(index)?))?,
                })
            })
            .collect()
    }

    pub async fn upsert_peer_sessions(
        &mut self,
        sessions: &[PeerSessionObservation<'_>],
    ) -> Result<(), StorageError> {
        if sessions.is_empty() {
            return Ok(());
        }
        let transaction = self.client.transaction().await?;
        let torrent = transaction.prepare(POSTGRES_UPSERT).await?;
        let track = transaction.prepare(&format!(
            "INSERT INTO peer_connection_metrics_track ({TRACK_COLUMNS}) VALUES (
             $1, $2, (SELECT id FROM torrents WHERE info_hash = $3), $4, $5, $6, $7, $8) {TRACK_CONFLICT}"
        )).await?;
        for session in sessions {
            transaction
                .execute(
                    &torrent,
                    &[
                        &session.torrent.info_hash,
                        &session.torrent.name,
                        &session.torrent.size,
                        &session.torrent.private,
                    ],
                )
                .await?;
            transaction
                .execute(
                    &track,
                    &[
                        &to_timestamp(session.timeframe_at_ms)?,
                        &session.downloader,
                        &session.torrent.info_hash,
                        &session.address.to_canonical(),
                        &i32::from(session.port),
                        &session.peer_id,
                        &session.client_name,
                        &session.last_flags,
                    ],
                )
                .await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn aggregate_peer_sessions(
        &mut self,
        today_start_ms: i64,
    ) -> Result<u64, StorageError> {
        let transaction = self
            .client
            .build_transaction()
            .isolation_level(IsolationLevel::ReadCommitted)
            .start()
            .await?;
        transaction
            .batch_execute("LOCK TABLE peer_connection_metrics_track IN SHARE ROW EXCLUSIVE MODE")
            .await?;
        let tracks = transaction.query(
            &format!("SELECT {TIMEFRAME_MS}, downloader, last_flags FROM peer_connection_metrics_track"),
            &[],
        ).await?.iter().map(|row| -> Result<SessionTrackRow, StorageError> {
            Ok((row.try_get(0)?, row.try_get(1)?, row.try_get(2)?))
        }).collect::<Result<Vec<_>, _>>()?;
        if tracks.is_empty() {
            transaction.commit().await?;
            return Ok(0);
        }
        let add = transaction
            .prepare(&insert_metrics(
                |index| format!("${index}"),
                &conflict_add(),
            ))
            .await?;
        let keep = transaction
            .prepare(&insert_metrics(|index| format!("${index}"), CONFLICT_KEEP))
            .await?;
        for ((timestamp, downloader), metrics) in aggregate_tracks(tracks) {
            let timeframe = to_timestamp(timestamp)?;
            let values = metrics.values();
            let mut parameters: Vec<&(dyn ToSql + Sync)> = vec![&timeframe, &downloader];
            parameters.extend(values.iter().map(|value| value as &(dyn ToSql + Sync)));
            let statement = if timestamp == today_start_ms {
                &add
            } else {
                &keep
            };
            transaction.execute(statement, &parameters).await?;
        }
        let consumed = transaction.execute(CLEAR_TRACKS, &[]).await?;
        transaction.commit().await?;
        Ok(consumed)
    }

    pub async fn cleanup_connection_metrics(
        &mut self,
        before_ms: i64,
    ) -> Result<u64, StorageError> {
        Ok(self
            .client
            .execute(
                "DELETE FROM peer_connection_metrics WHERE timeframe_at <= $1",
                &[&to_timestamp(before_ms)?],
            )
            .await?)
    }
}
