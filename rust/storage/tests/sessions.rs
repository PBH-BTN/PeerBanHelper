#[path = "support/sessions.rs"]
mod fixture;

use fixture::{TODAY, YESTERDAY, assert_first_day, observations};
use pbh_storage::{PeerSessionObservation, SQLITE_FILENAME, SqliteStore};
use rusqlite::Connection;

#[test]
fn consumes_pending_sessions_and_preserves_daily_bucket_merge_rules() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY, TODAY)
            .unwrap(),
        0
    );
    let first = observations();
    store.upsert_peer_sessions(&first[..1]).unwrap();
    store.upsert_peer_sessions(&first[1..]).unwrap();
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY, TODAY)
            .unwrap(),
        0
    );
    drop(store);

    let mut store = SqliteStore::open(&path).unwrap();
    assert_eq!(store.aggregate_peer_sessions(TODAY).unwrap(), 4);
    assert_eq!(store.aggregate_peer_sessions(TODAY).unwrap(), 0);
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY, TODAY)
            .unwrap(),
        4
    );
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY + 1, TODAY)
            .unwrap(),
        3
    );
    assert_first_day(
        &store
            .connection_metrics(YESTERDAY, TODAY, Some(" "))
            .unwrap(),
    );
    let primary = store
        .connection_metrics(TODAY, TODAY, Some("primary"))
        .unwrap();
    assert_eq!(primary[0].metrics.total_connections, 2);
    assert!(
        store
            .connection_metrics(TODAY, TODAY, Some("missing"))
            .unwrap()
            .is_empty()
    );

    let older = PeerSessionObservation {
        port: 9_999,
        ..first[4]
    };
    store.upsert_peer_sessions(&[first[2], older]).unwrap();
    assert_eq!(store.aggregate_peer_sessions(TODAY).unwrap(), 2);
    let updated = store.connection_metrics(YESTERDAY, TODAY, None).unwrap();
    assert_eq!(updated[0].metrics.total_connections, 4);
    assert_eq!(updated[0].metrics.question_status, 2);
    assert_eq!(updated[0].metrics.local_not_interested, 1);
    assert_eq!(updated[1].metrics.total_connections, 1);
    assert_eq!(store.cleanup_connection_metrics(YESTERDAY - 1).unwrap(), 0);
    assert_eq!(store.cleanup_connection_metrics(YESTERDAY).unwrap(), 1);
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY, TODAY)
            .unwrap(),
        4
    );
}

#[test]
fn failed_aggregation_rolls_back_metrics_and_can_retry_pending_rows() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let raw = Connection::open(&path).unwrap();
    raw.execute_batch("CREATE TRIGGER reject_metrics BEFORE INSERT ON peer_connection_metrics WHEN NEW.downloader = 'secondary' BEGIN SELECT RAISE(ABORT, 'fixture'); END").unwrap();
    store.upsert_peer_sessions(&observations()).unwrap();
    assert!(store.aggregate_peer_sessions(TODAY).is_err());
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY, TODAY)
            .unwrap(),
        0
    );
    raw.execute_batch("DROP TRIGGER reject_metrics").unwrap();
    assert_eq!(store.aggregate_peer_sessions(TODAY).unwrap(), 4);
    assert_first_day(&store.connection_metrics(YESTERDAY, TODAY, None).unwrap());
}
