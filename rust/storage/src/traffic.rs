//! Hourly cumulative downloader counters and traffic totals.

mod mysql;
mod postgres;
mod sqlite;

use std::collections::BTreeMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct TrafficSample<'a> {
    /// Start of the observation's hour in the caller's local time zone, as epoch milliseconds.
    pub timestamp_ms: i64,
    pub downloader: &'a str,
    pub uploaded: i64,
    pub downloaded: i64,
    pub protocol_uploaded: i64,
    pub protocol_downloaded: i64,
}

/// Traffic increments for a bucket returned from the journal.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct TrafficHour {
    pub timestamp_ms: i64,
    pub uploaded: i64,
    pub downloaded: i64,
}

/// Combine hourly increments by the caller's local date, ordered by day start.
pub fn aggregate_traffic_days(
    hours: impl IntoIterator<Item = TrafficHour>,
    mut day_start_ms: impl FnMut(i64) -> i64,
) -> Vec<TrafficHour> {
    let mut days = BTreeMap::new();
    for hour in hours {
        let timestamp_ms = day_start_ms(hour.timestamp_ms);
        let day = days.entry(timestamp_ms).or_insert(TrafficHour {
            timestamp_ms,
            uploaded: 0,
            downloaded: 0,
        });
        day.uploaded = day.uploaded.wrapping_add(hour.uploaded);
        day.downloaded = day.downloaded.wrapping_add(hour.downloaded);
    }
    days.into_values().collect()
}

const SAMPLE_COLUMNS: &str = "timestamp, downloader, data_overall_uploaded_at_start,
    data_overall_uploaded, data_overall_downloaded_at_start, data_overall_downloaded,
    protocol_overall_uploaded_at_start, protocol_overall_uploaded,
    protocol_overall_downloaded_at_start, protocol_overall_downloaded";

const UPDATE_CONFLICT: &str = "ON CONFLICT(timestamp, downloader) DO UPDATE SET
    data_overall_uploaded = CASE
        WHEN excluded.data_overall_uploaded_at_start > traffic_journal_v3.data_overall_uploaded
        THEN excluded.data_overall_uploaded_at_start ELSE traffic_journal_v3.data_overall_uploaded END,
    data_overall_downloaded = CASE
        WHEN excluded.data_overall_downloaded_at_start > traffic_journal_v3.data_overall_downloaded
        THEN excluded.data_overall_downloaded_at_start ELSE traffic_journal_v3.data_overall_downloaded END,
    protocol_overall_uploaded = CASE
        WHEN excluded.protocol_overall_uploaded_at_start > traffic_journal_v3.protocol_overall_uploaded
        THEN excluded.protocol_overall_uploaded_at_start ELSE traffic_journal_v3.protocol_overall_uploaded END,
    protocol_overall_downloaded = CASE
        WHEN excluded.protocol_overall_downloaded_at_start > traffic_journal_v3.protocol_overall_downloaded
        THEN excluded.protocol_overall_downloaded_at_start ELSE traffic_journal_v3.protocol_overall_downloaded END";

fn select_hours(
    timestamp_ms: &str,
    bounds: &str,
    downloader_parameter: Option<&str>,
    delta: impl Fn(&str, bool) -> String,
) -> String {
    let scoped = downloader_parameter.is_some();
    let uploaded = delta("data_overall_uploaded", scoped);
    let downloaded = delta("data_overall_downloaded", scoped);
    let suffix = match downloader_parameter {
        Some(parameter) => format!("AND downloader = {parameter}"),
        None => "GROUP BY timestamp".to_owned(),
    };
    format!(
        "SELECT {timestamp_ms}, {uploaded}, {downloaded} FROM traffic_journal_v3
         WHERE {bounds} {suffix} ORDER BY timestamp"
    )
}

fn nonblank(downloader: Option<&str>) -> Option<&str> {
    downloader.filter(|name| !name.trim().is_empty())
}
