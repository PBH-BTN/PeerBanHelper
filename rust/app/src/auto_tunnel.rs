//! Coordinates AutoSTUN TCP tunnels for explicitly selected downloaders.

use std::collections::{BTreeMap, BTreeSet};
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};
use std::sync::{Arc, Mutex, RwLock};
use std::time::Duration;

use anyhow::{Context, Result, bail};
use pbh_traversal::ConnectionMappings;
use pbh_traversal::forwarder::{ForwarderConfig, ForwarderSnapshot, TcpForwarder};
use pbh_traversal::tunnel::{
    HeartbeatConfig, TcpTunnel, TunnelConfig, TunnelListener, TunnelSnapshot,
};
use pbh_traversal::upnp::{IgdClient, MappingLease, PortMapping, Protocol};
use tokio::net::{UdpSocket, lookup_host};
use tokio::sync::watch;
use tokio::task::{JoinHandle, JoinSet};
use tokio::time::{MissedTickBehavior, interval, sleep, timeout};

use crate::downloader::{Client, Downloader};

const RETRY_INTERVAL: Duration = Duration::from_secs(5);
const DNS_TIMEOUT: Duration = Duration::from_secs(5);
const STATUS_INTERVAL: Duration = Duration::from_secs(1);
const UPNP_WAIT: Duration = Duration::from_secs(6);
const DOWNLOADER_UNAVAILABLE_LIMIT: u8 = 11;

#[derive(Clone)]
pub(crate) struct AutoTunnelOptions {
    pub(crate) enabled: bool,
    pub(crate) downloaders: Vec<String>,
    pub(crate) friendly_loopback_mapping: bool,
    pub(crate) tcp_servers: Vec<String>,
    pub(crate) heartbeat: String,
    pub(crate) verify_reachability: bool,
    pub(crate) upnp: bool,
    pub(crate) local_ports: BTreeMap<String, u16>,
}

#[derive(Clone)]
pub(crate) struct TunnelView {
    pub(crate) id: String,
    pub(crate) name: String,
    pub(crate) kind: String,
    pub(crate) tunnel: Option<TunnelSnapshot>,
    pub(crate) forwarder: Option<ForwarderSnapshot>,
    pub(crate) last_error: Option<String>,
}

#[derive(Clone)]
pub(crate) struct AutoTunnels(Arc<Manager>);

type Views = Arc<RwLock<BTreeMap<String, TunnelView>>>;
type Bans = Arc<RwLock<BTreeSet<IpAddr>>>;

struct Manager {
    views: Views,
    commands: watch::Sender<Commands>,
    workers: Mutex<Vec<JoinHandle<()>>>,
}

#[derive(Clone, Copy, Default)]
struct Commands {
    restart: u64,
    bans: u64,
    shutdown: bool,
}

struct Worker {
    id: String,
    host: String,
    client: Arc<Client>,
    options: Arc<AutoTunnelOptions>,
    mappings: ConnectionMappings,
    bans: Bans,
    views: Views,
}

struct Running {
    tunnel: TcpTunnel,
    forwarder: TcpForwarder,
    leases: Vec<MappingLease>,
}

struct Prepared {
    downloader_endpoint: SocketAddr,
    previous_port: u16,
    source: SocketAddr,
    transport: TunnelConfig,
}

