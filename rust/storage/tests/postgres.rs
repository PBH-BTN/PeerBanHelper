use std::net::IpAddr;
use std::time::{SystemTime, UNIX_EPOCH};

use pbh_storage::{
    BanHistoryEntry, BanHistoryFilter, BanHistoryOrder, BanHistoryQuery, BanRecord, HistoryTorrent,
    PcbAddressKey, PcbRangeKey, PcbState, PostgresConfig, PostgresStore,
};
use pbh_storage::{
    PeerRecordCursor, PeerRecordFilter, PeerRecordObservation, PeerRecordOrder, PeerRecordQuery,
    PeerRecordTorrent,
};
use serde_json::json;

#[path = "support/history_http.rs"]
mod history_http_fixture;
#[path = "support/metric_charts.rs"]
mod metric_charts_fixture;
#[path = "support/peer_charts.rs"]
mod peer_charts_fixture;
#[path = "support/sessions.rs"]
mod sessions_fixture;
#[path = "support/tracked_swarm.rs"]
mod tracked_swarm_fixture;
#[path = "support/traffic.rs"]
mod traffic_fixture;
use tokio_postgres::NoTls;

#[tokio::test]
#[ignore = "requires a disposable local PostgreSQL database in PBH_TEST_POSTGRES_URL"]
async fn postgres_snapshot_and_history_survive_restart_and_rollback() {
    let connection = std::env::var("PBH_TEST_POSTGRES_URL").expect("set PBH_TEST_POSTGRES_URL");
    let mut config: PostgresConfig = connection.parse().unwrap();
    let suffix = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    let schema = format!("pbh_storage_test_{suffix}");
    let (admin, connection) = config.connect(NoTls).await.unwrap();
    tokio::spawn(connection);
    admin
        .batch_execute(&format!("CREATE SCHEMA {schema}"))
        .await
        .unwrap();

    config.options(format!("-c search_path={schema}"));
    let result = exercise(&config).await;
    admin
        .batch_execute(&format!("DROP SCHEMA {schema} CASCADE"))
        .await
        .unwrap();
    result.unwrap();
}

