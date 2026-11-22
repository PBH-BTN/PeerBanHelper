use pbh_storage::{BanHistoryEntry, HistoryFieldMetric, HistoryTimestampCount, HistoryTorrent};
use serde_json::Value;

pub const FIRST: i64 = 1_700_100_000_000;
pub const END: i64 = FIRST + 1_000;
pub const UNBAN: i64 = FIRST + 100_000;
pub const DOWNLOADER: &str = "metrics-fixture";

pub fn bans(message: &Value) -> [BanHistoryEntry<'_>; 6] {
    let first = BanHistoryEntry {
        address: "2001:db8::9".parse().unwrap(),
        ban_at_ms: FIRST,
        unban_at_ms: UNBAN,
        port: 6881,
        peer_id: Some("-AB12345-first"),
        peer_client_name: None,
        peer_uploaded: Some(7),
        peer_downloaded: None,
        peer_progress: 0.5,
        torrent: HistoryTorrent {
            info_hash: "metrics-fixture-torrent",
            name: "Historical torrent",
            size: 1,
            private: None,
            progress: 0.25,
        },
        module_name: "FIXTURE",
        rule: message,
        description: message,
        flags: None,
        downloader: DOWNLOADER,
        structured_data: None,
        peer_geoip: None,
    };
    let last = BanHistoryEntry {
        ban_at_ms: END,
        peer_id: Some("-AB12345-second"),
        ..first
    };
    let different = BanHistoryEntry {
        ban_at_ms: END + 1_000,
        peer_id: Some("-CD12345-test"),
        peer_uploaded: Some(21),
        ..first
    };
    let null = BanHistoryEntry {
        peer_id: None,
        peer_uploaded: None,
        ..different
    };
    let empty = BanHistoryEntry {
        peer_id: Some(""),
        peer_uploaded: Some(0),
        ..different
    };
    let other = BanHistoryEntry {
        downloader: "other-metrics",
        ..first
    };
    [first, last, different, null, empty, other]
}

pub fn assert_peer_counts(counts: &[HistoryFieldMetric]) {
    assert_eq!(counts.len(), 2);
    assert_eq!(counts[0].data, "-AB12345");
    assert_eq!(counts[0].count, 2);
    assert_eq!(counts[0].percent, 2.0 / 3.0);
    assert_eq!(counts[1].data, "-CD12345");
    assert_eq!(counts[1].count, 1);
    assert_eq!(counts[1].percent, 1.0 / 3.0);
}

pub fn assert_sums(counts: &[HistoryFieldMetric]) {
    assert_eq!(
        counts,
        [
            HistoryFieldMetric {
                data: "21".into(),
                count: 21,
                percent: 0.6
            },
            HistoryFieldMetric {
                data: "7".into(),
                count: 14,
                percent: 0.4
            },
        ]
    );
}

pub fn assert_unban_counts(counts: &[HistoryTimestampCount]) {
    assert_eq!(
        counts,
        [HistoryTimestampCount {
            timestamp_ms: UNBAN,
            count: 2
        }]
    );
}
