use rusqlite::{Params, TransactionBehavior, params};

use super::{SAMPLE_COLUMNS, TrafficHour, TrafficSample, UPDATE_CONFLICT, nonblank, select_hours};
use crate::{SqliteStore, StorageError};

impl SqliteStore {
    pub fn upsert_traffic_samples(
        &mut self,
        samples: &[TrafficSample<'_>],
    ) -> Result<(), StorageError> {
        if samples.is_empty() {
            return Ok(());
        }
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        {
            let mut statement = transaction.prepare_cached(&format!(
                "INSERT INTO traffic_journal_v3 ({SAMPLE_COLUMNS}) VALUES (
                 ?1, ?2, ?3, MAX(0, ?3), ?4, MAX(0, ?4), ?5, MAX(0, ?5), ?6, MAX(0, ?6))
                 {UPDATE_CONFLICT}"
            ))?;
            for sample in samples {
                statement.execute(params![
                    sample.timestamp_ms,
                    sample.downloader,
                    sample.uploaded,
                    sample.downloaded,
                    sample.protocol_uploaded,
                    sample.protocol_downloaded,
                ])?;
            }
        }
        transaction.commit()?;
        Ok(())
    }

    pub fn traffic_hours(
        &self,
        downloader: Option<&str>,
        start_ms: i64,
        end_ms: i64,
    ) -> Result<Vec<TrafficHour>, StorageError> {
        const BOUNDS: &str = "timestamp BETWEEN ?1 AND ?2";
        if let Some(downloader) = nonblank(downloader) {
            let sql = select_hours("timestamp", BOUNDS, Some("?3"), sqlite_delta);
            self.query_traffic_hours(&sql, params![start_ms, end_ms, downloader])
        } else {
            let sql = select_hours("timestamp", BOUNDS, None, sqlite_delta);
            self.query_traffic_hours(&sql, [start_ms, end_ms])
        }
    }

    fn query_traffic_hours(
        &self,
        sql: &str,
        parameters: impl Params,
    ) -> Result<Vec<TrafficHour>, StorageError> {
        Ok(self
            .connection
            .prepare_cached(sql)?
            .query_map(parameters, |row| {
                Ok(TrafficHour {
                    timestamp_ms: row.get(0)?,
                    uploaded: row.get(1)?,
                    downloaded: row.get(2)?,
                })
            })?
            .collect::<rusqlite::Result<_>>()?)
    }
}

fn sqlite_delta(counter: &str, scoped: bool) -> String {
    let delta = format!("{counter} - {counter}_at_start");
    if scoped {
        format!("MAX(0, {delta})")
    } else {
        format!("SUM({delta})")
    }
}
