use std::io;
use std::net::SocketAddr;
use std::pin::Pin;
use std::sync::Mutex;
use std::sync::atomic::{AtomicU64, Ordering::Relaxed};
use std::task::{Context, Poll};

use serde::Serialize;
use tokio::io::{AsyncRead, AsyncWrite, ReadBuf};
use tokio::net::TcpStream;
use tokio::time::Instant;

use crate::epoch_millis;

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ForwarderMode {
    Epoll,
    Kqueue,
    Iocp,
    Default,
}

impl ForwarderMode {
    pub fn current() -> Self {
        if cfg!(any(target_os = "linux", target_os = "android")) {
            Self::Epoll
        } else if cfg!(any(
            target_vendor = "apple",
            target_os = "freebsd",
            target_os = "openbsd",
            target_os = "netbsd",
            target_os = "dragonfly"
        )) {
            Self::Kqueue
        } else if cfg!(windows) {
            Self::Iocp
        } else {
            Self::Default
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ForwarderSnapshot {
    pub listener: SocketAddr,
    pub upstream: SocketAddr,
    pub mode: ForwarderMode,
    pub pending_connections: usize,
    pub established_connections: usize,
    pub connection_handled: u64,
    pub connection_failed: u64,
    pub connection_blocked: u64,
    pub connection_rejected: u64,
    pub connection_timed_out: u64,
    pub total_to_upstream: u64,
    pub total_to_downstream: u64,
    pub connections: Vec<ConnectionSnapshot>,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ConnectionSnapshot {
    pub downstream: SocketAddr,
    /// Local source endpoint the downloader sees for this connection.
    pub upstream_local: SocketAddr,
    pub established_at_ms: u64,
    pub last_activity_at_ms: u64,
    pub to_upstream_bytes: u64,
    pub to_downstream_bytes: u64,
}

pub(super) enum Direction {
    Upstream,
    Downstream,
}

#[derive(Default)]
pub(super) struct Counters {
    pub handled: AtomicU64,
    pub failed: AtomicU64,
    pub blocked: AtomicU64,
    pub rejected: AtomicU64,
    pub timed_out: AtomicU64,
    pub to_upstream: AtomicU64,
    pub to_downstream: AtomicU64,
}

impl Counters {
    pub fn record_timeout(&self) {
        self.timed_out.fetch_add(1, Relaxed);
        self.failed.fetch_add(1, Relaxed);
    }
}

pub(super) struct Flow {
    downstream: SocketAddr,
    upstream_local: SocketAddr,
    established_at_ms: u64,
    last_activity_at_ms: AtomicU64,
    last_activity: Mutex<Instant>,
    to_upstream: AtomicU64,
    to_downstream: AtomicU64,
}

impl Flow {
    pub fn new(downstream: SocketAddr, upstream_local: SocketAddr) -> Self {
        Self {
            downstream,
            upstream_local,
            established_at_ms: epoch_millis(),
            last_activity_at_ms: AtomicU64::default(),
            last_activity: Mutex::new(Instant::now()),
            to_upstream: AtomicU64::default(),
            to_downstream: AtomicU64::default(),
        }
    }

    pub fn last_activity(&self) -> Instant {
        *self
            .last_activity
            .lock()
            .expect("forwarder activity lock poisoned")
    }

    pub fn snapshot(&self) -> ConnectionSnapshot {
        ConnectionSnapshot {
            downstream: self.downstream,
            upstream_local: self.upstream_local,
            established_at_ms: self.established_at_ms,
            last_activity_at_ms: self.last_activity_at_ms.load(Relaxed),
            to_upstream_bytes: self.to_upstream.load(Relaxed),
            to_downstream_bytes: self.to_downstream.load(Relaxed),
        }
    }

    fn record_write(&self, direction: &Direction, count: usize, totals: &Counters) {
        let (connection, total) = match direction {
            Direction::Upstream => (&self.to_upstream, &totals.to_upstream),
            Direction::Downstream => (&self.to_downstream, &totals.to_downstream),
        };
        connection.fetch_add(count as u64, Relaxed);
        total.fetch_add(count as u64, Relaxed);
        *self
            .last_activity
            .lock()
            .expect("forwarder activity lock poisoned") = Instant::now();
        self.last_activity_at_ms.store(epoch_millis(), Relaxed);
    }
}

/// Keeps Tokio's backpressure and half-close logic while counting completed writes.
pub(super) struct MeteredStream<'a> {
    socket: &'a mut TcpStream,
    flow: &'a Flow,
    totals: &'a Counters,
    direction: Direction,
}

impl<'a> MeteredStream<'a> {
    pub fn new(
        socket: &'a mut TcpStream,
        flow: &'a Flow,
        totals: &'a Counters,
        direction: Direction,
    ) -> Self {
        Self {
            socket,
            flow,
            totals,
            direction,
        }
    }
}

impl AsyncRead for MeteredStream<'_> {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        Pin::new(&mut *self.socket).poll_read(cx, buf)
    }
}

impl AsyncWrite for MeteredStream<'_> {
    fn poll_write(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<io::Result<usize>> {
        let result = Pin::new(&mut *self.socket).poll_write(cx, buf);
        if let Poll::Ready(Ok(count)) = result
            && count != 0
        {
            self.flow.record_write(&self.direction, count, self.totals);
        }
        result
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut *self.socket).poll_flush(cx)
    }

    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut *self.socket).poll_shutdown(cx)
    }
}
