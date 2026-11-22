//! PBHMetricsController's history charts available to authenticated readers.

use std::collections::BTreeMap;

use axum::extract::{Query, State, rejection::QueryRejection};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::{Json, Router, routing::get};
use pbh_storage::{
    HistoryMetricField, HistoryMetricOperation, HistoryTimestamp, HistoryTimestampCount,
    StorageError,
};
use serde::Deserialize;
use serde_json::{Value, json};
use time::{Date, Duration, Month, OffsetDateTime, PrimitiveDateTime, Time, UtcOffset};

use crate::{ban_api::BanLanguage, storage::Database, web::ScanState};

pub(crate) fn router(state: ScanState) -> Router {
    Router::new()
        .route("/api/statistic/analysis/field", get(field))
        .route("/api/statistic/analysis/banTrends", get(ban_trends))
        .route("/api/statistic/analysis/date", get(date))
        .with_state(state)
}

#[derive(Deserialize)]
struct FieldQuery {
    #[serde(rename = "type")]
    operation: String,
    field: String,
    filter: Option<f64>,
    downloader: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TrendQuery {
    start_at: i64,
    end_at: i64,
    downloader: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct DateQuery {
    start_at: i64,
    end_at: i64,
    field: String,
    #[serde(rename = "type")]
    period: String,
    filter: Option<String>,
}

async fn field(
    State(state): State<ScanState>,
    headers: HeaderMap,
    query: Result<Query<FieldQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let Ok(field) = HistoryMetricField::parse(&query.field) else {
        return internal_error(&headers);
    };
    let operation = match query.operation.as_str() {
        "count" => HistoryMetricOperation::Count,
        "sum" => HistoryMetricOperation::Sum,
        _ => return internal_error(&headers),
    };
    let threshold = query.filter.unwrap_or(0.0);
    let downloader = query.downloader.as_deref();
    let result = match &mut *state.database.lock().await {
        Database::Sqlite(store) => {
            store.history_field_metrics(field, operation, threshold, downloader)
        }
        Database::Postgres(store) => {
            store
                .history_field_metrics(field, operation, threshold, downloader)
                .await
        }
        Database::MySql(store) => {
            store
                .history_field_metrics(field, operation, threshold, downloader)
                .await
        }
    };
    match result {
        Ok(rows) => success(
            rows.into_iter()
                .map(|row| json!({"data": row.data, "count": row.count, "percent": row.percent}))
                .collect(),
        ),
        Err(error) => query_failed(&headers, error),
    }
}

async fn ban_trends(
    State(state): State<ScanState>,
    headers: HeaderMap,
    query: Result<Query<TrendQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    let downloader = query
        .downloader
        .as_deref()
        .filter(|name| !name.trim().is_empty());
    let (rows, postgres) = match read_timestamps(
        &state,
        HistoryTimestamp::BanAt,
        query.start_at,
        query.end_at,
        downloader,
    )
    .await
    {
        Ok(result) => result,
        Err(error) => return query_failed(&headers, error),
    };
    match group_timestamps(rows, |event| trend_day(event, postgres)) {
        Some(buckets) => success(
            buckets
                .into_iter()
                .map(|(key, value)| json!({"key": key, "value": value}))
                .collect(),
        ),
        None => internal_error(&headers),
    }
}

async fn date(
    State(state): State<ScanState>,
    headers: HeaderMap,
    query: Result<Query<DateQuery>, QueryRejection>,
) -> Response {
    let Ok(Query(query)) = query else {
        return internal_error(&headers);
    };
    if query.field.eq_ignore_ascii_case("banAt") && query.period == "day" {
        return dates_for_ban_day(&state, &headers, query.start_at, query.end_at).await;
    }
    let field = match query.field.as_str() {
        "banAt" => HistoryTimestamp::BanAt,
        "unbanAt" => HistoryTimestamp::UnbanAt,
        _ => return internal_error(&headers),
    };
    let Some(period) = Period::parse(&query.period) else {
        return internal_error(&headers);
    };
    let Ok(threshold) = query.filter.as_deref().unwrap_or("0.0").parse::<f64>() else {
        return internal_error(&headers);
    };
    let rows = match read_timestamps(&state, field, query.start_at, query.end_at, None).await {
        Ok((rows, _)) => rows,
        Err(error) => return query_failed(&headers, error),
    };
    let Some(buckets) = group_timestamps(rows, |event| calendar_bucket(event, period)) else {
        return internal_error(&headers);
    };
    let total = buckets.values().sum::<i64>();
    success(
        buckets
            .into_iter()
            .filter_map(|(timestamp, count)| {
                let percent = count as f64 / total as f64;
                (percent >= threshold)
                    .then_some(json!({"timestamp": timestamp, "count": count, "percent": percent}))
            })
            .collect(),
    )
}

async fn dates_for_ban_day(
    state: &ScanState,
    headers: &HeaderMap,
    start_at: i64,
    end_at: i64,
) -> Response {
    let (rows, postgres) =
        match read_timestamps(state, HistoryTimestamp::BanAt, start_at, end_at, None).await {
            Ok(result) => result,
            Err(error) => return query_failed(headers, error),
        };
    match group_timestamps(rows, |event| trend_day(event, postgres)) {
        Some(buckets) => success(buckets.into_iter().map(|(timestamp, count)| json!({"timestamp": timestamp, "count": count, "percent": 0.0})).collect()),
        None => internal_error(headers),
    }
}

async fn read_timestamps(
    state: &ScanState,
    field: HistoryTimestamp,
    start_at: i64,
    end_at: i64,
    downloader: Option<&str>,
) -> Result<(Vec<HistoryTimestampCount>, bool), StorageError> {
    match &mut *state.database.lock().await {
        Database::Sqlite(store) => Ok((
            store.history_timestamp_counts(field, start_at, end_at, downloader)?,
            false,
        )),
        Database::Postgres(store) => Ok((
            store
                .history_timestamp_counts(field, start_at, end_at, downloader)
                .await?,
            true,
        )),
        Database::MySql(store) => Ok((
            store
                .history_timestamp_counts(field, start_at, end_at, downloader)
                .await?,
            false,
        )),
    }
}

pub(crate) fn group_timestamps(
    rows: Vec<HistoryTimestampCount>,
    bucket: impl Fn(OffsetDateTime) -> Option<i64>,
) -> Option<BTreeMap<i64, i64>> {
    let mut counts = BTreeMap::new();
    for row in rows {
        let time =
            OffsetDateTime::from_unix_timestamp_nanos(i128::from(row.timestamp_ms) * 1_000_000)
                .ok()?;
        *counts.entry(bucket(time)?).or_insert(0_i64) += row.count;
    }
    Some(counts)
}

pub(crate) fn trend_day(event: OffsetDateTime, postgres: bool) -> Option<i64> {
    let offset = UtcOffset::local_offset_at(event).unwrap_or(UtcOffset::UTC);
    // PostgreSQL's Java OffsetDateTime reader exposes UTC, while SQLite and
    // MySQL expose local dates. TimeUtil attaches the local offset of the event.
    let observed_date = if postgres {
        event.date()
    } else {
        event.checked_to_offset(offset)?.date()
    };
    Some(
        observed_date
            .midnight()
            .assume_offset(offset)
            .unix_timestamp()
            * 1_000,
    )
}

#[derive(Debug, Clone, Copy)]
enum Period {
    Year,
    Month,
    Day,
    Hour,
    Minute,
    Second,
}

impl Period {
    fn parse(value: &str) -> Option<Self> {
        Some(match value {
            "year" => Self::Year,
            "month" => Self::Month,
            "day" => Self::Day,
            "hour" => Self::Hour,
            "minute" => Self::Minute,
            "second" => Self::Second,
            _ => return None,
        })
    }

    fn wall_time(self, local: OffsetDateTime) -> Option<PrimitiveDateTime> {
        let date = match self {
            // GregorianCalendar's day-of-month 0 is the final day of the preceding month.
            Self::Year => Date::from_calendar_date(local.year(), Month::January, 1)
                .ok()?
                .previous_day()?,
            Self::Month => Date::from_calendar_date(local.year(), local.month(), 1)
                .ok()?
                .previous_day()?,
            _ => local.date(),
        };
        let time = match self {
            Self::Year | Self::Month | Self::Day => Time::MIDNIGHT,
            Self::Hour => Time::from_hms(local.hour(), 0, 0).ok()?,
            Self::Minute => Time::from_hms(local.hour(), local.minute(), 0).ok()?,
            Self::Second => Time::from_hms(local.hour(), local.minute(), local.second()).ok()?,
        };
        Some(PrimitiveDateTime::new(date, time))
    }
}

fn calendar_bucket(event: OffsetDateTime, period: Period) -> Option<i64> {
    let offset = UtcOffset::local_offset_at(event).unwrap_or(UtcOffset::UTC);
    let wall = period.wall_time(event.checked_to_offset(offset)?)?;
    let estimated = wall.assume_offset(offset);
    let resolved = [
        estimated.checked_sub(Duration::hours(24)),
        Some(estimated),
        estimated.checked_add(Duration::hours(24)),
    ]
    .into_iter()
    .flatten()
    .filter_map(|instant| UtcOffset::local_offset_at(instant).ok())
    .map(|candidate| wall.assume_offset(candidate))
    .filter(|candidate| UtcOffset::local_offset_at(*candidate).ok() == Some(candidate.offset()))
    .max()
    .unwrap_or(estimated);
    Some(resolved.unix_timestamp() * 1_000)
}

fn success(data: Vec<Value>) -> Response {
    Json(json!({"success": true, "message": null, "data": data})).into_response()
}

fn query_failed(headers: &HeaderMap, error: StorageError) -> Response {
    crate::journal::log_warning!("Read history chart: {error}");
    internal_error(headers)
}

fn internal_error(headers: &HeaderMap) -> Response {
    let message =
        BanLanguage::from_headers(headers).render(&json!({"key": "WEBAPI_INTERNAL_ERROR"}));
    (
        StatusCode::INTERNAL_SERVER_ERROR,
        Json(json!({"success": false, "message": message, "data": null})),
    )
        .into_response()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn calendar_buckets_keep_java_zero_day_for_year_and_month() {
        let year = Date::from_calendar_date(2023, Month::December, 31).unwrap();
        let month = Date::from_calendar_date(2024, Month::May, 31).unwrap();
        let day = Date::from_calendar_date(2024, Month::June, 17).unwrap();
        let event =
            PrimitiveDateTime::new(day, Time::from_hms_milli(6, 7, 8, 999).unwrap()).assume_utc();
        assert_eq!(Period::Year.wall_time(event), Some(year.midnight()));
        assert_eq!(Period::Month.wall_time(event), Some(month.midnight()));
        assert_eq!(Period::Day.wall_time(event), Some(day.midnight()));
        assert_eq!(Period::Hour.wall_time(event), day.with_hms(6, 0, 0).ok());
        assert_eq!(Period::Minute.wall_time(event), day.with_hms(6, 7, 0).ok());
        assert_eq!(Period::Second.wall_time(event), day.with_hms(6, 7, 8).ok());
    }
}
