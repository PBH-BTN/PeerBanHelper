//! Subscription update records stored in the shared Java schema.

use mysql_async::{IsolationLevel as MySqlIsolation, TxOpts, Value, prelude::Queryable};
use rusqlite::{OptionalExtension, ToSql as SqliteToSql, params};
use tokio_postgres::{IsolationLevel as PostgresIsolation, types::ToSql as PostgresToSql};

use crate::{
    MySqlStore, PostgresStore, SqliteStore, StorageError, history_query::page_window,
    mysql::to_microseconds, postgres::to_timestamp,
};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SubscriptionLog {
    pub id: i64,
    pub rule_id: String,
    pub update_ms: i64,
    pub count: i32,
    pub manual: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SubscriptionLogPage {
    pub page: i64,
    pub size: i64,
    pub total: u64,
    pub records: Vec<SubscriptionLog>,
}

struct LogPageRequest {
    page: i64,
    size: i64,
}

impl LogPageRequest {
    fn window(&self, total: u64) -> Result<Option<(i64, i64)>, StorageError> {
        page_window(self.page, self.size, total)
    }

    fn result(
        &self,
        window: Option<(i64, i64)>,
        total: u64,
        records: Vec<SubscriptionLog>,
    ) -> SubscriptionLogPage {
        SubscriptionLogPage {
            page: self.page.max(1),
            size: window.map_or(self.size, |(size, _)| size),
            total,
            records,
        }
    }
}

type LogRow = (i64, String, i64, i32, String);

const COLUMNS: &str = "rule_id, update_time, count, update_type";
const MYSQL_TIME: &str =
    "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', update_time) / 1000) AS SIGNED)";
const POSTGRES_TIME: &str = "FLOOR(EXTRACT(EPOCH FROM update_time) * 1000)::bigint";

fn update_type(manual: bool) -> &'static str {
    if manual { "MANUAL" } else { "AUTO" }
}

fn decode(row: LogRow) -> Result<SubscriptionLog, StorageError> {
    let (id, rule_id, update_ms, count, update_type) = row;
    let manual = match update_type.as_str() {
        "MANUAL" => true,
        "AUTO" => false,
        _ => {
            return Err(StorageError::UnsupportedSchema(format!(
                "invalid subscription update type {update_type:?} for log {id}"
            )));
        }
    };
    Ok(SubscriptionLog {
        id,
        rule_id,
        update_ms,
        count,
        manual,
    })
}

fn filter(parameter: Option<&str>) -> String {
    parameter.map_or_else(String::new, |parameter| {
        format!(" WHERE rule_id = {parameter}")
    })
}

fn select(timestamp: &str, filter: &str) -> String {
    format!("SELECT id, rule_id, {timestamp}, count, update_type FROM rule_sub_log{filter}")
}

fn read_sqlite(row: &rusqlite::Row<'_>) -> rusqlite::Result<LogRow> {
    Ok((
        row.get(0)?,
        row.get(1)?,
        row.get(2)?,
        row.get(3)?,
        row.get(4)?,
    ))
}

fn read_postgres(row: &tokio_postgres::Row) -> Result<SubscriptionLog, StorageError> {
    decode((
        row.try_get(0)?,
        row.try_get(1)?,
        row.try_get(2)?,
        row.try_get(3)?,
        row.try_get(4)?,
    ))
}

impl SqliteStore {
    pub fn append_subscription_log(
        &mut self,
        rule_id: &str,
        update_ms: i64,
        count: i32,
        manual: bool,
    ) -> Result<(), StorageError> {
        self.connection.execute(
            &format!("INSERT INTO rule_sub_log ({COLUMNS}) VALUES (?, ?, ?, ?)"),
            params![rule_id, update_ms, count, update_type(manual)],
        )?;
        Ok(())
    }

    pub fn latest_subscription_log(
        &self,
        rule_id: &str,
    ) -> Result<Option<SubscriptionLog>, StorageError> {
        let sql = select("update_time", " WHERE rule_id = ?");
        self.connection
            .query_row(
                &format!("{sql} ORDER BY id DESC LIMIT 1"),
                [rule_id],
                read_sqlite,
            )
            .optional()?
            .map(decode)
            .transpose()
    }

    pub fn subscription_logs(
        &self,
        rule_id: Option<&str>,
        page: i64,
        size: i64,
    ) -> Result<SubscriptionLogPage, StorageError> {
        let request = LogPageRequest { page, size };
        let filter = filter(rule_id.map(|_| "?"));
        let transaction = self.connection.unchecked_transaction()?;
        let mut parameters: Vec<&dyn SqliteToSql> = rule_id
            .iter()
            .map(|name| name as &dyn SqliteToSql)
            .collect();
        let total = if size < 0 {
            0
        } else {
            transaction.query_row(
                &format!("SELECT COUNT(*) FROM rule_sub_log{filter}"),
                parameters.as_slice(),
                |row| row.get::<_, i64>(0),
            )? as u64
        };
        let window = request.window(total)?;
        let records = if let Some((size, offset)) = window {
            let sql = select("update_time", &filter);
            parameters.extend([&size as &dyn SqliteToSql, &offset]);
            let mut statement = transaction.prepare_cached(&format!(
                "{sql} ORDER BY update_time DESC, id DESC LIMIT ? OFFSET ?"
            ))?;
            statement
                .query_map(parameters.as_slice(), read_sqlite)?
                .map(|row| decode(row?))
                .collect::<Result<_, _>>()?
        } else {
            Vec::new()
        };
        transaction.commit()?;
        Ok(request.result(window, total, records))
    }
}

