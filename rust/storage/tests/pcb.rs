use pbh_storage::{PcbAddressKey, PcbRangeKey, PcbState, SQLITE_FILENAME, SqliteStore};
use rusqlite::Connection;

#[test]
fn pcb_merges_preserve_first_seen_and_load_through_the_original_keys() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let address = PcbAddressKey {
        address: "2001:db8::2".parse().unwrap(),
        port: 4_321,
        torrent_id: "torrent",
        downloader: "downloader",
    };
    let range = PcbRangeKey {
        range: "2001:db8::/64",
        torrent_id: "torrent",
        downloader: "downloader",
    };
    let different_port = PcbAddressKey {
        port: 3_000,
        ..address
    };
    let different_downloader = PcbAddressKey {
        downloader: "another",
        ..address
    };
    let different_torrent = PcbAddressKey {
        torrent_id: "another",
        ..address
    };
    let range_other_torrent = PcbRangeKey {
        torrent_id: "another",
        ..range
    };
    let first = PcbState {
        last_report_progress: 0.25,
        last_report_uploaded: 12,
        tracking_uploaded_increase_total: 21,
        rewind_counter: 8,
        progress_difference_counter: 4,
        first_time_seen_ms: 3_000,
        last_time_seen_ms: 4_000,
        ban_delay_window_end_at_ms: 5_000,
        fast_pcb_test_execute_at_ms: 6_000,
        last_torrent_completed_size: 7_000,
    };
    let replacement = PcbState {
        last_report_progress: 0.4,
        last_report_uploaded: 9,
        tracking_uploaded_increase_total: 12,
        rewind_counter: 2,
        progress_difference_counter: 1,
        first_time_seen_ms: 9_000,
        last_time_seen_ms: 15_000,
        ban_delay_window_end_at_ms: 16_000,
        fast_pcb_test_execute_at_ms: 17_000,
        last_torrent_completed_size: 18_000,
    };
    assert_eq!(store.load_pcb_address(address).unwrap(), None);
    assert_eq!(store.load_pcb_range(range).unwrap(), None);
    store
        .merge_pcb(
            &[
                (address, first),
                (different_port, first),
                (different_downloader, first),
                (different_torrent, first),
            ],
            &[(range, first), (range_other_torrent, first)],
        )
        .unwrap();
    store
        .merge_pcb(&[(address, replacement)], &[(range, replacement)])
        .unwrap();
    drop(store);

    let mut reopened = SqliteStore::open(&path).unwrap();
    let expected = PcbState {
        first_time_seen_ms: first.first_time_seen_ms,
        ..replacement
    };
    assert_eq!(reopened.load_pcb_address(address).unwrap(), Some(expected));
    assert_eq!(reopened.load_pcb_range(range).unwrap(), Some(expected));
    assert_eq!(
        reopened.load_pcb_address(different_port).unwrap(),
        Some(first)
    );
    assert_eq!(reopened.cleanup_pcb(first.last_time_seen_ms).unwrap(), 0);
    assert_eq!(
        reopened
            .delete_pcb_address(address.torrent_id, address.address)
            .unwrap(),
        3
    );
    assert_eq!(
        reopened
            .delete_pcb_range(range.torrent_id, range.range)
            .unwrap(),
        1
    );
    assert_eq!(reopened.load_pcb_address(address).unwrap(), None);
    assert_eq!(
        reopened.load_pcb_address(different_torrent).unwrap(),
        Some(first)
    );
    assert_eq!(
        reopened.load_pcb_range(range_other_torrent).unwrap(),
        Some(first)
    );
    assert_eq!(
        reopened.cleanup_pcb(first.last_time_seen_ms + 1).unwrap(),
        2
    );
    assert_eq!(reopened.load_pcb_range(range_other_torrent).unwrap(), None);
}

#[test]
fn pcb_nullable_java_rows_use_primitive_defaults_and_failed_batch_rolls_back() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let address = PcbAddressKey {
        address: "192.0.2.14".parse().unwrap(),
        port: 0,
        torrent_id: "fixture",
        downloader: "sqlite",
    };
    let range = PcbRangeKey {
        range: "192.0.2.0/24",
        torrent_id: address.torrent_id,
        downloader: address.downloader,
    };
    let first = PcbState {
        last_report_uploaded: 7,
        tracking_uploaded_increase_total: 12,
        ..Default::default()
    };
    store
        .merge_pcb(&[(address, first)], &[(range, first)])
        .unwrap();
    let sqlite = Connection::open(&path).unwrap();
    sqlite.execute_batch("UPDATE pcb_address SET last_report_uploaded=NULL, tracking_uploaded_increase_total=NULL;
                          UPDATE pcb_range SET last_report_uploaded=NULL, tracking_uploaded_increase_total=NULL;").unwrap();
    assert_eq!(
        store.load_pcb_address(address).unwrap(),
        Some(PcbState::default())
    );
    assert_eq!(
        store.load_pcb_range(range).unwrap(),
        Some(PcbState::default())
    );

    let invalid = PcbState {
        last_report_progress: f64::NAN,
        ..first
    };
    assert!(
        store
            .merge_pcb(&[(address, first)], &[(range, invalid)])
            .is_err()
    );
    assert_eq!(
        store.load_pcb_address(address).unwrap(),
        Some(PcbState::default())
    );
}
