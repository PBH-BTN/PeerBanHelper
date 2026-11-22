use tokio_postgres::{IsolationLevel, Row};

use super::{
    COLUMNS, COUNT, MAX_REPORT_ROWS, RESET, TrackedSwarmCursor, TrackedSwarmObservation,
    TrackedSwarmPage, TrackedSwarmQuery, TrackedSwarmRecord, UPDATE_CONFLICT, decode_record,
    invalid_record, select_fields,
};
use crate::{PostgresStore, StorageError, history_query::page_window, postgres::to_timestamp};

impl PostgresStore {
    pub async fn upsert_tracked_swarm(
        &mut self,
        observations: &[TrackedSwarmObservation<'_>],
    ) -> Result<(), StorageError> {
        if observations.is_empty() {
            return Ok(());
        }
        let transaction = self.client.transaction().await?;
        let insert = transaction
            .prepare(&format!(
                "INSERT INTO tracked_swarm ({COLUMNS}) VALUES (
                 $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $11, $12, $13, $13, $14, $15,
                 $16, $16, $14, $12) {UPDATE_CONFLICT}"
            ))
            .await?;
        for peer in observations {
            transaction
                .execute(
                    &insert,
                    &[
                        &peer.ip.to_canonical(),
                        &peer.port,
                        &peer.info_hash,
                        &peer.torrent_is_private,
                        &peer.torrent_size,
                        &peer.downloader,
                        &peer.downloader_progress,
                        &peer.peer_id,
                        &peer.client_name,
                        &peer.peer_progress.to_string(),
                        &peer.uploaded,
                        &peer.upload_speed,
                        &peer.downloaded,
                        &peer.download_speed,
                        &peer.last_flags.unwrap_or_default(),
                        &to_timestamp(peer.seen_at_ms)?,
                    ],
                )
                .await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn reset_tracked_swarm(&mut self) -> Result<u64, StorageError> {
        Ok(self.client.execute(RESET, &[]).await?)
    }

    pub async fn tracked_swarm_count(&self) -> Result<i64, StorageError> {
        Ok(self.client.query_one(COUNT, &[]).await?.try_get(0)?)
    }

    pub async fn tracked_swarm(
        &mut self,
        query: &TrackedSwarmQuery,
    ) -> Result<TrackedSwarmPage, StorageError> {
        let transaction = self
            .client
            .build_transaction()
            .read_only(true)
            .isolation_level(IsolationLevel::RepeatableRead)
            .start()
            .await?;
        let total = if query.size < 0 {
            0
        } else {
            transaction
                .query_one(COUNT, &[])
                .await?
                .try_get::<_, i64>(0)? as u64
        };
        let window = page_window(query.page, query.size, total)?;
        let records = match window {
            Some((size, offset)) => transaction
                .query(
                    &format!(
                        "SELECT {} FROM tracked_swarm ORDER BY {} LIMIT $1 OFFSET $2",
                        postgres_fields(),
                        query.order_sql(),
                    ),
                    &[&size, &offset],
                )
                .await?
                .iter()
                .map(decode)
                .collect::<Result<_, _>>()?,
            None => Vec::new(),
        };
        transaction.commit().await?;
        Ok(query.result(window, total, records))
    }

    pub async fn pending_tracked_swarm(
        &self,
        after: TrackedSwarmCursor,
        limit: usize,
    ) -> Result<Vec<TrackedSwarmRecord>, StorageError> {
        if limit == 0 {
            return Ok(Vec::new());
        }
        self.client
            .query(
                &format!(
                    "SELECT {} FROM tracked_swarm WHERE last_time_seen >= $1 AND id > $2
                     ORDER BY tracked_swarm.last_time_seen, id LIMIT $3",
                    postgres_fields(),
                ),
                &[
                    &to_timestamp(after.last_time_seen_ms)?,
                    &after.id,
                    &(limit.min(MAX_REPORT_ROWS) as i64),
                ],
            )
            .await?
            .iter()
            .map(decode)
            .collect()
    }
}

fn postgres_fields() -> String {
    select_fields(
        "host(ip)",
        "FLOOR(EXTRACT(EPOCH FROM first_time_seen) * 1000)::bigint",
        "FLOOR(EXTRACT(EPOCH FROM last_time_seen) * 1000)::bigint",
    )
}

fn decode(row: &Row) -> Result<TrackedSwarmRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.try_get::<_, $ty>($index)?
        };
    }
    decode_record!(get)
}
