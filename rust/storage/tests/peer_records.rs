use pbh_storage::{
    PeerRecordCursor, PeerRecordFilter, PeerRecordObservation, PeerRecordOrder, PeerRecordQuery,
    PeerRecordTorrent, SQLITE_FILENAME, SqliteStore,
};
use serde_json::json;

#[test]
fn access_history_accumulates_after_reset_and_ignores_older_observations() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let geoip = json!({"city": "Fixture"});
    let initial = PeerRecordObservation {
        address: "2001:db8::2".parse().unwrap(),
        port: 2_501,
        downloader_id: "primary",
        torrent: PeerRecordTorrent {
            info_hash: "fixture-hash",
            name: "Fixture torrent",
            size: 1000,
            private: Some(true),
        },
        peer_id: Some("-qB5150-extended"),
        client_name: Some("Fixture"),
        uploaded: 100,
        downloaded: 80,
        upload_speed: 10,
        download_speed: 20,
        last_flags: Some("P"),
        observed_at_ms: 1_000,
        peer_geoip: Some(&geoip),
    };
    let increased = PeerRecordObservation {
        uploaded: 150,
        downloaded: 100,
        observed_at_ms: 2_000,
        ..initial
    };
    let older = PeerRecordObservation {
        port: 9999,
        uploaded: 900,
        client_name: Some("Stale"),
        observed_at_ms: 1_500,
        ..initial
    };
    let reset = PeerRecordObservation {
        port: 6_881,
        uploaded: 20,
        downloaded: 5,
        upload_speed: 44,
        download_speed: 55,
        observed_at_ms: 3_000,
        peer_geoip: None,
        ..initial
    };
    let second = PeerRecordObservation {
        downloader_id: "secondary",
        ..reset
    };
    let third = PeerRecordObservation {
        address: "192.0.2.8".parse().unwrap(),
        ..reset
    };
    store
        .upsert_peer_records(&[initial, increased, older, reset, reset, second, third])
        .unwrap();
    drop(store);

    let mut reopened = SqliteStore::open(&path).unwrap();
    let query = PeerRecordQuery {
        size: 1,
        orders: vec![PeerRecordOrder::parse("uploaded|desc").unwrap()],
        ..PeerRecordQuery::new(PeerRecordFilter::Address(initial.address))
    };
    let page = reopened.peer_history(&query).unwrap();
    assert_eq!((page.page, page.size, page.total), (1, 1, 2));
    let first = &page.records[0];
    assert_eq!(first.downloader_id, "primary");
    assert_eq!(first.peer_id.as_deref(), Some("-qB5150-"));
    assert_eq!(
        (
            first.uploaded,
            first.uploaded_offset,
            first.downloaded,
            first.downloaded_offset
        ),
        (170, 20, 105, 5)
    );
    assert_eq!(
        (first.upload_speed, first.download_speed, first.port),
        (44, 55, 6_881)
    );
    assert_eq!(
        (first.first_time_seen_ms, first.last_time_seen_ms),
        (1_000, 3_000)
    );
    assert_eq!(first.peer_geoip, Some(geoip));
    assert_eq!(first.torrent.as_ref().unwrap().name, "Fixture torrent");

    let torrent_query = PeerRecordQuery::new(PeerRecordFilter::TorrentId(first.torrent_id));
    assert_eq!(reopened.peer_history(&torrent_query).unwrap().total, 3);
    let beyond = PeerRecordQuery { page: 3, ..query };
    let beyond_page = reopened.peer_history(&beyond).unwrap();
    assert_eq!(beyond_page.total, 2);
    assert!(beyond_page.records.is_empty());
    assert!(PeerRecordOrder::parse("not a column").is_err());

    let mut cursor = PeerRecordCursor {
        last_time_seen_ms: 2_999,
        id: i64::MAX,
    };
    for _ in 0..3 {
        let records = reopened.pending_peer_records(cursor, 1).unwrap();
        assert_eq!(records.len(), 1);
        let next = records[0].cursor();
        assert_eq!(next.last_time_seen_ms, 3_000);
        assert!(next.last_time_seen_ms > cursor.last_time_seen_ms || next.id > cursor.id);
        cursor = next;
    }
    assert!(
        reopened
            .pending_peer_records(cursor, 10)
            .unwrap()
            .is_empty()
    );
    assert_eq!(reopened.cleanup_peer_records(3_000).unwrap(), 0);
    assert_eq!(reopened.cleanup_peer_records(3_001).unwrap(), 3);
}
