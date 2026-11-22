//! Read-only peer, session and client aggregation for the protected charts.

use mysql_async::prelude::{FromRow, Queryable};
use tokio_postgres::{Row, types::ToSql};

use crate::metric_charts::Dialect;
use crate::mysql::to_microseconds;
use crate::postgres::to_timestamp;
use crate::{HistoryTimestampCount, MySqlStore, PostgresStore, SqliteStore, StorageError};

#[derive(Debug, Clone, Copy)]
pub struct ChartRange<'a> {
    pub start_ms: i64,
    pub end_ms: i64,
    pub downloader: Option<&'a str>,
}

#[derive(Debug, Clone, Copy)]
pub enum ChartAddressSource {
    BanHistory,
    PeerHistory,
}

#[derive(Debug, PartialEq, Eq)]
pub struct SessionSpan {
    pub first_seen_ms: i64,
    pub last_seen_ms: i64,
    pub flags: Option<String>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct ClientAnalysisRow {
    pub peer_id: Option<String>,
    pub client_name: Option<String>,
    pub uploaded: i64,
    pub downloaded: i64,
    pub count: i64,
}

#[derive(Debug, PartialEq, Eq)]
pub struct ClientAnalysisPage {
    pub total: i64,
    pub rows: Vec<ClientAnalysisRow>,
}

#[derive(Debug, Clone, Copy)]
pub struct ClientAnalysisOrder {
    column: &'static str,
    descending: bool,
}

impl ClientAnalysisOrder {
    pub fn parse(order: &str) -> Result<Self, StorageError> {
        let mut parts = order.split('|');
        let field = parts.next().unwrap_or_default();
        let column = match field {
            "peerId" | "peer_id" => "peer_id",
            "clientName" | "client_name" => "client_name",
            "uploaded" => "uploaded",
            "downloaded" => "downloaded",
            "ct" | "count" => "ct",
            _ => {
                return Err(StorageError::InvalidHistoryQuery(format!(
                    "unsupported client analysis sort field {field:?}"
                )));
            }
        };
        let descending = parts.next().is_some_and(|direction| {
            direction.eq_ignore_ascii_case("desc") || direction.eq_ignore_ascii_case("descend")
        });
        Ok(Self { column, descending })
    }

    fn order_clause(orders: &[Self]) -> String {
        let mut selected: Vec<Self> = Vec::new();
        for &order in orders {
            if let Some(previous) = selected.iter_mut().find(|item| item.column == order.column) {
                *previous = order;
            } else {
                selected.push(order);
            }
        }
        for (column, descending) in [
            ("uploaded", true),
            ("peer_id", false),
            ("client_name", false),
        ] {
            if !selected.iter().any(|order| order.column == column) {
                selected.push(Self { column, descending });
            }
        }
        selected
            .iter()
            .map(|order| {
                let direction = if order.descending { "DESC" } else { "ASC" };
                format!("{} {direction}", order.column)
            })
            .collect::<Vec<_>>()
            .join(", ")
    }
}

#[derive(Clone, Copy)]
enum Read<'a> {
    PeerTimestamps,
    Addresses(ChartAddressSource),
    SessionCount,
    SessionSpans,
    ClientCount,
    ClientRows {
        size: i64,
        offset: i64,
        orders: &'a [ClientAnalysisOrder],
    },
}