impl AutoTunnels {
    pub(crate) fn start(
        options: AutoTunnelOptions,
        downloaders: &[Downloader],
        mappings: ConnectionMappings,
        bans: Bans,
    ) -> Self {
        let views = Views::default();
        let (commands, receiver) = watch::channel(Commands::default());
        let options = Arc::new(options);
        let mut tasks = Vec::new();
        if options.enabled {
            for downloader in downloaders
                .iter()
                .filter(|entry| options.downloaders.contains(&entry.id))
            {
                let host = downloader.auto_tunnel_host();
                views.write().expect("AutoSTUN views lock poisoned").insert(
                    downloader.id.clone(),
                    TunnelView {
                        id: downloader.id.clone(),
                        name: downloader.name.clone(),
                        kind: downloader.kind.clone(),
                        tunnel: None,
                        forwarder: None,
                        last_error: host.is_none().then(|| {
                            String::from("Downloader does not support live BitTorrent port updates")
                        }),
                    },
                );
                if let Some(host) = host {
                    let worker = Worker {
                        id: downloader.id.clone(),
                        host: host.to_owned(),
                        client: downloader.client.clone(),
                        options: options.clone(),
                        mappings: mappings.clone(),
                        bans: bans.clone(),
                        views: views.clone(),
                    };
                    tasks.push(tokio::spawn(worker.run(receiver.clone())));
                }
            }
        }
        Self(Arc::new(Manager {
            views,
            commands,
            workers: Mutex::new(tasks),
        }))
    }

    pub(crate) fn snapshots(&self) -> Vec<TunnelView> {
        self.0
            .views
            .read()
            .expect("AutoSTUN views lock poisoned")
            .values()
            .cloned()
            .collect()
    }

    pub(crate) fn snapshot(&self, id: &str) -> Option<TunnelView> {
        self.0
            .views
            .read()
            .expect("AutoSTUN views lock poisoned")
            .get(id)
            .cloned()
    }

    pub(crate) fn restart(&self) {
        self.0
            .commands
            .send_modify(|commands| commands.restart = commands.restart.wrapping_add(1));
    }

    pub(crate) fn bans_changed(&self) {
        self.0
            .commands
            .send_modify(|commands| commands.bans = commands.bans.wrapping_add(1));
    }

    pub(crate) async fn shutdown(&self) {
        self.0
            .commands
            .send_modify(|commands| commands.shutdown = true);
        let workers = std::mem::take(
            &mut *self
                .0
                .workers
                .lock()
                .expect("AutoSTUN workers lock poisoned"),
        );
        for worker in workers {
            if let Err(error) = worker.await {
                eprintln!("AutoSTUN task stopped unexpectedly: {error}");
            }
        }
    }
}

impl Drop for Manager {
    fn drop(&mut self) {
        self.commands
            .send_modify(|commands| commands.shutdown = true);
    }
}

impl Worker {
    async fn run(self, mut commands: watch::Receiver<Commands>) {
        while !commands.borrow().shutdown {
            let generation = commands.borrow().restart;
            match self.start(&mut commands, generation).await {
                Ok(Some(running)) => self.monitor(running, &mut commands, generation).await,
                Ok(None) => continue,
                Err(error) => {
                    self.update(|view| {
                        view.last_error = Some(error.to_string());
                        view.tunnel = None;
                        view.forwarder = None;
                    });
                }
            }
            tokio::select! {
                () = sleep(RETRY_INTERVAL) => {}
                () = wait_restart(&mut commands, generation) => {}
            }
        }
    }

    async fn start(
        &self,
        commands: &mut watch::Receiver<Commands>,
        generation: u64,
    ) -> Result<Option<Running>> {
        let prepared = tokio::select! {
            biased;
            () = wait_restart(commands, generation) => return Ok(None),
            prepared = self.prepare() => prepared?,
        };
        let reservation = TunnelListener::bind(prepared.source)?;
        let leases = self
            .map_port(
                &prepared.transport,
                reservation.local_addr().port(),
                commands,
                generation,
            )
            .await;
        let result = self
            .launch(reservation, prepared, commands, generation)
            .await;
        match result {
            Ok(Some((tunnel, forwarder))) => Ok(Some(Running {
                tunnel,
                forwarder,
                leases,
            })),
            result => {
                remove_mappings(leases).await;
                result.map(|_| None)
            }
        }
    }

