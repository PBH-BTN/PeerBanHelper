use pbh_storage::{
    ConnectionMetrics, ConnectionMetricsBucket, PeerRecordTorrent, PeerSessionObservation,
};

pub const TODAY: i64 = 1_728_000_000_000;
pub const YESTERDAY: i64 = TODAY - 86_400_000;

pub fn observations() -> [PeerSessionObservation<'static>; 5] {
    let first = PeerSessionObservation {
        timeframe_at_ms: TODAY,
        downloader: "primary",
        torrent: PeerRecordTorrent {
            info_hash: "session-torrent",
            name: "Session fixture torrent",
            size: 1_000,
            private: Some(false),
        },
        address: "2001:db8::8".parse().unwrap(),
        port: 6_881,
        peer_id: Some("fixture peer ID"),
        client_name: Some("fixture client"),
        last_flags: Some("d u"),
    };
    let updated = PeerSessionObservation {
        last_flags: Some("D U I H X L E e P O"),
        ..first
    };
    let other_port = PeerSessionObservation {
        port: 6_882,
        last_flags: Some("K ? X"),
        ..first
    };
    let other_downloader = PeerSessionObservation {
        downloader: "secondary",
        last_flags: None,
        peer_id: None,
        ..first
    };
    let yesterday = PeerSessionObservation {
        timeframe_at_ms: YESTERDAY,
        last_flags: Some("d u L"),
        ..first
    };
    [first, updated, other_port, other_downloader, yesterday]
}

pub fn assert_first_day(buckets: &[ConnectionMetricsBucket]) {
    assert_eq!(buckets.len(), 2);
    assert_eq!(buckets[0].key, TODAY);
    assert_eq!(
        buckets[0].metrics,
        ConnectionMetrics {
            total_connections: 3,
            incoming_connections: 1,
            remote_accept_transfer_to_client: 1,
            local_accept_transfer_to_peer: 1,
            local_not_interested: 1,
            question_status: 1,
            optimistic_unchoke: 1,
            from_dht: 1,
            from_pex: 1,
            rc4_encrypted: 1,
            plain_text_encrypted: 1,
            utp_socket: 1,
            tcp_socket: 1,
            ..ConnectionMetrics::default()
        }
    );
    assert_eq!(buckets[1].key, YESTERDAY);
    assert_eq!(
        buckets[1].metrics,
        ConnectionMetrics {
            total_connections: 1,
            remote_refuse_transfer_to_client: 1,
            local_refuse_transfer_to_peer: 1,
            from_lsd: 1,
            tcp_socket: 1,
            ..ConnectionMetrics::default()
        }
    );
}
