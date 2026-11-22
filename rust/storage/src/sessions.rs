//! Session analysis tracking and daily connection metrics.

mod mysql;
mod postgres;
mod sqlite;

use std::{collections::BTreeMap, net::IpAddr};

use crate::{PeerRecordTorrent, StorageError};

#[derive(Debug, Clone, Copy)]
pub struct PeerSessionObservation<'a> {
    pub timeframe_at_ms: i64,
    pub downloader: &'a str,
    pub torrent: PeerRecordTorrent<'a>,
    pub address: IpAddr,
    pub port: u16,
    pub peer_id: Option<&'a str>,
    pub client_name: Option<&'a str>,
    pub last_flags: Option<&'a str>,
}

// Declare the field and SQL column order together so every dialect reads
// and writes their positional values in the same sequence.
macro_rules! metrics_fields {
    ($($field:ident),+ $(,)?) => {
        #[derive(Debug, Default, Clone, PartialEq, Eq)]
        pub struct ConnectionMetrics {
            $(pub $field: i64,)+
        }

        const METRIC_COLUMNS: &[&str] = &[$(stringify!($field)),+];

        impl ConnectionMetrics {
            fn values(&self) -> [i64; METRIC_COLUMNS.len()] {
                [$(self.$field),+]
            }

            fn read(mut column: impl FnMut(usize) -> Result<i64, StorageError>) -> Result<Self, StorageError> {
                let mut index = 0;
                let mut next = || {
                    index += 1;
                    column(index)
                };
                Ok(Self {
                    $($field: next()?,)+
                })
            }
        }
    };
}

metrics_fields!(
    total_connections,
    incoming_connections,
    remote_refuse_transfer_to_client,
    remote_accept_transfer_to_client,
    local_refuse_transfer_to_peer,
    local_accept_transfer_to_peer,
    local_not_interested,
    question_status,
    optimistic_unchoke,
    from_dht,
    from_pex,
    from_lsd,
    from_tracker_or_other,
    rc4_encrypted,
    plain_text_encrypted,
    utp_socket,
    tcp_socket,
);

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConnectionMetricsBucket {
    pub key: i64,
    pub metrics: ConnectionMetrics,
}

impl ConnectionMetrics {
    fn observe(&mut self, flags: Option<&str>) {
        self.total_connections += 1;
        let Some(flags) = flags else {
            return;
        };

        let remote = flags
            .bytes()
            .rev()
            .find(|flag| matches!(flag, b'd' | b'D' | b'K'));
        let local = flags
            .bytes()
            .rev()
            .find(|flag| matches!(flag, b'u' | b'U' | b'?'));
        self.incoming_connections += i64::from(flags.contains('I'));
        self.remote_refuse_transfer_to_client += i64::from(remote == Some(b'd'));
        self.remote_accept_transfer_to_client += i64::from(remote == Some(b'D'));
        self.local_refuse_transfer_to_peer += i64::from(local == Some(b'u'));
        self.local_accept_transfer_to_peer += i64::from(local == Some(b'U'));
        self.local_not_interested += i64::from(remote == Some(b'K'));
        self.question_status += i64::from(local == Some(b'?'));
        self.optimistic_unchoke += i64::from(flags.contains('O'));
        if flags.contains('H') {
            self.from_dht += 1;
        } else if flags.contains('X') {
            self.from_pex += 1;
        } else if flags.contains('L') {
            self.from_lsd += 1;
        } else {
            self.from_tracker_or_other += 1;
        }
        self.rc4_encrypted += i64::from(flags.contains('E'));
        self.plain_text_encrypted += i64::from(flags.contains('e'));
        let utp = flags.contains('P');
        self.utp_socket += i64::from(utp);
        self.tcp_socket += i64::from(!utp);
    }
}

const TRACK_COLUMNS: &str =
    "timeframe_at, downloader, torrent_id, address, port, peer_id, client_name, last_flags";
const TRACK_CONFLICT: &str = "ON CONFLICT(timeframe_at, downloader, torrent_id, address, port) DO UPDATE SET
    peer_id = excluded.peer_id, client_name = excluded.client_name, last_flags = excluded.last_flags";
const CLEAR_TRACKS: &str = "DELETE FROM peer_connection_metrics_track";

type SessionTrackRow = (i64, String, Option<String>);

fn aggregate_tracks(
    rows: impl IntoIterator<Item = SessionTrackRow>,
) -> BTreeMap<(i64, String), ConnectionMetrics> {
    let mut aggregates = BTreeMap::<_, ConnectionMetrics>::new();
    for (timestamp, downloader, flags) in rows {
        aggregates
            .entry((timestamp, downloader))
            .or_default()
            .observe(flags.as_deref());
    }
    aggregates
}

fn select_metrics(
    timestamp_ms: &str,
    bounds: &str,
    downloader_parameter: Option<&str>,
    sum: impl Fn(&str) -> String,
) -> String {
    let sums = METRIC_COLUMNS
        .iter()
        .copied()
        .map(sum)
        .collect::<Vec<_>>()
        .join(", ");
    let filter = downloader_parameter.map_or_else(String::new, |parameter| {
        format!("AND downloader = {parameter}")
    });
    format!(
        "SELECT {timestamp_ms}, {sums} FROM peer_connection_metrics
         WHERE {bounds} {filter} GROUP BY timeframe_at ORDER BY timeframe_at DESC"
    )
}

fn insert_metrics(placeholder: impl Fn(usize) -> String, conflict: &str) -> String {
    let columns = METRIC_COLUMNS.join(", ");
    let values = (1..=METRIC_COLUMNS.len() + 2)
        .map(placeholder)
        .collect::<Vec<_>>()
        .join(", ");
    format!(
        "INSERT INTO peer_connection_metrics (timeframe_at, downloader, {columns}) VALUES ({values}) {conflict}"
    )
}

fn additive_assignments(new_value: impl Fn(&str) -> String) -> String {
    // The Java entity merge leaves this one counter untouched on an existing bucket.
    METRIC_COLUMNS
        .iter()
        .copied()
        .filter(|column| *column != "local_not_interested")
        .map(|column| {
            format!(
                "{column} = peer_connection_metrics.{column} + {}",
                new_value(column)
            )
        })
        .collect::<Vec<_>>()
        .join(", ")
}

fn conflict_add() -> String {
    let assignments = additive_assignments(|column| format!("excluded.{column}"));
    format!("ON CONFLICT(timeframe_at, downloader) DO UPDATE SET {assignments}")
}

const CONFLICT_KEEP: &str = "ON CONFLICT(timeframe_at, downloader) DO NOTHING";

fn downloader_filter(downloader: Option<&str>) -> Option<&str> {
    downloader.filter(|name| !name.trim().is_empty())
}