async fn exercise(config: &PostgresConfig) -> Result<(), Box<dyn std::error::Error>> {
    let mut store = PostgresStore::open(config.clone()).await?;
    let address: IpAddr = "2001:db8::2".parse()?;
    let record = BanRecord {
        address,
        metadata: json!({"banAt": -100, "unbanAt": 200, "futureField": []}),
    };
    let rule = json!({"key": "RULE", "params": []});
    let reason = json!({"key": "REASON", "params": ["2001:db8::2"]});
    let event = BanHistoryEntry {
        address,
        ban_at_ms: -100,
        unban_at_ms: 200,
        port: 6881,
        peer_id: None,
        peer_client_name: Some("Fixture"),
        peer_uploaded: Some(42),
        peer_downloaded: None,
        peer_progress: 0.5,
        torrent: HistoryTorrent {
            info_hash: "fixture",
            name: "Fixture torrent",
            size: 4_096,
            private: Some(true),
            progress: 0.9,
        },
        module_name: "FixtureRule",
        rule: &rule,
        description: &reason,
        flags: None,
        downloader: "fixtureClient",
        structured_data: None,
        peer_geoip: None,
    };
    store
        .commit_ban_wave(Some(std::slice::from_ref(&record)), &[event])
        .await?;
    drop(store);
    let mut reopened = PostgresStore::open(config.clone()).await?;
    assert_eq!(
        reopened.load_bans().await?.as_slice(),
        std::slice::from_ref(&record)
    );

    let long_flags = "x".repeat(256);
    let invalid_history = BanHistoryEntry {
        flags: Some(&long_flags),
        ..event
    };
    assert!(
        reopened
            .commit_ban_wave(Some(&[]), &[invalid_history])
            .await
            .is_err()
    );
    assert_eq!(reopened.load_bans().await?, [record]);

    let history = reopened
        .ban_history(&BanHistoryQuery {
            filter: Some(BanHistoryFilter::Address(address)),
            orders: vec![BanHistoryOrder::parse("peerPort|desc").unwrap()],
            ..Default::default()
        })
        .await?;
    assert_eq!(history.total, 1);
    assert_eq!(history.records[0].ban_at_ms, -100);
    assert_eq!(history.records[0].rule, rule);
    let pcb_address = PcbAddressKey {
        address,
        port: 0,
        torrent_id: "fixture",
        downloader: "postgres",
    };
    let pcb_range = PcbRangeKey {
        range: "2001:db8::/64",
        torrent_id: "fixture",
        downloader: "postgres",
    };
    let pcb = PcbState {
        first_time_seen_ms: -123,
        last_time_seen_ms: 500,
        ban_delay_window_end_at_ms: 1_060,
        last_report_progress: 0.3,
        last_report_uploaded: 56,
        ..Default::default()
    };
    reopened
        .merge_pcb(&[(pcb_address, pcb)], &[(pcb_range, pcb)])
        .await?;
    let replacement = PcbState {
        first_time_seen_ms: 0,
        last_report_progress: 0.5,
        fast_pcb_test_execute_at_ms: 1_100,
        ..pcb
    };
    reopened
        .merge_pcb(&[(pcb_address, replacement)], &[(pcb_range, replacement)])
        .await?;
    let expected = PcbState {
        first_time_seen_ms: pcb.first_time_seen_ms,
        ..replacement
    };
    assert_eq!(
        reopened.load_pcb_address(pcb_address).await?,
        Some(expected)
    );
    assert_eq!(reopened.load_pcb_range(pcb_range).await?, Some(expected));
    assert_eq!(reopened.cleanup_pcb(500).await?, 0);
    assert_eq!(reopened.delete_pcb_address("fixture", address).await?, 1);
    assert_eq!(reopened.cleanup_pcb(501).await?, 1);
    assert_eq!(reopened.load_pcb_range(pcb_range).await?, None);
    let version = "btn.ability.ip_allowlist.cache.version";
    let payload = "btn.ability.ip_allowlist.cache.value";
    reopened
        .write_metadata(&[(version, Some("old")), (payload, None)])
        .await?;
    let too_long = "z".repeat(256);
    assert!(
        reopened
            .write_metadata(&[(version, Some("changed")), (&too_long, Some("reject"))])
            .await
            .is_err()
    );
    let snapshot = reopened
        .read_metadata(&[version, payload, "missing"])
        .await?;
    assert_eq!(snapshot[version].as_deref(), Some("old"));
    assert_eq!(snapshot.get(payload), Some(&None));
    assert!(!snapshot.contains_key("missing"));
    let observation = PeerRecordObservation {
        address,
        port: 3_000,
        downloader_id: "pg-one",
        torrent: PeerRecordTorrent {
            info_hash: "fixture",
            name: "Fixture torrent",
            size: 4_096,
            private: Some(true),
        },
        peer_id: Some("-qB5150-extra"),
        client_name: None,
        uploaded: 100,
        downloaded: 15,
        upload_speed: 3,
        download_speed: 4,
        last_flags: None,
        observed_at_ms: 1_000,
        peer_geoip: None,
    };
    let reset = PeerRecordObservation {
        port: 6881,
        uploaded: 10,
        observed_at_ms: 2_000,
        ..observation
    };
    let stale = PeerRecordObservation {
        uploaded: 400,
        observed_at_ms: 1_500,
        ..observation
    };
    reopened
        .upsert_peer_records(&[
            observation,
            reset,
            stale,
            PeerRecordObservation {
                downloader_id: "pg-two",
                ..reset
            },
        ])
        .await?;
    let records = reopened
        .peer_history(&PeerRecordQuery {
            orders: vec![PeerRecordOrder::parse("uploaded|desc").unwrap()],
            ..PeerRecordQuery::new(PeerRecordFilter::Address(address))
        })
        .await?;
    let first = &records.records[0];
    assert_eq!(
        (
            records.total,
            first.uploaded,
            first.port,
            first.first_time_seen_ms
        ),
        (2, 110, 6881, 1_000)
    );
    assert_eq!(first.peer_id.as_deref(), Some("-qB5150-"));
    let (raw, io) = config.connect(NoTls).await?;
    tokio::spawn(io);
    raw.execute("UPDATE peer_records SET last_time_seen = last_time_seen + INTERVAL '0.0009 seconds' WHERE downloader = 'pg-one'", &[]).await?;
    let cursor = PeerRecordCursor {
        last_time_seen_ms: 2_000,
        id: 0,
    };
    let first_report = reopened.pending_peer_records(cursor, 1).await?;
    let second_report = reopened
        .pending_peer_records(first_report[0].cursor(), 1)
        .await?;
    assert!(first_report[0].id < second_report[0].id);
    assert!(
        reopened
            .pending_peer_records(second_report[0].cursor(), 1)
            .await?
            .is_empty()
    );
    assert_eq!(reopened.cleanup_peer_records(2_001).await?, 2);
    exercise_tracked_swarm(&mut reopened).await?;
    exercise_traffic(&mut reopened).await?;
    let mut competing_alert_writer = PostgresStore::open(config.clone()).await?;
    exercise_alerts(&mut reopened, &mut competing_alert_writer).await?;
    exercise_sessions(&mut reopened, &mut competing_alert_writer).await?;
    drop(competing_alert_writer);
    assert_eq!(
        history.records[0].torrent.as_ref().unwrap().name,
        "Fixture torrent"
    );

    let (client, connection) = config.connect(NoTls).await?;
    tokio::spawn(connection);
    let row = client
        .query_one(
            "SELECT h.ip, (EXTRACT(EPOCH FROM h.ban_at) * 1000)::bigint, h.port, t.private_torrent
             FROM history h JOIN torrents t ON t.id = h.torrent_id",
            &[],
        )
        .await?;
    assert_eq!(row.get::<_, IpAddr>(0), address);
    assert_eq!(row.get::<_, i64>(1), -100);
    assert_eq!(row.get::<_, i32>(2), 6881);
    assert!(row.get::<_, bool>(3));
    let migrations: i64 = client
        .query_one(
            "SELECT count(*) FROM flyway_schema_history WHERE success",
            &[],
        )
        .await?
        .get(0);
    assert_eq!(migrations, 8);
    exercise_rank_history(&mut reopened).await?;
    exercise_metric_charts(&mut reopened).await?;
    peer_charts_fixture::exercise!(&mut reopened, await);
    Ok(())
}

