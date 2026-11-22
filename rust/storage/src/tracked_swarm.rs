//! Observed peers for the current swarm tracking session.

mod mysql;
mod postgres;
mod sqlite;

use std::net::IpAddr;

use crate::StorageError;

#[derive(Debug, Clone, Copy)]
pub struct TrackedSwarmObservation<'a> {
    pub ip: IpAddr,
    pub port: i32,
    pub info_hash: &'a str,
    pub torrent_is_private: bool,
    pub torrent_size: i64,
    pub downloader: &'a str,
    pub downloader_progress: f64,
    pub peer_id: Option<&'a str>,
    pub client_name: Option<&'a str>,
    pub peer_progress: f64,
    pub uploaded: i64,
    pub upload_speed: i64,
    pub downloaded: i64,
    pub download_speed: i64,
    pub last_flags: Option<&'a str>,
    pub seen_at_ms: i64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TrackedSwarmRecord {
    pub id: i64,
    pub ip: IpAddr,
    pub port: i32,
    pub info_hash: String,
    pub torrent_is_private: bool,
    pub torrent_size: i64,
    pub downloader: String,
    pub downloader_progress: f64,
    pub peer_id: Option<String>,
    pub client_name: Option<String>,
    pub peer_progress: f64,
    pub uploaded: i64,
    pub uploaded_offset: i64,
    pub upload_speed: i64,
    pub downloaded: i64,
    pub downloaded_offset: i64,
    pub download_speed: i64,
    pub last_flags: Option<String>,
    pub first_time_seen_ms: i64,
    pub last_time_seen_ms: i64,
    pub download_speed_max: i64,
    pub upload_speed_max: i64,
}

