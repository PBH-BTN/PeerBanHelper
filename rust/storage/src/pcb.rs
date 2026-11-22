//! Persistence contract of PCBAddressService and PCBRangeService.

mod mysql;
mod postgres;
mod sqlite;

use std::net::IpAddr;

pub use pbh_core::PcbState;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct PcbAddressKey<'a> {
    pub address: IpAddr,
    pub port: u16,
    pub torrent_id: &'a str,
    pub downloader: &'a str,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct PcbRangeKey<'a> {
    /// Java stores the range as text. Use the same normalized spelling when
    /// looking up an address prefix from an existing database.
    pub range: &'a str,
    pub torrent_id: &'a str,
    pub downloader: &'a str,
}

const STATE_COLUMNS: [&str; 10] = [
    "last_report_progress",
    "last_report_uploaded",
    "tracking_uploaded_increase_total",
    "rewind_counter",
    "progress_difference_counter",
    "first_time_seen",
    "last_time_seen",
    "ban_delay_window_end_at",
    "fast_pcb_test_execute_at",
    "last_torrent_completed_size",
];

const EPOCH: &str = "CAST('1970-01-01 00:00:00' AS DATETIME(3))";

#[derive(Clone, Copy)]
enum Table {
    Address,
    Range,
}

#[derive(Clone, Copy)]
enum Dialect {
    Sqlite,
    Postgres,
    MySql,
}

impl Table {
    fn name(self) -> &'static str {
        match self {
            Self::Address => "pcb_address",
            Self::Range => "pcb_range",
        }
    }

    fn key_columns(self) -> &'static [&'static str] {
        match self {
            Self::Address => &["ip", "port", "torrent_id", "downloader"],
            Self::Range => &["ip_range", "torrent_id", "downloader"],
        }
    }

    fn select_sql(self, dialect: Dialect) -> String {
        let columns = STATE_COLUMNS.map(|column| match column {
            // Legacy columns were nullable and Java reads them into primitive longs.
            "last_report_uploaded" | "tracking_uploaded_increase_total" => {
                format!("COALESCE({column}, 0)")
            }
            column if is_timestamp(column) => dialect.milliseconds(column),
            column => column.to_owned(),
        });
        let conditions = self
            .key_columns()
            .iter()
            .enumerate()
            .map(|(index, column)| format!("{column} = {}", dialect.parameter(index + 1)))
            .collect::<Vec<_>>()
            .join(" AND ");
        format!(
            "SELECT {} FROM {} WHERE {conditions}",
            columns.join(", "),
            self.name()
        )
    }

    fn merge_sql(self, dialect: Dialect) -> String {
        let columns = self
            .key_columns()
            .iter()
            .copied()
            .chain(STATE_COLUMNS)
            .collect::<Vec<_>>();
        let values = columns
            .iter()
            .enumerate()
            .map(|(index, column)| {
                let parameter = dialect.parameter(index + 1);
                if is_timestamp(column) {
                    dialect.timestamp(&parameter)
                } else {
                    parameter
                }
            })
            .collect::<Vec<_>>()
            .join(", ");
        let update = STATE_COLUMNS
            .into_iter()
            .filter(|column| *column != "first_time_seen")
            .map(|column| match dialect {
                Dialect::MySql => format!("{column} = VALUES({column})"),
                Dialect::Sqlite | Dialect::Postgres => format!("{column} = excluded.{column}"),
            })
            .collect::<Vec<_>>()
            .join(", ");
        let conflict = match dialect {
            Dialect::MySql => "ON DUPLICATE KEY UPDATE".to_owned(),
            Dialect::Sqlite | Dialect::Postgres => format!(
                "ON CONFLICT ({}) DO UPDATE SET",
                self.key_columns().join(", ")
            ),
        };
        format!(
            "INSERT INTO {} ({}) VALUES ({values}) {conflict} {update}",
            self.name(),
            columns.join(", ")
        )
    }

    fn delete_sql(self, dialect: Dialect) -> String {
        format!(
            "DELETE FROM {} WHERE torrent_id = {} AND {} = {}",
            self.name(),
            dialect.parameter(1),
            self.key_columns()[0],
            dialect.parameter(2)
        )
    }

    fn cleanup_sql(self, dialect: Dialect) -> String {
        format!(
            "DELETE FROM {} WHERE last_time_seen < {}",
            self.name(),
            dialect.timestamp(&dialect.parameter(1))
        )
    }
}

impl Dialect {
    fn parameter(self, index: usize) -> String {
        match self {
            Self::Postgres => format!("${index}"),
            Self::Sqlite | Self::MySql => "?".into(),
        }
    }

    fn timestamp(self, parameter: &str) -> String {
        match self {
            Self::MySql => format!("TIMESTAMPADD(MICROSECOND, {parameter}, {EPOCH})"),
            Self::Sqlite | Self::Postgres => parameter.to_owned(),
        }
    }

    fn milliseconds(self, column: &str) -> String {
        match self {
            Self::Sqlite => column.to_owned(),
            Self::Postgres => format!("FLOOR(EXTRACT(EPOCH FROM {column}) * 1000)::bigint"),
            Self::MySql => format!(
                "CAST(FLOOR(TIMESTAMPDIFF(MICROSECOND, {EPOCH}, {column}) / 1000) AS SIGNED)"
            ),
        }
    }
}

fn is_timestamp(column: &str) -> bool {
    matches!(
        column,
        "first_time_seen"
            | "last_time_seen"
            | "ban_delay_window_end_at"
            | "fast_pcb_test_execute_at"
    )
}

macro_rules! decode_state {
    ($get:ident) => {
        PcbState {
            last_report_progress: $get!(0, f64),
            last_report_uploaded: $get!(1, i64),
            tracking_uploaded_increase_total: $get!(2, i64),
            rewind_counter: $get!(3, i32),
            progress_difference_counter: $get!(4, i32),
            first_time_seen_ms: $get!(5, i64),
            last_time_seen_ms: $get!(6, i64),
            ban_delay_window_end_at_ms: $get!(7, i64),
            fast_pcb_test_execute_at_ms: $get!(8, i64),
            last_torrent_completed_size: $get!(9, i64),
        }
    };
}

use decode_state;
