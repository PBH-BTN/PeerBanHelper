//! Paginated ban counts per address.

use mysql_async::{IsolationLevel as MySqlIsolation, TxOpts, Value, prelude::Queryable};
use rusqlite::{ToSql as SqliteToSql, params_from_iter};
use tokio_postgres::{IsolationLevel as PostgresIsolation, types::ToSql as PostgresToSql};

use crate::{MySqlStore, PostgresStore, SqliteStore, StorageError, history_query::page_window};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BanRankQuery {
    pub page: i64,
    pub size: i64,
    pub filter: Option<String>,
}

impl Default for BanRankQuery {
    fn default() -> Self {
        Self {
            page: 1,
            size: 10,
            filter: None,
        }
    }
}

impl BanRankQuery {
    fn pattern(&self) -> Option<String> {
        self.filter
            .as_deref()
            .filter(|prefix| !prefix.is_empty())
            .map(|prefix| format!("{prefix}%"))
    }

    fn result(&self, window: Option<(i64, i64)>, total: u64, records: Vec<BanRank>) -> BanRankPage {
        BanRankPage {
            page: self.page.max(1),
            size: window.map_or(self.size, |(size, _)| size),
            total,
            records,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BanRank {
    pub peer_ip: Option<String>,
    pub count: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BanRankPage {
    pub page: i64,
    pub size: i64,
    pub total: u64,
    pub records: Vec<BanRank>,
}

struct RankSql {
    from: String,
    ip_expression: &'static str,
}

impl RankSql {
    fn new(ip_expression: &'static str, parameter: Option<&str>) -> Self {
        let filter = parameter.map_or_else(String::new, |parameter| {
            format!(" WHERE {ip_expression} LIKE {parameter}")
        });
        Self {
            from: format!("FROM history{filter} GROUP BY ip"),
            ip_expression,
        }
    }

    fn count(&self) -> String {
        format!(
            "SELECT COUNT(*) FROM (SELECT ip {}) AS grouped_ranks",
            self.from
        )
    }

    fn records(&self, limit: &str, offset: &str) -> String {
        format!(
            "SELECT {}, COUNT(*) AS count {} ORDER BY count DESC LIMIT {limit} OFFSET {offset}",
            self.ip_expression, self.from,
        )
    }
}

impl SqliteStore {
    pub fn ban_ranks(&self, query: &BanRankQuery) -> Result<BanRankPage, StorageError> {
        let pattern = query.pattern();
        let sql = RankSql::new("ip", pattern.as_ref().map(|_| "?"));
        let transaction = self.connection.unchecked_transaction()?;
        let mut parameters: Vec<&dyn SqliteToSql> = pattern
            .iter()
            .map(|value| value as &dyn SqliteToSql)
            .collect();
        let total = if query.size < 0 {
            0
        } else {
            transaction.query_row(&sql.count(), params_from_iter(&parameters), |row| {
                row.get::<_, i64>(0)
            })? as u64
        };
        let window = page_window(query.page, query.size, total)?;
        let records = if let Some((size, offset)) = window {
            parameters.extend([&size as &dyn SqliteToSql, &offset]);
            let mut statement = transaction.prepare_cached(&sql.records("?", "?"))?;
            statement
                .query_map(params_from_iter(&parameters), |row| {
                    Ok(BanRank {
                        peer_ip: row.get(0)?,
                        count: row.get(1)?,
                    })
                })?
                .collect::<rusqlite::Result<_>>()?
        } else {
            Vec::new()
        };
        transaction.commit()?;
        Ok(query.result(window, total, records))
    }
}

impl PostgresStore {
    pub async fn ban_ranks(&mut self, query: &BanRankQuery) -> Result<BanRankPage, StorageError> {
        let pattern = query.pattern();
        let sql = RankSql::new("host(ip)", pattern.as_ref().map(|_| "$1"));
        let transaction = self
            .client
            .build_transaction()
            .read_only(true)
            .isolation_level(PostgresIsolation::RepeatableRead)
            .start()
            .await?;
        let mut parameters: Vec<&(dyn PostgresToSql + Sync)> = pattern
            .iter()
            .map(|value| value as &(dyn PostgresToSql + Sync))
            .collect();
        let total = if query.size < 0 {
            0
        } else {
            transaction
                .query_one(&sql.count(), &parameters)
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
                .query(&sql.records(limit_parameter, offset_parameter), &parameters)
                .await?
                .iter()
                .map(|row| {
                    Ok(BanRank {
                        peer_ip: row.try_get(0)?,
                        count: row.try_get(1)?,
                    })
                })
                .collect::<Result<_, StorageError>>()?
        } else {
            Vec::new()
        };
        transaction.commit().await?;
        Ok(query.result(window, total, records))
    }
}

impl MySqlStore {
    pub async fn ban_ranks(&mut self, query: &BanRankQuery) -> Result<BanRankPage, StorageError> {
        let pattern = query.pattern();
        let sql = RankSql::new("ip", pattern.as_ref().map(|_| "?"));
        let mut options = TxOpts::new();
        options
            .with_readonly(true)
            .with_isolation_level(MySqlIsolation::RepeatableRead);
        let mut transaction = self.connection.start_transaction(options).await?;
        let mut parameters: Vec<Value> = pattern.into_iter().map(Value::from).collect();
        let total = if query.size < 0 {
            0
        } else {
            transaction
                .exec_first(sql.count(), parameters.clone())
                .await?
                .unwrap_or_default()
        };
        let window = page_window(query.page, query.size, total)?;
        let records = if let Some((size, offset)) = window {
            parameters.extend([Value::from(size), Value::from(offset)]);
            let rows: Vec<(Option<String>, i64)> =
                transaction.exec(sql.records("?", "?"), parameters).await?;
            rows.into_iter()
                .map(|(peer_ip, count)| BanRank { peer_ip, count })
                .collect()
        } else {
            Vec::new()
        };
        transaction.commit().await?;
        Ok(query.result(window, total, records))
    }
}
