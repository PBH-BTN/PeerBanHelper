use pbh_storage::{SQLITE_FILENAME, SqliteStore};

#[test]
fn preserves_java_type_filter_and_different_latest_and_page_order() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    assert!(store.latest_subscription_log("fixture").unwrap().is_none());
    store
        .append_subscription_log("fixture", 2_000, 12, false)
        .unwrap();
    store
        .append_subscription_log("other", 3_000, 20, true)
        .unwrap();
    store
        .append_subscription_log("fixture", 1_000, 2, true)
        .unwrap();
    store
        .append_subscription_log("third", 3_000, 24, false)
        .unwrap();
    drop(store);

    let store = SqliteStore::open(&path).unwrap();
    let latest = store.latest_subscription_log("fixture").unwrap().unwrap();
    assert_eq!(
        (
            latest.rule_id.as_str(),
            latest.update_ms,
            latest.count,
            latest.manual
        ),
        ("fixture", 1_000, 2, true)
    );
    let first = store.subscription_logs(Some("fixture"), 1, 1).unwrap();
    assert_eq!((first.page, first.size, first.total), (1, 1, 2));
    assert_eq!(
        (
            first.records[0].update_ms,
            first.records[0].count,
            first.records[0].manual
        ),
        (2_000, 12, false)
    );
    let second = store.subscription_logs(Some("fixture"), 2, 1).unwrap();
    assert_eq!(second.records, [latest]);
    let past_end = store.subscription_logs(Some("fixture"), 3, 1).unwrap();
    assert_eq!(past_end.total, 2);
    assert!(past_end.records.is_empty());
    assert_eq!(
        store.subscription_logs(Some("fixt"), 1, 10).unwrap().total,
        0
    );
    let combined = store.subscription_logs(None, 1, 10).unwrap();
    assert_eq!(combined.total, 4);
    assert_eq!(combined.records[0].rule_id, "third");
    assert_eq!(combined.records[1].rule_id, "other");
}
