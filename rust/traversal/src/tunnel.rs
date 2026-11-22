//! TCP STUN mapping and same-port keepalive for AutoSTUN.

mod transport;

use std::io;
use std::net::SocketAddr;
use std::num::NonZeroUsize;
use std::time::Duration;

use serde::Serialize;
use thiserror::Error;
use tokio::io::BufReader;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::watch;
use tokio::task::{JoinError, JoinHandle};
use tokio::time::{Instant, MissedTickBehavior, interval_at, sleep, timeout};
use tokio_util::sync::CancellationToken;

use crate::{PacketError, epoch_millis};

#[derive(Clone, Debug)]
pub struct HeartbeatConfig {
    pub remote: SocketAddr,
    pub host_header: String,
    pub first_delay: Duration,
    pub interval: Duration,
    pub timeout: Duration,
    pub max_failures: NonZeroUsize,
}

impl HeartbeatConfig {
    pub fn new(remote: SocketAddr, host_header: impl Into<String>) -> Self {
        Self {
            remote,
            host_header: host_header.into(),
            first_delay: Duration::from_secs(1),
            interval: Duration::from_secs(10),
            timeout: Duration::from_secs(3),
            max_failures: NonZeroUsize::new(5).expect("default heartbeat attempts are nonzero"),
        }
    }
}

#[derive(Clone, Debug)]
pub struct TunnelConfig {
    pub stun_servers: Vec<SocketAddr>,
    pub heartbeat: HeartbeatConfig,
    pub binding_timeout: Duration,
    pub retry_delay: Duration,
    pub max_rounds: NonZeroUsize,
    pub reachability_timeout: Option<Duration>,
}

