//! Metric chart queries over the ban history, with closed field selection.

use mysql_async::{Value, params, prelude::Queryable};

use crate::{
    MySqlStore, PostgresStore, SqliteStore, StorageError, mysql::to_microseconds,
    postgres::to_timestamp,
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct HistoryMetricField {
    column: &'static str,
    kind: FieldKind,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum FieldKind {
    Text,
    PeerId,
    Number,
    Address,
    Timestamp,
    Torrent,
}

impl HistoryMetricField {
    pub fn parse(field: &str) -> Result<Self, StorageError> {
        let mut normalized = String::with_capacity(field.len());
        let mut previous_lowercase = false;
        for ch in field.chars() {
            if previous_lowercase && ch.is_ascii_uppercase() {
                normalized.push('_');
            }
            normalized.push(ch.to_ascii_lowercase());
            previous_lowercase = ch.is_ascii_lowercase();
        }
        use FieldKind::{Address, Number, PeerId, Text, Timestamp, Torrent};
        let (column, kind) = match normalized.as_str() {
            "id" => ("h.id", Number),
            "ban_at" | "time" => ("h.ban_at", Timestamp),
            "unban_at" => ("h.unban_at", Timestamp),
            "ip" | "peer_ip" => ("h.ip", Address),
            "port" | "peer_port" => ("h.port", Number),
            "peer_id" => ("h.peer_id", PeerId),
            "client_name" | "peer_client_name" => ("h.peer_client_name", Text),
            "peer_uploaded" => ("h.peer_uploaded", Number),
            "peer_downloaded" => ("h.peer_downloaded", Number),
            "peer_progress" => ("h.peer_progress", Number),
            "downloader_progress" => ("h.downloader_progress", Number),
            "torrent_id" => ("h.torrent_id", Number),
            "torrent_name" => ("t.name", Torrent),
            "module" | "module_name" => ("h.module_name", Text),
            "rule_name" => ("h.rule_name", Text),
            "description" => ("h.description", Text),
            "flags" => ("h.flags", Text),
            "downloader" => ("h.downloader", Text),
            "structured_data" => ("h.structured_data", Text),
            "peer_geoip" => ("h.peer_geoip", Text),
            _ => {
                return Err(StorageError::InvalidHistoryQuery(format!(
                    "unsupported metric field {field:?}"
                )));
            }
        };
        Ok(Self { column, kind })
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HistoryMetricOperation {
    Count,
    Sum,
}

#[derive(Debug, Clone, PartialEq)]
pub struct HistoryFieldMetric {
    pub data: String,
    pub count: i64,
    pub percent: f64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HistoryTimestamp {
    BanAt,
    UnbanAt,
}

impl HistoryTimestamp {
    fn column(self) -> &'static str {
        match self {
            Self::BanAt => "ban_at",
            Self::UnbanAt => "unban_at",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct HistoryTimestampCount {
    pub timestamp_ms: i64,
    pub count: i64,
}

#[derive(Clone, Copy)]
pub(crate) enum Dialect {
    Sqlite,
    Postgres,
    MySql,
}

impl Dialect {
    fn format_key(self, field: HistoryMetricField) -> &'static str {
        if field.kind == FieldKind::Address && matches!(self, Self::Postgres) {
            "host(bucket)"
        } else if matches!(self, Self::MySql) {
            "CAST(bucket AS CHAR)"
        } else {
            "CAST(bucket AS TEXT)"
        }
    }

    fn format_weight(self) -> &'static str {
        match self {
            Self::Sqlite => "CAST(weight AS INTEGER), CAST(weight AS REAL)",
            Self::Postgres => "TRUNC(weight::numeric)::bigint, weight::double precision",
            Self::MySql => "CAST(TRUNCATE(weight, 0) AS SIGNED), weight + 0e0",
        }
    }

    pub(crate) fn timestamp_ms(self, column: &str) -> String {
        match self {
            Self::Sqlite => column.to_owned(),
            Self::Postgres => format!("FLOOR(EXTRACT(EPOCH FROM {column}) * 1000)::bigint"),
            Self::MySql => format!(
                "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', {column}) / 1000) AS SIGNED)"
            ),
        }
    }
}

fn field_sql(
    field: HistoryMetricField,
    operation: HistoryMetricOperation,
    downloader: Option<&str>,
    dialect: Dialect,
) -> Result<String, StorageError> {
    let column = field.column;
    let aggregate = match operation {
        HistoryMetricOperation::Count => "COUNT(*)".to_owned(),
        HistoryMetricOperation::Sum if field.kind == FieldKind::Number => format!("SUM({column})"),
        HistoryMetricOperation::Sum => {
            return Err(StorageError::InvalidHistoryQuery(
                "sum requires a numeric field".into(),
            ));
        }
    };
    let bucket = if field.kind == FieldKind::PeerId {
        format!("SUBSTR({column}, 1, 8)")
    } else {
        column.to_owned()
    };
    let join = if operation == HistoryMetricOperation::Sum || field.kind == FieldKind::Torrent {
        "JOIN torrents t ON h.torrent_id = t.id"
    } else {
        ""
    };
    let nonempty = if matches!(
        field.kind,
        FieldKind::Text | FieldKind::PeerId | FieldKind::Torrent
    ) {
        format!(" AND {column} <> ''")
    } else {
        String::new()
    };
    let downloader = downloader.map_or_else(String::new, |parameter| {
        format!(" AND h.downloader = {parameter}")
    });
    let key = dialect.format_key(field);
    let weight = dialect.format_weight();
    // A derived table also works for MySQL versions without CTE support.
    Ok(format!(
        "SELECT {key}, {weight} FROM (
        SELECT {bucket} AS bucket, {aggregate} AS weight FROM history h {join}
        WHERE {column} IS NOT NULL{nonempty}{downloader} GROUP BY {bucket}
    ) grouped ORDER BY weight DESC"
    ))
}

type FieldRow = (String, i64, f64);

fn filter_metrics(rows: Vec<FieldRow>, minimum_percent: f64) -> Vec<HistoryFieldMetric> {
    let total = rows.iter().map(|(_, _, weight)| weight).sum::<f64>();
    if total == 0.0 {
        return Vec::new();
    }
    rows.into_iter()
        .filter_map(|(data, count, amount)| {
            let ratio = amount / total;
            (ratio > minimum_percent).then_some(HistoryFieldMetric {
                data,
                count,
                percent: if total > 0.0 { ratio } else { 0.0 },
            })
        })
        .collect()
}

fn dates_sql(
    field: HistoryTimestamp,
    start: &str,
    end: &str,
    downloader: Option<&str>,
    dialect: Dialect,
) -> String {
    let column = field.column();
    let millis = dialect.timestamp_ms(column);
    let downloader = downloader.map_or_else(String::new, |parameter| {
        format!(" AND downloader = {parameter}")
    });
    format!("SELECT {millis}, COUNT(*) FROM history
             WHERE ban_at BETWEEN {start} AND {end} AND {column} IS NOT NULL{downloader} GROUP BY {column}")
}

impl SqliteStore {
    pub fn history_field_metrics(
        &self,
        field: HistoryMetricField,
        operation: HistoryMetricOperation,
        minimum_percent: f64,
        downloader: Option<&str>,
    ) -> Result<Vec<HistoryFieldMetric>, StorageError> {
        let downloader = downloader.filter(|name| !name.is_empty());
        let sql = field_sql(field, operation, downloader.map(|_| "?1"), Dialect::Sqlite)?;
        let mut statement = self.connection.prepare_cached(&sql)?;
        let rows = statement
            .query_map(rusqlite::params_from_iter(downloader), |row| {
                Ok((row.get(0)?, row.get(1)?, row.get(2)?))
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(filter_metrics(rows, minimum_percent))
    }

    pub fn history_timestamp_counts(
        &self,
        field: HistoryTimestamp,
        start_ms: i64,
        end_ms: i64,
        downloader: Option<&str>,
    ) -> Result<Vec<HistoryTimestampCount>, StorageError> {
        let sql = dates_sql(field, "?1", "?2", downloader.map(|_| "?3"), Dialect::Sqlite);
        let mut parameters: Vec<&dyn rusqlite::ToSql> = vec![&start_ms, &end_ms];
        if let Some(downloader) = &downloader {
            parameters.push(downloader);
        }
        let mut statement = self.connection.prepare_cached(&sql)?;
        Ok(statement
            .query_map(parameters.as_slice(), |row| {
                Ok(HistoryTimestampCount {
                    timestamp_ms: row.get(0)?,
                    count: row.get(1)?,
                })
            })?
            .collect::<rusqlite::Result<_>>()?)
    }
}

impl PostgresStore {
    pub async fn history_field_metrics(
        &self,
        field: HistoryMetricField,
        operation: HistoryMetricOperation,
        minimum_percent: f64,
        downloader: Option<&str>,
    ) -> Result<Vec<HistoryFieldMetric>, StorageError> {
        let downloader = downloader.filter(|name| !name.is_empty());
        let sql = field_sql(
            field,
            operation,
            downloader.map(|_| "$1"),
            Dialect::Postgres,
        )?;
        let parameters: Vec<&(dyn tokio_postgres::types::ToSql + Sync)> = downloader
            .iter()
            .map(|name| name as &(dyn tokio_postgres::types::ToSql + Sync))
            .collect();
        let rows = self
            .client
            .query(&sql, &parameters)
            .await?
            .iter()
            .map(|row| Ok((row.try_get(0)?, row.try_get(1)?, row.try_get(2)?)))
            .collect::<Result<_, StorageError>>()?;
        Ok(filter_metrics(rows, minimum_percent))
    }

    pub async fn history_timestamp_counts(
        &self,
        field: HistoryTimestamp,
        start_ms: i64,
        end_ms: i64,
        downloader: Option<&str>,
    ) -> Result<Vec<HistoryTimestampCount>, StorageError> {
        let sql = dates_sql(
            field,
            "$1",
            "$2",
            downloader.map(|_| "$3"),
            Dialect::Postgres,
        );
        let (start, end) = (to_timestamp(start_ms)?, to_timestamp(end_ms)?);
        let mut parameters: Vec<&(dyn tokio_postgres::types::ToSql + Sync)> = vec![&start, &end];
        if let Some(downloader) = &downloader {
            parameters.push(downloader);
        }
        self.client
            .query(&sql, &parameters)
            .await?
            .iter()
            .map(|row| {
                Ok(HistoryTimestampCount {
                    timestamp_ms: row.try_get(0)?,
                    count: row.try_get(1)?,
                })
            })
            .collect()
    }
}

impl MySqlStore {
    pub async fn history_field_metrics(
        &mut self,
        field: HistoryMetricField,
        operation: HistoryMetricOperation,
        minimum_percent: f64,
        downloader: Option<&str>,
    ) -> Result<Vec<HistoryFieldMetric>, StorageError> {
        let downloader = downloader.filter(|name| !name.is_empty());
        let sql = field_sql(field, operation, downloader.map(|_| "?"), Dialect::MySql)?;
        let parameters: Vec<Value> = downloader.into_iter().map(Value::from).collect();
        let rows = self.connection.exec(sql, parameters).await?;
        Ok(filter_metrics(rows, minimum_percent))
    }

    pub async fn history_timestamp_counts(
        &mut self,
        field: HistoryTimestamp,
        start_ms: i64,
        end_ms: i64,
        downloader: Option<&str>,
    ) -> Result<Vec<HistoryTimestampCount>, StorageError> {
        let sql = dates_sql(
            field,
            "TIMESTAMPADD(MICROSECOND, :start, CAST('1970-01-01 00:00:00' AS DATETIME(3)))",
            "TIMESTAMPADD(MICROSECOND, :end, CAST('1970-01-01 00:00:00' AS DATETIME(3)))",
            downloader.map(|_| ":downloader"),
            Dialect::MySql,
        );
        let (start, end) = (to_microseconds(start_ms)?, to_microseconds(end_ms)?);
        let rows: Vec<(i64, i64)> = match downloader {
            Some(name) => {
                self.connection
                    .exec(
                        sql,
                        params! {"start" => start, "end" => end, "downloader" => name},
                    )
                    .await?
            }
            None => {
                self.connection
                    .exec(sql, params! {"start" => start, "end" => end})
                    .await?
            }
        };
        Ok(rows
            .into_iter()
            .map(|(timestamp_ms, count)| HistoryTimestampCount {
                timestamp_ms,
                count,
            })
            .collect())
    }
}
