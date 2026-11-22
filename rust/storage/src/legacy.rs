//! Imports supported ORMLite schemas without writing to the source.

use std::{io, net::IpAddr, path::Path};

use rusqlite::functions::FunctionFlags;
use rusqlite::{Connection, OptionalExtension, TransactionBehavior};
use serde_json::{Value, json};
use url::Url;

use crate::{StorageError, decode_ban, history::java_host_address};

const COMPLETION_KEY: &str = "rust.ormlite-import";
const LEGACY_VERSION: &str = "20";

pub(crate) fn import_pending(
    connection: &mut Connection,
    current: &Path,
) -> Result<(), StorageError> {
    let legacy = current.with_file_name("peerbanhelper.db");
    let marker = current.with_file_name("migration_completed.marker");
    if !legacy.try_exists()? || marker.try_exists()? {
        return Ok(());
    }

    let absolute = std::path::absolute(&legacy)?;
    let mut source = Url::from_file_path(&absolute).map_err(|_| {
        StorageError::LegacyDatabase(format!("invalid SQLite file path: {}", absolute.display()))
    })?;
    source.set_query(Some("mode=ro"));
    register_conversions(connection)?;
    connection.execute("ATTACH DATABASE ?1 AS legacy", [source.as_str()])?;
    let result = import(connection);
    let detach = connection.execute_batch("DETACH DATABASE legacy");
    result?;
    detach?;

    // Record completion in the database first. If marker creation is
    // interrupted, the next startup writes the marker without replaying rows.
    match std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(marker)
    {
        Ok(file) => file.sync_all()?,
        Err(error) if error.kind() == io::ErrorKind::AlreadyExists => {}
        Err(error) => return Err(error.into()),
    }
    Ok(())
}

fn import(connection: &mut Connection) -> Result<(), StorageError> {
    let transaction = connection.transaction_with_behavior(TransactionBehavior::Immediate)?;
    let completed: Option<String> = transaction
        .query_row(
            "SELECT v FROM main.metadata WHERE k = ?1",
            [COMPLETION_KEY],
            |row| row.get(0),
        )
        .optional()?;
    if completed.as_deref() == Some(LEGACY_VERSION) {
        return Ok(());
    }

    let existing_tables = {
        let mut statement = transaction.prepare(
            "SELECT name FROM legacy.sqlite_schema WHERE type = 'table' AND name NOT GLOB 'sqlite_*'",
        )?;
        statement
            .query_map([], |row| row.get::<_, String>(0))?
            .collect::<rusqlite::Result<Vec<_>>>()?
    };
    for table in &existing_tables {
        if !IMPORTS.iter().any(|(name, _)| name == table)
            && !matches!(table.as_str(), "rules" | "modules")
        {
            return Err(StorageError::LegacyDatabase(format!(
                "unrecognized table {table}"
            )));
        }
    }
    let version = source_version(&transaction, &existing_tables)?;

    for (table, _) in IMPORTS
        .iter()
        .chain(std::iter::once(&("tracked_swarm", "")))
    {
        // These names come from the migration's fixed table list.
        let query = format!("SELECT EXISTS (SELECT 1 FROM main.{table})");
        if transaction.query_row(&query, [], |row| row.get::<_, bool>(0))? {
            return Err(StorageError::LegacyDatabase(format!(
                "destination table {table} already contains data"
            )));
        }
    }

    create_compatibility_views(&transaction, &existing_tables, version)?;
    if existing_tables.iter().any(|table| table == "banlist") {
        validate_banlist(&transaction)?;
    }
    for (table, sql) in IMPORTS {
        if existing_tables.iter().any(|existing| existing == table) {
            transaction.execute_batch(sql).map_err(|error| {
                StorageError::LegacyDatabase(format!("could not import {table}: {error}"))
            })?;
        }
    }
    transaction.execute(
        "INSERT INTO main.metadata (k, v) VALUES (?1, ?2)",
        [COMPLETION_KEY, LEGACY_VERSION],
    )?;
    for (table, _) in COMPATIBILITY_FIELDS {
        transaction.execute_batch(&format!("DROP VIEW IF EXISTS temp.pbh_legacy_{table}"))?;
    }
    transaction.commit()?;
    Ok(())
}

