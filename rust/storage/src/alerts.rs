//! Persisted alerts shared with the Java schema.

mod mysql;
mod postgres;
mod sqlite;

use serde_json::Value;

use crate::StorageError;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum AlertLevel {
    Tip,
    Info,
    Warn,
    Error,
    Fatal,
}

impl AlertLevel {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Tip => "TIP",
            Self::Info => "INFO",
            Self::Warn => "WARN",
            Self::Error => "ERROR",
            Self::Fatal => "FATAL",
        }
    }

    fn parse(level: &str) -> Option<Self> {
        [Self::Tip, Self::Info, Self::Warn, Self::Error, Self::Fatal]
            .into_iter()
            .find(|variant| variant.as_str() == level)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AlertDeduplication {
    Unread,
    IncludingRead,
}

impl AlertDeduplication {
    fn additional_filter(self) -> &'static str {
        match self {
            Self::Unread => " AND read_at IS NULL",
            Self::IncludingRead => "",
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub struct NewAlert<'a> {
    pub create_at_ms: i64,
    pub level: AlertLevel,
    pub identifier: &'a str,
    pub title: &'a Value,
    pub content: &'a Value,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AlertRecord {
    pub id: i64,
    pub create_at_ms: i64,
    pub read_at_ms: Option<i64>,
    pub level: AlertLevel,
    pub identifier: String,
    pub title: Value,
    pub content: Value,
}

const INSERT_COLUMNS: &str = "create_at, level, identifier, title, content";

fn unread_query(create_at_ms: &str, read_at_ms: &str) -> String {
    format!(
        "SELECT id, {create_at_ms}, {read_at_ms}, level, identifier, title, content
         FROM alert WHERE read_at IS NULL ORDER BY create_at DESC, id DESC"
    )
}

fn existing_query(identifier: &str, deduplication: AlertDeduplication) -> String {
    format!(
        "SELECT 1 FROM alert WHERE identifier = {identifier}{}",
        deduplication.additional_filter(),
    )
}

type AlertRow = (i64, i64, Option<i64>, String, String, String, String);

fn decode(row: AlertRow) -> Result<AlertRecord, StorageError> {
    let (id, create_at_ms, read_at_ms, level, identifier, title, content) = row;
    let invalid = |field, reason| StorageError::InvalidAlertRecord { id, field, reason };
    Ok(AlertRecord {
        id,
        create_at_ms,
        read_at_ms,
        level: AlertLevel::parse(&level).ok_or_else(|| invalid("level", level))?,
        identifier,
        title: serde_json::from_str(&title).map_err(|error| invalid("title", error.to_string()))?,
        content: serde_json::from_str(&content)
            .map_err(|error| invalid("content", error.to_string()))?,
    })
}
