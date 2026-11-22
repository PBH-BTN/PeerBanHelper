use pbh_storage::{
    AlertLevel, BanHistoryEntry, BanRecord, HistoryTorrent, SQLITE_FILENAME, SqliteStore,
    StorageError, TrafficHour,
};
use rusqlite::Connection;
use serde_json::{Value, json};

fn ban(address: &str, metadata: Value) -> BanRecord {
    BanRecord {
        address: address.parse().unwrap(),
        metadata,
    }
}

#[test]
fn saves_and_restores_java_metadata_across_restarts() {
    let persist = tempfile::tempdir().unwrap();
    let path = persist.path().join(SQLITE_FILENAME);
    let metadata = json!({"banAt": 100, "unbanAt": 200, "futureField": [true, null]});
    let ban = ban("2001:db8::2", metadata.clone());
    let mut store = SqliteStore::open(&path).unwrap();
    store.replace_bans(std::slice::from_ref(&ban)).unwrap();
    drop(store);

    let sqlite = Connection::open(&path).unwrap();
    let (address, saved): (String, String) = sqlite
        .query_row("SELECT address, metadata FROM banlist", [], |row| {
            Ok((row.get(0)?, row.get(1)?))
        })
        .unwrap();
    assert_eq!(address, "2001:db8::2");
    assert_eq!(serde_json::from_str::<Value>(&saved).unwrap(), metadata);

    let mut store = SqliteStore::open(&path).unwrap();
    assert_eq!(store.load_bans().unwrap(), [ban]);
    store.replace_bans(&[]).unwrap();
    assert!(store.load_bans().unwrap().is_empty());
}

#[test]
fn rejects_a_future_schema_without_rewriting_its_history() {
    let persist = tempfile::tempdir().unwrap();
    let path = persist.path().join(SQLITE_FILENAME);
    drop(SqliteStore::open(&path).unwrap());
    let sqlite = Connection::open(&path).unwrap();
    sqlite
        .execute_batch("UPDATE flyway_schema_history SET version = '9.9' WHERE installed_rank = 5")
        .unwrap();

    assert!(matches!(
        SqliteStore::open(&path),
        Err(StorageError::UnsupportedSchema(_))
    ));
    let newest: String = sqlite
        .query_row(
            "SELECT version FROM flyway_schema_history WHERE installed_rank = 5",
            [],
            |row| row.get(0),
        )
        .unwrap();
    assert_eq!(newest, "9.9");
}

#[test]
fn imports_old_database_fixture_without_modifying_it_or_replaying_rows() {
    let persist = tempfile::tempdir().unwrap();
    let reference = include_bytes!("../../../test/test-db/persist/peerbanhelper.db");
    let legacy_path = persist.path().join("peerbanhelper.db");
    std::fs::write(&legacy_path, reference).unwrap();
    let path = persist.path().join(SQLITE_FILENAME);

    let store = SqliteStore::open(&path).unwrap();
    let bans = store.load_bans().unwrap();
    assert_eq!(bans.len(), 9);
    assert_eq!(bans[0].metadata["downloader"]["name"], "test");
    let alerts = store.unread_alerts().unwrap();
    assert_eq!(alerts.len(), 1);
    assert_eq!(alerts[0].level, AlertLevel::Warn);
    assert!(alerts[0].title["key"].is_string());
    assert!(alerts[0].content["params"].is_array());
    let session_day = 1_783_872_000_000;
    assert_eq!(
        store
            .global_total_connections_count(session_day, session_day)
            .unwrap(),
        31
    );
    let timestamp_ms = 1_783_908_000_000;
    assert_eq!(
        store
            .traffic_hours(None, timestamp_ms, timestamp_ms)
            .unwrap(),
        [TrafficHour {
            timestamp_ms,
            uploaded: -1_503_026_930_647,
            downloaded: -370_330_524_836,
        }]
    );
    drop(store);
    let marker = persist.path().join("migration_completed.marker");
    assert!(marker.exists());

    let sqlite = Connection::open(&path).unwrap();
    for (table, count) in [
        ("torrents", 2),
        ("history", 9),
        ("banlist", 9),
        ("rule_sub_log", 3),
        ("pcb_address", 175),
        ("pcb_range", 173),
        ("traffic_journal_v3", 1),
        ("alert", 1),
        ("peer_connection_metrics", 1),
        ("peer_connection_metrics_track", 31),
    ] {
        let stored_count: i64 = sqlite
            .query_row(&format!("SELECT COUNT(*) FROM {table}"), [], |row| {
                row.get(0)
            })
            .unwrap();
        assert_eq!(stored_count, count, "{table}");
    }

    // Simulate losing the marker after the database transaction commits.
    std::fs::remove_file(&marker).unwrap();
    let reopened = SqliteStore::open(&path).unwrap();
    assert_eq!(reopened.load_bans().unwrap(), bans);
    assert!(marker.exists());
    assert_eq!(std::fs::read(&legacy_path).unwrap(), reference);
}

