#[path = "support/history_http.rs"]
mod fixture;

use fixture::{FIRST_BAN, PREFIX_MATCH, TORRENT_HASH, assert_first_rank, bans, peer};
use pbh_storage::{BanRankQuery, SQLITE_FILENAME, SqliteStore, TorrentSearchQuery};
use serde_json::json;

#[test]
fn ranks_distinct_addresses_filters_wildcards_and_paginates_counts() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut storage = SqliteStore::open(&path).unwrap();
    assert!(
        storage
            .ban_ranks(&BanRankQuery::default())
            .unwrap()
            .records
            .is_empty()
    );
    storage
        .commit_ban_wave(None, &bans(&json!({"key": "fixture", "params": []})))
        .unwrap();
    storage.upsert_peer_records(&[peer()]).unwrap();
    let initial = storage.ban_ranks(&BanRankQuery::default()).unwrap();
    assert_first_rank(&initial, 3);
    assert_eq!(
        initial
            .records
            .iter()
            .map(|rank| rank.count)
            .collect::<Vec<_>>(),
        [3, 2, 1]
    );
    assert_eq!(
        storage
            .ban_ranks(&BanRankQuery {
                filter: Some(String::new()),
                ..Default::default()
            })
            .unwrap(),
        initial
    );

    let mut query = BanRankQuery {
        filter: Some(PREFIX_MATCH.into()),
        size: 1,
        ..Default::default()
    };
    let first = storage.ban_ranks(&query).unwrap();
    assert_first_rank(&first, 2);
    assert_eq!(first.records.len(), 1);
    query.page = 2;
    assert_eq!(
        storage.ban_ranks(&query).unwrap().records[0]
            .peer_ip
            .as_deref(),
        Some("192.0.3.21")
    );
    query.page = 3;
    let outside = storage.ban_ranks(&query).unwrap();
    assert_eq!(outside.total, 2);
    assert!(outside.records.is_empty());
    query.page = 1;
    query.size = -1;
    let without_count = storage.ban_ranks(&query).unwrap();
    assert_eq!(
        (
            without_count.total,
            without_count.size,
            without_count.records.len()
        ),
        (0, 1000, 2)
    );
    assert!(
        storage
            .ban_ranks(&BanRankQuery {
                filter: Some(" ".into()),
                ..Default::default()
            })
            .unwrap()
            .records
            .is_empty()
    );

    let summary = storage
        .torrent_history_summary(TORRENT_HASH)
        .unwrap()
        .unwrap();
    assert_eq!(
        (
            summary.ban_count,
            summary.access_count,
            summary.torrent.private
        ),
        (6, 1, None)
    );
    assert!(
        storage
            .torrent_history_summary("missing")
            .unwrap()
            .is_none()
    );
}

#[test]
fn retention_deletes_through_inclusive_cutoff_in_multiple_batches() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(SQLITE_FILENAME);
    let mut storage = SqliteStore::open(&path).unwrap();
    let message = json!({"key": "fixture", "params": []});
    let events = bans(&message);
    storage.commit_ban_wave(None, &[events[0]; 201]).unwrap();
    storage.commit_ban_wave(None, &events[1..]).unwrap();
    storage.upsert_peer_records(&[peer()]).unwrap();
    assert_eq!(storage.cleanup_ban_history(FIRST_BAN - 1).unwrap(), 0);
    assert_eq!(storage.cleanup_ban_history(FIRST_BAN).unwrap(), 201);
    let summary = storage
        .torrent_history_summary(TORRENT_HASH)
        .unwrap()
        .unwrap();
    assert_eq!((summary.ban_count, summary.access_count), (5, 1));
}

#[test]
fn searches_torrents_by_name_hash_and_count_and_summarizes_peers() {
    use fixture::{ARCHIVE_HASH, BETA_HASH, FIRST_ADDRESS, assert_known_peer, discovery_peers};

    let directory = tempfile::tempdir().unwrap();
    let mut storage = SqliteStore::open(directory.path().join(SQLITE_FILENAME)).unwrap();
    let message = json!({"key":"fixture","params":[]});
    storage.commit_ban_wave(None, &bans(&message)).unwrap();
    storage.upsert_peer_records(&discovery_peers()).unwrap();
    let default = TorrentSearchQuery::new(2, 2, None, &[]).unwrap();
    let page = storage.search_torrents(&default).unwrap();
    assert_eq!((page.page, page.total, page.records.len()), (2, 3, 1));
    assert_eq!(page.records[0].torrent.info_hash, TORRENT_HASH);
    for term in ["Zulu", "archive-fixt%"] {
        let query = TorrentSearchQuery::new(1, 10, Some(term.into()), &[]).unwrap();
        let page = storage.search_torrents(&query).unwrap();
        assert_eq!(page.total, 1);
        assert_eq!(page.records[0].torrent.info_hash, ARCHIVE_HASH);
    }
    let normal =
        TorrentSearchQuery::new(1, 10, None, &["name|desc".into(), "name|asc".into()]).unwrap();
    assert_eq!(
        storage.search_torrents(&normal).unwrap().records[0]
            .torrent
            .info_hash,
        BETA_HASH
    );
    let count = TorrentSearchQuery::new(
        1,
        2,
        None,
        &[
            "ignored|asc".into(),
            "peerAccessCount|descend".into(),
            "peerBanCount|desc".into(),
        ],
    )
    .unwrap();
    let page = storage.search_torrents(&count).unwrap();
    assert_eq!((page.total, page.records.len()), (3, 2));
    assert_eq!(page.records[0].torrent.info_hash, BETA_HASH);
    assert_eq!(page.records[1].torrent.info_hash, ARCHIVE_HASH);
    assert!(TorrentSearchQuery::new(1, 10, None, &["name; DROP TABLE torrents".into()]).is_err());

    let known = storage
        .peer_history_summary(FIRST_ADDRESS.parse().unwrap())
        .unwrap();
    assert_known_peer(known);
    let ban_only = storage
        .peer_history_summary("2001:db8::4".parse().unwrap())
        .unwrap();
    assert!(ban_only.found());
    assert_eq!(
        (
            ban_only.ban_count,
            ban_only.torrent_access_count,
            ban_only.uploaded_to_peer,
            ban_only.downloaded_from_peer,
            ban_only.first_time_seen_ms,
            ban_only.last_time_seen_ms
        ),
        (2, 0, -1, -1, 0, 0)
    );
    let unknown = storage
        .peer_history_summary("198.51.100.211".parse().unwrap())
        .unwrap();
    assert!(!unknown.found());
    assert_eq!(
        (unknown.uploaded_to_peer, unknown.last_time_seen_ms),
        (-1, 0)
    );
}