async fn exercise_metric_charts(
    store: &mut PostgresStore,
) -> Result<(), Box<dyn std::error::Error>> {
    use metric_charts_fixture::{
        DOWNLOADER, END, FIRST, assert_peer_counts, assert_sums, assert_unban_counts, bans,
    };
    use pbh_storage::{HistoryMetricField, HistoryMetricOperation, HistoryTimestamp};

    store
        .commit_ban_wave(None, &bans(&json!({"key":"fixture","params":[]})))
        .await?;
    let peer = HistoryMetricField::parse("peerId")?;
    let count = HistoryMetricOperation::Count;
    assert_peer_counts(
        &store
            .history_field_metrics(peer, count, 0.0, Some(DOWNLOADER))
            .await?,
    );
    assert_eq!(
        store
            .history_field_metrics(peer, count, 1.0 / 3.0, Some(DOWNLOADER))
            .await?
            .len(),
        1
    );
    let uploaded = HistoryMetricField::parse("peer_uploaded")?;
    let sum = HistoryMetricOperation::Sum;
    assert_sums(
        &store
            .history_field_metrics(uploaded, sum, 0.0, Some(DOWNLOADER))
            .await?,
    );
    let ip = HistoryMetricField::parse("ip")?;
    let ips = store
        .history_field_metrics(ip, count, 0.0, Some(DOWNLOADER))
        .await?;
    assert_eq!(ips[0].count, 5);
    assert_eq!(
        ips[0].data.parse::<std::net::IpAddr>()?,
        "2001:db8::9".parse::<std::net::IpAddr>()?
    );
    let progress = HistoryMetricField::parse("peerProgress")?;
    let progress = store
        .history_field_metrics(progress, sum, 0.0, Some(DOWNLOADER))
        .await?;
    assert_eq!(progress[0].count, 2);
    assert_unban_counts(
        &store
            .history_timestamp_counts(HistoryTimestamp::UnbanAt, FIRST, END, Some(DOWNLOADER))
            .await?,
    );
    assert_eq!(
        store
            .history_timestamp_counts(HistoryTimestamp::BanAt, FIRST, END, Some(DOWNLOADER))
            .await?
            .len(),
        2
    );
    Ok(())
}