impl Read<'_> {
    fn sql(self, dialect: Dialect, has_downloader: bool) -> String {
        let (start, end, downloader) = match dialect {
            Dialect::Sqlite => ("?1", "?2", "?3"),
            Dialect::Postgres => ("$1", "$2", "$3"),
            Dialect::MySql => (
                "TIMESTAMPADD(MICROSECOND, ?, CAST('1970-01-01 00:00:00' AS DATETIME(3)))",
                "TIMESTAMPADD(MICROSECOND, ?, CAST('1970-01-01 00:00:00' AS DATETIME(3)))",
                "?",
            ),
        };
        let filter = if has_downloader {
            format!(" AND downloader = {downloader}")
        } else {
            String::new()
        };
        let containing = format!("first_time_seen >= {start} AND last_time_seen <= {end}{filter}");
        match self {
            Self::PeerTimestamps => {
                let stamp = dialect.timestamp_ms("last_time_seen");
                format!(
                    "SELECT {stamp}, COUNT(*) FROM peer_records WHERE last_time_seen BETWEEN {start} AND {end}{filter} GROUP BY last_time_seen"
                )
            }
            Self::Addresses(source) => {
                let (table, address, date) = match source {
                    ChartAddressSource::BanHistory => ("history", "ip", "ban_at"),
                    ChartAddressSource::PeerHistory => {
                        ("peer_records", "address", "last_time_seen")
                    }
                };
                let text = match dialect {
                    Dialect::Postgres => format!("{address}::text"),
                    _ => address.to_owned(),
                };
                format!(
                    "SELECT DISTINCT {text} FROM {table} WHERE {date} BETWEEN {start} AND {end} AND {address} IS NOT NULL{filter}"
                )
            }
            Self::SessionCount => format!(
                "SELECT COUNT(DISTINCT address) FROM peer_records WHERE last_time_seen >= {start} AND first_time_seen <= {end}{filter}"
            ),
            Self::SessionSpans => {
                let first = dialect.timestamp_ms("first_time_seen");
                let last = dialect.timestamp_ms("last_time_seen");
                format!("SELECT {first}, {last}, last_flags FROM peer_records WHERE {containing}")
            }
            Self::ClientCount => format!(
                "SELECT COUNT(*) FROM (SELECT 1 FROM peer_records WHERE {containing} AND uploaded > 0 AND downloaded > 0 GROUP BY peer_id, client_name) clients"
            ),
            Self::ClientRows {
                size,
                offset,
                orders,
            } => {
                let uploaded = cast_sum("uploaded", dialect);
                let downloaded = cast_sum("downloaded", dialect);
                let order = ClientAnalysisOrder::order_clause(orders);
                format!(
                    "SELECT peer_id, client_name, {uploaded} AS uploaded, {downloaded} AS downloaded, COUNT(*) AS ct \
                     FROM peer_records WHERE {containing} AND peer_records.uploaded > 0 AND peer_records.downloaded > 0 \
                     GROUP BY peer_id, client_name ORDER BY {order} LIMIT {size} OFFSET {offset}"
                )
            }
        }
    }
}

fn cast_sum(column: &str, dialect: Dialect) -> String {
    let bigint = match dialect {
        Dialect::Sqlite => "INTEGER",
        Dialect::Postgres => "BIGINT",
        Dialect::MySql => "SIGNED",
    };
    format!("CAST(SUM({column}) AS {bigint})")
}

fn client_query(
    page: i64,
    size: i64,
    orders: &[ClientAnalysisOrder],
) -> Result<Read<'_>, StorageError> {
    let offset = page.checked_sub(1).and_then(|page| page.checked_mul(size));
    match offset.filter(|offset| *offset >= 0 && page > 0 && size > 0) {
        Some(offset) => Ok(Read::ClientRows {
            size,
            offset,
            orders,
        }),
        None => Err(StorageError::InvalidHistoryQuery(
            "invalid client analysis pagination".into(),
        )),
    }
}

fn time_count((timestamp_ms, count): (i64, i64)) -> HistoryTimestampCount {
    HistoryTimestampCount {
        timestamp_ms,
        count,
    }
}

fn session_span((first_seen_ms, last_seen_ms, flags): (i64, i64, Option<String>)) -> SessionSpan {
    SessionSpan {
        first_seen_ms,
        last_seen_ms,
        flags,
    }
}

type ClientFields = (Option<String>, Option<String>, i64, i64, i64);

