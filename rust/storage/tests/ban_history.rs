use pbh_storage::{
    BanHistoryEntry, BanHistoryFilter, BanHistoryOrder, BanHistoryQuery, HistoryTorrent,
    SQLITE_FILENAME, SqliteHistoryReader, SqliteStore,
};
use serde_json::json;

#[test]
fn history_pagination_filters_and_sorts_without_writing() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    assert!(SqliteHistoryReader::open(&path).is_err());
    assert!(!path.exists());
    let mut store = SqliteStore::open(&path).unwrap();
    let ip = "2001:db8::3".parse().unwrap();
    let rule = json!({"key": "RULE", "params": []});
    let description = json!({"key": "DESCRIPTION", "params": ["2001:db8::3"]});
    let geoip = json!({"city": null});
    let older = BanHistoryEntry {
        address: ip,
        ban_at_ms: 50,
        unban_at_ms: 500,
        port: 6882,
        peer_id: None,
        peer_client_name: Some("older"),
        peer_uploaded: Some(100),
        peer_downloaded: None,
        peer_progress: 0.3,
        torrent: HistoryTorrent {
            info_hash: "first",
            name: "First torrent",
            size: 1_234,
            private: Some(true),
            progress: 1.0,
        },
        module_name: "FixtureRule",
        rule: &rule,
        description: &description,
        flags: None,
        downloader: "fixtureClient",
        structured_data: None,
        peer_geoip: Some(&geoip),
    };
    let current = BanHistoryEntry {
        ban_at_ms: 150,
        port: 6883,
        ..older
    };
    let other = BanHistoryEntry {
        address: "192.0.2.9".parse().unwrap(),
        ban_at_ms: 250,
        peer_uploaded: Some(10),
        torrent: HistoryTorrent {
            info_hash: "second",
            name: "Other torrent",
            ..older.torrent
        },
        ..older
    };
    store
        .commit_ban_wave(None, &[older, current, other])
        .unwrap();
    assert_eq!(
        store
            .ban_history(&BanHistoryQuery::default())
            .unwrap()
            .total,
        3
    );
    drop(store);
    let original = std::fs::read(&path).unwrap();

    let reader = SqliteHistoryReader::open(&path).unwrap();
    let latest = reader
        .ban_history(&BanHistoryQuery {
            size: 1,
            ..Default::default()
        })
        .unwrap();
    assert_eq!((latest.page, latest.size, latest.total), (1, 1, 3));
    assert_eq!(latest.records[0].ban_at_ms, 250);
    assert_eq!(
        latest.records[0].torrent.as_ref().unwrap().info_hash,
        "second"
    );

    let second = reader
        .ban_history(&BanHistoryQuery {
            page: 2,
            size: 1,
            orders: vec![BanHistoryOrder::parse("peerUploaded|descend").unwrap()],
            filter: None,
        })
        .unwrap();
    assert_eq!(second.records[0].ban_at_ms, 50);
    assert_eq!(second.records[0].description, description);
    assert_eq!(second.records[0].peer_geoip, Some(geoip));

    let repeated = reader
        .ban_history(&BanHistoryQuery {
            page: 2,
            size: 1,
            orders: vec![
                BanHistoryOrder::parse("peerUploaded|descend").unwrap(),
                BanHistoryOrder::parse("peer_uploaded|asc").unwrap(),
                BanHistoryOrder::parse("peerPort|desc").unwrap(),
            ],
            filter: None,
        })
        .unwrap();
    assert_eq!(repeated.records[0].port, 6883);

    let ipv6 = reader
        .ban_history(&BanHistoryQuery {
            page: 3,
            size: 1,
            filter: Some(BanHistoryFilter::Address(ip)),
            ..Default::default()
        })
        .unwrap();
    assert_eq!(ipv6.total, 2);
    assert!(ipv6.records.is_empty());
    let torrent = reader
        .ban_history(&BanHistoryQuery {
            filter: Some(BanHistoryFilter::TorrentId(second.records[0].torrent_id)),
            ..Default::default()
        })
        .unwrap();
    assert_eq!(torrent.records.len(), 2);
    assert!(torrent.records.iter().all(|row| row.address == ip));
    let after_id = reader
        .ban_history(&BanHistoryQuery {
            filter: Some(BanHistoryFilter::AfterId(second.records[0].id)),
            orders: vec![BanHistoryOrder::parse("id|asc").unwrap()],
            ..Default::default()
        })
        .unwrap();
    assert_eq!(after_id.total, 2);
    assert!(
        after_id
            .records
            .windows(2)
            .all(|records| records[0].id < records[1].id)
    );
    let capped = reader
        .ban_history(&BanHistoryQuery {
            size: 2_000,
            ..Default::default()
        })
        .unwrap();
    assert_eq!(
        (capped.page, capped.size, capped.total, capped.records.len()),
        (1, 1_000, 3, 3)
    );
    let uncounted = reader
        .ban_history(&BanHistoryQuery {
            page: -1,
            size: -1,
            ..Default::default()
        })
        .unwrap();
    assert_eq!(
        (
            uncounted.page,
            uncounted.size,
            uncounted.total,
            uncounted.records.len()
        ),
        (1, 1_000, 0, 3)
    );
    assert_eq!(std::fs::read(&path).unwrap(), original);
}

#[test]
fn history_sort_rejects_unknown_or_non_column_terms() {
    assert!(BanHistoryOrder::parse("not_a_column|asc").is_err());
    assert!(BanHistoryOrder::parse("peerIp DESC|asc").is_err());
}