impl PostgresStore {
    pub async fn append_subscription_log(
        &mut self,
        rule_id: &str,
        update_ms: i64,
        count: i32,
        manual: bool,
    ) -> Result<(), StorageError> {
        self.client
            .execute(
                &format!("INSERT INTO rule_sub_log ({COLUMNS}) VALUES ($1, $2, $3, $4)"),
                &[
                    &rule_id,
                    &to_timestamp(update_ms)?,
                    &count,
                    &update_type(manual),
                ],
            )
            .await?;
        Ok(())
    }

    pub async fn latest_subscription_log(
        &self,
        rule_id: &str,
    ) -> Result<Option<SubscriptionLog>, StorageError> {
        let sql = select(POSTGRES_TIME, " WHERE rule_id = $1");
        self.client
            .query_opt(&format!("{sql} ORDER BY id DESC LIMIT 1"), &[&rule_id])
            .await?
            .as_ref()
            .map(read_postgres)
            .transpose()
    }

    pub async fn subscription_logs(
        &mut self,
        rule_id: Option<&str>,
        page: i64,
        size: i64,
    ) -> Result<SubscriptionLogPage, StorageError> {
        let request = LogPageRequest { page, size };
        let filter = filter(rule_id.map(|_| "$1"));
        let transaction = self
            .client
            .build_transaction()
            .read_only(true)
            .isolation_level(PostgresIsolation::RepeatableRead)
            .start()
            .await?;
        let mut parameters: Vec<&(dyn PostgresToSql + Sync)> = rule_id
            .iter()
            .map(|name| name as &(dyn PostgresToSql + Sync))
            .collect();
        let total = if size < 0 {
            0
        } else {
            transaction
                .query_one(
                    &format!("SELECT COUNT(*) FROM rule_sub_log{filter}"),
                    &parameters,
                )
                .await?
                .try_get::<_, i64>(0)? as u64
        };
        let window = request.window(total)?;
        let records = if let Some((size, offset)) = window {
            let sql = select(POSTGRES_TIME, &filter);
            let (limit, offset_param) = if rule_id.is_some() {
                ("$2", "$3")
            } else {
                ("$1", "$2")
            };
            parameters.extend([&size as &(dyn PostgresToSql + Sync), &offset]);
            transaction.query(&format!("{sql} ORDER BY update_time DESC, id DESC LIMIT {limit} OFFSET {offset_param}"), &parameters)
                .await?.iter().map(read_postgres).collect::<Result<_, _>>()?
        } else {
            Vec::new()
        };
        transaction.commit().await?;
        Ok(request.result(window, total, records))
    }
}

impl MySqlStore {
    pub async fn append_subscription_log(
        &mut self,
        rule_id: &str,
        update_ms: i64,
        count: i32,
        manual: bool,
    ) -> Result<(), StorageError> {
        self.connection
            .exec_drop(
                format!(
                    "INSERT INTO rule_sub_log ({COLUMNS}) VALUES (
            ?, TIMESTAMPADD(MICROSECOND, ?, CAST('1970-01-01 00:00:00' AS DATETIME(3))), ?, ?)"
                ),
                (
                    rule_id,
                    to_microseconds(update_ms)?,
                    count,
                    update_type(manual),
                ),
            )
            .await?;
        Ok(())
    }

    pub async fn latest_subscription_log(
        &mut self,
        rule_id: &str,
    ) -> Result<Option<SubscriptionLog>, StorageError> {
        let sql = select(MYSQL_TIME, " WHERE rule_id = ?");
        let row: Option<LogRow> = self
            .connection
            .exec_first(format!("{sql} ORDER BY id DESC LIMIT 1"), (rule_id,))
            .await?;
        row.map(decode).transpose()
    }

    pub async fn subscription_logs(
        &mut self,
        rule_id: Option<&str>,
        page: i64,
        size: i64,
    ) -> Result<SubscriptionLogPage, StorageError> {
        let request = LogPageRequest { page, size };
        let filter = filter(rule_id.map(|_| "?"));
        let mut options = TxOpts::new();
        options
            .with_readonly(true)
            .with_isolation_level(MySqlIsolation::RepeatableRead);
        let mut transaction = self.connection.start_transaction(options).await?;
        let mut parameters: Vec<Value> = rule_id.into_iter().map(Value::from).collect();
        let total = if size < 0 {
            0
        } else {
            transaction
                .exec_first(
                    format!("SELECT COUNT(*) FROM rule_sub_log{filter}"),
                    parameters.clone(),
                )
                .await?
                .unwrap_or_default()
        };
        let window = request.window(total)?;
        let records = if let Some((size, offset)) = window {
            let sql = select(MYSQL_TIME, &filter);
            parameters.extend([Value::from(size), Value::from(offset)]);
            let rows: Vec<LogRow> = transaction
                .exec(
                    format!("{sql} ORDER BY update_time DESC, id DESC LIMIT ? OFFSET ?"),
                    parameters,
                )
                .await?;
            rows.into_iter().map(decode).collect::<Result<_, _>>()?
        } else {
            Vec::new()
        };
        transaction.commit().await?;
        Ok(request.result(window, total, records))
    }
}
