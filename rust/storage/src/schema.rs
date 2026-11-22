//! SQLite schema compatibility with the PBH 9.6.0 Flyway migrations.

use rusqlite::{Connection, TransactionBehavior, params};

use crate::StorageError;

struct Migration {
    version: &'static str,
    description: &'static str,
    file: &'static str,
    sql: &'static str,
}

macro_rules! migration {
    ($version:literal, $description:literal, $file:literal) => {
        Migration {
            version: $version,
            description: $description,
            file: $file,
            sql: include_str!(concat!(
                "../../../src/main/resources/db/migration/sqlite/",
                $file
            )),
        }
    };
}

const MIGRATIONS: [Migration; 5] = [
    migration!("1.1", "initial sqlite", "V1_1__initial_sqlite.sql"),
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
        installed_rank INTEGER PRIMARY KEY NOT NULL,
        version VARCHAR(50),
        description VARCHAR(200) NOT NULL,
        type VARCHAR(20) NOT NULL,
        script VARCHAR(1000) NOT NULL,
        checksum INTEGER,
        installed_by VARCHAR(100) NOT NULL,
        installed_on TEXT NOT NULL DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'now')),
        execution_time INTEGER NOT NULL,
        success BOOLEAN NOT NULL
    );
    CREATE INDEX flyway_schema_history_s_idx ON flyway_schema_history (success);";

pub(crate) fn prepare(connection: &mut Connection) -> Result<(), StorageError> {
    let transaction = connection.transaction_with_behavior(TransactionBehavior::Immediate)?;
    if !has_table(&transaction, "flyway_schema_history")? {
        let is_nonempty = transaction.query_row(
            "SELECT EXISTS (SELECT 1 FROM sqlite_schema WHERE name NOT GLOB 'sqlite_*')",
            [],
            |row| row.get::<_, bool>(0),
        )?;
        if is_nonempty {
            return Err(StorageError::UnsupportedSchema(
                "existing tables without Flyway history".into(),
            ));
        }
        transaction.execute_batch(CREATE_HISTORY)?;
    }

    let applied = validate_history(&transaction)?;
    if applied != 0 && !has_table(&transaction, "banlist")? {
        return Err(StorageError::UnsupportedSchema(
            "missing banlist table".into(),
        ));
    }

    for (index, migration) in MIGRATIONS.iter().enumerate().skip(applied) {
        transaction.execute_batch(migration.sql)?;
        transaction.execute(
            "INSERT INTO flyway_schema_history
            (installed_rank, version, description, type, script, checksum,
             installed_by, execution_time, success)
            VALUES (?1, ?2, ?3, 'SQL', ?4, ?5, '', 0, 1)",
            params![
                (index + 1) as i64,
                migration.version,
                migration.description,
                migration.file,
                checksum(migration.sql)
            ],
        )?;
    }

    transaction.commit()?;
    Ok(())
}

fn has_table(connection: &Connection, name: &str) -> rusqlite::Result<bool> {
    connection.query_row(
        "SELECT EXISTS (SELECT 1 FROM sqlite_schema WHERE type = 'table' AND name = ?1)",
        [name],
        |row| row.get(0),
    )
}

fn validate_history(connection: &Connection) -> Result<usize, StorageError> {
    let mut statement = connection.prepare(
        "SELECT version, description, type, script, checksum, success, installed_rank
         FROM flyway_schema_history ORDER BY installed_rank",
    )?;
    let mut applied = 0;
    let mut rows = statement.query([])?;
    while let Some(row) = rows.next()? {
        let version: Option<String> = row.get(0)?;
        let description: String = row.get(1)?;
        let kind: String = row.get(2)?;
        let script: String = row.get(3)?;
        let stored_checksum: Option<i32> = row.get(4)?;
        let success: bool = row.get(5)?;
        let rank: i64 = row.get(6)?;
        let migration_matches = MIGRATIONS.get(applied).is_some_and(|migration| {
            rank == (applied + 1) as i64
                && version.as_deref() == Some(migration.version)
                && description == migration.description
                && kind == "SQL"
                && script == migration.file
                && stored_checksum == Some(checksum(migration.sql))
                && success
        });
        if !migration_matches {
            return Err(StorageError::UnsupportedSchema(format!(
                "unexpected migration history row {script} (version {version:?})"
            )));
        }
        applied += 1;
    }
    Ok(applied)
}

pub(crate) fn checksum(sql: &str) -> i32 {
    // Flyway feeds the UTF-8 text to CRC32 one line at a time, with line
    // endings and the optional leading BOM removed.
    let mut hasher = crc32fast::Hasher::new();
    for line in sql.trim_start_matches('\u{feff}').lines() {
        hasher.update(line.as_bytes());
    }
    hasher.finalize() as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn advances_an_existing_version_one_database() {
        let mut connection = Connection::open_in_memory().unwrap();
        connection.execute_batch(CREATE_HISTORY).unwrap();
        connection.execute_batch(MIGRATIONS[0].sql).unwrap();
        connection
            .execute_batch(
                "INSERT INTO flyway_schema_history
                 (installed_rank, version, description, type, script, checksum,
                  installed_by, execution_time, success)
                 VALUES (1, '1.1', 'initial sqlite', 'SQL', 'V1_1__initial_sqlite.sql',
                         -753427312, '', 9, 1);
                 INSERT INTO banlist VALUES ('192.0.2.1', '{\"unbanAt\":123}');",
            )
            .unwrap();

        prepare(&mut connection).unwrap();

        assert_eq!(validate_history(&connection).unwrap(), MIGRATIONS.len());
        let stored: String = connection
            .query_row("SELECT metadata FROM banlist", [], |row| row.get(0))
            .unwrap();
        assert_eq!(stored, r#"{"unbanAt":123}"#);
        assert!(connection.prepare("SELECT ip_range FROM pcb_range").is_ok());
    }

    #[test]
    fn published_checksums_use_the_same_values_for_lf_and_crlf() {
        let expected = [-753427312, -449701131, 1923886582, 590547749, -1858253636];
        for (migration, expected) in MIGRATIONS.iter().zip(expected) {
            let lf = migration.sql.replace("\r\n", "\n");
            assert_eq!(checksum(&lf), expected, "{}", migration.file);
            assert_eq!(checksum(&lf.replace('\n', "\r\n")), expected);
        }
    }
}