    async fn prepare(&self) -> Result<Prepared> {
        let mut local_endpoints = resolve(&self.host, 1).await?;
        local_endpoints.retain(|address| is_local(address.ip().to_canonical()));
        let downloader_endpoint = local_endpoints
            .iter()
            .find(|address| address.ip().to_canonical().is_ipv4())
            .or_else(|| local_endpoints.first())
            .copied()
            .context("AutoSTUN requires a loopback or LAN downloader endpoint")?;
        self.client
            .login_for_auto_tunnel()
            .await
            .context("AutoSTUN downloader login failed")?;
        let previous_port = self
            .client
            .bt_listen_port()
            .await
            .context("Read downloader BitTorrent port")?;

        let (heartbeat_host, _) = split_endpoint(&self.options.heartbeat, 80)?;
        let heartbeat = resolve(&self.options.heartbeat, 80)
            .await
            .context("Resolve AutoSTUN heartbeat endpoint")?;
        let mut servers = Vec::new();
        for server in &self.options.tcp_servers {
            if let Ok(resolved) = resolve(server, 3478).await {
                servers.extend(resolved);
            }
        }
        let remote = heartbeat
            .iter()
            .copied()
            .filter(|address| {
                servers
                    .iter()
                    .any(|server| server.is_ipv4() == address.is_ipv4())
            })
            .find(|address| address.is_ipv4())
            .or_else(|| {
                heartbeat.into_iter().find(|address| {
                    servers
                        .iter()
                        .any(|server| server.is_ipv4() == address.is_ipv4())
                })
            })
            .context("No TCP STUN server matches an AutoSTUN heartbeat endpoint")?;
        servers.retain(|server| server.is_ipv4() == remote.is_ipv4());
        let unspecified = if remote.is_ipv4() {
            Ipv4Addr::UNSPECIFIED.into()
        } else {
            Ipv6Addr::UNSPECIFIED.into()
        };
        let local_port = self.options.local_ports.get(&self.id).copied().unwrap_or(0);
        let mut transport =
            TunnelConfig::new(servers, HeartbeatConfig::new(remote, heartbeat_host));
        if !self.options.verify_reachability {
            transport.reachability_timeout = None;
        }
        Ok(Prepared {
            downloader_endpoint,
            previous_port,
            source: SocketAddr::new(unspecified, local_port),
            transport,
        })
    }

    async fn map_port(
        &self,
        transport: &TunnelConfig,
        port: u16,
        commands: &mut watch::Receiver<Commands>,
        generation: u64,
    ) -> Vec<MappingLease> {
        if !self.options.upnp {
            return Vec::new();
        }
        let gateways = tokio::select! {
            biased;
            () = wait_restart(commands, generation) => return Vec::new(),
            gateways = discover_gateways(transport) => match gateways {
                Ok(result) => result,
                Err(error) => { eprintln!("AutoSTUN {} UPnP discovery: {error}", self.id); return Vec::new(); }
            },
        };
        let request = PortMapping {
            external_port: port,
            internal_port: port,
            protocol: Protocol::Tcp,
            description: format!("PeerBanHelper STUN Hole Puncher (TCP/{port})"),
            lease_duration: Duration::ZERO,
        };
        let mut leases = Vec::new();
        for gateway in gateways {
            if should_restart(*commands.borrow(), generation) {
                break;
            }
            match gateway.add_mapping(&request).await {
                Ok(lease) => leases.push(lease),
                Err(error) => eprintln!("AutoSTUN {} UPnP port mapping: {error}", self.id),
            }
        }
        leases
    }

