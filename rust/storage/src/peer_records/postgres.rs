use tokio_postgres::{IsolationLevel, Row, types::ToSql};

use super::{
    MAX_REPORT_ROWS, PeerRecord, PeerRecordCursor, PeerRecordFilter, PeerRecordObservation,
    PeerRecordPage, PeerRecordQuery, RECORD_FROM, UPDATE_CONFLICT, UPSERT_COLUMNS, compact_peer_id,
    decode_record, invalid_row, select_fields,
};
use crate::{
    BanHistoryTorrentRecord, PostgresStore, StorageError, history_query::page_window,
    postgres::to_timestamp, torrent::POSTGRES_UPSERT,
};

const LAST_SEEN: &str = "FLOOR(EXTRACT(EPOCH FROM p.last_time_seen) * 1000)::bigint";

impl PostgresStore {
    pub async fn upsert_peer_records(
        &mut self,
        observations: &[PeerRecordObservation<'_>],
    ) -> Result<(), StorageError> {
        if observations.is_empty() {
            return Ok(());
        }
        let transaction = self.client.transaction().await?;
        let torrent_upsert = transaction.prepare(POSTGRES_UPSERT).await?;
        let peer_upsert = transaction
            .prepare(&format!(
                "INSERT INTO peer_records ({UPSERT_COLUMNS}) VALUES (
                 $1, $2, (SELECT id FROM torrents WHERE info_hash = $3), $4, $5, $6,
                 $7, $7, $8, $9, $9, $10, $11, $12, $12, $13) {UPDATE_CONFLICT}"
            ))
            .await?;
        for peer in observations {
            let torrent = peer.torrent;
            transaction
                .execute(
                    &torrent_upsert,
                    &[
                        &torrent.info_hash,
                        &torrent.name,
                        &torrent.size,
                        &torrent.private,
                    ],
                )
                .await?;
            let time = to_timestamp(peer.observed_at_ms)?;
            transaction
                .execute(
                    &peer_upsert,
                    &[
                        &peer.address.to_canonical(),
                        &i32::from(peer.port),
                        &torrent.info_hash,
                        &peer.downloader_id,
                        &compact_peer_id(peer.peer_id),
                        &peer.client_name,
                        &peer.uploaded,
                        &peer.upload_speed,
                        &peer.downloaded,
                        &peer.download_speed,
                        &peer.last_flags,
                        &time,
                        &peer.peer_geoip.map(serde_json::Value::to_string),
                    ],
                )
                .await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn peer_history(
        &mut self,
        query: &PeerRecordQuery,
    ) -> Result<PeerRecordPage, StorageError> {
        let filter = match query.filter {
            PeerRecordFilter::Address(address) => PeerRecordFilter::Address(address.to_canonical()),
            filter => filter,
        };
        let (column, value): (_, &(dyn ToSql + Sync)) = match &filter {
            PeerRecordFilter::Address(address) => ("p.address", address),
            PeerRecordFilter::TorrentId(id) => ("p.torrent_id", id),
        };
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
                .query_one(
                    &format!("SELECT COUNT(*) FROM peer_records p WHERE {column} = $1"),
                    &[value],
                )
                .await?
                .try_get::<_, i64>(0)? as u64
        };
        let window = page_window(query.page, query.size, total)?;
        let records = match window {
            Some((size, offset)) => transaction.query(&format!(
                "SELECT {} {RECORD_FROM} WHERE {column} = $1 ORDER BY {} LIMIT $2 OFFSET $3",
                postgres_fields(), query.order_sql(),
            ), &[value, &size, &offset]).await?.iter().map(decode).collect::<Result<_, _>>()?,
            None => Vec::new(),
        };
        transaction.commit().await?;
        Ok(query.result(window, total, records))
    }

    pub async fn pending_peer_records(
        &mut self,
        after: PeerRecordCursor,
        limit: u32,
    ) -> Result<Vec<PeerRecord>, StorageError> {
        if limit == 0 {
            return Ok(Vec::new());
        }
        // The raw lower bound can use the timestamp index. The cursor and
        // ordering use whole milliseconds, including legacy microsecond rows.
        self.client
            .query(
                &format!(
                    "SELECT {} {RECORD_FROM} WHERE p.last_time_seen >= $1
              AND ({LAST_SEEN} > $2 OR ({LAST_SEEN} = $2 AND p.id > $3))
              ORDER BY {LAST_SEEN}, p.id LIMIT $4",
                    postgres_fields(),
                ),
                &[
                    &to_timestamp(after.last_time_seen_ms)?,
                    &after.last_time_seen_ms,
                    &after.id,
                    &i64::from(limit.min(MAX_REPORT_ROWS)),
                ],
            )
            .await?
            .iter()
            .map(decode)
            .collect()
    }

    pub async fn cleanup_peer_records(&mut self, cutoff_ms: i64) -> Result<u64, StorageError> {
        Ok(self
            .client
            .execute(
                "DELETE FROM peer_records WHERE last_time_seen < $1",
                &[&to_timestamp(cutoff_ms)?],
            )
            .await?)
    }
}

fn postgres_fields() -> String {
    select_fields(
        "host(p.address)",
        "FLOOR(EXTRACT(EPOCH FROM p.first_time_seen) * 1000)::bigint",
        LAST_SEEN,
    )
}

fn decode(row: &Row) -> Result<PeerRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.try_get::<_, $ty>($index)?
        };
    }
    decode_record!(get)
}
