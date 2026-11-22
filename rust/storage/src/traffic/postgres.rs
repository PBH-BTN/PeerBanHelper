use tokio_postgres::types::ToSql;

use super::{SAMPLE_COLUMNS, TrafficHour, TrafficSample, UPDATE_CONFLICT, nonblank, select_hours};
use crate::{PostgresStore, StorageError, postgres::to_timestamp};

impl PostgresStore {
    pub async fn upsert_traffic_samples(
        &mut self,
        samples: &[TrafficSample<'_>],
    ) -> Result<(), StorageError> {
        if samples.is_empty() {
            return Ok(());
        }
        let transaction = self.client.transaction().await?;
        let statement = transaction
            .prepare(&format!(
                "INSERT INTO traffic_journal_v3 ({SAMPLE_COLUMNS}) VALUES (
                 $1, $2, $3, GREATEST(0::bigint, $3), $4, GREATEST(0::bigint, $4),
                 $5, GREATEST(0::bigint, $5), $6, GREATEST(0::bigint, $6)) {UPDATE_CONFLICT}"
            ))
            .await?;
        for sample in samples {
            transaction
                .execute(
                    &statement,
                    &[
                        &to_timestamp(sample.timestamp_ms)?,
                        &sample.downloader,
                        &sample.uploaded,
                        &sample.downloaded,
                        &sample.protocol_uploaded,
                        &sample.protocol_downloaded,
                    ],
                )
                .await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn traffic_hours(
        &self,
        downloader: Option<&str>,
        start_ms: i64,
        end_ms: i64,
    ) -> Result<Vec<TrafficHour>, StorageError> {
        let downloader = nonblank(downloader);
        let sql = select_hours(
            "FLOOR(EXTRACT(EPOCH FROM timestamp) * 1000)::bigint",
            "timestamp BETWEEN $1 AND $2",
            downloader.map(|_| "$3"),
            postgres_delta,
        );
        let start = to_timestamp(start_ms)?;
        let end = to_timestamp(end_ms)?;
        let mut parameters: Vec<&(dyn ToSql + Sync)> = vec![&start, &end];
        if downloader.is_some() {
            parameters.push(&downloader);
        }
        self.client
            .query(&sql, &parameters)
            .await?
            .iter()
            .map(|row| {
                Ok(TrafficHour {
                    timestamp_ms: row.try_get(0)?,
                    uploaded: row.try_get(1)?,
                    downloaded: row.try_get(2)?,
                })
            })
            .collect()
    }
}

fn postgres_delta(counter: &str, scoped: bool) -> String {
    let delta = format!("{counter}::numeric - {counter}_at_start");
    if scoped {
        format!("GREATEST(0, {delta})::bigint")
    } else {
        format!("SUM({delta})::bigint")
    }
}
