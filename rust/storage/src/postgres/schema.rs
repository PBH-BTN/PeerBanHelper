//! Applies the published PostgreSQL migrations and preserves Flyway history.

use tokio_postgres::Client;

use crate::{StorageError, schema::checksum};

macro_rules! migration {
    ($version:literal, $description:literal, $file:literal) => {
        (
            $version,
            $description,
            $file,
            include_str!(concat!(
                "../../../../src/main/resources/db/migration/postgres/",
                $file
            )),
        )
    };
}

const MIGRATIONS: &[(&str, &str, &str, &str)] = &[
    migration!("1.1", "initial postgres", "V1_1__initial_postgres.sql"),
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
        "change private torrent type",
        "V1_5__change_private_torrent_type.sql"
    ),
    migration!(
        "1.6",
        "rename pcb range column",
        "V1_6__rename_pcb_range_column.sql"
    ),
    migration!(
        "1.7",
        "change tracked swarm private torrent type",
        "V1_7__change_tracked_swarm_private_torrent_type.sql"
    ),
    migration!("1.8", "fix auto increment", "V1_8__fix_auto_increment.sql"),
];

const CREATE_HISTORY: &str = "
    CREATE TABLE flyway_schema_history (
        installed_rank INTEGER CONSTRAINT flyway_schema_history_pk PRIMARY KEY,
        version VARCHAR(50),
        description VARCHAR(200) NOT NULL,
        type VARCHAR(20) NOT NULL,
        script VARCHAR(1000) NOT NULL,
        checksum INTEGER,
        installed_by VARCHAR(100) NOT NULL,
        installed_on TIMESTAMP NOT NULL DEFAULT now(),
        execution_time INTEGER NOT NULL,
        success BOOLEAN NOT NULL
    );
    CREATE INDEX flyway_schema_history_s_idx ON flyway_schema_history (success);";

pub(super) async fn prepare(client: &mut Client) -> Result<(), StorageError> {
    let transaction = client.transaction().await?;
    let schema: Option<String> = transaction
        .query_one("SELECT current_schema()", &[])
        .await?
        .try_get(0)?;
    let schema = schema
        .ok_or_else(|| StorageError::UnsupportedSchema("no active PostgreSQL schema".into()))?;
    transaction
        .execute(
            "SELECT pg_advisory_xact_lock(hashtext('peerbanhelper-rust-schema'), hashtext($1))",
            &[&schema],
        )
        .await?;
    // Limit unqualified references to the selected schema. Other search-path
    // entries may contain a different installation's tables.
    transaction
        .execute(
            "SELECT set_config('search_path', quote_ident($1), true)",
            &[&schema],
        )
        .await?;
    let has_history: bool = transaction
        .query_one(
            "SELECT to_regclass('flyway_schema_history') IS NOT NULL",
            &[],
        )
        .await?
        .try_get(0)?;
    if !has_history {
        let has_tables: bool = transaction
            .query_one(
                "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_tables WHERE schemaname = current_schema())",
                &[],
            )
            .await?
            .try_get(0)?;
        if has_tables {
            return Err(StorageError::UnsupportedSchema(
                "existing PostgreSQL tables without Flyway history".into(),
            ));
        }
        transaction.batch_execute(CREATE_HISTORY).await?;
    }

    let rows = transaction
        .query(
            "SELECT installed_rank, version, description, type, script, checksum, success
             FROM flyway_schema_history ORDER BY installed_rank",
            &[],
        )
        .await?;
    let mut applied = 0;
    let mut last_rank = 0;
    for row in rows {
        let rank: i32 = row.try_get(0)?;
        let version: Option<&str> = row.try_get(1)?;
        let description: &str = row.try_get(2)?;
        let kind: &str = row.try_get(3)?;
        let script: &str = row.try_get(4)?;
        let stored_checksum: Option<i32> = row.try_get(5)?;
        let success: bool = row.try_get(6)?;
        let matches_script = MIGRATIONS.get(applied).is_some_and(|(v, desc, file, sql)| {
            version == Some(*v)
                && kind == "SQL"
                && description == *desc
                && script == *file
                && stored_checksum == Some(checksum(sql))
        });
        let baseline = last_rank == 0
            && kind == "BASELINE"
            && version == Some("1")
            && stored_checksum.is_none();
        if !success || rank != last_rank + 1 || !(matches_script || baseline) {
            return Err(StorageError::UnsupportedSchema(format!(
                "unexpected PostgreSQL migration history row {script} (version {version:?})"
            )));
        }
        last_rank = rank;
        applied += usize::from(matches_script);
    }

    for (version, description, file, sql) in MIGRATIONS.iter().skip(applied) {
        transaction.batch_execute(sql).await?;
        last_rank += 1;
        transaction
            .execute(
                "INSERT INTO flyway_schema_history
                (installed_rank, version, description, type, script, checksum,
                 installed_by, execution_time, success)
                VALUES ($1, $2, $3, 'SQL', $4, $5, current_user, 0, TRUE)",
                &[&last_rank, version, description, file, &checksum(sql)],
            )
            .await?;
    }
    // Detect a broken user schema before making it available to callers.
    transaction
        .prepare("SELECT address, metadata FROM banlist")
        .await?;
    transaction.commit().await?;
    Ok(())
}