#[test]
fn a_legacy_table_failure_rolls_back_earlier_imports() {
    let persist = tempfile::tempdir().unwrap();
    let reference = include_bytes!("../../../test/test-db/persist/peerbanhelper.db");
    let legacy_path = persist.path().join("peerbanhelper.db");
    std::fs::write(&legacy_path, reference).unwrap();
    let legacy = Connection::open(&legacy_path).unwrap();
    legacy
        .execute_batch(
            "UPDATE history SET banAt = 'bad date' WHERE id = (SELECT MIN(id) FROM history)",
        )
        .unwrap();
    drop(legacy);

    let path = persist.path().join(SQLITE_FILENAME);
    assert!(
        matches!(SqliteStore::open(&path), Err(StorageError::LegacyDatabase(message)) if message.contains("history"))
    );
    let sqlite = Connection::open(&path).unwrap();
    let torrent_count: i64 = sqlite
        .query_row("SELECT COUNT(*) FROM torrents", [], |row| row.get(0))
        .unwrap();
    assert_eq!(torrent_count, 0);
    assert!(!persist.path().join("migration_completed.marker").exists());
}

#[test]
fn restores_version_two_history_without_recreating_source_tables() {
    let persist = tempfile::tempdir().unwrap();
    let legacy_path = persist.path().join("peerbanhelper.db");
    let legacy = Connection::open(&legacy_path).unwrap();
    // The earliest shape has no metadata version marker or downloader fields.
    legacy.execute_batch(
        "CREATE TABLE torrents (id INTEGER PRIMARY KEY, infoHash TEXT, name TEXT, size INTEGER);
         INSERT INTO torrents VALUES (1, 'fixture', 'Fixture torrent', 4096);
         CREATE TABLE modules (id INTEGER PRIMARY KEY, name TEXT);
         INSERT INTO modules VALUES (1, 'FixtureRule');
         CREATE TABLE rules (id INTEGER PRIMARY KEY, module_id INTEGER, rule TEXT);
         INSERT INTO rules VALUES (1, 1, 'RULE');
         CREATE TABLE history (
             id INTEGER PRIMARY KEY, banAt INTEGER, unbanAt INTEGER, ip TEXT, port INTEGER,
             peerId TEXT, peerClientName TEXT, peerUploaded INTEGER, peerDownloaded INTEGER,
             peerProgress REAL, torrent_id INTEGER, rule_id INTEGER, description TEXT, flags TEXT
         );
         INSERT INTO history VALUES (1, 100, 200, '192.0.2.1', 1234, 'peer', 'fixture',
                                     10, 20, 0.5, 1, 1, 'REASON', NULL);",
    ).unwrap();
    drop(legacy);
    let original = std::fs::read(&legacy_path).unwrap();

    let path = persist.path().join(SQLITE_FILENAME);
    drop(SqliteStore::open(&path).unwrap());
    let sqlite = Connection::open(&path).unwrap();
    let (downloader, progress, private, rule): (String, f64, Option<bool>, String) = sqlite
        .query_row(
            "SELECT h.downloader, h.downloader_progress, t.private_torrent, h.rule_name
         FROM history h JOIN torrents t ON h.torrent_id = t.id",
            [],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
        )
        .unwrap();
    assert_eq!(downloader, "");
    assert_eq!(progress, 0.0);
    assert_eq!(private, None);
    assert_eq!(
        serde_json::from_str::<Value>(&rule).unwrap(),
        json!({"key":"RULE","params":[]})
    );
    assert_eq!(std::fs::read(&legacy_path).unwrap(), original);
}