fn client_row(
    (peer_id, client_name, uploaded, downloaded, count): ClientFields,
) -> ClientAnalysisRow {
    ClientAnalysisRow {
        peer_id,
        client_name,
        uploaded,
        downloaded,
        count,
    }
}

impl SqliteStore {
    fn peer_chart_rows<T>(
        &self,
        range: ChartRange<'_>,
        read: Read<'_>,
        map: impl FnMut(&rusqlite::Row<'_>) -> rusqlite::Result<T>,
    ) -> Result<Vec<T>, StorageError> {
        let mut parameters: Vec<&dyn rusqlite::ToSql> = vec![&range.start_ms, &range.end_ms];
        if let Some(downloader) = &range.downloader {
            parameters.push(downloader);
        }
        let mut statement = self
            .connection
            .prepare_cached(&read.sql(Dialect::Sqlite, range.downloader.is_some()))?;
        Ok(statement
            .query_map(parameters.as_slice(), map)?
            .collect::<rusqlite::Result<_>>()?)
    }

    pub fn peer_chart_timestamps(
        &self,
        range: ChartRange<'_>,
    ) -> Result<Vec<HistoryTimestampCount>, StorageError> {
        self.peer_chart_rows(range, Read::PeerTimestamps, |row| {
            Ok(time_count((row.get(0)?, row.get(1)?)))
        })
    }

    pub fn chart_addresses(
        &self,
        range: ChartRange<'_>,
        source: ChartAddressSource,
    ) -> Result<Vec<String>, StorageError> {
        self.peer_chart_rows(range, Read::Addresses(source), |row| row.get(0))
    }

    pub fn session_address_count(&self, range: ChartRange<'_>) -> Result<i64, StorageError> {
        Ok(self.peer_chart_rows(range, Read::SessionCount, |row| row.get(0))?[0])
    }

    pub fn session_spans(&self, range: ChartRange<'_>) -> Result<Vec<SessionSpan>, StorageError> {
        self.peer_chart_rows(range, Read::SessionSpans, |row| {
            Ok(session_span((row.get(0)?, row.get(1)?, row.get(2)?)))
        })
    }

    pub fn client_analysis(
        &self,
        range: ChartRange<'_>,
        page: i64,
        size: i64,
        orders: &[ClientAnalysisOrder],
    ) -> Result<ClientAnalysisPage, StorageError> {
        let read = client_query(page, size, orders)?;
        let total = self.peer_chart_rows(range, Read::ClientCount, |row| row.get(0))?[0];
        let rows = self.peer_chart_rows(range, read, |row| {
            Ok(client_row((
                row.get(0)?,
                row.get(1)?,
                row.get(2)?,
                row.get(3)?,
                row.get(4)?,
            )))
        })?;
        Ok(ClientAnalysisPage { total, rows })
    }
}

impl PostgresStore {
    async fn peer_chart_rows(
        &self,
        range: ChartRange<'_>,
        read: Read<'_>,
    ) -> Result<Vec<Row>, StorageError> {
        let (start, end) = (to_timestamp(range.start_ms)?, to_timestamp(range.end_ms)?);
        let mut parameters: Vec<&(dyn ToSql + Sync)> = vec![&start, &end];
        if let Some(downloader) = &range.downloader {
            parameters.push(downloader);
        }
        Ok(self
            .client
            .query(
                &read.sql(Dialect::Postgres, range.downloader.is_some()),
                &parameters,
            )
            .await?)
    }

    pub async fn peer_chart_timestamps(
        &self,
        range: ChartRange<'_>,
    ) -> Result<Vec<HistoryTimestampCount>, StorageError> {
        self.peer_chart_rows(range, Read::PeerTimestamps)
            .await?
            .iter()
            .map(|row| Ok(time_count((row.try_get(0)?, row.try_get(1)?))))
            .collect()
    }

    pub async fn chart_addresses(
        &self,
        range: ChartRange<'_>,
        source: ChartAddressSource,
    ) -> Result<Vec<String>, StorageError> {
        self.peer_chart_rows(range, Read::Addresses(source))
            .await?
            .iter()
            .map(|row| Ok(row.try_get(0)?))
            .collect()
    }

