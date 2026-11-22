//! Paginated torrent discovery with stored access and ban counts.

use mysql_async::{IsolationLevel as MySqlIsolation, TxOpts, Value, prelude::Queryable};
use rusqlite::ToSql as SqliteToSql;
use tokio_postgres::{IsolationLevel as PostgresIsolation, types::ToSql as PostgresToSql};

use crate::{
    MySqlStore, PostgresStore, SqliteStore, StorageError, TorrentHistorySummary,
    history_query::page_window,
    torrent::{SUMMARY_COLUMNS, SummaryRow},
};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TorrentSearchQuery {
    page: i64,
    size: i64,
    keyword: Option<String>,
    order: TorrentOrder,
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum TorrentOrder {
    Count {
        column: &'static str,
        descending: bool,
    },
    Fields(Vec<(&'static str, bool)>),
}

impl TorrentOrder {
    fn parse(terms: &[String]) -> Result<Self, StorageError> {
        let parsed = terms.iter().map(|term| {
            let mut parts = term.split('|');
            let field = parts.next().unwrap_or_default();
            let descending = parts.next().is_some_and(|direction| {
                direction.eq_ignore_ascii_case("desc") || direction.eq_ignore_ascii_case("descend")
            });
            (field, descending)
        });
        // The Java controller uses the first count sort, even if there are
        // normal sort fields before it, and uses id descending as its only tie-break.
        if let Some(count) = parsed.clone().find_map(|(field, descending)| {
            let column = match field {
                "peerBanCount" => "peer_ban_count",
                "peerAccessCount" => "peer_access_count",
                _ => return None,
            };
            Some(Self::Count { column, descending })
        }) {
            return Ok(count);
        }

        let mut fields: Vec<(&str, bool)> = Vec::new();
        for (field, descending) in parsed {
            if let Some(existing) = fields.iter_mut().find(|(key, _)| *key == field) {
                existing.1 = descending;
            } else {
                fields.push((field, descending));
            }
        }
        if !fields.iter().any(|(field, _)| *field == "id") {
            fields.push(("id", true));
        }
        let fields = fields
            .into_iter()
            .map(|(field, descending)| {
                let column = match field.to_ascii_lowercase().as_str() {
                    "id" => "t.id",
                    "infohash" | "info_hash" => "t.info_hash",
                    "name" => "t.name",
                    "size" => "t.size",
                    "private_torrent" => "t.private_torrent",
                    _ => {
                        return Err(StorageError::InvalidHistoryQuery(format!(
                            "unsupported torrent sort field {field:?}"
                        )));
                    }
                };
                Ok((column, descending))
            })
            .collect::<Result<_, _>>()?;
        Ok(Self::Fields(fields))
    }

    fn sql(&self) -> String {
        fn term(column: &str, descending: bool) -> String {
            let direction = if descending { "DESC" } else { "ASC" };
            format!("{column} {direction}")
        }
        match self {
            Self::Count { column, descending } => {
                format!("{}, t.id DESC", term(column, *descending))
            }
            Self::Fields(fields) => fields
                .iter()
                .map(|&(column, descending)| term(column, descending))
                .collect::<Vec<_>>()
                .join(", "),
        }
    }
}

impl TorrentSearchQuery {
    pub fn new(
        page: i64,
        size: i64,
        keyword: Option<String>,
        order_by: &[String],
    ) -> Result<Self, StorageError> {
        Ok(Self {
            page,
            size,
            keyword,
            order: TorrentOrder::parse(order_by)?,
        })
    }

    fn pattern(&self) -> Option<String> {
        self.keyword.as_ref().map(|keyword| format!("%{keyword}%"))
    }

    fn filter(&self, parameter: &str) -> String {
        if self.keyword.is_some() {
            format!("WHERE (t.name LIKE {parameter} OR t.info_hash LIKE {parameter})")
        } else {
            String::new()
        }
    }

    fn count_sql(&self, parameter: &str) -> String {
        format!("SELECT COUNT(*) FROM torrents t {}", self.filter(parameter))
    }

    fn records_sql(&self, parameter: &str, limit: &str, offset: &str) -> String {
        let filter = self.filter(parameter);
        let order = self.order.sql();
        let page = format!("ORDER BY {order} LIMIT {limit} OFFSET {offset}");
        if matches!(self.order, TorrentOrder::Count { .. }) {
            format!("SELECT {SUMMARY_COLUMNS} FROM torrents t {filter} {page}")
        } else {
            // Field sorts can fetch the page first and count only its torrents.
            format!(
                "SELECT {SUMMARY_COLUMNS} FROM (SELECT t.* FROM torrents t {filter} {page}) t ORDER BY {order}"
            )
        }
    }

    fn result(
        &self,
        window: Option<(i64, i64)>,
        total: u64,
        records: Vec<TorrentHistorySummary>,
    ) -> TorrentSearchPage {
        TorrentSearchPage {
            page: self.page.max(1),
            size: window.map_or(self.size, |(size, _)| size),
            total,
            records,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TorrentSearchPage {
    pub page: i64,
    pub size: i64,
    pub total: u64,
    pub records: Vec<TorrentHistorySummary>,
}

impl SqliteStore {
    pub fn search_torrents(
        &self,
        query: &TorrentSearchQuery,
    ) -> Result<TorrentSearchPage, StorageError> {
        let pattern = query.pattern();
        let mut parameters: Vec<&dyn SqliteToSql> = pattern
            .iter()
            .map(|value| value as &dyn SqliteToSql)
            .collect();
        let transaction = self.connection.unchecked_transaction()?;
        let total = if query.size < 0 {
            0
        } else {
            transaction.query_row(&query.count_sql("?1"), parameters.as_slice(), |row| {
                row.get::<_, i64>(0)
            })? as u64
        };
        let window = page_window(query.page, query.size, total)?;
        let records = if let Some((size, offset)) = window {
            let (limit_parameter, offset_parameter) = if pattern.is_some() {
                ("?2", "?3")
            } else {
                ("?1", "?2")
            };
            parameters.extend([&size as &dyn SqliteToSql, &offset]);
            let mut statement = transaction.prepare_cached(&query.records_sql(
                "?1",
                limit_parameter,
                offset_parameter,
            ))?;
            statement
                .query_map(parameters.as_slice(), TorrentHistorySummary::read_sqlite)?
                .collect::<rusqlite::Result<_>>()?
        } else {
            Vec::new()
        };
        transaction.commit()?;
        Ok(query.result(window, total, records))
    }
}

impl PostgresStore {
    pub async fn search_torrents(
        &mut self,
        query: &TorrentSearchQuery,
    ) -> Result<TorrentSearchPage, StorageError> {
        let pattern = query.pattern();
        let mut parameters: Vec<&(dyn PostgresToSql + Sync)> = pattern
            .iter()
            .map(|value| value as &(dyn PostgresToSql + Sync))
            .collect();
        let transaction = self
            .client
            .build_transaction()
            .read_only(true)
            .isolation_level(PostgresIsolation::RepeatableRead)
            .start()
            .await?;
        let total = if query.size < 0 {
            0
        } else {
            transaction
                .query_one(&query.count_sql("$1"), &parameters)
                .await?
                .try_get::<_, i64>(0)? as u64
        };
        let window = page_window(query.page, query.size, total)?;
        let records = if let Some((size, offset)) = window {
            let (limit_parameter, offset_parameter) = if pattern.is_some() {
                ("$2", "$3")
            } else {
                ("$1", "$2")
            };
            parameters.extend([&size as &(dyn PostgresToSql + Sync), &offset]);
            transaction
                .query(
                    &query.records_sql("$1", limit_parameter, offset_parameter),
                    &parameters,
                )
                .await?
                .iter()
                .map(TorrentHistorySummary::read_postgres)
                .collect::<Result<_, _>>()?
        } else {
            Vec::new()
        };
        transaction.commit().await?;
        Ok(query.result(window, total, records))
    }
}

impl MySqlStore {
    pub async fn search_torrents(
        &mut self,
        query: &TorrentSearchQuery,
    ) -> Result<TorrentSearchPage, StorageError> {
        let mut parameters = query.pattern().map_or_else(Vec::new, |pattern| {
            vec![Value::from(&pattern), Value::from(pattern)]
        });
        let mut options = TxOpts::new();
        options
            .with_readonly(true)
            .with_isolation_level(MySqlIsolation::RepeatableRead);
        let mut transaction = self.connection.start_transaction(options).await?;
        let total = if query.size < 0 {
            0
        } else {
            transaction
                .exec_first(query.count_sql("?"), parameters.clone())
                .await?
                .unwrap_or_default()
        };
        let window = page_window(query.page, query.size, total)?;
        let records = if let Some((size, offset)) = window {
            parameters.extend([Value::from(size), Value::from(offset)]);
            let rows: Vec<SummaryRow> = transaction
                .exec(query.records_sql("?", "?", "?"), parameters)
                .await?;
            rows.into_iter().map(Into::into).collect()
        } else {
            Vec::new()
        };
        transaction.commit().await?;
        Ok(query.result(window, total, records))
    }
}
