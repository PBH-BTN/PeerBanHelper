//! AutoSTUN's fixed-target TCP relay.

mod telemetry;

use std::collections::HashMap;
use std::future::pending;
use std::io;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::num::NonZeroUsize;
use std::sync::atomic::Ordering::Relaxed;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use socket2::SockRef;
use thiserror::Error;
use tokio::io::copy_bidirectional;
use tokio::net::{TcpListener, TcpSocket, TcpStream};
use tokio::task::{JoinError, JoinHandle, JoinSet};
use tokio::time::{Instant, MissedTickBehavior, interval, timeout};
use tokio_util::sync::CancellationToken;

use crate::ConnectionMappings;
use crate::mappings::canonical;

pub use self::telemetry::{ConnectionSnapshot, ForwarderMode, ForwarderSnapshot};
use self::telemetry::{Counters, Direction, Flow, MeteredStream};

const BAN_RECHECK: Duration = Duration::from_secs(30);

#[derive(Clone, Debug)]
pub struct ForwarderConfig {
    pub upstream: SocketAddr,
    pub connect_timeout: Duration,
    pub idle_timeout: Option<Duration>,
    pub max_connections: Option<NonZeroUsize>,
    pub friendly_loopback_mapping: bool,
}

impl ForwarderConfig {
    pub fn new(upstream: SocketAddr) -> Self {
        Self {
            upstream,
            connect_timeout: Duration::from_secs(30),
            idle_timeout: None,
            max_connections: None,
            friendly_loopback_mapping: true,
        }
    }
}