fn table_columns(connection: &Connection, table: &str) -> rusqlite::Result<Vec<String>> {
    let mut statement = connection.prepare("SELECT name FROM pragma_table_info(?1, 'legacy')")?;
    statement.query_map([table], |row| row.get(0))?.collect()
}

fn source_version(connection: &Connection, tables: &[String]) -> Result<u8, StorageError> {
    let version: Option<String> = if tables.iter().any(|table| table == "metadata") {
        connection
            .query_row(
                "SELECT value FROM legacy.metadata WHERE key = 'version'",
                [],
                |row| row.get(0),
            )
            .optional()?
    } else {
        None
    };
    if let Some(version) = version {
        return version
            .parse()
            .ok()
            .filter(|version| (2..=20).contains(version))
            .ok_or_else(|| {
                StorageError::LegacyDatabase(format!("unsupported schema version {version}"))
            });
    }

    let history = table_columns(connection, "history")?;
    if history.is_empty() {
        return Err(StorageError::LegacyDatabase(
            "missing schema version and history table".into(),
        ));
    }
    // Match the Java upgrader's legacy detection when the metadata row is missing.
    Ok(if history.iter().any(|column| column == "downloader") {
        3
    } else {
        2
    })
}

fn create_compatibility_views(
    connection: &Connection,
    tables: &[String],
    version: u8,
) -> Result<(), StorageError> {
    for (table, fields) in COMPATIBILITY_FIELDS {
        if !tables.iter().any(|existing| existing == table) {
            continue;
        }
        let columns = table_columns(connection, table)?;
        let mut projection = String::new();
        for field in *fields {
            if columns
                .iter()
                .any(|column| column.eq_ignore_ascii_case(field.name))
            {
                continue;
            }
            if version >= field.added_in {
                return Err(StorageError::LegacyDatabase(format!(
                    "schema version {version} is missing {table}.{}",
                    field.name
                )));
            }
            // The table and field names come from the fixed declarations below.
            projection.push_str(&format!(", {} AS {}", field.default, field.name));
        }
        connection.execute_batch(&format!(
            "CREATE TEMP VIEW pbh_legacy_{table} AS SELECT *{projection} FROM legacy.{table}"
        ))?;
    }
    Ok(())
}

fn validate_banlist(connection: &Connection) -> Result<(), StorageError> {
    let mut statement = connection.prepare("SELECT address, metadata FROM legacy.banlist")?;
    let mut rows = statement.query([])?;
    while let Some(row) = rows.next()? {
        let record = decode_ban(row.get(0)?, &row.get::<_, String>(1)?)?;
        if record
            .metadata
            .get("unbanAt")
            .and_then(Value::as_i64)
            .is_none()
        {
            return Err(StorageError::LegacyDatabase(format!(
                "legacy ban {} has no millisecond unbanAt field",
                record.address
            )));
        }
    }
    Ok(())
}

struct LegacyField {
    name: &'static str,
    added_in: u8,
    default: &'static str,
}

// v3–v20 column additions from SQLiteSchemaUpgrader, supplied by a read-only
// projection when the source predates the column. Older alerts and bans that
// can be decoded stay available and are imported without destructive cleanup.
const COMPATIBILITY_FIELDS: &[(&str, &[LegacyField])] = &[
    (
        "torrents",
        &[LegacyField {
            name: "privateTorrent",
            added_in: 9,
            default: "NULL",
        }],
    ),
    (
        "history",
        &[
            LegacyField {
                name: "downloader",
                added_in: 3,
                default: "''",
            },
            LegacyField {
                name: "downloaderProgress",
                added_in: 10,
                default: "0",
            },
            LegacyField {
                name: "structuredData",
                added_in: 15,
                default: "NULL",
            },
        ],
    ),
    (
        "peer_records",
        &[
            LegacyField {
                name: "uploadSpeed",
                added_in: 11,
                default: "0",
            },
            LegacyField {
                name: "downloadSpeed",
                added_in: 11,
                default: "0",
            },
            LegacyField {
                name: "port",
                added_in: 20,
                default: "0",
            },
        ],
    ),
];

