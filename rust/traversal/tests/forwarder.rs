use std::io;
use std::net::{Ipv4Addr, SocketAddr};
use std::num::NonZeroUsize;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering::Relaxed};
use std::time::Duration;

use pbh_traversal::ConnectionMappings;
use pbh_traversal::forwarder::{ForwarderConfig, ForwarderError, TcpForwarder};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpSocket, TcpStream};
use tokio::time::{sleep, timeout};

const LOOPBACK: SocketAddr = SocketAddr::new(std::net::IpAddr::V4(Ipv4Addr::LOCALHOST), 0);
const DEADLINE: Duration = Duration::from_secs(3);

#[tokio::test]
async fn copies_both_directions_then_honors_half_close_and_releases_mapping() {
    let (listener, backend, mappings, mut config) = sockets().await;
    config.friendly_loopback_mapping = false;
    let forwarder = TcpForwarder::start(listener, config, mappings.clone()).unwrap();
    let mut peer = TcpStream::connect(forwarder.local_addr()).await.unwrap();
    let peer_addr = peer.local_addr().unwrap();
    let (mut upstream, proxy_addr) = accept(&backend).await;
    wait_until(|| mappings.translate(proxy_addr) == Some(peer_addr)).await;

    peer.write_all(b"playlist").await.unwrap();
    peer.shutdown().await.unwrap();
    let mut first = Vec::new();
    timeout(DEADLINE, upstream.read_to_end(&mut first))
        .await
        .unwrap()
        .unwrap();
    assert_eq!(first, b"playlist");
    upstream.write_all(b"blocks").await.unwrap();
    let mut second = [0; 6];
    timeout(DEADLINE, peer.read_exact(&mut second))
        .await
        .unwrap()
        .unwrap();
    assert_eq!(&second, b"blocks");

    let snapshot = forwarder.snapshot();
    assert_eq!(snapshot.established_connections, 1);
    assert_eq!(snapshot.pending_connections, 0);
    assert_eq!(snapshot.connection_handled, 1);
    assert_eq!(snapshot.total_to_upstream, 8);
    assert_eq!(snapshot.total_to_downstream, 6);
    let flow = &snapshot.connections[0];
    assert_eq!(flow.downstream, peer_addr);
    assert_eq!(flow.upstream_local, proxy_addr);
    assert_eq!(flow.to_upstream_bytes, 8);
    assert_eq!(flow.to_downstream_bytes, 6);
    assert_ne!(flow.last_activity_at_ms, 0);
    upstream.shutdown().await.unwrap();
    closed(&mut peer).await;
    wait_until(|| forwarder.snapshot().connections.is_empty()).await;
    assert_eq!(mappings.translate(proxy_addr), None);
    forwarder.shutdown().await.unwrap();
}

#[tokio::test]
async fn rejects_duplicates_and_capacity_then_releases_reservations_on_cancel() {
    let (listener, backend, mappings, mut config) = sockets().await;
    config.max_connections = NonZeroUsize::new(1);
    let forwarder = TcpForwarder::start(listener, config, mappings.clone()).unwrap();
    let mut peer = connect_from("127.0.0.2".parse().unwrap(), forwarder.local_addr()).await;
    let peer_addr = peer.local_addr().unwrap();
    let (mut upstream, proxy_addr) = accept(&backend).await;
    wait_until(|| mappings.translate(proxy_addr) == Some(peer_addr)).await;
    assert_eq!(proxy_addr.ip(), peer_addr.ip());
    let mut duplicate = connect_from("127.0.0.2".parse().unwrap(), forwarder.local_addr()).await;
    closed(&mut duplicate).await;
    let mut over_capacity = TcpStream::connect(forwarder.local_addr()).await.unwrap();
    closed(&mut over_capacity).await;
    assert_eq!(forwarder.snapshot().connection_rejected, 2);

    assert_eq!(forwarder.disconnect_ip(peer_addr.ip()), 1);
    assert_eq!(forwarder.disconnect_ip(peer_addr.ip()), 0);
    closed(&mut peer).await;
    closed(&mut upstream).await;
    wait_until(|| forwarder.snapshot().established_connections == 0).await;
    assert_eq!(mappings.translate(proxy_addr), None);

    let mut replacement = connect_from("127.0.0.2".parse().unwrap(), forwarder.local_addr()).await;
    let (mut replacement_upstream, _) = accept(&backend).await;
    let local_addr = forwarder.local_addr();
    forwarder.shutdown().await.unwrap();
    closed(&mut replacement).await;
    closed(&mut replacement_upstream).await;
    assert!(TcpStream::connect(local_addr).await.is_err());
}

