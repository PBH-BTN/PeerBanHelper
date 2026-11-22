use std::{net::IpAddr, time::SystemTime};

use tokio_postgres::{Row, Transaction, types::ToSql};

use super::{Dialect, PcbAddressKey, PcbRangeKey, PcbState, Table, decode_state};
use crate::{PostgresStore, StorageError, postgres::to_timestamp};

impl PostgresStore {
    pub async fn load_pcb_address(
        &mut self,
        key: PcbAddressKey<'_>,
    ) -> Result<Option<PcbState>, StorageError> {
        let row = self
            .client
            .query_opt(
                &Table::Address.select_sql(Dialect::Postgres),
                &[
                    &key.address.to_canonical(),
                    &i32::from(key.port),
                    &key.torrent_id,
                    &key.downloader,
                ],
            )
            .await?;
        Ok(row.as_ref().map(decode).transpose()?)
    }

    pub async fn load_pcb_range(
        &mut self,
        key: PcbRangeKey<'_>,
    ) -> Result<Option<PcbState>, StorageError> {
        let row = self
            .client
            .query_opt(
                &Table::Range.select_sql(Dialect::Postgres),
                &[&key.range, &key.torrent_id, &key.downloader],
            )
            .await?;
        Ok(row.as_ref().map(decode).transpose()?)
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
        let transaction = self.client.transaction().await?;
        let address = transaction
            .prepare(&Table::Address.merge_sql(Dialect::Postgres))
            .await?;
        for (key, state) in addresses {
            let ip = key.address.to_canonical();
            let port = i32::from(key.port);
            merge(
                &transaction,
                &address,
                &[&ip, &port, &key.torrent_id, &key.downloader],
                state,
            )
            .await?;
        }
        let range = transaction
            .prepare(&Table::Range.merge_sql(Dialect::Postgres))
            .await?;
        for (key, state) in ranges {
            merge(
                &transaction,
                &range,
                &[&key.range, &key.torrent_id, &key.downloader],
                state,
            )
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
        Ok(self
            .client
            .execute(
                &Table::Address.delete_sql(Dialect::Postgres),
                &[&torrent_id, &address.to_canonical()],
            )
            .await?)
    }

    pub async fn delete_pcb_range(
        &mut self,
        torrent_id: &str,
        range: &str,
    ) -> Result<u64, StorageError> {
        Ok(self
            .client
            .execute(
                &Table::Range.delete_sql(Dialect::Postgres),
                &[&torrent_id, &range],
            )
            .await?)
    }

    pub async fn cleanup_pcb(&mut self, cutoff_ms: i64) -> Result<u64, StorageError> {
        let cutoff = to_timestamp(cutoff_ms)?;
        let transaction = self.client.transaction().await?;
        let addresses = transaction
            .execute(&Table::Address.cleanup_sql(Dialect::Postgres), &[&cutoff])
            .await?;
        let ranges = transaction
            .execute(&Table::Range.cleanup_sql(Dialect::Postgres), &[&cutoff])
            .await?;
        transaction.commit().await?;
        Ok(addresses + ranges)
    }
}

async fn merge(
    transaction: &Transaction<'_>,
    statement: &tokio_postgres::Statement,
    key: &[&(dyn ToSql + Sync)],
    state: &PcbState,
) -> Result<(), StorageError> {
    let times = [
        to_timestamp(state.first_time_seen_ms)?,
        to_timestamp(state.last_time_seen_ms)?,
        to_timestamp(state.ban_delay_window_end_at_ms)?,
        to_timestamp(state.fast_pcb_test_execute_at_ms)?,
    ];
    let mut parameters = key.to_vec();
    parameters.extend(fields(state, &times));
    transaction.execute(statement, &parameters).await?;
    Ok(())
}

fn fields<'a>(state: &'a PcbState, times: &'a [SystemTime; 4]) -> [&'a (dyn ToSql + Sync); 10] {
    [
        &state.last_report_progress,
        &state.last_report_uploaded,
        &state.tracking_uploaded_increase_total,
        &state.rewind_counter,
        &state.progress_difference_counter,
        &times[0],
        &times[1],
        &times[2],
        &times[3],
        &state.last_torrent_completed_size,
    ]
}

fn decode(row: &Row) -> Result<PcbState, tokio_postgres::Error> {
    macro_rules! get {
        ($index:expr, $ty:ty) => {
            row.try_get::<_, $ty>($index)?
        };
    }
    Ok(decode_state!(get))
}