    async fn launch(
        &self,
        reservation: TunnelListener,
        prepared: Prepared,
        commands: &mut watch::Receiver<Commands>,
        generation: u64,
    ) -> Result<Option<(TcpTunnel, TcpForwarder)>> {
        let (tunnel, listener) = tokio::select! {
            biased;
            () = wait_restart(commands, generation) => return Ok(None),
            result = reservation.establish(prepared.transport) => result?,
        };
        let port = tunnel.snapshot().mapping.mapped.port();
        let mut target = prepared.downloader_endpoint;
        target.set_port(port);
        let mut forwarding = ForwarderConfig::new(target);
        forwarding.friendly_loopback_mapping = self.options.friendly_loopback_mapping;
        let bans = self.bans.clone();
        let forwarder = match TcpForwarder::start_with_policy(
            listener,
            forwarding,
            self.mappings.clone(),
            move |ip| {
                bans.read()
                    .expect("AutoSTUN bans lock poisoned")
                    .contains(&ip)
            },
        ) {
            Ok(forwarder) => forwarder,
            Err(error) => {
                if let Err(heartbeat) = tunnel.shutdown().await {
                    eprintln!("AutoSTUN {}: {heartbeat}", self.id);
                }
                return Err(error.into());
            }
        };
        if should_restart(*commands.borrow(), generation) {
            self.close(tunnel, forwarder).await;
            return Ok(None);
        }
        if port != prepared.previous_port {
            let result = self.client.set_bt_listen_port(port).await;
            if result.is_err() || should_restart(*commands.borrow(), generation) {
                self.restore_port(port, prepared.previous_port).await;
                self.close(tunnel, forwarder).await;
                return result
                    .context("Update downloader BitTorrent port")
                    .map(|()| None);
            }
        }
        Ok(Some((tunnel, forwarder)))
    }

    async fn restore_port(&self, ours: u16, previous: u16) {
        if self
            .client
            .bt_listen_port()
            .await
            .is_ok_and(|current| current == ours)
            && let Err(error) = self.client.set_bt_listen_port(previous).await
        {
            eprintln!(
                "AutoSTUN {} could not roll back downloader port: {error}",
                self.id
            );
        }
    }

    async fn monitor(
        &self,
        running: Running,
        commands: &mut watch::Receiver<Commands>,
        generation: u64,
    ) {
        let mut tick = interval(STATUS_INTERVAL);
        tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
        let health = until_downloader_unavailable(&self.client);
        tokio::pin!(health);
        let mut bans_version = commands.borrow().bans;
        let reason = loop {
            self.update(|view| {
                view.tunnel = Some(running.tunnel.snapshot());
                view.forwarder = Some(running.forwarder.snapshot());
                view.last_error = None;
            });
            if should_restart(*commands.borrow(), generation) {
                break None;
            }
            tokio::select! {
                status = running.tunnel.wait_stopped() => break Some(format!("AutoSTUN tunnel stopped: {:?}", status.stop_reason)),
                () = &mut health => break Some("AutoSTUN downloader is unavailable".into()),
                result = commands.changed() => {
                    if result.is_err() || should_restart(*commands.borrow(), generation) { break None; }
                    let next = commands.borrow().bans;
                    if next != bans_version {
                        running.forwarder.disconnect_banned();
                        bans_version = next;
                    }
                }
                _ = tick.tick() => {}
            }
        };
        self.update(|view| {
            view.tunnel = None;
            view.forwarder = None;
            view.last_error = reason;
        });
        self.close(running.tunnel, running.forwarder).await;
        remove_mappings(running.leases).await;
    }

    async fn close(&self, tunnel: TcpTunnel, forwarder: TcpForwarder) {
        if let Err(error) = tunnel.shutdown().await {
            eprintln!("AutoSTUN {}: {error}", self.id);
        }
        if let Err(error) = forwarder.shutdown().await {
            eprintln!("AutoSTUN {}: {error}", self.id);
        }
    }

    fn update(&self, update: impl FnOnce(&mut TunnelView)) {
        let mut views = self.views.write().expect("AutoSTUN views lock poisoned");
        if let Some(view) = views.get_mut(&self.id) {
            update(view);
        }
    }
}

async fn wait_restart(commands: &mut watch::Receiver<Commands>, generation: u64) {
    let _ = commands
        .wait_for(|&command| should_restart(command, generation))
        .await;
}

fn should_restart(commands: Commands, generation: u64) -> bool {
    commands.shutdown || commands.restart != generation
}