impl TunnelConfig {
    pub fn new(stun_servers: Vec<SocketAddr>, heartbeat: HeartbeatConfig) -> Self {
        Self {
            stun_servers,
            heartbeat,
            binding_timeout: Duration::from_secs(5),
            retry_delay: Duration::from_secs(10),
            max_rounds: NonZeroUsize::MIN,
            reachability_timeout: Some(Duration::from_secs(5)),
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TcpMapping {
    pub local: SocketAddr,
    pub mapped: SocketAddr,
    pub server: SocketAddr,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
pub enum TunnelStop {
    Shutdown,
    HeartbeatExhausted,
    WorkerFailed,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TunnelSnapshot {
    pub mapping: TcpMapping,
    pub valid: bool,
    pub started_at_ms: u64,
    pub last_success_at_ms: u64,
    pub consecutive_failures: usize,
    pub stop_reason: Option<TunnelStop>,
}

#[derive(Debug, Error)]
pub enum TunnelError {
    #[error("AutoSTUN could not reserve the local TCP port")]
    Listen(#[source] io::Error),
    #[error("no usable TCP STUN server matches the listener's address family")]
    NoUsableServer,
    #[error("AutoSTUN heartbeat configuration has an invalid address, host or interval")]
    InvalidHeartbeat,
    #[error("all {attempts} TCP STUN attempts failed")]
    Binding {
        attempts: usize,
        #[source]
        last: BindingError,
    },
    #[error("AutoSTUN mapped TCP port could not be reached")]
    Unreachable(#[source] io::Error),
    #[error("AutoSTUN mapped TCP port test timed out")]
    ReachabilityTimeout,
    #[error("AutoSTUN heartbeat task failed")]
    Worker(#[from] JoinError),
}

#[derive(Debug, Error)]
pub enum BindingError {
    #[error("TCP STUN I/O failed")]
    Io(#[from] io::Error),
    #[error("TCP STUN transaction timed out")]
    Timeout,
    #[error(transparent)]
    Packet(#[from] PacketError),
    #[error("TCP STUN returned an invalid mapped endpoint")]
    InvalidMapping,
}

/// Reserves the port before UPnP or STUN and hands the same socket to the forwarder.
pub struct TunnelListener {
    listener: TcpListener,
    local_addr: SocketAddr,
    started_at_ms: u64,
}

impl TunnelListener {
    /// Creates a reusable TCP listener inside the Tokio runtime.
    pub fn bind(local: SocketAddr) -> Result<Self, TunnelError> {
        let listener = transport::listen(local).map_err(TunnelError::Listen)?;
        let local_addr = listener.local_addr().map_err(TunnelError::Listen)?;
        Ok(Self {
            listener,
            local_addr,
            started_at_ms: epoch_millis(),
        })
    }

    pub fn local_addr(&self) -> SocketAddr {
        self.local_addr
    }

    pub async fn establish(
        self,
        config: TunnelConfig,
    ) -> Result<(TcpTunnel, TcpListener), TunnelError> {
        let heartbeat = &config.heartbeat;
        if !matching_endpoint(self.local_addr, heartbeat.remote)
            || !transport::valid_host_header(&heartbeat.host_header)
            || heartbeat.interval.is_zero()
        {
            return Err(TunnelError::InvalidHeartbeat);
        }
        let mapping = discover_mapping(self.local_addr, &config).await?;
        if let Some(limit) = config.reachability_timeout {
            timeout(
                limit,
                transport::verify_reachable(&self.listener, mapping.mapped),
            )
            .await
            .map_err(|_| TunnelError::ReachabilityTimeout)?
            .map_err(TunnelError::Unreachable)?;
        }
        let status = TunnelSnapshot {
            mapping,
            valid: true,
            started_at_ms: self.started_at_ms,
            last_success_at_ms: 0,
            consecutive_failures: 0,
            stop_reason: None,
        };
        Ok((TcpTunnel::start(status, config.heartbeat), self.listener))
    }
}

pub struct TcpTunnel {
    status: watch::Receiver<TunnelSnapshot>,
    stop: CancellationToken,
    task: Option<JoinHandle<()>>,
}

impl TcpTunnel {
    fn start(status: TunnelSnapshot, config: HeartbeatConfig) -> Self {
        let (update, status) = watch::channel(status);
        let stop = CancellationToken::new();
        let task = tokio::spawn(keep_alive(config, update, stop.clone()));
        Self {
            status,
            stop,
            task: Some(task),
        }
    }

    pub fn snapshot(&self) -> TunnelSnapshot {
        let status = *self.status.borrow();
        if status.valid && self.task.as_ref().is_some_and(JoinHandle::is_finished) {
            TunnelSnapshot {
                valid: false,
                stop_reason: Some(TunnelStop::WorkerFailed),
                ..status
            }
        } else {
            status
        }
    }

    /// Waits until the heartbeat fails or this tunnel is shut down.
    pub async fn wait_stopped(&self) -> TunnelSnapshot {
        let mut status = self.status.clone();
        match status.wait_for(|status| !status.valid).await {
            Ok(stopped) => *stopped,
            Err(_) => TunnelSnapshot {
                valid: false,
                stop_reason: Some(TunnelStop::WorkerFailed),
                ..*self.status.borrow()
            },
        }
    }

    /// Stops the heartbeat. The application owns the listener and any UPnP lease.
    pub async fn shutdown(mut self) -> Result<(), TunnelError> {
        self.stop.cancel();
        self.task
            .take()
            .expect("heartbeat task remains available")
            .await?;
        Ok(())
    }
}

impl Drop for TcpTunnel {
    fn drop(&mut self) {
        self.stop.cancel();
    }
}

async fn discover_mapping(
    local: SocketAddr,
    config: &TunnelConfig,
) -> Result<TcpMapping, TunnelError> {
    let servers: Vec<_> = config
        .stun_servers
        .iter()
        .copied()
        .filter(|&server| matching_endpoint(local, server))
        .collect();
    if servers.is_empty() {
        return Err(TunnelError::NoUsableServer);
    }
    let rounds = config.max_rounds.get();
    let mut last_error = None;
    let mut attempts = 0_usize;
    for round in 1..=rounds {
        for &server in &servers {
            match transport::binding(local, server, config.binding_timeout).await {
                Ok(mapping) => return Ok(mapping),
                Err(error) => {
                    last_error = Some(error);
                    attempts = attempts.saturating_add(1);
                }
            }
        }
        if round != rounds {
            sleep(config.retry_delay).await;
        }
    }
    Err(TunnelError::Binding {
        attempts,
        last: last_error.expect("at least one STUN attempt"),
    })
}

async fn keep_alive(
    config: HeartbeatConfig,
    updates: watch::Sender<TunnelSnapshot>,
    stop: CancellationToken,
) {
    let local = updates.borrow().mapping.local;
    let mut heartbeat = interval_at(Instant::now() + config.first_delay, config.interval);
    heartbeat.set_missed_tick_behavior(MissedTickBehavior::Skip);
    let mut stream: Option<BufReader<TcpStream>> = None;
    let mut failures = 0;
    let reason = loop {
        tokio::select! {
            biased;
            () = stop.cancelled() => break TunnelStop::Shutdown,
            _ = heartbeat.tick() => {}
        }
        let result = tokio::select! {
            biased;
            () = stop.cancelled() => break TunnelStop::Shutdown,
            result = timeout(config.timeout, transport::heartbeat(local, &config, &mut stream)) => result,
        };
        if let Ok(Ok(())) = result {
            failures = 0;
            updates.send_modify(|status| {
                status.consecutive_failures = 0;
                status.last_success_at_ms = epoch_millis();
            });
        } else {
            if let Some(failed) = stream.take() {
                transport::reset(failed.get_ref());
            }
            failures += 1;
            updates.send_modify(|status| status.consecutive_failures = failures);
            if failures == config.max_failures.get() {
                break TunnelStop::HeartbeatExhausted;
            }
        }
    };
    drop(stream);
    updates.send_modify(|status| {
        status.valid = false;
        status.stop_reason = Some(reason);
    });
}

fn matching_endpoint(local: SocketAddr, remote: SocketAddr) -> bool {
    local.is_ipv4() == remote.is_ipv4()
        && remote.port() != 0
        && !remote.ip().is_unspecified()
        && !remote.ip().is_multicast()
}
