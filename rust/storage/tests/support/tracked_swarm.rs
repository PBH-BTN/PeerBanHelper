use pbh_storage::{
    TrackedSwarmObservation, TrackedSwarmOrder, TrackedSwarmPage, TrackedSwarmQuery,
};

pub const CURSOR_KEY: &str = "BtnAbilitySubmitSwarm.cursor";
pub const FIRST_SEEN: i64 = 1_700_000_000_000;
pub const LAST_SEEN: i64 = FIRST_SEEN + 2_000;

pub fn observations() -> [TrackedSwarmObservation<'static>; 5] {
    let initial = TrackedSwarmObservation {
        ip: "2001:db8::8".parse().unwrap(),
        port: 6881,
        info_hash: "observed-torrent",
        torrent_is_private: true,
        torrent_size: 1000,
        downloader: "primary",
        downloader_progress: 0.25,
        peer_id: Some("Fixture peer ID"),
        client_name: Some("Fixture client"),
        peer_progress: 0.5,
        uploaded: 100,
        upload_speed: 10,
        downloaded: 80,
        download_speed: 20,
        last_flags: Some("D"),
        seen_at_ms: FIRST_SEEN,
    };
    let grew = TrackedSwarmObservation {
        uploaded: 150,
        upload_speed: 44,
        downloaded: 100,
        download_speed: 1,
        seen_at_ms: FIRST_SEEN + 1_000,
        ..initial
    };
    let reset = TrackedSwarmObservation {
        torrent_is_private: false,
        torrent_size: 900,
        downloader_progress: 0.5,
        peer_id: None,
        client_name: Some("Reconnected"),
        peer_progress: 0.75,
        uploaded: 20,
        upload_speed: 1,
        downloaded: 5,
        download_speed: 55,
        last_flags: None,
        seen_at_ms: LAST_SEEN,
        ..initial
    };
    let other_port = TrackedSwarmObservation {
        port: 6882,
        ..reset
    };
    let other_downloader = TrackedSwarmObservation {
        downloader: "other",
        ..initial
    };
    [initial, grew, reset, other_port, other_downloader]
}

pub fn details_query() -> TrackedSwarmQuery {
    TrackedSwarmQuery {
        page: 1,
        size: 1,
        orders: vec![TrackedSwarmOrder::parse("uploaded|descend").unwrap()],
    }
}

pub fn assert_details(details: &TrackedSwarmPage) {
    assert_eq!((details.page, details.size, details.total), (1, 1, 3));
    assert_eq!(details.records.len(), 1);
    let record = &details.records[0];
    let initial = observations()[0];
    assert_eq!(record.ip, initial.ip);
    assert_eq!(record.port, initial.port);
    assert_eq!(record.info_hash, initial.info_hash);
    assert_eq!(record.downloader, initial.downloader);
    assert!(record.torrent_is_private);
    assert_eq!(record.torrent_size, initial.torrent_size);
    assert_eq!(record.downloader_progress, initial.downloader_progress);
    assert_eq!(record.peer_progress, initial.peer_progress);
    assert_eq!(record.upload_speed, initial.upload_speed);
    assert_eq!(record.download_speed, initial.download_speed);
    assert_eq!(record.first_time_seen_ms, FIRST_SEEN);
    assert_eq!(record.last_time_seen_ms, LAST_SEEN);
    assert_eq!((record.uploaded, record.uploaded_offset), (170, 20));
    assert_eq!((record.downloaded, record.downloaded_offset), (105, 5));
    assert_eq!(
        (record.upload_speed_max, record.download_speed_max),
        (44, 55)
    );
    assert_eq!(record.peer_id, None);
    assert_eq!(record.client_name.as_deref(), Some("Reconnected"));
    assert_eq!(record.last_flags.as_deref(), Some(""));
}