async fn exercise_rank_history(
    store: &mut PostgresStore,
) -> Result<(), Box<dyn std::error::Error>> {
    use history_http_fixture::{
        ARCHIVE_HASH, BETA_HASH, FIRST_ADDRESS, FIRST_BAN, PREFIX_MATCH, TORRENT_HASH,
        assert_first_rank, assert_known_peer, bans, discovery_peers,
    };
    use pbh_storage::{BanRankQuery, TorrentSearchQuery};

    assert_eq!(store.cleanup_ban_history(FIRST_BAN - 1).await?, 1);
    let message = json!({"key":"fixture","params":[]});
    let events = bans(&message);
    store.commit_ban_wave(None, &events).await?;
    store.upsert_peer_records(&discovery_peers()).await?;
    assert_known_peer(store.peer_history_summary(FIRST_ADDRESS.parse()?).await?);
    let ban_only = store.peer_history_summary("2001:db8::4".parse()?).await?;
    assert_eq!(
        (
            ban_only.ban_count,
            ban_only.torrent_access_count,
            ban_only.uploaded_to_peer,
            ban_only.first_time_seen_ms
        ),
        (2, 0, -1, 0)
    );
    let missing = store
        .peer_history_summary("198.51.100.211".parse()?)
        .await?;
    assert!(!missing.found());
    assert_eq!(missing.downloaded_from_peer, -1);
    let by_name = TorrentSearchQuery::new(1, 10, Some("Zulu Arch%".into()), &[])?;
    let matches = store.search_torrents(&by_name).await?;
    assert_eq!(matches.total, 1);
    assert_eq!(matches.records[0].torrent.info_hash, ARCHIVE_HASH);
    let by_activity = TorrentSearchQuery::new(
        1,
        1,
        Some("-fixture-torrent".into()),
        &["peerAccessCount|descend".into()],
    )?;
    let matches = store.search_torrents(&by_activity).await?;
    assert_eq!(
        (
            matches.total,
            matches.records.len(),
            matches.records[0].access_count
        ),
        (3, 1, 2)
    );
    assert_eq!(matches.records[0].torrent.info_hash, BETA_HASH);
    assert_first_rank(&store.ban_ranks(&BanRankQuery::default()).await?, 3);
    let mut filtered = BanRankQuery {
        filter: Some(PREFIX_MATCH.into()),
        size: 1,
        ..Default::default()
    };
    assert_first_rank(&store.ban_ranks(&filtered).await?, 2);
    filtered.page = 2;
    assert_eq!(store.ban_ranks(&filtered).await?.records[0].count, 1);
    filtered.page = 3;
    let last = store.ban_ranks(&filtered).await?;
    assert_eq!(last.total, 2);
    assert!(last.records.is_empty());
    let v6 = BanRankQuery {
        filter: Some("2001:db8".into()),
        ..Default::default()
    };
    assert_eq!(store.ban_ranks(&v6).await?.records[0].count, 2);
    assert_eq!(store.cleanup_ban_history(FIRST_BAN).await?, 1);
    let summary = store.torrent_history_summary(TORRENT_HASH).await?.unwrap();
    assert_eq!(
        (
            summary.ban_count,
            summary.access_count,
            summary.torrent.private
        ),
        (5, 1, None)
    );
    assert!(store.torrent_history_summary("missing").await?.is_none());
    exercise_subscription_logs(store).await?;
    Ok(())
}

async fn exercise_subscription_logs(
    store: &mut PostgresStore,
) -> Result<(), Box<dyn std::error::Error>> {
    let early = 1_728_000_000_000i64;
    let late = early + 1_000;
    assert!(store.latest_subscription_log("fixture").await?.is_none());
    store
        .append_subscription_log("fixture", late, 12, false)
        .await?;
    store
        .append_subscription_log("other", late, 20, true)
        .await?;
    store
        .append_subscription_log("fixture", early, 2, true)
        .await?;
    let most_recent_id = store.latest_subscription_log("fixture").await?.unwrap();
    assert_eq!(
        (
            most_recent_id.update_ms,
            most_recent_id.count,
            most_recent_id.manual
        ),
        (early, 2, true)
    );
    let first = store.subscription_logs(Some("fixture"), 1, 1).await?;
    assert_eq!(
        (
            first.total,
            first.records[0].update_ms,
            first.records[0].count,
            first.records[0].manual
        ),
        (2, late, 12, false)
    );
    assert_eq!(
        store
            .subscription_logs(Some("fixture"), 2, 1)
            .await?
            .records,
        [most_recent_id]
    );
    assert_eq!(store.subscription_logs(None, 1, 10).await?.total, 3);
    Ok(())
}

