use pbh_storage::{
    BanHistoryEntry, BanRankPage, HistoryTorrent, PeerHistorySummary, PeerRecordObservation,
    PeerRecordTorrent,
};
use serde_json::Value;

pub const FIRST_BAN: i64 = 1_728_000_000_000;
pub const PREFIX_MATCH: &str = "192.0.%";
pub const FIRST_ADDRESS: &str = "192.0.2.21";
pub const TORRENT_HASH: &str = "rank-fixture-torrent";
pub const ARCHIVE_HASH: &str = "archive-fixture-torrent";
pub const BETA_HASH: &str = "beta-fixture-torrent";

pub fn bans(message: &Value) -> [BanHistoryEntry<'_>; 6] {
    let first = BanHistoryEntry {
        address: FIRST_ADDRESS.parse().unwrap(),
        ban_at_ms: FIRST_BAN,
        unban_at_ms: FIRST_BAN + 10_000,
        port: 6881,
        peer_id: None,
        peer_client_name: None,
        peer_uploaded: None,
        peer_downloaded: None,
        peer_progress: 0.5,
        torrent: HistoryTorrent {
            info_hash: TORRENT_HASH,
            name: "Fixture torrent",
            size: 1_024,
            private: None,
            progress: 0.9,
        },
        module_name: "FIXTURE",
        rule: message,
        description: message,
        flags: None,
        downloader: "test",
        structured_data: None,
        peer_geoip: None,
    };
    let later = BanHistoryEntry {
        ban_at_ms: FIRST_BAN + 1_000,
        ..first
    };
    let v6 = BanHistoryEntry {
        address: "2001:db8::4".parse().unwrap(),
        ..later
    };
    let second_v4 = BanHistoryEntry {
        address: "192.0.3.21".parse().unwrap(),
        ..later
    };
    [first, later, later, v6, v6, second_v4]
}

pub fn peer() -> PeerRecordObservation<'static> {
    PeerRecordObservation {
        address: FIRST_ADDRESS.parse().unwrap(),
        port: 6881,
        downloader_id: "test",
        torrent: PeerRecordTorrent {
            info_hash: TORRENT_HASH,
            name: "Fixture torrent",
            size: 1_024,
            private: None,
        },
        peer_id: None,
        client_name: None,
        uploaded: 42,
        downloaded: 10,
        upload_speed: 0,
        download_speed: 0,
        last_flags: None,
        observed_at_ms: FIRST_BAN,
        peer_geoip: None,
    }
}

pub fn assert_first_rank(page: &BanRankPage, total: u64) {
    assert_eq!((page.page, page.total), (1, total));
    assert_eq!(page.records[0].peer_ip.as_deref(), Some(FIRST_ADDRESS));
    assert_eq!(page.records[0].count, 3);
}

pub fn discovery_peers() -> [PeerRecordObservation<'static>; 4] {
    let initial = peer();
    let archive = PeerRecordObservation {
        torrent: PeerRecordTorrent {
            info_hash: ARCHIVE_HASH,
            name: "Zulu Archive",
            size: 2_048,
            private: Some(true),
        },
        uploaded: 7,
        downloaded: 2,
        observed_at_ms: FIRST_BAN + 1_000,
        ..initial
    };
    let beta = PeerRecordObservation {
        address: "2001:db8::15".parse().unwrap(),
        torrent: PeerRecordTorrent {
            info_hash: BETA_HASH,
            name: "Beta Swarm",
            size: 3_072,
            private: Some(false),
        },
        ..initial
    };
    let second_beta = PeerRecordObservation {
        address: "2001:db8::16".parse().unwrap(),
        ..beta
    };
    [initial, archive, beta, second_beta]
}

pub fn assert_known_peer(summary: PeerHistorySummary) {
    assert!(summary.found());
    assert_eq!(
        summary,
        PeerHistorySummary {
            ban_count: 3,
            torrent_access_count: 2,
            uploaded_to_peer: 49,
            downloaded_from_peer: 12,
            first_time_seen_ms: FIRST_BAN,
            last_time_seen_ms: FIRST_BAN + 1_000,
        }
    );
}
