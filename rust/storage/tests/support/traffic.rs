use pbh_storage::{TrafficHour, TrafficSample};

pub const HOUR_MS: i64 = 3_600_000;
pub const DAY_MS: i64 = 24 * HOUR_MS;
pub const OFFSET_MS: i64 = 5 * HOUR_MS + HOUR_MS / 2;
pub const MIDNIGHT: i64 = 20_000 * DAY_MS - OFFSET_MS;
pub const FIRST_HOUR: i64 = MIDNIGHT - HOUR_MS;
pub const LAST_HOUR: i64 = MIDNIGHT + HOUR_MS;
pub const FIRST_PRIMARY: TrafficHour = TrafficHour {
    timestamp_ms: FIRST_HOUR,
    uploaded: 55,
    downloaded: 70,
};
pub const ALL_HOURS: [TrafficHour; 3] = [
    TrafficHour {
        timestamp_ms: FIRST_HOUR,
        uploaded: 67,
        downloaded: 80,
    },
    TrafficHour {
        timestamp_ms: MIDNIGHT,
        uploaded: 35,
        downloaded: 50,
    },
    TrafficHour {
        timestamp_ms: LAST_HOUR,
        uploaded: 5,
        downloaded: 7,
    },
];

pub fn samples() -> [TrafficSample<'static>; 10] {
    let first = TrafficSample {
        timestamp_ms: FIRST_HOUR,
        downloader: "primary",
        uploaded: 100,
        downloaded: 200,
        protocol_uploaded: 5,
        protocol_downloaded: 9,
    };
    let peak = TrafficSample {
        uploaded: 155,
        downloaded: 260,
        protocol_uploaded: 10,
        protocol_downloaded: 12,
        ..first
    };
    let reset = TrafficSample {
        uploaded: 30,
        downloaded: 1,
        protocol_uploaded: 1,
        protocol_downloaded: 2,
        ..first
    };
    let final_in_hour = TrafficSample {
        uploaded: 125,
        downloaded: 270,
        protocol_uploaded: 15,
        protocol_downloaded: 2,
        ..first
    };
    let other = TrafficSample {
        downloader: "secondary",
        uploaded: 8,
        downloaded: 50,
        ..first
    };
    let other_peak = TrafficSample {
        uploaded: 20,
        downloaded: 60,
        ..other
    };
    let midnight = TrafficSample {
        timestamp_ms: MIDNIGHT,
        uploaded: 25,
        downloaded: 5,
        ..first
    };
    let midnight_peak = TrafficSample {
        uploaded: 60,
        downloaded: 55,
        ..midnight
    };
    let last = TrafficSample {
        timestamp_ms: LAST_HOUR,
        ..midnight
    };
    let last_peak = TrafficSample {
        uploaded: 30,
        downloaded: 12,
        ..last
    };
    [
        first,
        peak,
        reset,
        final_in_hour,
        other,
        other_peak,
        midnight,
        midnight_peak,
        last,
        last_peak,
    ]
}