#[tokio::test]
async fn blocks_banned_connections_and_cancels_existing_banned_peers() {
    let (listener, backend, mappings, config) = sockets().await;
    let banned = Arc::new(AtomicBool::new(true));
    let policy = banned.clone();
    let forwarder =
        TcpForwarder::start_with_policy(listener, config, mappings.clone(), move |_| {
            policy.load(Relaxed)
        })
        .unwrap();
    let mut denied = TcpStream::connect(forwarder.local_addr()).await.unwrap();
    closed(&mut denied).await;
    assert_eq!(forwarder.snapshot().connection_blocked, 1);
    banned.store(false, Relaxed);
    let mut allowed = TcpStream::connect(forwarder.local_addr()).await.unwrap();
    let allowed_addr = allowed.local_addr().unwrap();
    let (mut upstream, listen_as) = accept(&backend).await;
    wait_until(|| mappings.translate(listen_as) == Some(allowed_addr)).await;
    banned.store(true, Relaxed);
    assert_eq!(forwarder.disconnect_banned(), 1);
    assert_eq!(forwarder.disconnect_banned(), 0);
    closed(&mut allowed).await;
    closed(&mut upstream).await;
    assert_eq!(forwarder.snapshot().connection_blocked, 2);
    forwarder.shutdown().await.unwrap();
}

#[tokio::test]
async fn activity_extends_the_idle_deadline_and_timeout_cleans_up() {
    let (listener, backend, mappings, mut config) = sockets().await;
    config.idle_timeout = Some(Duration::from_millis(300));
    let forwarder = TcpForwarder::start(listener, config, mappings.clone()).unwrap();
    let mut downstream = TcpStream::connect(forwarder.local_addr()).await.unwrap();
    let peer_addr = downstream.local_addr().unwrap();
    let (mut upstream, proxy_addr) = accept(&backend).await;
    wait_until(|| mappings.translate(proxy_addr) == Some(peer_addr)).await;
    for _ in 0..3 {
        sleep(Duration::from_millis(115)).await;
        upstream.write_all(&[42]).await.unwrap();
        assert_eq!(
            timeout(DEADLINE, downstream.read_u8())
                .await
                .unwrap()
                .unwrap(),
            42
        );
    }
    closed(&mut downstream).await;
    closed(&mut upstream).await;
    wait_until(|| forwarder.snapshot().connections.is_empty()).await;
    assert_eq!(mappings.translate(proxy_addr), None);
    let stats = forwarder.snapshot();
    assert_eq!(stats.connection_timed_out, 1);
    assert_eq!(stats.connection_failed, 1);
    assert_eq!(stats.total_to_downstream, 3);
    forwarder.shutdown().await.unwrap();
}

#[tokio::test]
async fn handles_upstream_connection_failure_without_stale_state() {
    let (listener, upstream, mappings, mut config) = sockets().await;
    drop(upstream);
    config.connect_timeout = Duration::from_millis(350);
    config.friendly_loopback_mapping = false;
    let forwarder = TcpForwarder::start(listener, config, mappings).unwrap();
    for expected in [1, 2] {
        let mut peer = TcpStream::connect(forwarder.local_addr()).await.unwrap();
        closed(&mut peer).await;
        wait_until(|| forwarder.snapshot().pending_connections == 0).await;
        let snapshot = forwarder.snapshot();
        assert_eq!(snapshot.connection_handled, expected);
        assert_eq!(snapshot.connection_failed, expected);
        assert_eq!(snapshot.established_connections, 0);
    }
    forwarder.shutdown().await.unwrap();
}

#[tokio::test]
async fn rejects_public_upstream_and_self_forwarding() {
    let listener = TcpListener::bind(LOOPBACK).await.unwrap();
    assert!(matches!(
        TcpForwarder::start(
            listener,
            ForwarderConfig::new("192.0.2.15:4500".parse().unwrap()),
            ConnectionMappings::default()
        ),
        Err(ForwarderError::InvalidUpstream)
    ));
    let listener = TcpListener::bind(LOOPBACK).await.unwrap();
    let local = listener.local_addr().unwrap();
    assert!(matches!(
        TcpForwarder::start(
            listener,
            ForwarderConfig::new(local),
            ConnectionMappings::default()
        ),
        Err(ForwarderError::ForwardingLoop)
    ));
}

async fn sockets() -> (
    TcpListener,
    TcpListener,
    ConnectionMappings,
    ForwarderConfig,
) {
    let listener = TcpListener::bind(LOOPBACK).await.unwrap();
    let upstream = TcpListener::bind(LOOPBACK).await.unwrap();
    let config = ForwarderConfig::new(upstream.local_addr().unwrap());
    (listener, upstream, ConnectionMappings::default(), config)
}

async fn connect_from(ip: Ipv4Addr, destination: SocketAddr) -> TcpStream {
    let client = TcpSocket::new_v4().unwrap();
    client.bind(SocketAddr::new(ip.into(), 0)).unwrap();
    client.connect(destination).await.unwrap()
}

async fn accept(listener: &TcpListener) -> (TcpStream, SocketAddr) {
    timeout(DEADLINE, listener.accept()).await.unwrap().unwrap()
}

async fn closed(stream: &mut TcpStream) {
    let mut buf = [0; 1];
    let result = timeout(DEADLINE, stream.read(&mut buf)).await.unwrap();
    assert!(
        matches!(result, Ok(0))
            || matches!(result, Err(error) if error.kind() == io::ErrorKind::ConnectionReset)
    );
}

async fn wait_until(mut condition: impl FnMut() -> bool) {
    timeout(DEADLINE, async {
        while !condition() {
            sleep(Duration::from_millis(5)).await;
        }
    })
    .await
    .unwrap();
}
