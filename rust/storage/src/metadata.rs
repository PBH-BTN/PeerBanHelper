//! Compatible storage for Java MetadataService keys and values.

use std::collections::BTreeMap;

use mysql_async::{IsolationLevel as MySqlIsolation, TxOpts, prelude::Queryable};
use rusqlite::{OptionalExtension, TransactionBehavior, params};
use tokio_postgres::IsolationLevel as PostgresIsolation;

use crate::{MySqlStore, PostgresStore, SqliteStore, StorageError};

type Metadata = BTreeMap<String, Option<String>>;

impl SqliteStore {
    /// Reads a consistent snapshot. Missing keys are omitted and SQL NULL
    /// values are kept, matching `MetadataService.getOrDefault` semantics.
    pub fn read_metadata(&self, keys: &[&str]) -> Result<Metadata, StorageError> {
        let transaction = self.connection.unchecked_transaction()?;
        let mut result = BTreeMap::new();
        {
            let mut statement =
                transaction.prepare_cached("SELECT v FROM metadata WHERE k = ?1")?;
            for key in keys {
                if let Some(value) = statement.query_row([key], |row| row.get(0)).optional()? {
                    result.insert((*key).to_owned(), value);
                }
            }
        }
        transaction.commit()?;
        Ok(result)
    }

    /// Atomically merges keys without replacing unrelated metadata rows.
    pub fn write_metadata(&mut self, entries: &[(&str, Option<&str>)]) -> Result<(), StorageError> {
        if entries.is_empty() {
            return Ok(());
        }
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        {
            let mut statement = transaction.prepare_cached(
                "INSERT INTO metadata (k, v) VALUES (?1, ?2) ON CONFLICT (k) DO UPDATE SET v = excluded.v",
            )?;
            for (key, value) in ordered(entries) {
                statement.execute(params![key, value])?;
            }
        }
        transaction.commit()?;
        Ok(())
    }
}

impl PostgresStore {
    /// Reads a consistent snapshot while distinguishing absent keys from SQL NULL.
    pub async fn read_metadata(&mut self, keys: &[&str]) -> Result<Metadata, StorageError> {
        let transaction = self
            .client
            .build_transaction()
            .isolation_level(PostgresIsolation::RepeatableRead)
            .read_only(true)
            .start()
            .await?;
        let statement = transaction
            .prepare("SELECT v FROM metadata WHERE k = $1")
            .await?;
        let mut result = BTreeMap::new();
        for key in keys {
            if let Some(row) = transaction.query_opt(&statement, &[key]).await? {
                result.insert((*key).to_owned(), row.try_get(0)?);
            }
        }
        transaction.commit().await?;
        Ok(result)
    }

    pub async fn write_metadata(
        &mut self,
        entries: &[(&str, Option<&str>)],
    ) -> Result<(), StorageError> {
        if entries.is_empty() {
            return Ok(());
        }
        let transaction = self.client.transaction().await?;
        let statement = transaction.prepare(
            "INSERT INTO metadata (k, v) VALUES ($1, $2) ON CONFLICT (k) DO UPDATE SET v = excluded.v",
        ).await?;
        for (key, value) in ordered(entries) {
            transaction.execute(&statement, &[&key, &value]).await?;
        }
        transaction.commit().await?;
        Ok(())
    }
}

impl MySqlStore {
    /// Reads a consistent snapshot while distinguishing absent keys from SQL NULL.
    pub async fn read_metadata(&mut self, keys: &[&str]) -> Result<Metadata, StorageError> {
        let mut options = TxOpts::new();
        options
            .with_isolation_level(MySqlIsolation::RepeatableRead)
            .with_readonly(true);
        let mut transaction = self.connection.start_transaction(options).await?;
        let statement = transaction
            .prep("SELECT v FROM metadata WHERE k = ?")
            .await?;
        let mut result = BTreeMap::new();
        for key in keys {
            let value: Option<(Option<String>,)> =
                transaction.exec_first(&statement, (*key,)).await?;
            if let Some((value,)) = value {
                result.insert((*key).to_owned(), value);
            }
        }
        transaction.commit().await?;
        Ok(result)
    }

    pub async fn write_metadata(
        &mut self,
        entries: &[(&str, Option<&str>)],
    ) -> Result<(), StorageError> {
        if entries.is_empty() {
            return Ok(());
        }
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        let statement = transaction
            .prep("INSERT INTO metadata (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)")
            .await?;
        for (key, value) in ordered(entries) {
            transaction.exec_drop(&statement, (key, value)).await?;
        }
        transaction.commit().await?;
        Ok(())
    }
}

// A consistent lock order across writers avoids swapping the cache payload
// and version locks. The caller's last update wins for a repeated key.
fn ordered<'a>(entries: &[(&'a str, Option<&'a str>)]) -> BTreeMap<&'a str, Option<&'a str>> {
    entries.iter().copied().collect()
}
