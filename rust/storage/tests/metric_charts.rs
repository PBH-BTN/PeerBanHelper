#[path = "support/metric_charts.rs"]
mod fixture;

use fixture::{DOWNLOADER, END, FIRST, assert_peer_counts, assert_sums, assert_unban_counts, bans};
use pbh_storage::{
    HistoryMetricField, HistoryMetricOperation, HistoryTimestamp, HistoryTimestampCount,
    SqliteStore,
};
use serde_json::json;

#[test]
fn groups_allowed_fields_applies_strict_threshold_and_filters_empty_keys() {
    let mut store = SqliteStore::open(":memory:").unwrap();
    store
        .commit_ban_wave(None, &bans(&json!({"key":"fixture","params":[]})))
        .unwrap();
    let peer_id = HistoryMetricField::parse("peerId").unwrap();
    let count = HistoryMetricOperation::Count;
    assert_peer_counts(
        &store
            .history_field_metrics(peer_id, count, 0.0, Some(DOWNLOADER))
            .unwrap(),
    );
    let threshold = store
        .history_field_metrics(peer_id, count, 1.0 / 3.0, Some(DOWNLOADER))
        .unwrap();
    assert_eq!(threshold.len(), 1);
    assert_eq!(threshold[0].count, 2);
    assert!(
        store
            .history_field_metrics(peer_id, count, 0.0, Some(" "))
            .unwrap()
            .is_empty()
    );
    let torrent = HistoryMetricField::parse("torrent_name").unwrap();
    let all = store
        .history_field_metrics(torrent, count, 0.0, Some(""))
        .unwrap();
    assert_eq!(
        (all[0].data.as_str(), all[0].count, all[0].percent),
        ("Historical torrent", 6, 1.0)
    );

    let uploaded = HistoryMetricField::parse("peer_uploaded").unwrap();
    let sum = HistoryMetricOperation::Sum;
    assert_sums(
        &store
            .history_field_metrics(uploaded, sum, 0.0, Some(DOWNLOADER))
            .unwrap(),
    );
    assert!(
        store
            .history_field_metrics(uploaded, count, 0.0, Some(DOWNLOADER))
            .unwrap()
            .iter()
            .any(|group| group.data == "0")
    );
    let progress = HistoryMetricField::parse("peerProgress").unwrap();
    let fractional = store
        .history_field_metrics(progress, sum, 0.0, Some(DOWNLOADER))
        .unwrap();
    assert_eq!((fractional[0].count, fractional[0].percent), (2, 1.0));
    assert!(
        store
            .history_field_metrics(peer_id, sum, 0.0, None)
            .is_err()
    );
    assert!(HistoryMetricField::parse("h.peer_id + 1").is_err());
}

#[test]
fn filters_on_ban_time_even_when_bucketing_unban_time() {
    let mut store = SqliteStore::open(":memory:").unwrap();
    store
        .commit_ban_wave(None, &bans(&json!({"key":"fixture","params":[]})))
        .unwrap();
    let mut counts = store
        .history_timestamp_counts(HistoryTimestamp::BanAt, FIRST, END, None)
        .unwrap();
    counts.sort_by_key(|row| row.timestamp_ms);
    assert_eq!(
        counts,
        [
            HistoryTimestampCount {
                timestamp_ms: FIRST,
                count: 2
            },
            HistoryTimestampCount {
                timestamp_ms: END,
                count: 1
            }
        ]
    );
    assert_unban_counts(
        &store
            .history_timestamp_counts(HistoryTimestamp::UnbanAt, FIRST, END, Some(DOWNLOADER))
            .unwrap(),
    );
    assert!(
        store
            .history_timestamp_counts(HistoryTimestamp::BanAt, END, FIRST, None)
            .unwrap()
            .is_empty()
    );
}
