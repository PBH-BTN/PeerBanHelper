use std::sync::Barrier;

use pbh_storage::{AlertDeduplication, AlertLevel, NewAlert, SQLITE_FILENAME, SqliteStore};
use serde_json::json;

#[test]
fn preserves_alert_messages_read_status_and_retention() {
    let temporary = tempfile::tempdir().unwrap();
    let path = temporary.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let title = json!({"key":"fixture.title","params":["示例"]});
    let content =
        json!({"key":"fixture.content","params":[{"key":"nested.message","params":[42]}]});
    let alert = NewAlert {
        create_at_ms: 1_000,
        level: AlertLevel::Warn,
        identifier: "daily-fixture",
        title: &title,
        content: &content,
    };
    let including_read = AlertDeduplication::IncludingRead;
    let unread = AlertDeduplication::Unread;
    assert!(store.publish_alert(&alert, including_read).unwrap());
    assert!(!store.publish_alert(&alert, unread).unwrap());
    let later = NewAlert {
        identifier: "later",
        level: AlertLevel::Fatal,
        create_at_ms: 2_000,
        ..alert
    };
    assert!(store.publish_alert(&later, unread).unwrap());
    drop(store);

    let mut store = SqliteStore::open(&path).unwrap();
    let alerts = store.unread_alerts().unwrap();
    assert_eq!(
        alerts.iter().map(|record| record.level).max(),
        Some(AlertLevel::Fatal)
    );
    assert_eq!(alerts[0].identifier, later.identifier);
    let saved = &alerts[1];
    assert_eq!(saved.create_at_ms, 1_000);
    assert_eq!(saved.read_at_ms, None);
    assert_eq!(saved.title, title);
    assert_eq!(saved.content, content);
    assert!(store.mark_alert_read(saved.id, 4_000).unwrap());
    assert!(store.mark_alert_read(saved.id, 4_000).unwrap());
    assert!(!store.mark_alert_read(-1, 4_000).unwrap());
    assert!(!store.alert_exists(alert.identifier, unread).unwrap());
    assert!(
        store
            .alert_exists(alert.identifier, including_read)
            .unwrap()
    );
    assert!(!store.publish_alert(&alert, including_read).unwrap());
    let repeated = NewAlert {
        create_at_ms: 4_000,
        ..alert
    };
    assert!(store.publish_alert(&repeated, unread).unwrap());
    assert_eq!(
        store
            .mark_alerts_read_by_identifier(repeated.identifier, 5_000)
            .unwrap(),
        1
    );
    assert_eq!(
        store
            .mark_alerts_read_by_identifier(repeated.identifier, 5_000)
            .unwrap(),
        0
    );
    assert_eq!(store.mark_all_alerts_read(5_000).unwrap(), 1);
    assert!(store.unread_alerts().unwrap().is_empty());
    assert_eq!(store.cleanup_alerts(999).unwrap(), 0);
    assert_eq!(store.cleanup_alerts(1_000).unwrap(), 1);
    assert!(
        store
            .alert_exists(alert.identifier, including_read)
            .unwrap()
    );
    assert!(store.delete_alert(alerts[0].id).unwrap());
    assert!(!store.delete_alert(alerts[0].id).unwrap());
    assert_eq!(store.cleanup_alerts(5_000).unwrap(), 1);
    assert!(
        !store
            .alert_exists(alert.identifier, including_read)
            .unwrap()
    );
}

#[test]
fn competing_sqlite_publications_insert_at_most_one_alert() {
    let temporary = tempfile::tempdir().unwrap();
    let path = temporary.path().join(SQLITE_FILENAME);
    let mut main = SqliteStore::open(&path).unwrap();
    let mut competitor = SqliteStore::open(&path).unwrap();
    let title = json!({"key":"title","params":[]});
    let body = json!({"key":"body","params":[]});
    let alert = NewAlert {
        create_at_ms: 1_000,
        level: AlertLevel::Warn,
        identifier: "daily-fixture",
        title: &title,
        content: &body,
    };
    let barrier = Barrier::new(2);
    std::thread::scope(|scope| {
        let competing = scope.spawn(|| {
            barrier.wait();
            competitor
                .publish_alert(&alert, AlertDeduplication::IncludingRead)
                .unwrap()
        });
        barrier.wait();
        let inserted = main
            .publish_alert(&alert, AlertDeduplication::IncludingRead)
            .unwrap();
        assert_ne!(inserted, competing.join().unwrap());
    });
    assert_eq!(main.unread_alerts().unwrap().len(), 1);
}
