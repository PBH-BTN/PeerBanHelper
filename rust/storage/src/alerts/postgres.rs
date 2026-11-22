use tokio_postgres::IsolationLevel;

use super::{
    AlertDeduplication, AlertRecord, INSERT_COLUMNS, NewAlert, decode, existing_query, unread_query,
};
use crate::{PostgresStore, StorageError, postgres::to_timestamp};

impl PostgresStore {
    pub async fn publish_alert(
        &mut self,
        alert: &NewAlert<'_>,
        deduplication: AlertDeduplication,
    ) -> Result<bool, StorageError> {
        let transaction = self
            .client
            .build_transaction()
            .isolation_level(IsolationLevel::ReadCommitted)
            .start()
            .await?;
        // Read the latest committed rows after this lock serializes publications.
        transaction
            .batch_execute("LOCK TABLE alert IN SHARE ROW EXCLUSIVE MODE")
            .await?;
        let existing = existing_query("$3::varchar", deduplication);
        let inserted = transaction
            .execute(
                &format!(
                    "INSERT INTO alert ({INSERT_COLUMNS}) SELECT $1, $2, $3::varchar, $4, $5
                 WHERE NOT EXISTS ({existing})"
                ),
                &[
                    &to_timestamp(alert.create_at_ms)?,
                    &alert.level.as_str(),
                    &alert.identifier,
                    &alert.title.to_string(),
                    &alert.content.to_string(),
                ],
            )
            .await?;
        transaction.commit().await?;
        Ok(inserted != 0)
    }

    pub async fn alert_exists(
        &self,
        identifier: &str,
        visibility: AlertDeduplication,
    ) -> Result<bool, StorageError> {
        let existing = existing_query("$1", visibility);
        Ok(self
            .client
            .query_one(&format!("SELECT EXISTS ({existing})"), &[&identifier])
            .await?
            .try_get(0)?)
    }

    pub async fn unread_alerts(&self) -> Result<Vec<AlertRecord>, StorageError> {
        let sql = unread_query(
            "FLOOR(EXTRACT(EPOCH FROM create_at) * 1000)::bigint",
            "FLOOR(EXTRACT(EPOCH FROM read_at) * 1000)::bigint",
        );
        self.client
            .query(&sql, &[])
            .await?
            .iter()
            .map(|row| {
                decode((
                    row.try_get(0)?,
                    row.try_get(1)?,
                    row.try_get(2)?,
                    row.try_get(3)?,
                    row.try_get(4)?,
                    row.try_get(5)?,
                    row.try_get(6)?,
                ))
            })
            .collect()
    }

    pub async fn mark_alert_read(
        &mut self,
        id: i64,
        read_at_ms: i64,
    ) -> Result<bool, StorageError> {
        Ok(self
            .client
            .execute(
                "UPDATE alert SET read_at = $1 WHERE id = $2",
                &[&to_timestamp(read_at_ms)?, &id],
            )
            .await?
            != 0)
    }

    pub async fn mark_alerts_read_by_identifier(
        &mut self,
        identifier: &str,
        read_at_ms: i64,
    ) -> Result<u64, StorageError> {
        Ok(self
            .client
            .execute(
                "UPDATE alert SET read_at = $1 WHERE identifier = $2 AND read_at IS NULL",
                &[&to_timestamp(read_at_ms)?, &identifier],
            )
            .await?)
    }

    pub async fn mark_all_alerts_read(&mut self, read_at_ms: i64) -> Result<u64, StorageError> {
        Ok(self
            .client
            .execute(
                "UPDATE alert SET read_at = $1 WHERE read_at IS NULL",
                &[&to_timestamp(read_at_ms)?],
            )
            .await?)
    }

    pub async fn delete_alert(&mut self, id: i64) -> Result<bool, StorageError> {
        Ok(self
            .client
            .execute("DELETE FROM alert WHERE id = $1", &[&id])
            .await?
            != 0)
    }

    pub async fn cleanup_alerts(&mut self, before_ms: i64) -> Result<u64, StorageError> {
        Ok(self
            .client
            .execute(
                "DELETE FROM alert WHERE create_at <= $1",
                &[&to_timestamp(before_ms)?],
            )
            .await?)
    }
}