async fn exercise_sessions(
    store: &mut PostgresStore,
    competing: &mut PostgresStore,
) -> Result<(), Box<dyn std::error::Error>> {
    use pbh_storage::PeerSessionObservation;
    use sessions_fixture::{TODAY, YESTERDAY, assert_first_day, observations};

    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY, TODAY)
            .await?,
        0
    );
    let first = observations();
    store.upsert_peer_sessions(&first).await?;
    let (first_reduction, second_reduction) = tokio::join!(
        store.aggregate_peer_sessions(TODAY),
        competing.aggregate_peer_sessions(TODAY),
    );
    let mut reduced = [first_reduction?, second_reduction?];
    reduced.sort();
    assert_eq!(reduced, [0, 4]);
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY, TODAY)
            .await?,
        4
    );
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY + 1, TODAY)
            .await?,
        3
    );
    assert_first_day(
        &store
            .connection_metrics(YESTERDAY, TODAY, Some(" "))
            .await?,
    );
    assert_eq!(
        store
            .connection_metrics(TODAY, TODAY, Some("primary"))
            .await?[0]
            .metrics
            .total_connections,
        2
    );

    let older = PeerSessionObservation {
        port: 9_999,
        ..first[4]
    };
    store.upsert_peer_sessions(&[first[2], older]).await?;
    assert_eq!(store.aggregate_peer_sessions(TODAY).await?, 2);
    let updated = store.connection_metrics(YESTERDAY, TODAY, None).await?;
    assert_eq!(updated[0].metrics.total_connections, 4);
    assert_eq!(updated[0].metrics.question_status, 2);
    assert_eq!(updated[0].metrics.local_not_interested, 1);
    assert_eq!(updated[1].metrics.total_connections, 1);
    assert_eq!(store.cleanup_connection_metrics(YESTERDAY - 1).await?, 0);
    assert_eq!(store.cleanup_connection_metrics(YESTERDAY).await?, 1);
    assert_eq!(
        store
            .global_total_connections_count(YESTERDAY, TODAY)
            .await?,
        4
    );
    Ok(())
}

async fn exercise_alerts(
    store: &mut PostgresStore,
    competitor: &mut PostgresStore,
) -> Result<(), Box<dyn std::error::Error>> {
    use pbh_storage::{AlertDeduplication, AlertLevel, NewAlert};

    let title = json!({"key":"fixture.title","params":["示例"]});
    let content = json!({"key":"fixture.content","params":[42]});
    let alert = NewAlert {
        create_at_ms: 1_700_000_000_000,
        level: AlertLevel::Warn,
        identifier: "daily-fixture",
        title: &title,
        content: &content,
    };
    let including_read = AlertDeduplication::IncludingRead;
    let unread = AlertDeduplication::Unread;
    let (first, second) = tokio::join!(
        store.publish_alert(&alert, including_read),
        competitor.publish_alert(&alert, including_read),
    );
    assert_ne!(first?, second?);
    let alerts = store.unread_alerts().await?;
    assert_eq!(alerts.len(), 1);
    let saved = &alerts[0];
    assert_eq!(
        (saved.create_at_ms, saved.level, saved.read_at_ms),
        (alert.create_at_ms, AlertLevel::Warn, None)
    );
    assert_eq!(saved.title, title);
    assert_eq!(saved.content, content);
    let read_at = alert.create_at_ms + 1_000;
    assert!(store.mark_alert_read(saved.id, read_at).await?);
    assert!(store.mark_alert_read(saved.id, read_at).await?);
    assert!(!store.mark_alert_read(-1, read_at).await?);
    assert!(!store.alert_exists(alert.identifier, unread).await?);
    assert!(store.alert_exists(alert.identifier, including_read).await?);
    assert!(!store.publish_alert(&alert, including_read).await?);
    let repeated = NewAlert {
        create_at_ms: read_at,
        ..alert
    };
    assert!(store.publish_alert(&repeated, unread).await?);
    assert_eq!(
        store
            .mark_alerts_read_by_identifier(alert.identifier, read_at)
            .await?,
        1
    );
    assert_eq!(store.mark_all_alerts_read(read_at).await?, 0);
    assert_eq!(store.cleanup_alerts(alert.create_at_ms - 1).await?, 0);
    assert_eq!(store.cleanup_alerts(alert.create_at_ms).await?, 1);
    assert!(!store.delete_alert(saved.id).await?);
    assert_eq!(store.cleanup_alerts(read_at).await?, 1);
    assert!(!store.alert_exists(alert.identifier, including_read).await?);
    Ok(())
}

