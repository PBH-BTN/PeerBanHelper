#[path = "support/tracked_swarm.rs"]
mod fixture;

use fixture::{CURSOR_KEY, LAST_SEEN, assert_details, details_query, observations};
use pbh_storage::{SQLITE_FILENAME, SqliteStore, TrackedSwarmCursor, TrackedSwarmOrder};
use rusqlite::Connection;

#[test]
fn tracks_current_session_with_the_java_btn_cursor_and_keeps_sequence_on_reset() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    assert_eq!(store.reset_tracked_swarm().unwrap(), 0);
    let peers = observations();
    store.upsert_tracked_swarm(&peers[..1]).unwrap();
    let initial_id = store.tracked_swarm(&details_query()).unwrap().records[0].id;
    store.upsert_tracked_swarm(&peers[1..]).unwrap();
    drop(store);

    let mut store = SqliteStore::open(&path).unwrap();
    assert_eq!(store.tracked_swarm_count().unwrap(), 3);
    let details = store.tracked_swarm(&details_query()).unwrap();
    assert_details(&details);
    assert_eq!(details.records[0].id, initial_id);
    let first = store
        .pending_tracked_swarm(
            TrackedSwarmCursor {
                last_time_seen_ms: LAST_SEEN,
                id: 0,
            },
            1,
        )
        .unwrap();
    assert_eq!(first[0].id, initial_id);
    let pending = store
        .pending_tracked_swarm(first[0].cursor(), 1000)
        .unwrap();
    assert_eq!(pending.len(), 1);
    assert_eq!(pending[0].port, 6882);
    assert!(
        store
            .pending_tracked_swarm(pending[0].cursor(), 1000)
            .unwrap()
            .is_empty()
    );
    assert!(
        store
            .pending_tracked_swarm(TrackedSwarmCursor::default(), 0)
            .unwrap()
            .is_empty()
    );
    let all = store
        .pending_tracked_swarm(TrackedSwarmCursor::default(), 1000)
        .unwrap();
    let latest_id = all.iter().map(|record| record.id).max().unwrap();
    let cursor = format!("{},{}", LAST_SEEN, pending[0].id);
    store
        .write_metadata(&[(CURSOR_KEY, Some(&cursor))])
        .unwrap();
    assert_eq!(store.reset_tracked_swarm().unwrap(), 3);
    assert_eq!(store.tracked_swarm_count().unwrap(), 0);
    assert_eq!(
        store.read_metadata(&[CURSOR_KEY]).unwrap()[CURSOR_KEY],
        Some(cursor)
    );
    store.upsert_tracked_swarm(&[peers[2]]).unwrap();
    let next = store
        .pending_tracked_swarm(pending[0].cursor(), 1000)
        .unwrap();
    assert!(next[0].id > latest_id);
    assert_eq!((next[0].uploaded, next[0].downloaded), (20, 5));

    let mut query = details_query();
    query.page = 2;
    let last = store.tracked_swarm(&query).unwrap();
    assert_eq!(last.total, 1);
    assert!(last.records.is_empty());
    assert!(TrackedSwarmOrder::parse("uploaded desc, id|asc").is_err());
}

#[test]
fn rolls_back_all_observations_if_the_sqlite_batch_fails() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let setup = Connection::open(&path).unwrap();
    setup.execute_batch("CREATE TRIGGER reject_fixture BEFORE INSERT ON tracked_swarm WHEN NEW.port = 6882 BEGIN SELECT RAISE(ABORT, 'fixture'); END").unwrap();
    let peers = observations();
    assert!(store.upsert_tracked_swarm(&[peers[0], peers[3]]).is_err());
    assert_eq!(store.tracked_swarm_count().unwrap(), 0);
}
