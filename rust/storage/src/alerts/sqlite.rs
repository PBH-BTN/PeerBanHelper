use rusqlite::{TransactionBehavior, params};

use super::{
    AlertDeduplication, AlertRecord, INSERT_COLUMNS, NewAlert, decode, existing_query, unread_query,
};
use crate::{SqliteStore, StorageError};

impl SqliteStore {
    pub fn publish_alert(
        &mut self,
        alert: &NewAlert<'_>,
        deduplication: AlertDeduplication,
    ) -> Result<bool, StorageError> {
        let transaction = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        let existing = existing_query("?3", deduplication);
        let inserted = transaction.execute(
            &format!(
                "INSERT INTO alert ({INSERT_COLUMNS}) SELECT ?1, ?2, ?3, ?4, ?5
                 WHERE NOT EXISTS ({existing})"
            ),
            params![
                alert.create_at_ms,
                alert.level.as_str(),
                alert.identifier,
                alert.title.to_string(),
                alert.content.to_string(),
            ],
        )?;
        transaction.commit()?;
        Ok(inserted != 0)
    }

    pub fn alert_exists(
        &self,
        identifier: &str,
        visibility: AlertDeduplication,
    ) -> Result<bool, StorageError> {
        let existing = existing_query("?1", visibility);
        Ok(self.connection.query_row(
            &format!("SELECT EXISTS ({existing})"),
            [identifier],
            |row| row.get(0),
        )?)
    }

    pub fn unread_alerts(&self) -> Result<Vec<AlertRecord>, StorageError> {
        let mut statement = self
            .connection
            .prepare_cached(&unread_query("create_at", "read_at"))?;
        let mut rows = statement.query([])?;
        let mut alerts = Vec::new();
        while let Some(row) = rows.next()? {
            alerts.push(decode((
                row.get(0)?,
                row.get(1)?,
                row.get(2)?,
                row.get(3)?,
                row.get(4)?,
                row.get(5)?,
                row.get(6)?,
            ))?);
        }
        Ok(alerts)
    }

    pub fn mark_alert_read(&mut self, id: i64, read_at_ms: i64) -> Result<bool, StorageError> {
        Ok(self.connection.execute(
            "UPDATE alert SET read_at = ? WHERE id = ?",
            params![read_at_ms, id],
        )? != 0)
    }

    pub fn mark_alerts_read_by_identifier(
        &mut self,
        identifier: &str,
        read_at_ms: i64,
    ) -> Result<u64, StorageError> {
        Ok(self.connection.execute(
            "UPDATE alert SET read_at = ? WHERE identifier = ? AND read_at IS NULL",
            params![read_at_ms, identifier],
        )? as u64)
    }

    pub fn mark_all_alerts_read(&mut self, read_at_ms: i64) -> Result<u64, StorageError> {
        Ok(self.connection.execute(
            "UPDATE alert SET read_at = ? WHERE read_at IS NULL",
            [read_at_ms],
        )? as u64)
    }

    pub fn delete_alert(&mut self, id: i64) -> Result<bool, StorageError> {
        Ok(self
            .connection
            .execute("DELETE FROM alert WHERE id = ?", [id])?
            != 0)
    }

    pub fn cleanup_alerts(&mut self, before_ms: i64) -> Result<u64, StorageError> {
        Ok(self
            .connection
            .execute("DELETE FROM alert WHERE create_at <= ?", [before_ms])? as u64)
    }
}