async fn until_downloader_unavailable(client: &Client) {
    let mut failures = 0;
    while failures < DOWNLOADER_UNAVAILABLE_LIMIT {
        sleep(RETRY_INTERVAL).await;
        if timeout(RETRY_INTERVAL, client.bt_listen_port())
            .await
            .is_ok_and(|result| result.is_ok())
        {
            failures = 0;
        } else {
            failures += 1;
        }
    }
}

async fn discover_gateways(transport: &TunnelConfig) -> Result<Vec<pbh_traversal::upnp::Gateway>> {
    let Some(&destination) = transport
        .stun_servers
        .iter()
        .find(|address| address.is_ipv4() && !address.ip().is_loopback())
    else {
        return Ok(Vec::new());
    };
    let route = UdpSocket::bind((Ipv4Addr::UNSPECIFIED, 0)).await?;
    route.connect(destination).await?;
    let IpAddr::V4(interface) = route.local_addr()?.ip() else {
        return Ok(Vec::new());
    };
    if interface.is_loopback() {
        return Ok(Vec::new());
    }
    Ok(IgdClient::new(interface)?.discover(UPNP_WAIT).await?)
}

async fn remove_mappings(leases: Vec<MappingLease>) {
    let mut removals = JoinSet::new();
    for lease in leases {
        removals.spawn(async move { lease.remove().await });
    }
    while let Some(result) = removals.join_next().await {
        match result {
            Ok(Ok(())) => {}
            Ok(Err(error)) => eprintln!("AutoSTUN UPnP cleanup: {error}"),
            Err(error) => eprintln!("AutoSTUN UPnP cleanup task: {error}"),
        }
    }
}

async fn resolve(endpoint: &str, default_port: u16) -> Result<Vec<SocketAddr>> {
    if let Ok(address) = endpoint.trim().parse::<SocketAddr>() {
        if address.port() == 0 {
            bail!("Invalid AutoSTUN endpoint port");
        }
        return Ok(vec![address]);
    }
    let (host, port) = split_endpoint(endpoint, default_port)?;
    let host = host.trim_matches(['[', ']']);
    let addresses = timeout(DNS_TIMEOUT, lookup_host((host, port)))
        .await
        .context("AutoSTUN DNS lookup timed out")??;
    let mut seen = BTreeSet::new();
    Ok(addresses.filter(|address| seen.insert(*address)).collect())
}

fn split_endpoint(endpoint: &str, default_port: u16) -> Result<(String, u16)> {
    let endpoint = endpoint.trim();
    if let Ok(address) = endpoint.parse::<SocketAddr>() {
        let ip = address.ip();
        let host = if ip.is_ipv6() {
            format!("[{ip}]")
        } else {
            ip.to_string()
        };
        return Ok((host, address.port()));
    }
    if let Ok(ip) = endpoint.trim_matches(['[', ']']).parse::<IpAddr>() {
        return Ok((
            if ip.is_ipv6() {
                format!("[{ip}]")
            } else {
                ip.to_string()
            },
            default_port,
        ));
    }
    let (host, port) = match endpoint.rsplit_once(':') {
        Some((host, port)) => (
            host,
            port.parse::<u16>()
                .context("Invalid AutoSTUN endpoint port")?,
        ),
        None => (endpoint, default_port),
    };
    if host.is_empty()
        || port == 0
        || !host
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'.'))
    {
        bail!("Invalid AutoSTUN endpoint host or port");
    }
    Ok((host.to_owned(), port))
}

