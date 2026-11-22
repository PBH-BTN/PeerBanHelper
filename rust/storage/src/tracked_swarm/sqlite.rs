use rusqlite::{Connection, Params, Row, TransactionBehavior, params};

use super::{
    COLUMNS, COUNT, MAX_REPORT_ROWS, RESET, TrackedSwarmCursor, TrackedSwarmObservation,
    TrackedSwarmPage, TrackedSwarmQuery, TrackedSwarmRecord, UPDATE_CONFLICT, decode_record,
    invalid_record,
};
use crate::{SqliteStore, StorageError, history::java_host_address, history_query::page_window};

impl SqliteStore {
    pub fn upsert_tracked_swarm(
        &mut self,
        observations: &[TrackedSwarmObservation<'_>],
    ) -> Result<(), StorageError> {
        if observations.is_empty() {
            return Ok(());
        }
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        {
            let mut insert = transaction.prepare_cached(&format!(
                "INSERT INTO tracked_swarm ({COLUMNS}) VALUES (
                 ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?11, ?12, ?13, ?13, ?14, ?15,
                 ?16, ?16, ?14, ?12) {UPDATE_CONFLICT}"
            ))?;
            for peer in observations {
                insert.execute(params![
                    java_host_address(peer.ip),
                    peer.port,
                    peer.info_hash,
                    peer.torrent_is_private,
                    peer.torrent_size,
                    peer.downloader,
                    peer.downloader_progress,
                    peer.peer_id,
                    peer.client_name,
                    peer.peer_progress.to_string(),
                    peer.uploaded,
                    peer.upload_speed,
                    peer.downloaded,
                    peer.download_speed,
                    peer.last_flags.unwrap_or_default(),
                    peer.seen_at_ms,
                ])?;
            }
        }
        transaction.commit()?;
        Ok(())
    }

    /// Reset the session rows while keeping the sequence and BTN cursor.
    pub fn reset_tracked_swarm(&mut self) -> Result<u64, StorageError> {
        Ok(self.connection.execute(RESET, [])? as u64)
    }

    pub fn tracked_swarm_count(&self) -> Result<i64, StorageError> {
        Ok(self.connection.query_row(COUNT, [], |row| row.get(0))?)
    }

    pub fn tracked_swarm(
        &self,
        query: &TrackedSwarmQuery,
    ) -> Result<TrackedSwarmPage, StorageError> {
        let transaction = self.connection.unchecked_transaction()?;
        let total = if query.size < 0 {
            0
        } else {
            transaction.query_row(COUNT, [], |row| row.get::<_, i64>(0))? as u64
        };
        let window = page_window(query.page, query.size, total)?;
        let records = match window {
            Some((size, offset)) => read_rows(
                &transaction,
                &format!(
                    "SELECT id, {COLUMNS} FROM tracked_swarm ORDER BY {} LIMIT ? OFFSET ?",
                    query.order_sql(),
                ),
                params![size, offset],
            )?,
            None => Vec::new(),
        };
        transaction.commit()?;
        Ok(query.result(window, total, records))
    }

    pub fn pending_tracked_swarm(
        &self,
        after: TrackedSwarmCursor,
        limit: usize,
    ) -> Result<Vec<TrackedSwarmRecord>, StorageError> {
        // Java's BTN report advances both watermarks, even across session resets.
        read_rows(
            &self.connection,
            &format!(
                "SELECT id, {COLUMNS} FROM tracked_swarm WHERE last_time_seen >= ? AND id > ?
                 ORDER BY last_time_seen, id LIMIT ?"
            ),
            params![
                after.last_time_seen_ms,
                after.id,
                limit.min(MAX_REPORT_ROWS) as i64
            ],
        )
    }
}

fn read_rows(
    connection: &Connection,
    sql: &str,
    parameters: impl Params,
) -> Result<Vec<TrackedSwarmRecord>, StorageError> {
    let mut statement = connection.prepare_cached(sql)?;
    let mut rows = statement.query(parameters)?;
    let mut records = Vec::new();
    while let Some(row) = rows.next()? {
        records.push(decode(row)?);
    }
    Ok(records)
}

fn decode(row: &Row<'_>) -> Result<TrackedSwarmRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.get::<_, $ty>($index)?
        };
    }
    decode_record!(get)
}
