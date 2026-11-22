use pbh_storage::{
    BanHistoryEntry, ChartRange, HistoryTorrent, PeerRecordObservation, PeerRecordTorrent,
};
use serde_json::Value;

pub const START: i64 = 1_701_000_000_000;
pub const END: i64 = START + 1_000;
pub const DOWNLOADER: &str = "peer-chart-fixture";

pub fn range() -> ChartRange<'static> {
    ChartRange {
        start_ms: START,
        end_ms: END,
        downloader: Some(DOWNLOADER),
    }
}

pub fn peers() -> [PeerRecordObservation<'static>; 7] {
    let first = PeerRecordObservation {
        address: "2001:db8::1".parse().unwrap(),
        port: 6881,
        downloader_id: DOWNLOADER,
        torrent: PeerRecordTorrent {
            info_hash: "chart-torrent-a",
            name: "A",
            size: 10,
            private: None,
        },
        peer_id: Some("-AB12345"),
        client_name: Some("Alpha"),
        uploaded: 30,
        downloaded: 11,
        upload_speed: 0,
        download_speed: 0,
        last_flags: Some("DI"),
        observed_at_ms: START,
        peer_geoip: None,
    };
    let second = PeerRecordObservation {
        torrent: PeerRecordTorrent {
            info_hash: "chart-torrent-b",
            name: "B",
            ..first.torrent
        },
        uploaded: 40,
        observed_at_ms: END,
        ..first
    };
    let beta = PeerRecordObservation {
        address: "198.51.100.2".parse().unwrap(),
        peer_id: Some("-PS00001"),
        client_name: Some("Beta"),
        last_flags: Some("D"),
        uploaded: 20,
        observed_at_ms: END,
        ..first
    };
    let no_transfer = PeerRecordObservation {
        address: "198.51.100.3".parse().unwrap(),
        uploaded: 0,
        last_flags: None,
        ..beta
    };
    let crossing = PeerRecordObservation {
        address: "198.51.100.4".parse().unwrap(),
        observed_at_ms: START - 1_000,
        ..first
    };
    let crossing_latest = PeerRecordObservation {
        observed_at_ms: END + 1_000,
        ..crossing
    };
    let other = PeerRecordObservation {
        address: "198.51.100.5".parse().unwrap(),
        downloader_id: "other-peer-chart-fixture",
        ..first
    };
    [
        first,
        second,
        beta,
        no_transfer,
        crossing,
        crossing_latest,
        other,
    ]
}

pub fn bans(message: &Value) -> [BanHistoryEntry<'_>; 3] {
    let first = BanHistoryEntry {
        address: "2001:db8::9".parse().unwrap(),
        ban_at_ms: START,
        unban_at_ms: END + 1_000,
        port: 6881,
        peer_id: None,
        peer_client_name: None,
        peer_uploaded: None,
        peer_downloaded: None,
        peer_progress: 0.0,
        torrent: HistoryTorrent {
            info_hash: "chart-torrent-a",
            name: "A",
            size: 10,
            private: None,
            progress: 0.0,
        },
        module_name: "Fixture",
        rule: message,
        description: message,
        flags: None,
        downloader: DOWNLOADER,
        structured_data: None,
        peer_geoip: None,
    };
    let second = BanHistoryEntry {
        ban_at_ms: END,
        ..first
    };
    let outside = BanHistoryEntry {
        address: "198.51.100.9".parse().unwrap(),
        // Published MySQL history columns store seconds rather than milliseconds.
        ban_at_ms: END + 1_000,
        ..first
    };
    [first, second, outside]
}

macro_rules! exercise {
    ($store:expr $(, $wait:ident)?) => {{
        use pbh_storage::{ChartAddressSource, ClientAnalysisOrder, HistoryTimestampCount};
        let store = $store;
        let range = $crate::peer_charts_fixture::range();
        let peers = $crate::peer_charts_fixture::peers();
        let message = serde_json::json!({"key": "FIXTURE", "params": []});
        let bans = $crate::peer_charts_fixture::bans(&message);
        store.upsert_peer_records(&peers)$(.$wait)?.unwrap();
        store.commit_ban_wave(None, &bans)$(.$wait)?.unwrap();
        let mut timestamps = store.peer_chart_timestamps(range)$(.$wait)?.unwrap();
        timestamps.sort_by_key(|row| row.timestamp_ms);
        assert_eq!(timestamps, [
            HistoryTimestampCount { timestamp_ms: range.start_ms, count: 1 },
            HistoryTimestampCount { timestamp_ms: range.end_ms, count: 3 },
        ]);
        let addresses = store.chart_addresses(range, ChartAddressSource::PeerHistory)$(.$wait)?.unwrap();
        assert_eq!(addresses.len(), 3, "distinct addresses from peer history");
        assert!(addresses.iter().any(|address| address.starts_with("2001:db8:")));
        let addresses = store.chart_addresses(range, ChartAddressSource::BanHistory)$(.$wait)?.unwrap();
        assert_eq!(addresses.len(), 1, "distinct addresses from ban history");
        assert!(addresses[0].starts_with("2001:db8:"));
        let sessions = store.session_spans(range)$(.$wait)?.unwrap();
        assert_eq!(sessions.len(), 4);
        assert!(sessions.iter().any(|row| row.first_seen_ms == range.start_ms && row.flags.as_deref() == Some("DI")));
        assert_eq!(store.session_address_count(range)$(.$wait)?.unwrap(), 4);
        let missing = pbh_storage::ChartRange { downloader: Some("missing'pbh"), ..range };
        assert_eq!(store.session_address_count(missing)$(.$wait)?.unwrap(), 0);
        let clients = store.client_analysis(range, 1, 1, &[])$(.$wait)?.unwrap();
        assert_eq!(clients.total, 2);
        let alpha = &clients.rows[0];
        assert_eq!(alpha.peer_id.as_deref(), Some("-AB12345"));
        assert_eq!(alpha.client_name.as_deref(), Some("Alpha"));
        assert_eq!((alpha.uploaded, alpha.downloaded, alpha.count), (70, 22, 2));
        let clients = store.client_analysis(range, 2, 1, &[])$(.$wait)?.unwrap();
        assert_eq!(clients.rows[0].client_name.as_deref(), Some("Beta"));
        let orders = [ClientAnalysisOrder::parse("count|asc").unwrap()];
        let clients = store.client_analysis(range, 1, 1, &orders)$(.$wait)?.unwrap();
        assert_eq!(clients.rows[0].client_name.as_deref(), Some("Beta"));
        assert!(store.client_analysis(range, i64::MAX, 2, &[])$(.$wait)?.is_err());
        assert!(ClientAnalysisOrder::parse("uploaded desc, downloads").is_err());
    }};
}

pub(crate) use exercise;