#[derive(Debug, Error)]
pub enum ForwarderError {
    #[error("the AutoSTUN upstream must be a fixed local unicast TCP endpoint")]
    InvalidUpstream,
    #[error("the AutoSTUN listener and downloader would form a forwarding loop")]
    ForwardingLoop,
    #[error("the AutoSTUN TCP listener failed")]
    Listener(#[source] io::Error),
    #[error("the AutoSTUN TCP listener task failed")]
    Task(#[from] JoinError),
}

pub struct TcpForwarder {
    local: SocketAddr,
    state: Arc<State>,
    task: Option<JoinHandle<Result<(), ForwarderError>>>,
}

impl TcpForwarder {
    /// Accepts an already bound application listener with one fixed local downloader.
    pub fn start(
        listener: TcpListener,
        config: ForwarderConfig,
        mappings: ConnectionMappings,
    ) -> Result<Self, ForwarderError> {
        Self::start_with_policy(listener, config, mappings, |_| false)
    }

    pub fn start_with_policy(
        listener: TcpListener,
        mut config: ForwarderConfig,
        mappings: ConnectionMappings,
        is_banned: impl Fn(IpAddr) -> bool + Send + Sync + 'static,
    ) -> Result<Self, ForwarderError> {
        let local = listener.local_addr().map_err(ForwarderError::Listener)?;
        config.upstream = canonical(config.upstream);
        validate_upstream(local, config.upstream)?;
        let state = Arc::new(State {
            config,
            mappings,
            is_banned: Box::new(is_banned),
            stop: CancellationToken::new(),
            sessions: Mutex::new(HashMap::new()),
            counters: Counters::default(),
        });
        let task = tokio::spawn(serve(listener, state.clone()));
        Ok(Self {
            local,
            state,
            task: Some(task),
        })
    }

    pub fn local_addr(&self) -> SocketAddr {
        self.local
    }

    pub fn upstream_addr(&self) -> SocketAddr {
        self.state.config.upstream
    }

    pub fn snapshot(&self) -> ForwarderSnapshot {
        self.state.snapshot(self.local)
    }

    /// Cancels one source IP, including any in-progress upstream connection.
    pub fn disconnect_ip(&self, ip: IpAddr) -> usize {
        usize::from(self.state.cancel_ip(ip.to_canonical()))
    }

    /// Closes all peers currently banned by the application's admission policy.
    pub fn disconnect_banned(&self) -> usize {
        self.state.disconnect_banned()
    }

    /// Stops the listener and waits for all sockets and address mappings to be released.
    pub async fn shutdown(mut self) -> Result<(), ForwarderError> {
        self.state.stop.cancel();
        self.task
            .take()
            .expect("forwarder task remains available")
            .await?
    }
}

impl Drop for TcpForwarder {
    fn drop(&mut self) {
        self.state.stop.cancel();
    }
}

struct State {
    config: ForwarderConfig,
    mappings: ConnectionMappings,
    is_banned: Box<dyn Fn(IpAddr) -> bool + Send + Sync>,
    stop: CancellationToken,
    sessions: Mutex<HashMap<IpAddr, Session>>,
    counters: Counters,
}

struct Session {
    stop: CancellationToken,
    flow: Option<Arc<Flow>>,
}

struct Reservation {
    state: Arc<State>,
    ip: IpAddr,
    stop: CancellationToken,
}

impl State {
    fn reserve(self: &Arc<Self>, ip: IpAddr) -> Option<Reservation> {
        if (self.is_banned)(ip) {
            self.counters.blocked.fetch_add(1, Relaxed);
            return None;
        }
        let mut sessions = self
            .sessions
            .lock()
            .expect("forwarder sessions lock poisoned");
        if sessions.contains_key(&ip)
            || self
                .config
                .max_connections
                .is_some_and(|limit| sessions.len() >= limit.get())
        {
            self.counters.rejected.fetch_add(1, Relaxed);
            return None;
        }
        let stop = self.stop.child_token();
        sessions.insert(
            ip,
            Session {
                stop: stop.clone(),
                flow: None,
            },
        );
        self.counters.handled.fetch_add(1, Relaxed);
        Some(Reservation {
            state: self.clone(),
            ip,
            stop,
        })
    }

    fn cancel_ip(&self, ip: IpAddr) -> bool {
        let sessions = self
            .sessions
            .lock()
            .expect("forwarder sessions lock poisoned");
        if let Some(session) = sessions.get(&ip)
            && !session.stop.is_cancelled()
        {
            session.stop.cancel();
            return true;
        }
        false
    }

    fn disconnect_banned(&self) -> usize {
        let sources: Vec<_> = self
            .sessions
            .lock()
            .expect("forwarder sessions lock poisoned")
            .keys()
            .copied()
            .collect();
        let closed = sources
            .into_iter()
            .filter(|&ip| (self.is_banned)(ip) && self.cancel_ip(ip))
            .count();
        self.counters.blocked.fetch_add(closed as u64, Relaxed);
        closed
    }

    fn snapshot(&self, listener: SocketAddr) -> ForwarderSnapshot {
        let sessions = self
            .sessions
            .lock()
            .expect("forwarder sessions lock poisoned");
        let mut connections: Vec<_> = sessions
            .values()
            .filter_map(|session| session.flow.as_ref())
            .map(|flow| flow.snapshot())
            .collect();
        connections.sort_unstable_by_key(|connection| connection.downstream);
        ForwarderSnapshot {
            listener,
            upstream: self.config.upstream,
            mode: ForwarderMode::current(),
            pending_connections: sessions.len() - connections.len(),
            established_connections: connections.len(),
            connections,
            connection_handled: self.counters.handled.load(Relaxed),
            connection_failed: self.counters.failed.load(Relaxed),
            connection_blocked: self.counters.blocked.load(Relaxed),
            connection_rejected: self.counters.rejected.load(Relaxed),
            connection_timed_out: self.counters.timed_out.load(Relaxed),
            total_to_upstream: self.counters.to_upstream.load(Relaxed),
            total_to_downstream: self.counters.to_downstream.load(Relaxed),
        }
    }
}

impl Reservation {
    fn establish(&self, downstream: SocketAddr, upstream_local: SocketAddr) -> Arc<Flow> {
        let flow = Arc::new(Flow::new(downstream, upstream_local));
        self.state
            .sessions
            .lock()
            .expect("forwarder sessions lock poisoned")
            .get_mut(&self.ip)
            .expect("live reservation maintains its session")
            .flow = Some(flow.clone());
        flow
    }
}

impl Drop for Reservation {
    fn drop(&mut self) {
        self.state
            .sessions
            .lock()
            .expect("forwarder sessions lock poisoned")
            .remove(&self.ip);
    }
}

async fn serve(listener: TcpListener, state: Arc<State>) -> Result<(), ForwarderError> {
    let mut recheck = interval(BAN_RECHECK);
    recheck.set_missed_tick_behavior(MissedTickBehavior::Skip);
    let mut workers = JoinSet::new();
    let result = loop {
        tokio::select! {
            () = state.stop.cancelled() => break Ok(()),
            _ = recheck.tick() => { state.disconnect_banned(); }
            Some(joined) = workers.join_next() => {
                if joined.is_err() { state.counters.failed.fetch_add(1, Relaxed); }
            }
            incoming = listener.accept() => {
                match incoming {
                    Ok((socket, peer)) => {
                        let peer = canonical(peer);
                        if let Some(reservation) = state.reserve(peer.ip()) {
                            workers.spawn(relay(socket, peer, reservation));
                        }
                    }
                    Err(error) if matches!(error.kind(), io::ErrorKind::Interrupted | io::ErrorKind::ConnectionAborted | io::ErrorKind::ConnectionReset) => {}
                    Err(error) => break Err(ForwarderError::Listener(error)),
                }
            }
        }
    };
    drop(listener);
    state.stop.cancel();
    while let Some(joined) = workers.join_next().await {
        if joined.is_err() {
            state.counters.failed.fetch_add(1, Relaxed);
        }
    }
    result
}

async fn relay(mut downstream: TcpStream, peer: SocketAddr, reservation: Reservation) {
    let state = &reservation.state;
    let connected = tokio::select! {
        biased;
        () = reservation.stop.cancelled() => return,
        result = timeout(state.config.connect_timeout, connect(peer, &state.config)) => result,
    };
    let mut upstream = match connected {
        Ok(Ok(socket)) => socket,
        Ok(Err(_)) => {
            state.counters.failed.fetch_add(1, Relaxed);
            return;
        }
        Err(_) => {
            state.counters.record_timeout();
            return;
        }
    };
    if (state.is_banned)(peer.ip()) {
        if state.cancel_ip(peer.ip()) {
            state.counters.blocked.fetch_add(1, Relaxed);
        }
        return;
    }
    let Ok(upstream_local) = configure_streams(&downstream, &upstream) else {
        state.counters.failed.fetch_add(1, Relaxed);
        return;
    };
    let Ok(_mapping) = state.mappings.register(upstream_local, peer) else {
        state.counters.failed.fetch_add(1, Relaxed);
        return;
    };
    let flow = reservation.establish(peer, upstream_local);
    let mut downstream = MeteredStream::new(
        &mut downstream,
        &flow,
        &state.counters,
        Direction::Downstream,
    );
    let mut upstream =
        MeteredStream::new(&mut upstream, &flow, &state.counters, Direction::Upstream);
    tokio::select! {
        biased;
        () = reservation.stop.cancelled() => {}
        () = idle(&flow, state.config.idle_timeout) => { state.counters.record_timeout(); }
        result = copy_bidirectional(&mut downstream, &mut upstream) => {
            if result.is_err() { state.counters.failed.fetch_add(1, Relaxed); }
        }
    }
}

fn configure_streams(downstream: &TcpStream, upstream: &TcpStream) -> io::Result<SocketAddr> {
    for socket in [downstream, upstream] {
        SockRef::from(socket).set_keepalive(true)?;
        socket.set_nodelay(true)?;
    }
    upstream.local_addr().map(canonical)
}

async fn idle(flow: &Flow, limit: Option<Duration>) {
    let Some(limit) = limit else {
        return pending().await;
    };
    loop {
        let deadline = flow.last_activity() + limit;
        tokio::time::sleep_until(deadline).await;
        if Instant::now().saturating_duration_since(flow.last_activity()) >= limit {
            return;
        }
    }
}

async fn connect(downstream: SocketAddr, config: &ForwarderConfig) -> io::Result<TcpStream> {
    let target = config.upstream;
    if config.friendly_loopback_mapping
        && target.is_ipv4()
        && target.ip().is_loopback()
        && let IpAddr::V4(peer) = downstream.ip()
    {
        let [_, b, c, d] = peer.octets();
        let local_ip = Ipv4Addr::new(127, b, c, d);
        for port in [downstream.port(), 0] {
            let socket = TcpSocket::new_v4()?;
            if socket.bind(SocketAddr::new(local_ip.into(), port)).is_ok()
                && let Ok(stream) = socket.connect(target).await
            {
                return Ok(stream);
            }
        }
    }
    TcpStream::connect(target).await
}

fn validate_upstream(listener: SocketAddr, upstream: SocketAddr) -> Result<(), ForwarderError> {
    let is_local = match upstream.ip() {
        IpAddr::V4(ip) => ip.is_loopback() || ip.is_private() || ip.is_link_local(),
        IpAddr::V6(ip) => ip.is_loopback() || ip.is_unique_local() || ip.is_unicast_link_local(),
    };
    if !is_local || upstream.port() == 0 {
        return Err(ForwarderError::InvalidUpstream);
    }
    if listener.port() == upstream.port()
        && (listener.ip() == upstream.ip()
            || listener.ip().is_unspecified() && upstream.ip().is_loopback())
    {
        return Err(ForwarderError::ForwardingLoop);
    }
    Ok(())
}
