//! Peer access history and cumulative traffic counters.

mod mysql;
mod postgres;
mod sqlite;

use std::net::IpAddr;

use serde_json::Value;

use crate::{BanHistoryTorrentRecord, StorageError};

#[derive(Debug, Clone, Copy)]
pub struct PeerRecordTorrent<'a> {
    pub info_hash: &'a str,
    pub name: &'a str,
    pub size: i64,
    pub private: Option<bool>,
}

#[derive(Debug, Clone, Copy)]
pub struct PeerRecordObservation<'a> {
    pub address: IpAddr,
    pub port: u16,
    pub downloader_id: &'a str,
    pub torrent: PeerRecordTorrent<'a>,
    pub peer_id: Option<&'a str>,
    pub client_name: Option<&'a str>,
    pub uploaded: i64,
    pub downloaded: i64,
    pub upload_speed: i64,
    pub download_speed: i64,
    pub last_flags: Option<&'a str>,
    pub observed_at_ms: i64,
    pub peer_geoip: Option<&'a Value>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct PeerRecord {
    pub id: i64,
    pub address: IpAddr,
    pub port: u16,
    pub torrent_id: i64,
    pub downloader_id: String,
    pub peer_id: Option<String>,
    pub client_name: Option<String>,
    pub uploaded: i64,
    pub uploaded_offset: i64,
    pub upload_speed: i64,
    pub downloaded: i64,
    pub downloaded_offset: i64,
    pub download_speed: i64,
    pub last_flags: Option<String>,
    pub first_time_seen_ms: i64,
    pub last_time_seen_ms: i64,
    pub peer_geoip: Option<Value>,
    pub torrent: Option<BanHistoryTorrentRecord>,
}