fn is_local(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ip) => ip.is_loopback() || ip.is_private() || ip.is_link_local(),
        IpAddr::V6(ip) => ip.is_loopback() || ip.is_unique_local() || ip.is_unicast_link_local(),
    }
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicBool, AtomicU16, AtomicUsize, Ordering::Relaxed};

    use axum::extract::State;
    use axum::http::StatusCode;
    use axum::routing::{get, post};
    use axum::{Json, Router};
    use pbh_core::AddressPolicy;
    use serde_json::{Value, json};
    use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
    use tokio::net::{TcpListener, TcpStream};

    use super::*;
    use crate::config::DownloaderConfig;

    const OLD_PORT: u16 = 6881;
    const DEADLINE: Duration = Duration::from_secs(6);
    const LOOPBACK: SocketAddr = SocketAddr::new(IpAddr::V4(Ipv4Addr::LOCALHOST), 0);

    struct Fixture {
        api: SocketAddr,
        backend: TcpListener,
        heartbeat: SocketAddr,
        stun: SocketAddr,
        calls: Arc<PortState>,
        bindings: Arc<AtomicUsize>,
        tasks: Vec<JoinHandle<()>>,
    }

    struct PortState {
        actual: AtomicU16,
        fail: AtomicBool,
        pause_update: AtomicBool,
        resume_update: tokio::sync::Notify,
        writes: Mutex<Vec<u16>>,
    }

    impl Drop for Fixture {
        fn drop(&mut self) {
            for task in &self.tasks {
                task.abort();
            }
        }
    }

    #[tokio::test]
    async fn only_enabled_apply_mode_runs_and_restarts_the_downloader_tunnel() {
        let fixture = Fixture::start().await;
        let writer = fixture.downloader(true);
        let reader = fixture.downloader(false);
        let mappings = ConnectionMappings::default();
        let bans = Bans::default();

        let mut disabled = fixture.options();
        disabled.enabled = false;
        let inactive = AutoTunnels::start(
            disabled,
            std::slice::from_ref(&writer),
            mappings.clone(),
            bans.clone(),
        );
        assert!(inactive.snapshots().is_empty());
        inactive.shutdown().await;
        let observer =
            AutoTunnels::start(fixture.options(), &[reader], mappings.clone(), bans.clone());
        assert!(observer.snapshot("qb").unwrap().last_error.is_some());
        observer.shutdown().await;
        assert_eq!(fixture.bindings.load(Relaxed), 0);

        let auto = AutoTunnels::start(fixture.options(), &[writer], mappings.clone(), bans.clone());
        wait_until(|| {
            auto.snapshot("qb")
                .is_some_and(|view| view.tunnel.is_some())
        })
        .await;
        let first = auto.snapshot("qb").unwrap().tunnel.unwrap();
        let peer_endpoint = SocketAddr::new(Ipv4Addr::LOCALHOST.into(), first.mapping.local.port());
        assert_eq!(
            fixture.calls.actual.load(Relaxed),
            fixture.backend.local_addr().unwrap().port()
        );

        let mut peer = TcpStream::connect(peer_endpoint).await.unwrap();
        let (mut downloader, proxy_addr) = timeout(DEADLINE, fixture.backend.accept())
            .await
            .unwrap()
            .unwrap();
        let source = peer.local_addr().unwrap();
        peer.write_all(b"test").await.unwrap();
        let mut incoming = [0; 4];
        timeout(DEADLINE, downloader.read_exact(&mut incoming))
            .await
            .unwrap()
            .unwrap();
        assert_eq!(&incoming, b"test");
        assert_eq!(mappings.translate(proxy_addr), Some(source));
        bans.write().unwrap().insert(source.ip());
        auto.bans_changed();
        let mut closing = [0; 1];
        let closed = timeout(DEADLINE, peer.read(&mut closing)).await.unwrap();
        assert!(matches!(closed, Ok(0)) || closed.is_err());
        wait_until(|| mappings.translate(proxy_addr).is_none()).await;
        bans.write().unwrap().clear();

        auto.restart();
        wait_until(|| {
            fixture.bindings.load(Relaxed) >= 2
                && auto
                    .snapshot("qb")
                    .is_some_and(|view| view.tunnel.is_some())
        })
        .await;
        let second = auto.snapshot("qb").unwrap().tunnel.unwrap();
        let restarted_endpoint =
            SocketAddr::new(Ipv4Addr::LOCALHOST.into(), second.mapping.local.port());
        auto.shutdown().await;
        assert!(TcpStream::connect(restarted_endpoint).await.is_err());
        assert_eq!(
            *fixture.calls.writes.lock().unwrap(),
            [fixture.backend.local_addr().unwrap().port()]
        );
        assert!(auto.snapshot("qb").unwrap().forwarder.is_none());
    }

    #[tokio::test]
    async fn rolls_back_a_port_write_that_applied_before_the_downloader_reported_failure() {
        let fixture = Fixture::start().await;
        fixture.calls.fail.store(true, Relaxed);
        let auto = AutoTunnels::start(
            fixture.options(),
            &[fixture.downloader(true)],
            ConnectionMappings::default(),
            Bans::default(),
        );
        wait_until(|| {
            auto.snapshot("qb")
                .is_some_and(|view| view.last_error.is_some())
        })
        .await;
        assert_eq!(fixture.calls.actual.load(Relaxed), OLD_PORT);
        assert_eq!(
            *fixture.calls.writes.lock().unwrap(),
            [fixture.backend.local_addr().unwrap().port(), OLD_PORT]
        );
        assert!(auto.snapshot("qb").unwrap().tunnel.is_none());
        auto.shutdown().await;
    }

    #[tokio::test]
    async fn shutdown_during_startup_waits_for_the_port_write_and_rolls_it_back() {
        let fixture = Fixture::start().await;
        let target = fixture.backend.local_addr().unwrap().port();
        fixture.calls.pause_update.store(true, Relaxed);
        let auto = AutoTunnels::start(
            fixture.options(),
            &[fixture.downloader(true)],
            ConnectionMappings::default(),
            Bans::default(),
        );
        wait_until(|| fixture.calls.actual.load(Relaxed) == target).await;
        let shutdown = auto.clone();
        let closing = tokio::spawn(async move { shutdown.shutdown().await });
        wait_until(|| auto.0.commands.borrow().shutdown).await;
        fixture.calls.resume_update.notify_one();
        timeout(DEADLINE, closing).await.unwrap().unwrap();
        assert_eq!(fixture.calls.actual.load(Relaxed), OLD_PORT);
        assert_eq!(*fixture.calls.writes.lock().unwrap(), [target, OLD_PORT]);
        assert!(auto.snapshot("qb").unwrap().tunnel.is_none());
    }

    impl Fixture {
        async fn start() -> Self {
            let api = TcpListener::bind(LOOPBACK).await.unwrap();
            let api_addr = api.local_addr().unwrap();
            let backend = TcpListener::bind(LOOPBACK).await.unwrap();
            let port = backend.local_addr().unwrap().port();
            let heartbeat = TcpListener::bind(LOOPBACK).await.unwrap();
            let heartbeat_addr = heartbeat.local_addr().unwrap();
            let stun = TcpListener::bind(LOOPBACK).await.unwrap();
            let stun_addr = stun.local_addr().unwrap();
            let calls = Arc::new(PortState {
                actual: AtomicU16::new(OLD_PORT),
                fail: AtomicBool::new(false),
                pause_update: AtomicBool::new(false),
                resume_update: tokio::sync::Notify::new(),
                writes: Mutex::default(),
            });
            let routes = Router::new()
                .route(
                    "/api/v2/app/buildInfo",
                    get(|| async { Json(json!({"libtorrent":"2.0.11"})) }),
                )
                .route("/api/v2/app/version", get(|| async { "v5.2.1" }))
                .route(
                    "/api/v2/app/preferences",
                    get(|State(state): State<Arc<PortState>>| async move {
                        Json(json!({"listen_port":state.actual.load(Relaxed)}))
                    }),
                )
                .route("/api/v2/app/setPreferences", post(write_prefs))
                .with_state(calls.clone());
            let api_task = tokio::spawn(async { axum::serve(api, routes).await.unwrap() });
            let bindings = Arc::new(AtomicUsize::new(0));
            let readings = bindings.clone();
            let binding_task = tokio::spawn(async move {
                loop {
                    let (mut connection, _) = stun.accept().await.unwrap();
                    let mut header = [0; 20];
                    connection.read_exact(&mut header).await.unwrap();
                    assert_eq!(&header[..8], &[0, 1, 0, 0, 0x21, 0x12, 0xa4, 0x42]);
                    let mut reply = vec![1, 1, 0, 12];
                    reply.extend_from_slice(&header[4..]);
                    reply.extend_from_slice(&[0, 1, 0, 8, 0, 1]);
                    reply.extend_from_slice(&port.to_be_bytes());
                    reply.extend_from_slice(&[127, 0, 0, 1]);
                    connection.write_all(&reply).await.unwrap();
                    readings.fetch_add(1, Relaxed);
                }
            });
            let heartbeat_task = tokio::spawn(async move {
                let mut sockets = JoinSet::new();
                loop {
                    tokio::select! {
                        Some(_) = sockets.join_next() => {}
                        result = heartbeat.accept() => {
                            let (connection, _) = result.unwrap();
                            sockets.spawn(heartbeat_response(connection));
                        }
                    }
                }
            });
            Self {
                api: api_addr,
                backend,
                heartbeat: heartbeat_addr,
                stun: stun_addr,
                calls,
                bindings,
                tasks: vec![api_task, binding_task, heartbeat_task],
            }
        }

        fn downloader(&self, apply: bool) -> Downloader {
            let config: DownloaderConfig = serde_yaml_ng::from_str(&format!(
                "type: qbittorrent\nendpoint: http://{}\n",
                self.api
            ))
            .unwrap();
            Downloader::configure(
                &config,
                "qb",
                Duration::from_secs(5),
                "http://127.0.0.1:9898",
                apply,
                &AddressPolicy::default(),
            )
            .unwrap()
            .unwrap()
        }

        fn options(&self) -> AutoTunnelOptions {
            AutoTunnelOptions {
                enabled: true,
                downloaders: vec!["qb".into()],
                friendly_loopback_mapping: false,
                tcp_servers: vec![self.stun.to_string()],
                heartbeat: self.heartbeat.to_string(),
                verify_reachability: false,
                upnp: true,
                local_ports: BTreeMap::new(),
            }
        }
    }

    async fn write_prefs(State(state): State<Arc<PortState>>, body: String) -> StatusCode {
        let json = form_urlencoded::parse(body.as_bytes())
            .find(|(key, _)| key == "json")
            .unwrap()
            .1;
        let value: Value = serde_json::from_str(&json).unwrap();
        if let Some(port) = value.get("listen_port").and_then(Value::as_u64) {
            let port = port.try_into().unwrap();
            state.writes.lock().unwrap().push(port);
            state.actual.store(port, Relaxed);
            if port != OLD_PORT && state.pause_update.load(Relaxed) {
                state.resume_update.notified().await;
            }
            if port != OLD_PORT && state.fail.load(Relaxed) {
                return StatusCode::INTERNAL_SERVER_ERROR;
            }
        }
        StatusCode::OK
    }

    async fn heartbeat_response(stream: TcpStream) {
        let mut stream = BufReader::new(stream);
        loop {
            let mut line = String::new();
            if stream.read_line(&mut line).await.unwrap_or_default() == 0 {
                return;
            }
            assert_eq!(line, "HEAD / HTTP/1.1\r\n");
            while line != "\r\n" {
                line.clear();
                if stream.read_line(&mut line).await.unwrap_or_default() == 0 {
                    return;
                }
            }
            if stream
                .get_mut()
                .write_all(b"HTTP/1.1 204 No Content\r\n\r\n")
                .await
                .is_err()
            {
                return;
            }
        }
    }

    async fn wait_until(mut ready: impl FnMut() -> bool) {
        timeout(DEADLINE, async {
            while !ready() {
                sleep(Duration::from_millis(10)).await;
            }
        })
        .await
        .unwrap();
    }
}
