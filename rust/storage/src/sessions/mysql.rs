use mysql_async::{IsolationLevel, Params, Row, TxOpts, Value, params, prelude::Queryable};

use super::{
    CLEAR_TRACKS, ConnectionMetrics, ConnectionMetricsBucket, PeerSessionObservation,
    SessionTrackRow, TRACK_COLUMNS, additive_assignments, aggregate_tracks, downloader_filter,
    insert_metrics, select_metrics,
};
use crate::{
    MySqlStore, StorageError, history::java_host_address, mysql::to_microseconds,
    torrent::MYSQL_UPSERT,
};

const TIMEFRAME_MS: &str =
    "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', timeframe_at) / 1000) AS SIGNED)";
const TIMEFRAME_PARAM: &str =
    "TIMESTAMPADD(MICROSECOND, ?, CAST('1970-01-01 00:00:00' AS DATETIME(3)))";
const TRACK_CONFLICT: &str = "ON DUPLICATE KEY UPDATE
    peer_id = VALUES(peer_id), client_name = VALUES(client_name), last_flags = VALUES(last_flags)";

impl MySqlStore {
    pub async fn global_total_connections_count(
        &mut self,
        start_ms: i64,
        end_ms: i64,
    ) -> Result<i64, StorageError> {
        let total = self.connection.exec_first(
            "SELECT CAST(COALESCE(SUM(total_connections), 0) AS SIGNED) FROM peer_connection_metrics
             WHERE timeframe_at BETWEEN TIMESTAMPADD(MICROSECOND, ?, CAST('1970-01-01 00:00:00' AS DATETIME(3)))
             AND TIMESTAMPADD(MICROSECOND, ?, CAST('1970-01-01 00:00:00' AS DATETIME(3)))",
            (to_microseconds(start_ms)?, to_microseconds(end_ms)?),
        ).await?;
        Ok(total.unwrap_or_default())
    }

    pub async fn connection_metrics(
        &mut self,
        start_ms: i64,
        end_ms: i64,
        downloader: Option<&str>,
    ) -> Result<Vec<ConnectionMetricsBucket>, StorageError> {
        let downloader = downloader_filter(downloader);
        let sql = select_metrics(
            TIMEFRAME_MS,
            &format!("timeframe_at BETWEEN {TIMEFRAME_PARAM} AND {TIMEFRAME_PARAM}"),
            downloader.map(|_| "?"),
            |column| format!("CAST(SUM({column}) AS SIGNED)"),
        );
        let start = to_microseconds(start_ms)?;
        let end = to_microseconds(end_ms)?;
        let rows: Vec<Row> = if let Some(downloader) = downloader {
            self.connection.exec(sql, (start, end, downloader)).await?
        } else {
            self.connection.exec(sql, (start, end)).await?
        };
        rows.iter()
            .map(|row| {
                Ok(ConnectionMetricsBucket {
                    key: read_i64(row, 0)?,
                    metrics: ConnectionMetrics::read(|index| read_i64(row, index))?,
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
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        let torrent = transaction.prep(MYSQL_UPSERT).await?;
        let track = transaction
            .prep(format!(
                "INSERT INTO peer_connection_metrics_track ({TRACK_COLUMNS}) VALUES (
             TIMESTAMPADD(MICROSECOND, :time, CAST('1970-01-01 00:00:00' AS DATETIME(3))),
             :downloader, (SELECT id FROM torrents WHERE info_hash = :info_hash), :address, :port,
             :peer_id, :client_name, :last_flags) {TRACK_CONFLICT}"
            ))
            .await?;
        for session in sessions {
            transaction.exec_drop(&torrent, params! {
                "info_hash" => session.torrent.info_hash, "name" => session.torrent.name,
                "size" => session.torrent.size, "private_torrent" => session.torrent.private,
            }).await?;
            transaction.exec_drop(&track, params! {
                "time" => to_microseconds(session.timeframe_at_ms)?, "downloader" => session.downloader,
                "info_hash" => session.torrent.info_hash, "address" => java_host_address(session.address),
                "port" => session.port, "peer_id" => session.peer_id, "client_name" => session.client_name,
                "last_flags" => session.last_flags,
            }).await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn aggregate_peer_sessions(
        &mut self,
        today_start_ms: i64,
    ) -> Result<u64, StorageError> {
        // The locking scan at repeatable read protects existing rows and the
        // gaps between them from writes until the consume completes.
        let mut options = TxOpts::new();
        options.with_isolation_level(IsolationLevel::RepeatableRead);
        let mut transaction = self.connection.start_transaction(options).await?;
        let tracks: Vec<SessionTrackRow> = transaction.query(format!(
            "SELECT {TIMEFRAME_MS}, downloader, last_flags FROM peer_connection_metrics_track FOR UPDATE"
        )).await?;
        if tracks.is_empty() {
            transaction.commit().await?;
            return Ok(0);
        }

        let placeholder = |index| {
            if index == 1 {
                TIMEFRAME_PARAM.to_owned()
            } else {
                "?".to_owned()
            }
        };
        let assignments = additive_assignments(|column| format!("VALUES({column})"));
        let add = transaction
            .prep(insert_metrics(
                placeholder,
                &format!("ON DUPLICATE KEY UPDATE {assignments}"),
            ))
            .await?;
        let keep = transaction
            .prep(insert_metrics(
                placeholder,
                "ON DUPLICATE KEY UPDATE id = id",
            ))
            .await?;
        for ((timestamp, downloader), metrics) in aggregate_tracks(tracks) {
            let mut parameters = vec![
                Value::from(to_microseconds(timestamp)?),
                Value::from(downloader),
            ];
            parameters.extend(metrics.values().into_iter().map(Value::from));
            let statement = if timestamp == today_start_ms {
                &add
            } else {
                &keep
            };
            transaction
                .exec_drop(statement, Params::Positional(parameters))
                .await?;
        }
        transaction.query_drop(CLEAR_TRACKS).await?;
        let consumed = transaction.affected_rows();
        transaction.commit().await?;
        Ok(consumed)
    }

    pub async fn cleanup_connection_metrics(
        &mut self,
        before_ms: i64,
    ) -> Result<u64, StorageError> {
        self.connection
            .exec_drop(
                format!(
                    "DELETE FROM peer_connection_metrics WHERE timeframe_at <= {TIMEFRAME_PARAM}"
                ),
                (to_microseconds(before_ms)?,),
            )
            .await?;
        Ok(self.connection.affected_rows())
    }
}

fn read_i64(row: &Row, index: usize) -> Result<i64, StorageError> {
    row.get_opt::<i64, _>(index)
        .and_then(Result::ok)
        .ok_or_else(|| {
            StorageError::UnsupportedSchema(format!(
                "invalid MySQL connection metrics column {index}"
            ))
        })
}
