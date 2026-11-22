use mysql_async::{IsolationLevel, Row, TxOpts, params, prelude::Queryable};

use super::{
    COLUMNS, COUNT, MAX_REPORT_ROWS, RESET, TrackedSwarmCursor, TrackedSwarmObservation,
    TrackedSwarmPage, TrackedSwarmQuery, TrackedSwarmRecord, decode_record, invalid_record,
    select_fields,
};
use crate::{
    MySqlStore, StorageError, history::java_host_address, history_query::page_window,
    mysql::to_microseconds,
};

const OBSERVED_AT: &str =
    "TIMESTAMPADD(MICROSECOND, :time, CAST('1970-01-01 00:00:00' AS DATETIME(3)))";

// Compute deltas before overwriting their offsets. MySQL and MariaDB apply
// these assignments in order.
const UPDATE_CONFLICT: &str = "ON DUPLICATE KEY UPDATE
    uploaded = uploaded + IF(VALUES(uploaded) < uploaded_offset, VALUES(uploaded), VALUES(uploaded) - uploaded_offset),
    downloaded = downloaded + IF(VALUES(downloaded) < downloaded_offset, VALUES(downloaded), VALUES(downloaded) - downloaded_offset),
    uploaded_offset = VALUES(uploaded_offset), downloaded_offset = VALUES(downloaded_offset),
    peer_id = VALUES(peer_id), client_name = VALUES(client_name), last_flags = VALUES(last_flags),
    last_time_seen = VALUES(last_time_seen),
    download_speed_max = GREATEST(download_speed_max, VALUES(download_speed_max)),
    upload_speed_max = GREATEST(upload_speed_max, VALUES(upload_speed_max))";

impl MySqlStore {
    pub async fn upsert_tracked_swarm(
        &mut self,
        observations: &[TrackedSwarmObservation<'_>],
    ) -> Result<(), StorageError> {
        if observations.is_empty() {
            return Ok(());
        }
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        let insert = transaction
            .prep(format!(
                "INSERT INTO tracked_swarm ({COLUMNS}) VALUES (
                 :ip, :port, :info_hash, :private, :size, :downloader, :downloader_progress,
                 :peer_id, :client_name, :peer_progress, :uploaded, :uploaded, :upload_speed,
                 :downloaded, :downloaded, :download_speed, :flags, {OBSERVED_AT}, {OBSERVED_AT},
                 :download_speed, :upload_speed) {UPDATE_CONFLICT}"
            ))
            .await?;
        for peer in observations {
            transaction
                .exec_drop(
                    &insert,
                    params! {
                        "ip" => java_host_address(peer.ip), "port" => peer.port,
                        "info_hash" => peer.info_hash, "private" => peer.torrent_is_private,
                        "size" => peer.torrent_size, "downloader" => peer.downloader,
                        "downloader_progress" => peer.downloader_progress,
                        "peer_id" => peer.peer_id, "client_name" => peer.client_name,
                        "peer_progress" => peer.peer_progress.to_string(), "uploaded" => peer.uploaded,
                        "upload_speed" => peer.upload_speed, "downloaded" => peer.downloaded,
                        "download_speed" => peer.download_speed, "flags" => peer.last_flags.unwrap_or_default(),
                        "time" => to_microseconds(peer.seen_at_ms)?,
                    },
                )
                .await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn reset_tracked_swarm(&mut self) -> Result<u64, StorageError> {
        self.connection.query_drop(RESET).await?;
        Ok(self.connection.affected_rows())
    }

    pub async fn tracked_swarm_count(&mut self) -> Result<i64, StorageError> {
        Ok(self.connection.query_first(COUNT).await?.unwrap_or(0))
    }

    pub async fn tracked_swarm(
        &mut self,
        query: &TrackedSwarmQuery,
    ) -> Result<TrackedSwarmPage, StorageError> {
        let mut options = TxOpts::new();
        options
            .with_readonly(true)
            .with_isolation_level(IsolationLevel::RepeatableRead);
        let mut transaction = self.connection.start_transaction(options).await?;
        let total = if query.size < 0 {
            0
        } else {
            transaction.query_first(COUNT).await?.unwrap_or(0)
        };
        let window = page_window(query.page, query.size, total)?;
        let records = match window {
            Some((size, offset)) => {
                let rows: Vec<Row> = transaction
                    .exec(
                        format!(
                            "SELECT {} FROM tracked_swarm ORDER BY {} LIMIT ? OFFSET ?",
                            mysql_fields(),
                            query.order_sql(),
                        ),
                        (size, offset),
                    )
                    .await?;
                rows.iter().map(decode).collect::<Result<_, _>>()?
            }
            None => Vec::new(),
        };
        transaction.commit().await?;
        Ok(query.result(window, total, records))
    }

    pub async fn pending_tracked_swarm(
        &mut self,
        after: TrackedSwarmCursor,
        limit: usize,
    ) -> Result<Vec<TrackedSwarmRecord>, StorageError> {
        if limit == 0 {
            return Ok(Vec::new());
        }
        let rows: Vec<Row> = self
            .connection
            .exec(
                format!(
                    "SELECT {} FROM tracked_swarm WHERE last_time_seen >= {OBSERVED_AT} AND id > :id
                     ORDER BY tracked_swarm.last_time_seen, id LIMIT :limit",
                    mysql_fields(),
                ),
                params! {
                    "time" => to_microseconds(after.last_time_seen_ms)?, "id" => after.id,
                    "limit" => limit.min(MAX_REPORT_ROWS) as i64,
                },
            )
            .await?;
        rows.iter().map(decode).collect()
    }
}

fn mysql_fields() -> String {
    select_fields(
        "ip",
        "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', first_time_seen) / 1000) AS SIGNED)",
        "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', last_time_seen) / 1000) AS SIGNED)",
    )
}

fn decode(row: &Row) -> Result<TrackedSwarmRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.get_opt::<$ty, _>($index)
                .and_then(Result::ok)
                .ok_or_else(|| {
                    StorageError::UnsupportedSchema(format!(
                        "invalid MySQL tracked swarm column {}",
                        $index
                    ))
                })?
        };
    }
    decode_record!(get)
}
