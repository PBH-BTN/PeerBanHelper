use mysql_async::{TxOpts, params, prelude::Queryable};

use super::{SAMPLE_COLUMNS, TrafficHour, TrafficSample, nonblank, select_hours};
use crate::{MySqlStore, StorageError, mysql::to_microseconds};

const UPDATE_CONFLICT: &str = "ON DUPLICATE KEY UPDATE
    data_overall_uploaded = GREATEST(data_overall_uploaded, VALUES(data_overall_uploaded_at_start)),
    data_overall_downloaded = GREATEST(data_overall_downloaded, VALUES(data_overall_downloaded_at_start)),
    protocol_overall_uploaded = GREATEST(protocol_overall_uploaded, VALUES(protocol_overall_uploaded_at_start)),
    protocol_overall_downloaded = GREATEST(protocol_overall_downloaded, VALUES(protocol_overall_downloaded_at_start))";

const BOUNDS: &str = "timestamp BETWEEN
    TIMESTAMPADD(MICROSECOND, :start, CAST('1970-01-01 00:00:00' AS DATETIME(3)))
    AND TIMESTAMPADD(MICROSECOND, :end, CAST('1970-01-01 00:00:00' AS DATETIME(3)))";

impl MySqlStore {
    pub async fn upsert_traffic_samples(
        &mut self,
        samples: &[TrafficSample<'_>],
    ) -> Result<(), StorageError> {
        if samples.is_empty() {
            return Ok(());
        }
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        let statement = transaction
            .prep(format!(
                "INSERT INTO traffic_journal_v3 ({SAMPLE_COLUMNS}) VALUES (
                 TIMESTAMPADD(MICROSECOND, :sample_time, CAST('1970-01-01 00:00:00' AS DATETIME(3))),
                 :downloader, :uploaded, GREATEST(0, :uploaded), :downloaded, GREATEST(0, :downloaded),
                 :protocol_uploaded, GREATEST(0, :protocol_uploaded),
                 :protocol_downloaded, GREATEST(0, :protocol_downloaded)) {UPDATE_CONFLICT}"
            ))
            .await?;
        for sample in samples {
            transaction
                .exec_drop(
                    &statement,
                    params! {
                        "sample_time" => to_microseconds(sample.timestamp_ms)?,
                        "downloader" => sample.downloader,
                        "uploaded" => sample.uploaded,
                        "downloaded" => sample.downloaded,
                        "protocol_uploaded" => sample.protocol_uploaded,
                        "protocol_downloaded" => sample.protocol_downloaded,
                    },
                )
                .await?;
        }
        transaction.commit().await?;
        Ok(())
    }

    pub async fn traffic_hours(
        &mut self,
        downloader: Option<&str>,
        start_ms: i64,
        end_ms: i64,
    ) -> Result<Vec<TrafficHour>, StorageError> {
        let downloader = nonblank(downloader);
        let sql = select_hours(
            "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', timestamp) / 1000) AS SIGNED)",
            BOUNDS,
            downloader.map(|_| ":downloader"),
            mysql_delta,
        );
        let start = to_microseconds(start_ms)?;
        let end = to_microseconds(end_ms)?;
        let rows: Vec<(i64, i64, i64)> = if let Some(downloader) = downloader {
            self.connection
                .exec(
                    sql,
                    params! {"start" => start, "end" => end, "downloader" => downloader},
                )
                .await?
        } else {
            self.connection
                .exec(sql, params! {"start" => start, "end" => end})
                .await?
        };
        Ok(rows
            .into_iter()
            .map(|(timestamp_ms, uploaded, downloaded)| TrafficHour {
                timestamp_ms,
                uploaded,
                downloaded,
            })
            .collect())
    }
}

fn mysql_delta(counter: &str, scoped: bool) -> String {
    // The existing schema uses unsigned BIGINT. Subtract in DECIMAL so that
    // the result can still represent an older row whose end precedes its start.
    let delta = format!("CAST({counter} AS DECIMAL(20, 0)) - {counter}_at_start");
    if scoped {
        format!("CAST(GREATEST(0, {delta}) AS SIGNED)")
    } else {
        format!("CAST(SUM({delta}) AS SIGNED)")
    }
}
