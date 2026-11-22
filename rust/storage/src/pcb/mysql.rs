use std::net::IpAddr;

use mysql_async::{Params, Row, TxOpts, Value, prelude::Queryable};

use super::{Dialect, PcbAddressKey, PcbRangeKey, PcbState, Table, decode_state};
use crate::{MySqlStore, StorageError, history::java_host_address};

impl MySqlStore {
    pub async fn load_pcb_address(
        &mut self,
        key: PcbAddressKey<'_>,
    ) -> Result<Option<PcbState>, StorageError> {
        let row: Option<Row> = self
            .connection
            .exec_first(
                Table::Address.select_sql(Dialect::MySql),
                (
                    java_host_address(key.address),
                    key.port,
                    key.torrent_id,
                    key.downloader,
                ),
            )
            .await?;
        row.as_ref().map(decode).transpose()
    }

    pub async fn load_pcb_range(
        &mut self,
        key: PcbRangeKey<'_>,
    ) -> Result<Option<PcbState>, StorageError> {
        let row: Option<Row> = self
            .connection
            .exec_first(
                Table::Range.select_sql(Dialect::MySql),
                (key.range, key.torrent_id, key.downloader),
            )
            .await?;
        row.as_ref().map(decode).transpose()
    }

    /// Flush both sets of dirty rule state as one write transaction.
    pub async fn merge_pcb(
        &mut self,
        addresses: &[(PcbAddressKey<'_>, PcbState)],
        ranges: &[(PcbRangeKey<'_>, PcbState)],
    ) -> Result<(), StorageError> {
        if addresses.is_empty() && ranges.is_empty() {
            return Ok(());
        }
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        let address = transaction
            .prep(Table::Address.merge_sql(Dialect::MySql))
            .await?;
        for (key, state) in addresses {
            let key_fields = [
                java_host_address(key.address).into(),
                key.port.into(),
                key.torrent_id.into(),
                key.downloader.into(),
            ];
            let fields = key_fields.into_iter().chain(values(state)?).collect();
            transaction
                .exec_drop(&address, Params::Positional(fields))
                .await?;
        }
        let range = transaction
            .prep(Table::Range.merge_sql(Dialect::MySql))
            .await?;
        for (key, state) in ranges {
            let key_fields = [
                key.range.into(),
                key.torrent_id.into(),
                key.downloader.into(),
            ];
            let fields = key_fields.into_iter().chain(values(state)?).collect();
            transaction
                .exec_drop(&range, Params::Positional(fields))
                .await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn delete_pcb_address(
        &mut self,
        torrent_id: &str,
        address: IpAddr,
    ) -> Result<u64, StorageError> {
        self.connection
            .exec_drop(
                Table::Address.delete_sql(Dialect::MySql),
                (torrent_id, java_host_address(address)),
            )
            .await?;
        Ok(self.connection.affected_rows())
    }

    pub async fn delete_pcb_range(
        &mut self,
        torrent_id: &str,
        range: &str,
    ) -> Result<u64, StorageError> {
        self.connection
            .exec_drop(Table::Range.delete_sql(Dialect::MySql), (torrent_id, range))
            .await?;
        Ok(self.connection.affected_rows())
    }

    pub async fn cleanup_pcb(&mut self, cutoff_ms: i64) -> Result<u64, StorageError> {
        let cutoff = to_microseconds(cutoff_ms)?;
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        transaction
            .exec_drop(Table::Address.cleanup_sql(Dialect::MySql), (cutoff,))
            .await?;
        let addresses = transaction.affected_rows();
        transaction
            .exec_drop(Table::Range.cleanup_sql(Dialect::MySql), (cutoff,))
            .await?;
        let ranges = transaction.affected_rows();
        transaction.commit().await?;
        Ok(addresses + ranges)
    }
}

fn values(state: &PcbState) -> Result<[Value; 10], StorageError> {
    Ok([
        state.last_report_progress.into(),
        state.last_report_uploaded.into(),
        state.tracking_uploaded_increase_total.into(),
        state.rewind_counter.into(),
        state.progress_difference_counter.into(),
        to_microseconds(state.first_time_seen_ms)?.into(),
        to_microseconds(state.last_time_seen_ms)?.into(),
        to_microseconds(state.ban_delay_window_end_at_ms)?.into(),
        to_microseconds(state.fast_pcb_test_execute_at_ms)?.into(),
        state.last_torrent_completed_size.into(),
    ])
}

fn to_microseconds(milliseconds: i64) -> Result<i64, StorageError> {
    milliseconds
        .checked_mul(1000)
        .ok_or(StorageError::InvalidTimestamp(milliseconds))
}

fn decode(row: &Row) -> Result<PcbState, StorageError> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.get_opt::<$ty, _>($index)
                .and_then(Result::ok)
                .ok_or_else(|| {
                    StorageError::UnsupportedSchema(format!("invalid MySQL PCB column {}", $index))
                })?
        };
    }
    Ok(decode_state!(get))
}