async fn exercise_traffic(store: &mut PostgresStore) -> Result<(), Box<dyn std::error::Error>> {
    use pbh_storage::aggregate_traffic_days;
    use traffic_fixture::{
        ALL_HOURS, DAY_MS, FIRST_HOUR, FIRST_PRIMARY, HOUR_MS, LAST_HOUR, MIDNIGHT, OFFSET_MS,
        samples,
    };

    let observations = samples();
    store.upsert_traffic_samples(&observations[..2]).await?;
    store.upsert_traffic_samples(&observations[2..]).await?;
    let hours = store.traffic_hours(None, FIRST_HOUR, LAST_HOUR).await?;
    assert_eq!(hours, ALL_HOURS);
    assert_eq!(
        store
            .traffic_hours(Some("primary"), FIRST_HOUR, FIRST_HOUR)
            .await?,
        [FIRST_PRIMARY]
    );
    assert_eq!(
        store
            .traffic_hours(Some(" "), FIRST_HOUR + 1, LAST_HOUR)
            .await?,
        hours[1..]
    );
    let days = aggregate_traffic_days(hours, |time| {
        (time + OFFSET_MS).div_euclid(DAY_MS) * DAY_MS - OFFSET_MS
    });
    assert_eq!((days[1].timestamp_ms, days[1].uploaded), (MIDNIGHT, 40));

    let mut next = observations[0];
    next.timestamp_ms = LAST_HOUR + HOUR_MS;
    let mut invalid = next;
    let oversized = "x".repeat(256);
    invalid.downloader = &oversized;
    assert!(
        store
            .upsert_traffic_samples(&[next, invalid])
            .await
            .is_err()
    );
    assert!(
        store
            .traffic_hours(None, next.timestamp_ms, next.timestamp_ms)
            .await?
            .is_empty()
    );
    Ok(())
}

async fn exercise_tracked_swarm(
    store: &mut PostgresStore,
) -> Result<(), Box<dyn std::error::Error>> {
    use pbh_storage::TrackedSwarmCursor;
    use tracked_swarm_fixture::{
        CURSOR_KEY, LAST_SEEN, assert_details, details_query, observations,
    };

    let peers = observations();
    store.upsert_tracked_swarm(&peers).await?;
    assert_eq!(store.tracked_swarm_count().await?, 3);
    let details = store.tracked_swarm(&details_query()).await?;
    assert_details(&details);
    let cursor = details.records[0].cursor();
    let second = store.pending_tracked_swarm(cursor, 1).await?;
    assert_eq!(second[0].port, 6882);
    assert!(
        store
            .pending_tracked_swarm(second[0].cursor(), 1000)
            .await?
            .is_empty()
    );
    let cursor_text = format!("{LAST_SEEN},{}", second[0].id);
    store
        .write_metadata(&[(CURSOR_KEY, Some(&cursor_text))])
        .await?;
    assert_eq!(store.reset_tracked_swarm().await?, 3);
    assert_eq!(
        store.read_metadata(&[CURSOR_KEY]).await?[CURSOR_KEY],
        Some(cursor_text)
    );
    assert_eq!(store.tracked_swarm_count().await?, 0);
    let mut invalid = peers[3];
    let oversized = "x".repeat(256);
    invalid.info_hash = &oversized;
    assert!(
        store
            .upsert_tracked_swarm(&[peers[2], invalid])
            .await
            .is_err()
    );
    assert!(
        store
            .pending_tracked_swarm(TrackedSwarmCursor::default(), 10)
            .await?
            .is_empty()
    );
    store.upsert_tracked_swarm(&[peers[2]]).await?;
    let next = store
        .pending_tracked_swarm(second[0].cursor(), 1000)
        .await?;
    assert_eq!(next.len(), 1);
    assert_eq!((next[0].uploaded, next[0].downloaded), (20, 5));
    Ok(())
}
