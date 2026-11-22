//! Applies the published MySQL migrations and preserves Flyway history.

use mysql_async::{Conn, from_row_opt, prelude::Queryable};

use crate::{StorageError, schema::checksum};

macro_rules! migration {
    ($version:literal, $description:literal, $file:literal) => {
        (
            $version,
            $description,
            $file,
            include_str!(concat!(
                "../../../../src/main/resources/db/migration/mysql/",
                $file
            )),
        )
    };
}

const MIGRATIONS: &[(&str, &str, &str, &str)] = &[
    migration!("1.1", "initial mysql", "V1_1__initial_mysql.sql"),
    migration!(
        "1.2",
        "add index for history traffics",
        "V1_2__add_index_for_history_traffics.sql"
    ),
    migration!(
        "1.3",
        "remove unique index for peer records port",
        "V1_3__remove_unique_index_for_peer_records_port.sql"
    ),
    migration!(
        "1.4",
        "add null for peer id connection metrics track",
        "V1_4__add_null_for_peer_id_connection_metrics_track.sql"
    ),
    migration!(
        "1.5",
        "rename pcb range column",
        "V1_5__rename_pcb_range_column.sql"
    ),
];

const CREATE_HISTORY: &str = "
    CREATE TABLE flyway_schema_history (
        installed_rank INT NOT NULL PRIMARY KEY,
        version VARCHAR(50),
        description VARCHAR(200) NOT NULL,
        type VARCHAR(20) NOT NULL,
        script VARCHAR(1000) NOT NULL,
        checksum INT,
        installed_by VARCHAR(100) NOT NULL,
        installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        execution_time INT NOT NULL,
        success BOOL NOT NULL,
        KEY flyway_schema_history_s_idx (success)
    ) ENGINE=InnoDB";

type HistoryRow = (
    i32,
    Option<String>,
    String,
    String,
    String,
    Option<i32>,
    bool,
);

pub(super) async fn prepare(connection: &mut Conn) -> Result<(), StorageError> {
    let lock_name: Option<String> = connection
        .query_first("SELECT CONCAT('peerbanhelper-rust-schema:', MD5(DATABASE()))")
        .await?
        .flatten();
    let lock_name = lock_name
        .ok_or_else(|| StorageError::UnsupportedSchema("no active MySQL database".into()))?;
    let locked: Option<u8> = connection
        .exec_first("SELECT GET_LOCK(?, 30)", (&lock_name,))
        .await?
        .flatten();
    if locked != Some(1) {
        return Err(StorageError::UnsupportedSchema(
            "cannot lock MySQL schema for verification".into(),
        ));
    }

    let result = migrate(connection).await;
    let release = connection
        .exec_drop("SELECT RELEASE_LOCK(?)", (&lock_name,))
        .await;
    result?;
    release?;
    Ok(())
}

async fn migrate(connection: &mut Conn) -> Result<(), StorageError> {
    let tables: Vec<(String, String)> = connection
        .query(
            "SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'",
        )
        .await?;
    if !tables
        .iter()
        .any(|(name, _)| name == "flyway_schema_history")
    {
        if !tables.is_empty() {
            return Err(StorageError::UnsupportedSchema(
                "existing MySQL tables without Flyway history".into(),
            ));
        }
        connection.query_drop(CREATE_HISTORY).await?;
    }
    connection
        .query_drop("SET SESSION default_storage_engine = 'InnoDB'")
        .await?;
    let rows: Vec<mysql_async::Row> = connection
        .query(
            "SELECT installed_rank, version, description, type, script, checksum, success
             FROM flyway_schema_history ORDER BY installed_rank",
        )
        .await?;
    let mut applied = 0;
    let mut last_rank = 0;
    for row in rows {
        let (rank, version, description, kind, script, stored_checksum, success): HistoryRow =
            from_row_opt(row).map_err(|_| {
                StorageError::UnsupportedSchema("invalid MySQL Flyway history fields".into())
            })?;
        let matches_script = MIGRATIONS.get(applied).is_some_and(|(v, desc, file, sql)| {
            version.as_deref() == Some(*v)
                && kind == "SQL"
                && description == *desc
                && script == *file
                && stored_checksum == Some(checksum(sql))
        });
        let baseline = last_rank == 0
            && kind == "BASELINE"
            && version.as_deref() == Some("1")
            && stored_checksum.is_none();
        if !success || rank != last_rank + 1 || !(matches_script || baseline) {
            return Err(StorageError::UnsupportedSchema(format!(
                "unexpected MySQL migration history row {script} (version {version:?}, success {success})"
            )));
        }
        last_rank = rank;
        applied += usize::from(matches_script);
    }

    for (version, description, file, sql) in MIGRATIONS.iter().skip(applied) {
        last_rank += 1;
        // MySQL DDL can commit implicitly. Mark each migration in progress
        // before running it so a partial script is never retried blindly.
        connection
            .exec_drop(
                "INSERT INTO flyway_schema_history
            (installed_rank, version, description, type, script, checksum,
             installed_by, execution_time, success)
             VALUES (?, ?, ?, 'SQL', ?, ?, CURRENT_USER(), 0, FALSE)",
                (last_rank, *version, *description, *file, checksum(sql)),
            )
            .await?;
        connection.query_drop(*sql).await?;
        connection
            .exec_drop(
                "UPDATE flyway_schema_history SET success = TRUE WHERE installed_rank = ?",
                (last_rank,),
            )
            .await?;
    }
    let transactional_tables: u8 = connection
        .query_first(
            "SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND ENGINE = 'InnoDB'
               AND TABLE_NAME IN ('banlist', 'history', 'torrents', 'flyway_schema_history', 'pcb_address', 'pcb_range', 'metadata', 'peer_records', 'tracked_swarm', 'traffic_journal_v3', 'alert', 'peer_connection_metrics', 'peer_connection_metrics_track')",
        )
        .await?
        .unwrap_or(0);
    if transactional_tables != 13 {
        return Err(StorageError::UnsupportedSchema(
            "MySQL persistence requires transactional InnoDB tables for bans, peer history, torrents, Flyway history, PCB state, metadata, swarm tracking, traffic, alerts and connection metrics".into(),
        ));
    }
    connection
        .prep("SELECT address, metadata FROM banlist")
        .await?;
    Ok(())
}
