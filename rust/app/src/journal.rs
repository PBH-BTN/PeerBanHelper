//! Console diagnostics and the WebUI log ring use the same entries.

use std::collections::VecDeque;
use std::convert::Infallible;
use std::fmt;
use std::path::Path;
use std::sync::{Mutex, OnceLock};
use std::time::Duration;

use axum::extract::State;
use axum::http::{HeaderMap, StatusCode};
use axum::response::sse::{Event, KeepAlive};
use axum::response::{IntoResponse, Response, Sse};
use axum::{Json, Router, routing::get};
use futures_util::stream;
use serde::Serialize;
use serde_json::json;
use tokio::sync::{broadcast, watch};

use crate::ban_api::BanLanguage;
use crate::journal_file::LogFile;

#[derive(Clone, Serialize)]
struct Entry {
    time: i64,
    thread: String,
    level: &'static str,
    content: String,
    offset: u64,
}

#[derive(Default)]
struct Ring {
    entries: VecDeque<Entry>,
    offset: u64,
}

struct Journal {
    ring: Mutex<Ring>,
    file: Mutex<Option<LogFile>>,
    updates: broadcast::Sender<Entry>,
    capacity: usize,
    minimum_level: Level,
}

#[derive(Clone, Copy, PartialEq, PartialOrd)]
enum Level {
    Info,
    Warn,
    Error,
    Off,
}

impl Level {
    fn minimum(value: &str) -> Self {
        match value.to_ascii_uppercase().as_str() {
            "WARN" => Self::Warn,
            "ERROR" => Self::Error,
            "OFF" => Self::Off,
            _ => Self::Info,
        }
    }

    fn label(self) -> &'static str {
        match self {
            Self::Info => "INFO",
            Self::Warn => "WARN",
            Self::Error => "ERROR",
            Self::Off => "OFF",
        }
    }
}

fn journal() -> &'static Journal {
    static JOURNAL: OnceLock<Journal> = OnceLock::new();
    JOURNAL.get_or_init(|| {
        let capacity = std::env::var("PBH_LOGGER_RINGDEQUE_SIZE")
            .ok()
            .and_then(|value| value.parse::<usize>().ok())
            .unwrap_or(100);
        Journal {
            ring: Mutex::new(Ring::default()),
            file: Mutex::default(),
            updates: broadcast::channel(capacity.clamp(1, 10_000)).0,
            capacity,
            minimum_level: Level::minimum(&std::env::var("PBH_LOG_LEVEL").unwrap_or_default()),
        }
    })
}

pub(crate) fn open_file(data_dir: &Path) {
    let directory =
        std::env::var_os("PBH_LOGSDIR").map_or_else(|| data_dir.join("logs"), Into::into);
    let mut file = journal().file.lock().expect("log file lock poisoned");
    if file.is_some() {
        return;
    }
    match LogFile::open(directory) {
        Ok(log) => *file = Some(log),
        Err(error) => eprintln!("Open PeerBanHelper log file: {error}"),
    }
}

pub(crate) fn info(message: fmt::Arguments<'_>) {
    record(Level::Info, message);
}

pub(crate) fn warning(message: fmt::Arguments<'_>) {
    record(Level::Warn, message);
}

macro_rules! log_warning {
    ($($arg:tt)*) => {
        $crate::journal::warning(format_args!($($arg)*))
    };
}

pub(crate) use log_warning;

fn record(level: Level, message: fmt::Arguments<'_>) {
    let journal = journal();
    if level < journal.minimum_level {
        return;
    }
    eprintln!("{message}");
    let now = time::OffsetDateTime::now_utc();
    let mut ring = journal.ring.lock().expect("log ring lock poisoned");
    ring.offset += 1;
    let entry = Entry {
        time: (now.unix_timestamp_nanos() / 1_000_000) as i64,
        thread: std::thread::current()
            .name()
            .unwrap_or("peerbanhelper")
            .to_owned(),
        level: level.label(),
        content: message.to_string(),
        offset: ring.offset,
    };
    if journal.capacity > 0 {
        if ring.entries.len() == journal.capacity {
            ring.entries.pop_front();
        }
        ring.entries.push_back(entry.clone());
    }
    let _ = journal.updates.send(entry.clone());
    drop(ring);
    if let Some(file) = journal
        .file
        .lock()
        .expect("log file lock poisoned")
        .as_mut()
        && let Err(error) = file.write(now, &entry.thread, entry.level, &entry.content)
    {
        eprintln!("Write PeerBanHelper log file: {error}");
    }
}

pub(crate) fn router(closing: watch::Receiver<bool>) -> Router {
    Router::new()
        .route("/api/logs/history", get(history))
        .route("/api/logs/live", get(live))
        .with_state(closing)
}

async fn history(headers: HeaderMap) -> Response {
    if let Some(response) = demo_response(&headers) {
        return response;
    }
    let data = journal()
        .ring
        .lock()
        .expect("log ring lock poisoned")
        .entries
        .clone();
    Json(json!({"success": true, "message": null, "data": data})).into_response()
}

async fn live(State(closing): State<watch::Receiver<bool>>, headers: HeaderMap) -> Response {
    if let Some(response) = demo_response(&headers) {
        return response;
    }
    let journal = journal();
    let (entries, updates) = {
        let ring = journal.ring.lock().expect("log ring lock poisoned");
        (ring.entries.clone(), journal.updates.subscribe())
    };
    let events = stream::unfold(
        (entries, updates, closing),
        |(mut entries, mut updates, mut closing)| async move {
            if *closing.borrow() {
                return None;
            }
            let next = match entries.pop_front() {
                Some(entry) => entry,
                None => loop {
                    let event = tokio::select! {
                        event = updates.recv() => event,
                        _ = closing.wait_for(|close| *close) => return None,
                    };
                    match event {
                        Ok(entry) => break entry,
                        Err(broadcast::error::RecvError::Lagged(_)) => continue,
                        Err(broadcast::error::RecvError::Closed) => return None,
                    }
                },
            };
            let event = Event::default()
                .json_data(next)
                .expect("log entry is JSON serializable");
            Some((Ok::<_, Infallible>(event), (entries, updates, closing)))
        },
    );
    Sse::new(events)
        .keep_alive(KeepAlive::new().interval(Duration::from_secs(15)))
        .into_response()
}

fn demo_response(headers: &HeaderMap) -> Option<Response> {
    let demo = std::env::var("PBH_DEMOMODE").is_ok_and(|value| value.eq_ignore_ascii_case("true"));
    demo.then(|| {
        let message = BanLanguage::from_headers(headers)
            .render(&json!({"key": "DEMO_MODE_OPERATION_NOT_PERMITTED"}));
        (
            StatusCode::BAD_REQUEST,
            Json(json!({"success": false, "message": message, "data": null})),
        )
            .into_response()
    })
}
