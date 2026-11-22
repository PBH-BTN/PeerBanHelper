use rusqlite::{
    Connection, Params, Row, TransactionBehavior, params, params_from_iter, types::Value,
};

use super::{
    MAX_REPORT_ROWS, PeerRecord, PeerRecordCursor, PeerRecordFilter, PeerRecordObservation,
    PeerRecordPage, PeerRecordQuery, RECORD_FROM, UPDATE_CONFLICT, UPSERT_COLUMNS, compact_peer_id,
    decode_record, invalid_row, select_fields,
};
use crate::{
    BanHistoryTorrentRecord, SqliteStore, StorageError, history::java_host_address,
    history_query::page_window, torrent::SQLITE_UPSERT,
};

impl SqliteStore {
    pub fn upsert_peer_records(
        &mut self,
        observations: &[PeerRecordObservation<'_>],
    ) -> Result<(), StorageError> {
        if observations.is_empty() {
            return Ok(());
        }
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        {
            let mut torrent_upsert = transaction.prepare_cached(SQLITE_UPSERT)?;
            let mut peer_upsert = transaction.prepare_cached(&format!(
                "INSERT INTO peer_records ({UPSERT_COLUMNS}) VALUES (
                    ?1, ?2, (SELECT id FROM torrents WHERE info_hash = ?3), ?4, ?5, ?6,
                    ?7, ?7, ?8, ?9, ?9, ?10, ?11, ?12, ?12, ?13) {UPDATE_CONFLICT}"
            ))?;
            for peer in observations {
                let torrent = peer.torrent;
                torrent_upsert.execute(params![
                    torrent.info_hash,
                    torrent.name,
                    torrent.size,
                    torrent.private
                ])?;
                peer_upsert.execute(params![
                    java_host_address(peer.address),
                    peer.port,
                    torrent.info_hash,
                    peer.downloader_id,
                    compact_peer_id(peer.peer_id),
                    peer.client_name,
                    peer.uploaded,
                    peer.upload_speed,
                    peer.downloaded,
                    peer.download_speed,
                    peer.last_flags,
                    peer.observed_at_ms,
                    peer.peer_geoip.map(serde_json::Value::to_string),
                ])?;
            }
        }
        transaction.commit()?;
        Ok(())
    }

    pub fn peer_history(&self, query: &PeerRecordQuery) -> Result<PeerRecordPage, StorageError> {
        let (column, value): (_, Value) = match query.filter {
            PeerRecordFilter::Address(address) => ("p.address", java_host_address(address).into()),
            PeerRecordFilter::TorrentId(id) => ("p.torrent_id", id.into()),
        };
        let transaction = self.connection.unchecked_transaction()?;
        let total = if query.size < 0 {
            0
        } else {
            transaction.query_row(
                &format!("SELECT COUNT(*) FROM peer_records p WHERE {column} = ?1"),
                [&value],
                |row| row.get::<_, i64>(0),
            )? as u64
        };
        let window = page_window(query.page, query.size, total)?;
        let records = match window {
            Some((size, offset)) => sqlite_rows(
                &transaction,
                &format!(
                    "SELECT {} {RECORD_FROM} WHERE {column} = ?1 ORDER BY {} LIMIT ?2 OFFSET ?3",
                    sqlite_fields(),
                    query.order_sql(),
                ),
                params_from_iter([value, size.into(), offset.into()]),
            )?,
            None => Vec::new(),
        };
        transaction.commit()?;
        Ok(query.result(window, total, records))
    }

    pub fn pending_peer_records(
        &self,
        after: PeerRecordCursor,
        limit: u32,
    ) -> Result<Vec<PeerRecord>, StorageError> {
        sqlite_rows(
            &self.connection,
            &format!(
                "SELECT {} {RECORD_FROM}
             WHERE p.last_time_seen > ?1 OR (p.last_time_seen = ?1 AND p.id > ?2)
             ORDER BY p.last_time_seen, p.id LIMIT ?3",
                sqlite_fields(),
            ),
            params![
                after.last_time_seen_ms,
                after.id,
                i64::from(limit.min(MAX_REPORT_ROWS))
            ],
        )
    }

    pub fn cleanup_peer_records(&mut self, cutoff_ms: i64) -> Result<u64, StorageError> {
        Ok(self.connection.execute(
            "DELETE FROM peer_records WHERE last_time_seen < ?",
            [cutoff_ms],
        )? as u64)
    }
}

fn sqlite_fields() -> String {
    select_fields("p.address", "p.first_time_seen", "p.last_time_seen")
}

fn sqlite_rows(
    connection: &Connection,
    sql: &str,
    parameters: impl Params,
) -> Result<Vec<PeerRecord>, StorageError> {
    let mut statement = connection.prepare_cached(sql)?;
    let mut rows = statement.query(parameters)?;
    let mut records = Vec::new();
    while let Some(row) = rows.next()? {
        records.push(decode(row)?);
    }
    Ok(records)
}

fn decode(row: &Row<'_>) -> Result<PeerRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.get::<_, $ty>($index)?
        };
    }
    decode_record!(get)
}