fn register_conversions(connection: &Connection) -> rusqlite::Result<()> {
    let flags = FunctionFlags::SQLITE_UTF8
        | FunctionFlags::SQLITE_DETERMINISTIC
        | FunctionFlags::SQLITE_DIRECTONLY;
    connection.create_scalar_function("pbh_host_address", 1, flags, |arguments| {
        let address = arguments
            .get::<String>(0)?
            .parse::<IpAddr>()
            .map_err(|error| rusqlite::Error::UserFunctionError(Box::new(error)))?;
        Ok(java_host_address(address))
    })?;
    connection.create_scalar_function("pbh_translation", 1, flags, |arguments| {
        let raw = arguments.get::<String>(0)?;
        let parsed = serde_json::from_str::<Value>(&raw).ok();
        let value = match parsed {
            Some(value) if value.get("key").is_some_and(Value::is_string) => value,
            _ => json!({"key": raw, "params": []}),
        };
        Ok(value.to_string())
    })?;
    connection.create_scalar_function("pbh_epoch_ms", 1, flags, |arguments| {
        arguments.get::<Option<i64>>(0)
    })?;
    Ok(())
}

// Each SELECT is based on the corresponding Java `*Migrator` and the v20
// fixture. Explicit columns keep legacy schema changes visible in review.
const IMPORTS: &[(&str, &str)] = &[
    ("metadata", "INSERT INTO main.metadata (k, v)
        SELECT key, value FROM legacy.metadata WHERE key <> 'version'"),
    ("torrents", "INSERT INTO main.torrents (id, info_hash, name, size, private_torrent)
        SELECT id, infoHash, name, size, privateTorrent FROM temp.pbh_legacy_torrents"),
    ("history", "INSERT INTO main.history (
        id, ban_at, unban_at, ip, port, peer_id, peer_client_name, peer_uploaded, peer_downloaded,
        peer_progress, downloader_progress, torrent_id, module_name, rule_name, description,
        flags, downloader, structured_data
    ) SELECT h.id, pbh_epoch_ms(h.banAt), pbh_epoch_ms(h.unbanAt), pbh_host_address(h.ip), h.port,
        h.peerId, h.peerClientName, h.peerUploaded, h.peerDownloaded, h.peerProgress,
        COALESCE(h.downloaderProgress, 0), h.torrent_id, m.name, pbh_translation(r.rule),
        pbh_translation(h.description), h.flags, h.downloader, NULLIF(NULLIF(h.structuredData, ''), '{}')
    FROM temp.pbh_legacy_history h
    LEFT JOIN legacy.rules r ON h.rule_id = r.id
    LEFT JOIN legacy.modules m ON r.module_id = m.id"),
    ("peer_records", "INSERT INTO main.peer_records (
        id, address, port, torrent_id, downloader, peer_id, client_name, uploaded, uploaded_offset,
        upload_speed, downloaded, downloaded_offset, download_speed, last_flags, first_time_seen, last_time_seen
    ) SELECT id, pbh_host_address(address), port, torrent_id, downloader, peerId, clientName, uploaded,
        uploadedOffset, uploadSpeed, downloaded, downloadedOffset, downloadSpeed, lastFlags,
        pbh_epoch_ms(firstTimeSeen), pbh_epoch_ms(lastTimeSeen) FROM temp.pbh_legacy_peer_records"),
    ("traffic_journal_v3", "INSERT INTO main.traffic_journal_v3 (
        id, timestamp, downloader, data_overall_uploaded_at_start, data_overall_uploaded,
        data_overall_downloaded_at_start, data_overall_downloaded, protocol_overall_uploaded_at_start,
        protocol_overall_uploaded, protocol_overall_downloaded_at_start, protocol_overall_downloaded
    ) SELECT id, pbh_epoch_ms(timestamp), downloader, dataOverallUploadedAtStart, dataOverallUploaded,
        dataOverallDownloadedAtStart, dataOverallDownloaded, protocolOverallUploadedAtStart,
        protocolOverallUploaded, protocolOverallDownloadedAtStart, protocolOverallDownloaded
    FROM legacy.traffic_journal_v3"),
    ("rule_sub_info", "INSERT INTO main.rule_sub_info (
        rule_id, enabled, rule_name, sub_url, last_update, ent_count
    ) SELECT ruleId, enabled, ruleName, subUrl, pbh_epoch_ms(lastUpdate), entCount FROM legacy.rule_sub_info"),
    ("rule_sub_log", "INSERT INTO main.rule_sub_log (id, rule_id, update_time, count, update_type)
        SELECT id, ruleId, pbh_epoch_ms(updateTime), count, updateType FROM legacy.rule_sub_log"),
    ("pcb_address", "INSERT INTO main.pcb_address (
        id, ip, port, torrent_id, last_report_progress, last_report_uploaded,
        tracking_uploaded_increase_total, rewind_counter, progress_difference_counter, first_time_seen,
        last_time_seen, downloader, ban_delay_window_end_at, fast_pcb_test_execute_at, last_torrent_completed_size
    ) SELECT id, pbh_host_address(ip), port, torrentId, lastReportProgress, lastReportUploaded,
        trackingUploadedIncreaseTotal, rewindCounter, progressDifferenceCounter, pbh_epoch_ms(firstTimeSeen),
        pbh_epoch_ms(lastTimeSeen), downloader, pbh_epoch_ms(banDelayWindowEndAt),
        pbh_epoch_ms(fastPcbTestExecuteAt), lastTorrentCompletedSize FROM legacy.pcb_address"),
    ("pcb_range", "INSERT INTO main.pcb_range (
        id, ip_range, torrent_id, last_report_progress, last_report_uploaded,
        tracking_uploaded_increase_total, rewind_counter, progress_difference_counter, first_time_seen,
        last_time_seen, downloader, ban_delay_window_end_at, fast_pcb_test_execute_at, last_torrent_completed_size
    ) SELECT id, \"range\", torrentId, lastReportProgress, lastReportUploaded, trackingUploadedIncreaseTotal,
        rewindCounter, progressDifferenceCounter, pbh_epoch_ms(firstTimeSeen), pbh_epoch_ms(lastTimeSeen),
        downloader, pbh_epoch_ms(banDelayWindowEndAt), pbh_epoch_ms(fastPcbTestExecuteAt),
        lastTorrentCompletedSize FROM legacy.pcb_range"),
    ("peer_connection_metrics", "INSERT INTO main.peer_connection_metrics (
        id, timeframe_at, downloader, total_connections, incoming_connections, remote_refuse_transfer_to_client,
        remote_accept_transfer_to_client, local_refuse_transfer_to_peer, local_accept_transfer_to_peer,
        local_not_interested, question_status, optimistic_unchoke, from_dht, from_pex, from_lsd,
        from_tracker_or_other, rc4_encrypted, plain_text_encrypted, utp_socket, tcp_socket
    ) SELECT id, pbh_epoch_ms(timeframeAt), downloader, totalConnections, incomingConnections,
        remoteRefuseTransferToClient, remoteAcceptTransferToClient, localRefuseTransferToPeer,
        localAcceptTransferToPeer, localNotInterested, questionStatus, optimisticUnchoke,
        fromDHT, fromPEX, fromLSD, fromTrackerOrOther, rc4Encrypted, plainTextEncrypted, utpSocket, tcpSocket
    FROM legacy.peer_connection_metrics"),
    ("peer_connection_metrics_track", "INSERT INTO main.peer_connection_metrics_track (
        id, timeframe_at, downloader, torrent_id, address, port, peer_id, client_name, last_flags
    ) SELECT id, pbh_epoch_ms(timeframeAt), downloader, torrent_id, pbh_host_address(address),
        port, peerId, clientName, lastFlags FROM legacy.peer_connection_metrics_track"),
    ("alert", "INSERT INTO main.alert (id, create_at, read_at, level, identifier, title, content)
        SELECT id, pbh_epoch_ms(createAt), pbh_epoch_ms(readAt), level, identifier,
        pbh_translation(COALESCE(title, '')), pbh_translation(COALESCE(content, '')) FROM legacy.alert"),
    ("banlist", "INSERT INTO main.banlist (address, metadata) SELECT address, metadata FROM legacy.banlist"),
];