impl TrackedSwarmRecord {
    pub fn cursor(&self) -> TrackedSwarmCursor {
        TrackedSwarmCursor {
            last_time_seen_ms: self.last_time_seen_ms,
            id: self.id,
        }
    }
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct TrackedSwarmCursor {
    pub last_time_seen_ms: i64,
    pub id: i64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct TrackedSwarmOrder {
    column: &'static str,
    descending: bool,
}

impl TrackedSwarmOrder {
    pub fn parse(term: &str) -> Result<Self, StorageError> {
        let mut parts = term.split('|');
        let field = parts.next().unwrap_or_default();
        let column = COLUMNS
            .split(',')
            .map(str::trim)
            .chain(["id"])
            .find(|column| column.eq_ignore_ascii_case(field))
            .ok_or_else(|| StorageError::InvalidTrackedSwarmSort(field.to_owned()))?;
        let descending = parts.next().is_some_and(|direction| {
            direction.eq_ignore_ascii_case("desc") || direction.eq_ignore_ascii_case("descend")
        });
        Ok(Self { column, descending })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TrackedSwarmQuery {
    pub page: i64,
    pub size: i64,
    pub orders: Vec<TrackedSwarmOrder>,
}

impl Default for TrackedSwarmQuery {
    fn default() -> Self {
        Self {
            page: 1,
            size: 10,
            orders: Vec::new(),
        }
    }
}

impl TrackedSwarmQuery {
    fn order_sql(&self) -> String {
        let mut orders: Vec<TrackedSwarmOrder> = Vec::with_capacity(self.orders.len() + 1);
        for order in &self.orders {
            if let Some(previous) = orders.iter_mut().find(|item| item.column == order.column) {
                *previous = *order;
            } else {
                orders.push(*order);
            }
        }
        if !orders.iter().any(|order| order.column == "id") {
            orders.push(TrackedSwarmOrder {
                column: "id",
                descending: false,
            });
        }
        orders
            .iter()
            .map(|order| {
                let direction = if order.descending { "DESC" } else { "ASC" };
                format!("tracked_swarm.{} {direction}", order.column)
            })
            .collect::<Vec<_>>()
            .join(", ")
    }

    fn result(
        &self,
        window: Option<(i64, i64)>,
        total: u64,
        records: Vec<TrackedSwarmRecord>,
    ) -> TrackedSwarmPage {
        TrackedSwarmPage {
            page: self.page.max(1) as u64,
            size: window.map_or(self.size, |(size, _)| size) as u64,
            total,
            records,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct TrackedSwarmPage {
    pub page: u64,
    pub size: u64,
    pub total: u64,
    pub records: Vec<TrackedSwarmRecord>,
}

const MAX_REPORT_ROWS: usize = 1000;
const COUNT: &str = "SELECT COUNT(*) FROM tracked_swarm";
const RESET: &str = "DELETE FROM tracked_swarm";
const COLUMNS: &str = "ip, port, info_hash, torrent_is_private, torrent_size, downloader,
    downloader_progress, peer_id, client_name, peer_progress, uploaded, uploaded_offset,
    upload_speed, downloaded, downloaded_offset, download_speed, last_flags, first_time_seen,
    last_time_seen, download_speed_max, upload_speed_max";

// The Java service freezes the initial torrent metadata, progress and raw speeds.
// Later observations update the traffic deltas, identity, flags and peak speeds.
const UPDATE_CONFLICT: &str = "ON CONFLICT(ip, port, info_hash, downloader) DO UPDATE SET
    uploaded = tracked_swarm.uploaded + CASE WHEN excluded.uploaded < tracked_swarm.uploaded_offset
        THEN excluded.uploaded ELSE excluded.uploaded - tracked_swarm.uploaded_offset END,
    downloaded = tracked_swarm.downloaded + CASE WHEN excluded.downloaded < tracked_swarm.downloaded_offset
        THEN excluded.downloaded ELSE excluded.downloaded - tracked_swarm.downloaded_offset END,
    uploaded_offset = excluded.uploaded_offset, downloaded_offset = excluded.downloaded_offset,
    peer_id = excluded.peer_id, client_name = excluded.client_name, last_flags = excluded.last_flags,
    last_time_seen = excluded.last_time_seen,
    download_speed_max = CASE WHEN excluded.download_speed_max > tracked_swarm.download_speed_max
        THEN excluded.download_speed_max ELSE tracked_swarm.download_speed_max END,
    upload_speed_max = CASE WHEN excluded.upload_speed_max > tracked_swarm.upload_speed_max
        THEN excluded.upload_speed_max ELSE tracked_swarm.upload_speed_max END";

fn select_fields(ip: &str, first_time: &str, last_time: &str) -> String {
    format!(
        "id, {ip}, port, info_hash, torrent_is_private, torrent_size, downloader,
         downloader_progress, peer_id, client_name, peer_progress, uploaded, uploaded_offset,
         upload_speed, downloaded, downloaded_offset, download_speed, last_flags, {first_time},
         {last_time}, download_speed_max, upload_speed_max"
    )
}

fn invalid_record(id: i64, field: &'static str, reason: impl ToString) -> StorageError {
    StorageError::InvalidTrackedSwarmRecord {
        id,
        field,
        reason: reason.to_string(),
    }
}

macro_rules! decode_record {
    ($get:ident) => {{
        let id = $get!(0, i64);
        let ip = $get!(1, String);
        let progress = $get!(10, String);
        Ok(TrackedSwarmRecord {
            id,
            ip: ip
                .parse()
                .map_err(|error| invalid_record(id, "ip", error))?,
            port: $get!(2, i32),
            info_hash: $get!(3, String),
            torrent_is_private: $get!(4, bool),
            torrent_size: $get!(5, i64),
            downloader: $get!(6, String),
            downloader_progress: $get!(7, f64),
            peer_id: $get!(8, Option<String>),
            client_name: $get!(9, Option<String>),
            peer_progress: progress
                .parse()
                .map_err(|error| invalid_record(id, "peer_progress", error))?,
            uploaded: $get!(11, i64),
            uploaded_offset: $get!(12, i64),
            upload_speed: $get!(13, i64),
            downloaded: $get!(14, i64),
            downloaded_offset: $get!(15, i64),
            download_speed: $get!(16, i64),
            last_flags: $get!(17, Option<String>),
            first_time_seen_ms: $get!(18, i64),
            last_time_seen_ms: $get!(19, i64),
            download_speed_max: $get!(20, i64),
            upload_speed_max: $get!(21, i64),
        })
    }};
}

use decode_record;
