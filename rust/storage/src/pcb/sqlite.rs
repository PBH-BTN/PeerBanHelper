use std::net::IpAddr;

use rusqlite::{
    OptionalExtension, Row, TransactionBehavior, params, params_from_iter, types::Value,
};

use super::{Dialect, PcbAddressKey, PcbRangeKey, PcbState, Table, decode_state};
use crate::{SqliteStore, StorageError, history::java_host_address};

impl SqliteStore {
    pub fn load_pcb_address(
        &self,
        key: PcbAddressKey<'_>,
    ) -> Result<Option<PcbState>, StorageError> {
        let state = self
            .connection
            .prepare_cached(&Table::Address.select_sql(Dialect::Sqlite))?
            .query_row(
                params![
                    java_host_address(key.address),
                    key.port,
                    key.torrent_id,
                    key.downloader
                ],
                decode,
            )
            .optional()?;
        Ok(state)
    }

    pub fn load_pcb_range(&self, key: PcbRangeKey<'_>) -> Result<Option<PcbState>, StorageError> {
        let state = self
            .connection
            .prepare_cached(&Table::Range.select_sql(Dialect::Sqlite))?
            .query_row(params![key.range, key.torrent_id, key.downloader], decode)
            .optional()?;
        Ok(state)
    }

    /// Flush both sets of dirty rule state as one write transaction.
    pub fn merge_pcb(
        &mut self,
        addresses: &[(PcbAddressKey<'_>, PcbState)],
        ranges: &[(PcbRangeKey<'_>, PcbState)],
    ) -> Result<(), StorageError> {
        if addresses.is_empty() && ranges.is_empty() {
            return Ok(());
        }
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        {
            let mut address =
                transaction.prepare_cached(&Table::Address.merge_sql(Dialect::Sqlite))?;
            for (key, state) in addresses {
                let key_fields = [
                    java_host_address(key.address).into(),
                    i64::from(key.port).into(),
                    key.torrent_id.to_owned().into(),
                    key.downloader.to_owned().into(),
                ];
                address.execute(params_from_iter(
                    key_fields.into_iter().chain(values(state)),
                ))?;
            }
            let mut range = transaction.prepare_cached(&Table::Range.merge_sql(Dialect::Sqlite))?;
            for (key, state) in ranges {
                let key_fields = [
                    key.range.to_owned().into(),
                    key.torrent_id.to_owned().into(),
                    key.downloader.to_owned().into(),
                ];
                range.execute(params_from_iter(
                    key_fields.into_iter().chain(values(state)),
                ))?;
            }
        }
        transaction.commit()?;
        Ok(())
    }

    /// Clear a torrent's state for an address across its ports and downloaders.
    pub fn delete_pcb_address(
        &mut self,
        torrent_id: &str,
        address: IpAddr,
    ) -> Result<u64, StorageError> {
        Ok(self.connection.execute(
            &Table::Address.delete_sql(Dialect::Sqlite),
            params![torrent_id, java_host_address(address)],
        )? as u64)
    }

    pub fn delete_pcb_range(&mut self, torrent_id: &str, range: &str) -> Result<u64, StorageError> {
        Ok(self.connection.execute(
            &Table::Range.delete_sql(Dialect::Sqlite),
            params![torrent_id, range],
        )? as u64)
    }

    /// Remove records whose last observation is strictly before the cutoff.
    pub fn cleanup_pcb(&mut self, cutoff_ms: i64) -> Result<u64, StorageError> {
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        let addresses =
            transaction.execute(&Table::Address.cleanup_sql(Dialect::Sqlite), [cutoff_ms])?;
        let ranges =
            transaction.execute(&Table::Range.cleanup_sql(Dialect::Sqlite), [cutoff_ms])?;
        transaction.commit()?;
        Ok(addresses as u64 + ranges as u64)
    }
}

fn values(state: &PcbState) -> [Value; 10] {
    [
        state.last_report_progress.into(),
        state.last_report_uploaded.into(),
        state.tracking_uploaded_increase_total.into(),
        state.rewind_counter.into(),
        state.progress_difference_counter.into(),
        state.first_time_seen_ms.into(),
        state.last_time_seen_ms.into(),
        state.ban_delay_window_end_at_ms.into(),
        state.fast_pcb_test_execute_at_ms.into(),
        state.last_torrent_completed_size.into(),
    ]
}

fn decode(row: &Row<'_>) -> rusqlite::Result<PcbState> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.get::<_, $ty>($index)?
        };
    }
    Ok(decode_state!(get))
}