impl PeerRecord {
    pub fn cursor(&self) -> PeerRecordCursor {
        PeerRecordCursor {
            last_time_seen_ms: self.last_time_seen_ms,
            id: self.id,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PeerRecordCursor {
    pub last_time_seen_ms: i64,
    pub id: i64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PeerRecordFilter {
    Address(IpAddr),
    TorrentId(i64),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PeerRecordOrder {
    column: &'static str,
    descending: bool,
}

impl PeerRecordOrder {
    pub fn parse(term: &str) -> Result<Self, StorageError> {
        let mut fields = term.split('|');
        let field = fields.next().unwrap_or_default();
        let column = match field {
            "id" => "p.id",
            "address" => "p.address",
            "port" => "p.port",
            "torrent_id" => "p.torrent_id",
            "downloader" => "p.downloader",
            "peerId" | "peer_id" => "p.peer_id",
            "clientName" | "client_name" => "p.client_name",
            "uploaded" => "p.uploaded",
            "uploaded_offset" => "p.uploaded_offset",
            "upload_speed" => "p.upload_speed",
            "downloaded" => "p.downloaded",
            "downloaded_offset" => "p.downloaded_offset",
            "download_speed" => "p.download_speed",
            "last_flags" => "p.last_flags",
            "firstTimeSeen" | "first_time_seen" => "p.first_time_seen",
            "lastTimeSeen" | "last_time_seen" => "p.last_time_seen",
            "peer_geoip" => "p.peer_geoip",
            _ => {
                return Err(StorageError::InvalidHistoryQuery(format!(
                    "unsupported peer record sort field {field:?}"
                )));
            }
        };
        let descending = fields.next().is_some_and(|direction| {
            direction.eq_ignore_ascii_case("desc") || direction.eq_ignore_ascii_case("descend")
        });
        Ok(Self { column, descending })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PeerRecordQuery {
    pub page: i64,
    pub size: i64,
    pub filter: PeerRecordFilter,
    pub orders: Vec<PeerRecordOrder>,
}

impl PeerRecordQuery {
    pub fn new(filter: PeerRecordFilter) -> Self {
        Self {
            page: 1,
            size: 10,
            filter,
            orders: Vec::new(),
        }
    }

    fn order_sql(&self) -> String {
        let mut orders: Vec<PeerRecordOrder> = Vec::with_capacity(self.orders.len() + 4);
        let defaults = [
            PeerRecordOrder {
                column: "p.last_time_seen",
                descending: true,
            },
            PeerRecordOrder {
                column: "p.address",
                descending: matches!(self.filter, PeerRecordFilter::Address(_)),
            },
            PeerRecordOrder {
                column: "p.port",
                descending: false,
            },
            PeerRecordOrder {
                column: "p.id",
                descending: true,
            },
        ];
        for order in &self.orders {
            if let Some(previous) = orders
                .iter_mut()
                .find(|previous| previous.column == order.column)
            {
                *previous = *order;
            } else {
                orders.push(*order);
            }
        }
        for order in defaults {
            if !orders
                .iter()
                .any(|existing| existing.column == order.column)
            {
                orders.push(order);
            }
        }
        orders
            .into_iter()
            .map(|order| {
                let direction = if order.descending { "DESC" } else { "ASC" };
                format!("{} {direction}", order.column)
            })
            .collect::<Vec<_>>()
            .join(", ")
    }

    fn result(
        &self,
        window: Option<(i64, i64)>,
        total: u64,
        records: Vec<PeerRecord>,
    ) -> PeerRecordPage {
        PeerRecordPage {
            page: self.page.max(1) as u64,
            size: window.map_or(self.size, |(size, _)| size) as u64,
            total,
            records,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct PeerRecordPage {
    pub page: u64,
    pub size: u64,
    pub total: u64,
    pub records: Vec<PeerRecord>,
}

const RECORD_FROM: &str = "FROM peer_records p LEFT JOIN torrents t ON p.torrent_id = t.id";
const UPSERT_COLUMNS: &str = "address, port, torrent_id, downloader, peer_id, client_name,
       uploaded, uploaded_offset, upload_speed, downloaded, downloaded_offset, download_speed,
       last_flags, first_time_seen, last_time_seen, peer_geoip";

const UPDATE_CONFLICT: &str = "ON CONFLICT (address, torrent_id, downloader) DO UPDATE SET
    uploaded = peer_records.uploaded + CASE WHEN excluded.uploaded < peer_records.uploaded_offset
        THEN excluded.uploaded ELSE excluded.uploaded - peer_records.uploaded_offset END,
    downloaded = peer_records.downloaded + CASE WHEN excluded.downloaded < peer_records.downloaded_offset
        THEN excluded.downloaded ELSE excluded.downloaded - peer_records.downloaded_offset END,
    uploaded_offset = excluded.uploaded, downloaded_offset = excluded.downloaded,
    upload_speed = excluded.upload_speed, download_speed = excluded.download_speed,
    port = excluded.port, peer_id = excluded.peer_id, client_name = excluded.client_name,
    last_flags = excluded.last_flags, last_time_seen = excluded.last_time_seen
    WHERE excluded.last_time_seen >= peer_records.last_time_seen";

const MAX_REPORT_ROWS: u32 = 1_000;

fn select_fields(address: &str, first_seen: &str, last_seen: &str) -> String {
    format!("p.id, {address}, p.port, p.torrent_id, p.downloader, p.peer_id, p.client_name,
       p.uploaded, p.uploaded_offset, p.upload_speed, p.downloaded, p.downloaded_offset, p.download_speed,
       p.last_flags, {first_seen}, {last_seen}, p.peer_geoip, t.id, t.info_hash, t.name, t.size, t.private_torrent")
}

fn compact_peer_id(id: Option<&str>) -> Option<&str> {
    id.map(|id| {
        let mut units = 0;
        for (index, character) in id.char_indices() {
            units += character.len_utf16();
            if units > 8 {
                return &id[..index];
            }
        }
        id
    })
}

fn invalid_row(id: i64, field: &'static str, error: impl std::fmt::Display) -> StorageError {
    StorageError::InvalidPeerRecord {
        id,
        field,
        reason: error.to_string(),
    }
}

macro_rules! decode_record {
    ($get:ident) => {{
        let id = $get!(0, i64);
        let address = $get!(1, String)
            .parse::<std::net::IpAddr>()
            .map_err(|error| invalid_row(id, "address", error))?
            .to_canonical();
        let port = u16::try_from($get!(2, i32)).map_err(|error| invalid_row(id, "port", error))?;
        let torrent = match $get!(17, Option<i64>) {
            Some(torrent_id) => Some(BanHistoryTorrentRecord {
                id: torrent_id,
                info_hash: $get!(18, String),
                name: $get!(19, String),
                size: $get!(20, i64),
                private: $get!(21, Option<bool>),
            }),
            None => None,
        };
        Ok(PeerRecord {
            id,
            address,
            port,
            torrent_id: $get!(3, i64),
            downloader_id: $get!(4, String),
            peer_id: $get!(5, Option<String>),
            client_name: $get!(6, Option<String>),
            uploaded: $get!(7, i64),
            uploaded_offset: $get!(8, i64),
            upload_speed: $get!(9, i64),
            downloaded: $get!(10, i64),
            downloaded_offset: $get!(11, i64),
            download_speed: $get!(12, i64),
            last_flags: $get!(13, Option<String>),
            first_time_seen_ms: $get!(14, i64),
            last_time_seen_ms: $get!(15, i64),
            peer_geoip: $get!(16, Option<String>)
                .map(|raw| {
                    serde_json::from_str(&raw).map_err(|error| invalid_row(id, "peer_geoip", error))
                })
                .transpose()?,
            torrent,
        })
    }};
}

use decode_record;
