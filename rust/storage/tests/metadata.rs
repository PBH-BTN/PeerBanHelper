use pbh_storage::{SQLITE_FILENAME, SqliteStore};
use rusqlite::Connection;

const VERSION: &str = "btn.ability.ip_allowlist.cache.version";
const PAYLOAD: &str = "btn.ability.ip_allowlist.cache.value";

#[test]
fn metadata_keeps_java_values_and_distinguishes_null_from_missing() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let sqlite = Connection::open(&path).unwrap();
    sqlite
        .execute_batch(
            "INSERT INTO metadata (k, v) VALUES ('nullable', NULL), ('unrelated', 'keep')",
        )
        .unwrap();
    store
        .write_metadata(&[
            (VERSION, Some("1")),
            (PAYLOAD, Some("[\"2001:db8::/120\"]")),
            (VERSION, Some("2")),
        ])
        .unwrap();
    drop(store);

    let mut store = SqliteStore::open(&path).unwrap();
    let result = store
        .read_metadata(&["missing", "nullable", "unrelated", VERSION, PAYLOAD])
        .unwrap();
    assert_eq!(result.get("missing"), None);
    assert_eq!(result.get("nullable"), Some(&None));
    assert_eq!(result["unrelated"].as_deref(), Some("keep"));
    assert_eq!(result[VERSION].as_deref(), Some("2"));
    assert_eq!(result[PAYLOAD].as_deref(), Some("[\"2001:db8::/120\"]"));
    store.write_metadata(&[(PAYLOAD, None)]).unwrap();
    assert_eq!(
        store.read_metadata(&[PAYLOAD]).unwrap().get(PAYLOAD),
        Some(&None)
    );
}

#[test]
fn metadata_batch_rolls_back_if_a_later_key_fails() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    store
        .write_metadata(&[(VERSION, Some("1")), (PAYLOAD, Some("[]"))])
        .unwrap();
    let sqlite = Connection::open(&path).unwrap();
    sqlite
        .execute_batch(
            "CREATE TRIGGER reject_last_key BEFORE INSERT ON metadata
                           WHEN NEW.k = 'z.rejected' BEGIN SELECT RAISE(ABORT, 'fixture'); END;",
        )
        .unwrap();

    let updates = [
        (VERSION, Some("2")),
        (PAYLOAD, Some("[\"192.0.2.2\"]")),
        ("z.rejected", Some("blocked")),
    ];
    assert!(store.write_metadata(&updates).is_err());
    let result = store
        .read_metadata(&[VERSION, PAYLOAD, "z.rejected"])
        .unwrap();
    assert_eq!(result[VERSION].as_deref(), Some("1"));
    assert_eq!(result[PAYLOAD].as_deref(), Some("[]"));
    assert!(!result.contains_key("z.rejected"));
}
