use mysql_async::{IsolationLevel, Row, TxOpts, Value, params, prelude::Queryable};

use super::{
    MAX_REPORT_ROWS, PeerRecord, PeerRecordCursor, PeerRecordFilter, PeerRecordObservation,
    PeerRecordPage, PeerRecordQuery, RECORD_FROM, UPSERT_COLUMNS, compact_peer_id, decode_record,
    invalid_row, select_fields,
};
use crate::{
    BanHistoryTorrentRecord, MySqlStore, StorageError, history::java_host_address,
    history_query::page_window, mysql::to_microseconds, torrent::MYSQL_UPSERT,
};

const LAST_SEEN: &str = "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', p.last_time_seen) / 1000) AS SIGNED)";

// MySQL evaluates assignments in order. Account for deltas before replacing
// offsets, and leave last_time_seen until after every stale-observation guard.
const MYSQL_CONFLICT: &str = "ON DUPLICATE KEY UPDATE
    uploaded = IF(VALUES(last_time_seen) < last_time_seen, uploaded,
        uploaded + IF(VALUES(uploaded) < uploaded_offset, VALUES(uploaded), VALUES(uploaded) - uploaded_offset)),
    downloaded = IF(VALUES(last_time_seen) < last_time_seen, downloaded,
        downloaded + IF(VALUES(downloaded) < downloaded_offset, VALUES(downloaded), VALUES(downloaded) - downloaded_offset)),
    uploaded_offset = IF(VALUES(last_time_seen) < last_time_seen, uploaded_offset, VALUES(uploaded)),
    downloaded_offset = IF(VALUES(last_time_seen) < last_time_seen, downloaded_offset, VALUES(downloaded)),
    upload_speed = IF(VALUES(last_time_seen) < last_time_seen, upload_speed, VALUES(upload_speed)),
    download_speed = IF(VALUES(last_time_seen) < last_time_seen, download_speed, VALUES(download_speed)),
    port = IF(VALUES(last_time_seen) < last_time_seen, port, VALUES(port)),
    peer_id = IF(VALUES(last_time_seen) < last_time_seen, peer_id, VALUES(peer_id)),
    client_name = IF(VALUES(last_time_seen) < last_time_seen, client_name, VALUES(client_name)),
    last_flags = IF(VALUES(last_time_seen) < last_time_seen, last_flags, VALUES(last_flags)),
    last_time_seen = GREATEST(last_time_seen, VALUES(last_time_seen))";

impl MySqlStore {
    pub async fn upsert_peer_records(
        &mut self,
        observations: &[PeerRecordObservation<'_>],
    ) -> Result<(), StorageError> {
        if observations.is_empty() {
            return Ok(());
        }
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        let torrent_upsert = transaction.prep(MYSQL_UPSERT).await?;
        let peer_upsert = transaction.prep(format!(
            "INSERT INTO peer_records ({UPSERT_COLUMNS}) VALUES (
                 :address, :port, (SELECT id FROM torrents WHERE info_hash = :info_hash), :downloader,
                 :peer_id, :client_name, :uploaded, :uploaded, :upload_speed,
                 :downloaded, :downloaded, :download_speed, :flags,
                 TIMESTAMPADD(MICROSECOND, :time, CAST('1970-01-01 00:00:00' AS DATETIME(3))),
                 TIMESTAMPADD(MICROSECOND, :time, CAST('1970-01-01 00:00:00' AS DATETIME(3))), :geoip) {MYSQL_CONFLICT}"
        )).await?;
        for peer in observations {
            let torrent = peer.torrent;
            transaction.exec_drop(&torrent_upsert, params! {
                "info_hash" => torrent.info_hash, "name" => torrent.name, "size" => torrent.size, "private_torrent" => torrent.private,
            }).await?;
            transaction.exec_drop(&peer_upsert, params! {
                "address" => java_host_address(peer.address), "port" => peer.port, "info_hash" => torrent.info_hash,
                "downloader" => peer.downloader_id, "peer_id" => compact_peer_id(peer.peer_id), "client_name" => peer.client_name,
                "uploaded" => peer.uploaded, "upload_speed" => peer.upload_speed, "downloaded" => peer.downloaded,
                "download_speed" => peer.download_speed, "flags" => peer.last_flags, "time" => to_microseconds(peer.observed_at_ms)?,
                "geoip" => peer.peer_geoip.map(serde_json::Value::to_string),
            }).await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn peer_history(
        &mut self,
        query: &PeerRecordQuery,
    ) -> Result<PeerRecordPage, StorageError> {
        let (column, value): (_, Value) = match query.filter {
            PeerRecordFilter::Address(address) => ("p.address", java_host_address(address).into()),
            PeerRecordFilter::TorrentId(id) => ("p.torrent_id", id.into()),
        };
        let mut options = TxOpts::new();
        options
            .with_readonly(true)
            .with_isolation_level(IsolationLevel::RepeatableRead);
        let mut transaction = self.connection.start_transaction(options).await?;
        let total = if query.size < 0 {
            0
        } else {
            transaction
                .exec_first::<u64, _, _>(
                    format!("SELECT COUNT(*) FROM peer_records p WHERE {column} = ?"),
                    (value.clone(),),
                )
                .await?
                .unwrap_or(0)
        };
        let window = page_window(query.page, query.size, total)?;
        let records = match window {
            Some((size, offset)) => {
                let rows: Vec<Row> = transaction.exec(format!(
                    "SELECT {} {RECORD_FROM} WHERE {column} = ? ORDER BY {} LIMIT ? OFFSET ?",
                    mysql_fields(), query.order_sql(),
                ), (value, size, offset)).await?;
                rows.iter().map(decode).collect::<Result<_, _>>()?
            }
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
        let rows: Vec<Row> = self.connection.exec(format!(
            "SELECT {} {RECORD_FROM}
             WHERE p.last_time_seen >= TIMESTAMPADD(MICROSECOND, :time, CAST('1970-01-01 00:00:00' AS DATETIME(3)))
               AND ({LAST_SEEN} > :ms OR ({LAST_SEEN} = :ms AND p.id > :id))
             ORDER BY {LAST_SEEN}, p.id LIMIT :limit", mysql_fields(),
        ), params! { "time" => to_microseconds(after.last_time_seen_ms)?, "ms" => after.last_time_seen_ms,
                     "id" => after.id, "limit" => limit.min(MAX_REPORT_ROWS) }).await?;
        rows.iter().map(decode).collect()
    }

    pub async fn cleanup_peer_records(&mut self, cutoff_ms: i64) -> Result<u64, StorageError> {
        self.connection.exec_drop(
            "DELETE FROM peer_records WHERE last_time_seen < TIMESTAMPADD(MICROSECOND, ?, CAST('1970-01-01 00:00:00' AS DATETIME(3)))",
            (to_microseconds(cutoff_ms)?,),
        ).await?;
        Ok(self.connection.affected_rows())
    }
}

fn mysql_fields() -> String {
    select_fields(
        "p.address",
        "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', p.first_time_seen) / 1000) AS SIGNED)",
        LAST_SEEN,
    )
}

fn decode(row: &Row) -> Result<PeerRecord, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.get_opt::<$ty, _>($index)
                .and_then(Result::ok)
                .ok_or_else(|| {
                    StorageError::UnsupportedSchema(format!(
                        "invalid MySQL peer record column {}",
                        $index
                    ))
                })?
        };
    }
    decode_record!(get)
}