    pub async fn session_address_count(&self, range: ChartRange<'_>) -> Result<i64, StorageError> {
        Ok(self.peer_chart_rows(range, Read::SessionCount).await?[0].try_get(0)?)
    }

    pub async fn session_spans(
        &self,
        range: ChartRange<'_>,
    ) -> Result<Vec<SessionSpan>, StorageError> {
        self.peer_chart_rows(range, Read::SessionSpans)
            .await?
            .iter()
            .map(|row| {
                Ok(session_span((
                    row.try_get(0)?,
                    row.try_get(1)?,
                    row.try_get(2)?,
                )))
            })
            .collect()
    }

    pub async fn client_analysis(
        &self,
        range: ChartRange<'_>,
        page: i64,
        size: i64,
        orders: &[ClientAnalysisOrder],
    ) -> Result<ClientAnalysisPage, StorageError> {
        let read = client_query(page, size, orders)?;
        let total = self.peer_chart_rows(range, Read::ClientCount).await?[0].try_get(0)?;
        let rows = self
            .peer_chart_rows(range, read)
            .await?
            .iter()
            .map(|row| {
                Ok(client_row((
                    row.try_get(0)?,
                    row.try_get(1)?,
                    row.try_get(2)?,
                    row.try_get(3)?,
                    row.try_get(4)?,
                )))
            })
            .collect::<Result<_, StorageError>>()?;
        Ok(ClientAnalysisPage { total, rows })
    }
}

impl MySqlStore {
    async fn peer_chart_rows<T: FromRow + Send + 'static>(
        &mut self,
        range: ChartRange<'_>,
        read: Read<'_>,
    ) -> Result<Vec<T>, StorageError> {
        let mut parameters = vec![
            to_microseconds(range.start_ms)?.into(),
            to_microseconds(range.end_ms)?.into(),
        ];
        if let Some(downloader) = range.downloader {
            parameters.push(downloader.into());
        }
        Ok(self
            .connection
            .exec(
                read.sql(Dialect::MySql, range.downloader.is_some()),
                mysql_async::Params::Positional(parameters),
            )
            .await?)
    }

    pub async fn peer_chart_timestamps(
        &mut self,
        range: ChartRange<'_>,
    ) -> Result<Vec<HistoryTimestampCount>, StorageError> {
        Ok(self
            .peer_chart_rows(range, Read::PeerTimestamps)
            .await?
            .into_iter()
            .map(time_count)
            .collect())
    }

    pub async fn chart_addresses(
        &mut self,
        range: ChartRange<'_>,
        source: ChartAddressSource,
    ) -> Result<Vec<String>, StorageError> {
        self.peer_chart_rows(range, Read::Addresses(source)).await
    }

    pub async fn session_address_count(
        &mut self,
        range: ChartRange<'_>,
    ) -> Result<i64, StorageError> {
        Ok(self.peer_chart_rows(range, Read::SessionCount).await?[0])
    }

    pub async fn session_spans(
        &mut self,
        range: ChartRange<'_>,
    ) -> Result<Vec<SessionSpan>, StorageError> {
        Ok(self
            .peer_chart_rows(range, Read::SessionSpans)
            .await?
            .into_iter()
            .map(session_span)
            .collect())
    }

    pub async fn client_analysis(
        &mut self,
        range: ChartRange<'_>,
        page: i64,
        size: i64,
        orders: &[ClientAnalysisOrder],
    ) -> Result<ClientAnalysisPage, StorageError> {
        let read = client_query(page, size, orders)?;
        let total = self.peer_chart_rows(range, Read::ClientCount).await?[0];
        let rows = self
            .peer_chart_rows(range, read)
            .await?
            .into_iter()
            .map(client_row)
            .collect();
        Ok(ClientAnalysisPage { total, rows })
    }
}
