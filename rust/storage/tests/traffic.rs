#[path = "support/traffic.rs"]
mod fixture;

use fixture::{
    ALL_HOURS, DAY_MS, FIRST_HOUR, FIRST_PRIMARY, LAST_HOUR, MIDNIGHT, OFFSET_MS, samples,
};
use pbh_storage::{SQLITE_FILENAME, SqliteStore, TrafficHour, aggregate_traffic_days};
use rusqlite::Connection;

#[test]
fn preserves_hourly_baselines_peaks_and_local_chart_days_after_reopen() {
    let temporary = tempfile::tempdir().unwrap();
    let path = temporary.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let observations = samples();
    store.upsert_traffic_samples(&observations[..2]).unwrap();
    drop(store);
    let mut store = SqliteStore::open(&path).unwrap();
    store.upsert_traffic_samples(&observations[2..]).unwrap();
    store.upsert_traffic_samples(&observations).unwrap();

    let hours = store.traffic_hours(None, FIRST_HOUR, LAST_HOUR).unwrap();
    assert_eq!(hours, ALL_HOURS);
    assert_eq!(
        store
            .traffic_hours(Some(" \t"), FIRST_HOUR, LAST_HOUR)
            .unwrap(),
        hours
    );
    assert_eq!(
        store
            .traffic_hours(Some("primary"), FIRST_HOUR, FIRST_HOUR)
            .unwrap(),
        [FIRST_PRIMARY]
    );
    assert_eq!(
        store
            .traffic_hours(None, FIRST_HOUR + 1, LAST_HOUR)
            .unwrap(),
        hours[1..]
    );
    assert!(
        store
            .traffic_hours(Some("missing"), FIRST_HOUR, LAST_HOUR)
            .unwrap()
            .is_empty()
    );
    let days = aggregate_traffic_days(hours.into_iter().rev(), |time| {
        (time + OFFSET_MS).div_euclid(DAY_MS) * DAY_MS - OFFSET_MS
    });
    assert_eq!(
        days,
        [
            TrafficHour {
                timestamp_ms: MIDNIGHT - DAY_MS,
                uploaded: 67,
                downloaded: 80
            },
            TrafficHour {
                timestamp_ms: MIDNIGHT,
                uploaded: 40,
                downloaded: 57
            },
        ]
    );

    let raw = Connection::open(&path).unwrap();
    let counters: (i64, i64, i64, i64) = raw
        .query_row(
            "SELECT protocol_overall_uploaded_at_start, protocol_overall_uploaded,
            protocol_overall_downloaded_at_start, protocol_overall_downloaded
         FROM traffic_journal_v3 WHERE timestamp = ? AND downloader = 'primary'",
            [FIRST_HOUR],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
        )
        .unwrap();
    assert_eq!(counters, (5, 15, 9, 12));
}

#[test]
fn preserves_older_signed_deltas_and_rolls_back_a_failed_write_batch() {
    let temporary = tempfile::tempdir().unwrap();
    let path = temporary.path().join(SQLITE_FILENAME);
    let mut store = SqliteStore::open(&path).unwrap();
    let raw = Connection::open(&path).unwrap();
    raw.execute_batch("CREATE TRIGGER reject_traffic BEFORE INSERT ON traffic_journal_v3 WHEN NEW.downloader = 'secondary' BEGIN SELECT RAISE(ABORT, 'fixture'); END").unwrap();
    let observations = samples();
    assert!(store.upsert_traffic_samples(&observations).is_err());
    assert!(
        store
            .traffic_hours(None, FIRST_HOUR, LAST_HOUR)
            .unwrap()
            .is_empty()
    );

    store.upsert_traffic_samples(&observations[..1]).unwrap();
    raw.execute(
        "UPDATE traffic_journal_v3 SET data_overall_uploaded = 10",
        [],
    )
    .unwrap();
    let combined = store.traffic_hours(None, FIRST_HOUR, LAST_HOUR).unwrap();
    let scoped = store
        .traffic_hours(Some("primary"), FIRST_HOUR, LAST_HOUR)
        .unwrap();
    assert_eq!((combined[0].uploaded, scoped[0].uploaded), (-90, 0));
}
