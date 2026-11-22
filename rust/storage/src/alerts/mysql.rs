use mysql_async::{TxOpts, params, prelude::Queryable};

use super::{
    AlertDeduplication, AlertRecord, AlertRow, INSERT_COLUMNS, NewAlert, decode, existing_query,
    unread_query,
};
use crate::{MySqlStore, StorageError, mysql::to_microseconds};

const LOCK_NAME: &str = "CONCAT('peerbanhelper-rust-alert:', MD5(DATABASE()))";
const AT_TIME: &str =
    "TIMESTAMPADD(MICROSECOND, :time, CAST('1970-01-01 00:00:00' AS DATETIME(3)))";

impl MySqlStore {
    pub async fn publish_alert(
        &mut self,
        alert: &NewAlert<'_>,
        deduplication: AlertDeduplication,
    ) -> Result<bool, StorageError> {
        let parameters = params! {
            "time" => to_microseconds(alert.create_at_ms)?, "level" => alert.level.as_str(),
            "identifier" => alert.identifier, "title" => alert.title.to_string(),
            "content" => alert.content.to_string(),
        };
        let existing = existing_query(":identifier", deduplication);
        // The table has no unique alert key. Serialize publication against this schema,
        // including identifiers the server collation treats as equivalent.
        let lock: Option<u8> = self.connection.query_first(format!(
            "SELECT IF(IS_USED_LOCK({LOCK_NAME}) = CONNECTION_ID(), 1, GET_LOCK({LOCK_NAME}, 10))"
        )).await?.flatten();
        if lock != Some(1) {
            return Err(StorageError::AlertPublicationLock);
        }
        let result = self.connection.exec_drop(format!(
            "INSERT INTO alert ({INSERT_COLUMNS}) SELECT {AT_TIME}, :level, :identifier, :title, :content
             WHERE NOT EXISTS ({existing})"
        ), parameters).await.map(|()| self.connection.affected_rows() != 0);
        let release = self
            .connection
            .query_drop(format!("SELECT RELEASE_LOCK({LOCK_NAME})"))
            .await;
        let inserted = result?;
        release?;
        Ok(inserted)
    }

    pub async fn alert_exists(
        &mut self,
        identifier: &str,
        visibility: AlertDeduplication,
    ) -> Result<bool, StorageError> {
        let existing = existing_query("?", visibility);
        Ok(self
            .connection
            .exec_first(format!("SELECT EXISTS ({existing})"), (identifier,))
            .await?
            .unwrap_or(false))
    }

    pub async fn unread_alerts(&mut self) -> Result<Vec<AlertRecord>, StorageError> {
        let sql = unread_query(
            "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', create_at) / 1000) AS SIGNED)",
            "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', read_at) / 1000) AS SIGNED)",
        );
        let rows: Vec<AlertRow> = self.connection.query(sql).await?;
        rows.into_iter().map(decode).collect()
    }

    pub async fn mark_alert_read(
        &mut self,
        id: i64,
        read_at_ms: i64,
    ) -> Result<bool, StorageError> {
        let time = to_microseconds(read_at_ms)?;
        let mut transaction = self.connection.start_transaction(TxOpts::default()).await?;
        // DATETIME has whole-second precision. An existing row can already carry
        // this timestamp, so use its presence instead of changed-row count.
        let existing: Option<i64> = transaction
            .exec_first("SELECT id FROM alert WHERE id = ? FOR UPDATE", (id,))
            .await?;
        if existing.is_some() {
            transaction
                .exec_drop(
                    format!("UPDATE alert SET read_at = {AT_TIME} WHERE id = :id"),
                    params! {"time" => time, "id" => id},
                )
                .await?;
        }
        transaction.commit().await?;
        Ok(existing.is_some())
    }

    pub async fn mark_alerts_read_by_identifier(
        &mut self,
        identifier: &str,
        read_at_ms: i64,
    ) -> Result<u64, StorageError> {
        self.connection.exec_drop(
            format!("UPDATE alert SET read_at = {AT_TIME} WHERE identifier = :identifier AND read_at IS NULL"),
            params! {"time" => to_microseconds(read_at_ms)?, "identifier" => identifier},
        ).await?;
        Ok(self.connection.affected_rows())
    }

    pub async fn mark_all_alerts_read(&mut self, read_at_ms: i64) -> Result<u64, StorageError> {
        self.connection
            .exec_drop(
                format!("UPDATE alert SET read_at = {AT_TIME} WHERE read_at IS NULL"),
                params! {"time" => to_microseconds(read_at_ms)?},
            )
            .await?;
        Ok(self.connection.affected_rows())
    }

    pub async fn delete_alert(&mut self, id: i64) -> Result<bool, StorageError> {
        self.connection
            .exec_drop("DELETE FROM alert WHERE id = ?", (id,))
            .await?;
        Ok(self.connection.affected_rows() != 0)
    }

    pub async fn cleanup_alerts(&mut self, before_ms: i64) -> Result<u64, StorageError> {
        self.connection
            .exec_drop(
                format!("DELETE FROM alert WHERE create_at <= {AT_TIME}"),
                params! {"time" => to_microseconds(before_ms)?},
            )
            .await?;
        Ok(self.connection.affected_rows())
    }
}