#[test]
fn version_nineteen_peer_record_uses_the_reference_missing_port() {
    let persist = tempfile::tempdir().unwrap();
    let legacy_path = persist.path().join("peerbanhelper.db");
    let legacy = Connection::open(&legacy_path).unwrap();
    legacy
        .execute_batch(
            "CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT);
         INSERT INTO metadata VALUES ('version', '19');
         CREATE TABLE peer_records (
             id INTEGER PRIMARY KEY, address TEXT, torrent_id INTEGER, downloader TEXT,
             peerId TEXT, clientName TEXT, uploaded INTEGER, uploadedOffset INTEGER,
             uploadSpeed INTEGER, downloaded INTEGER, downloadedOffset INTEGER,
             downloadSpeed INTEGER, lastFlags TEXT, firstTimeSeen INTEGER, lastTimeSeen INTEGER
         );
         INSERT INTO peer_records VALUES (6, '2001:db8::2', 7, 'fixture', 'peer', 'fixture',
                                          3, 4, 5, 6, 7, 8, NULL, 100, 200);",
        )
        .unwrap();
    drop(legacy);

    let path = persist.path().join(SQLITE_FILENAME);
    drop(SqliteStore::open(&path).unwrap());
    let sqlite = Connection::open(path).unwrap();
    let record: (String, u16, i64) = sqlite
        .query_row(
            "SELECT address, port, upload_speed FROM peer_records WHERE id = 6",
            [],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        )
        .unwrap();
    assert_eq!(record, ("2001:db8:0:0:0:0:0:2".into(), 0, 5));
}

#[test]
fn a_failed_snapshot_write_preserves_the_previous_bans() {
    let mut store = SqliteStore::open(":memory:").unwrap();
    let stored = ban("192.0.2.1", json!({"unbanAt":200}));
    let good = ban("192.0.2.2", json!({}));
    let bad = ban("192.0.2.3", json!([]));
    store.replace_bans(std::slice::from_ref(&stored)).unwrap();

    assert!(matches!(
        store.replace_bans(&[good, bad]),
        Err(StorageError::InvalidBanMetadata { .. })
    ));
    assert_eq!(store.load_bans().unwrap(), [stored]);
}

#[test]
fn history_shares_the_snapshot_transaction_and_has_java_ipv6_encoding() {
    let persist = tempfile::tempdir().unwrap();
    let path = persist.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let bad = ban("192.0.2.1", json!([]));
    let good = ban("192.0.2.2", json!({"unbanAt": 200}));
    let rule = json!({"key": "RULE", "params": []});
    let description = json!({"key": "DESCRIPTION", "params": ["2001:db8::2"]});
    let event = BanHistoryEntry {
        address: "2001:db8::2".parse().unwrap(),
        ban_at_ms: 100,
        unban_at_ms: 200,
        port: 6881,
        peer_id: None,
        peer_client_name: Some("Fixture"),
        peer_uploaded: Some(42),
        peer_downloaded: None,
        peer_progress: 0.5,
        torrent: HistoryTorrent {
            info_hash: "fixture",
            name: "Fixture torrent",
            size: 4096,
            private: None,
            progress: 0.9,
        },
        module_name: "FixtureRule",
        rule: &rule,
        description: &description,
        flags: None,
        downloader: "fixtureClient",
        structured_data: None,
        peer_geoip: None,
    };
    store.replace_bans(std::slice::from_ref(&good)).unwrap();
    assert!(store.commit_ban_wave(Some(&[bad]), &[event]).is_err());
    assert_eq!(store.load_bans().unwrap(), [good]);
    let sqlite = Connection::open(&path).unwrap();
    let count: i64 = sqlite
        .query_row("SELECT COUNT(*) FROM history", [], |row| row.get(0))
        .unwrap();
    assert_eq!(count, 0);

    store.commit_ban_wave(None, &[event]).unwrap();
    assert_eq!(store.load_bans().unwrap().len(), 1);
    let (address, info_hash, unban_at): (String, String, i64) = sqlite.query_row(
        "SELECT h.ip, t.info_hash, h.unban_at FROM history h JOIN torrents t ON h.torrent_id = t.id",
        [],
        |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
    ).unwrap();
    assert_eq!(address, "2001:db8:0:0:0:0:0:2");
    assert_eq!(info_hash, "fixture");
    assert_eq!(unban_at, 200);
}
