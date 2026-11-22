use std::net::{Ipv4Addr, SocketAddr};
use std::num::NonZeroUsize;
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering::Relaxed};
use std::time::Duration;

use pbh_traversal::PacketError;
use pbh_traversal::tunnel::{
    BindingError, HeartbeatConfig, TunnelConfig, TunnelError, TunnelListener, TunnelStop,
};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};
use tokio::time::{sleep, timeout};

const LOOPBACK: SocketAddr = SocketAddr::new(std::net::IpAddr::V4(Ipv4Addr::LOCALHOST), 0);
const DEADLINE: Duration = Duration::from_secs(5);

#[tokio::test]
async fn retains_the_binding_port_for_probe_listener_and_repeated_keepalive() {
    let reservation = TunnelListener::bind("0.0.0.0:0".parse().unwrap()).unwrap();
    let local = reservation.local_addr();
    let expected = SocketAddr::new(Ipv4Addr::LOCALHOST.into(), local.port());
    let bad = TcpListener::bind(LOOPBACK).await.unwrap();
    let good = TcpListener::bind(LOOPBACK).await.unwrap();
    let bad_addr = bad.local_addr().unwrap();
    let good_addr = good.local_addr().unwrap();
    let bad_fixture = tokio::spawn(async move {
        let (mut stream, source) = bad.accept().await.unwrap();
        assert_eq!(source, expected);
        let request = read_binding(&mut stream).await;
        let mut reply = success(&request, expected);
        reply[19] ^= 1;
        stream.write_all(&reply).await.unwrap();
    });
    let good_fixture = tokio::spawn(async move {
        let (mut stream, source) = good.accept().await.unwrap();
        assert_eq!(source, expected);
        let request = read_binding(&mut stream).await;
        let reply = success(&request, source);
        for chunk in reply.chunks(5) {
            stream.write_all(chunk).await.unwrap();
            sleep(Duration::from_millis(3)).await;
        }
    });

    let heartbeat = TcpListener::bind(LOOPBACK).await.unwrap();
    let heartbeat_addr = heartbeat.local_addr().unwrap();
    let messages = Arc::new(AtomicUsize::new(0));
    let fixture_messages = messages.clone();
    let heart_fixture = tokio::spawn(async move {
        let (stream, source) = heartbeat.accept().await.unwrap();
        assert_eq!(source, expected);
        let mut stream = BufReader::new(stream);
        while request_line(&mut stream).await.as_deref() == Some("HEAD / HTTP/1.1") {
            stream
                .get_mut()
                .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n")
                .await
                .unwrap();
            fixture_messages.fetch_add(1, Relaxed);
        }
    });

    let config = TunnelConfig::new(vec![bad_addr, good_addr], heartbeat_config(heartbeat_addr));
    let (tunnel, listener) = timeout(DEADLINE, reservation.establish(config))
        .await
        .unwrap()
        .unwrap();
    assert_eq!(listener.local_addr().unwrap(), local);
    assert_eq!(tunnel.snapshot().mapping.local, expected);
    assert_eq!(tunnel.snapshot().mapping.mapped, expected);
    assert_eq!(tunnel.snapshot().mapping.server, good_addr);
    assert!(tunnel.snapshot().valid);
    let mut peer = TcpStream::connect(expected).await.unwrap();
    let (mut accepted, _) = timeout(DEADLINE, listener.accept()).await.unwrap().unwrap();
    peer.write_all(b"still listening").await.unwrap();
    let mut incoming = [0; 15];
    accepted.read_exact(&mut incoming).await.unwrap();
    assert_eq!(&incoming, b"still listening");

    wait_until(|| messages.load(Relaxed) >= 2 && tunnel.snapshot().last_success_at_ms > 0).await;
    assert_eq!(tunnel.snapshot().consecutive_failures, 0);
    tunnel.shutdown().await.unwrap();
    timeout(DEADLINE, heart_fixture).await.unwrap().unwrap();
    bad_fixture.await.unwrap();
    good_fixture.await.unwrap();
}

#[tokio::test]
async fn failed_binding_rounds_are_bounded_and_retry_after_the_pause() {
    let reservation = TunnelListener::bind(LOOPBACK).unwrap();
    let local = reservation.local_addr();
    let silent = TcpListener::bind(LOOPBACK).await.unwrap();
    let refusing = TcpListener::bind(LOOPBACK).await.unwrap();
    let silent_addr = silent.local_addr().unwrap();
    let refusing_addr = refusing.local_addr().unwrap();
    let silent_fixture = tokio::spawn(async move {
        for _ in 0..2 {
            let (mut stream, source) = silent.accept().await.unwrap();
            assert_eq!(source, local);
            read_binding(&mut stream).await;
            let mut empty = [0; 1];
            let _ = stream.read(&mut empty).await;
        }
    });
    let declining_fixture = tokio::spawn(async move {
        for _ in 0..2 {
            let (mut stream, source) = refusing.accept().await.unwrap();
            assert_eq!(source, local);
            let request = read_binding(&mut stream).await;
            let mut packet = vec![1, 17, 0, 8];
            packet.extend_from_slice(&request[4..]);
            packet.extend_from_slice(&[0, 9, 0, 4, 0, 0, 4, 0]);
            stream.write_all(&packet).await.unwrap();
        }
    });
    let mut config = TunnelConfig::new(
        vec![silent_addr, refusing_addr],
        heartbeat_config(silent_addr),
    );
    config.binding_timeout = Duration::from_millis(150);
    config.retry_delay = Duration::from_millis(40);
    config.max_rounds = NonZeroUsize::new(2).unwrap();
    let result = timeout(DEADLINE, reservation.establish(config))
        .await
        .unwrap();
    assert!(matches!(
        result,
        Err(TunnelError::Binding {
            attempts: 4,
            last: BindingError::Packet(PacketError::Server(400))
        })
    ));
    timeout(DEADLINE, silent_fixture).await.unwrap().unwrap();
    timeout(DEADLINE, declining_fixture).await.unwrap().unwrap();
}

#[tokio::test]
async fn heartbeat_reconnects_on_same_port_and_exposes_terminal_failure() {
    let reservation = TunnelListener::bind(LOOPBACK).unwrap();
    let local = reservation.local_addr();
    let stun = TcpListener::bind(LOOPBACK).await.unwrap();
    let stun_addr = stun.local_addr().unwrap();
    let stun_fixture = tokio::spawn(async move {
        let (mut stream, source) = stun.accept().await.unwrap();
        let request = read_binding(&mut stream).await;
        stream.write_all(&success(&request, source)).await.unwrap();
    });
    let heartbeat = TcpListener::bind(LOOPBACK).await.unwrap();
    let heartbeat_addr = heartbeat.local_addr().unwrap();
    let heartbeat_fixture = tokio::spawn(async move {
        for good_response in [true, false, false] {
            let (stream, source) = heartbeat.accept().await.unwrap();
            assert_eq!(source, local);
            let mut stream = BufReader::new(stream);
            assert_eq!(
                request_line(&mut stream).await.as_deref(),
                Some("HEAD / HTTP/1.1")
            );
            if good_response {
                stream
                    .get_mut()
                    .write_all(b"HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
                    .await
                    .unwrap();
            } else {
                stream
                    .get_mut()
                    .write_all(b"bad HTTP response\r\n\r\n")
                    .await
                    .unwrap();
            }
        }
    });
    let mut config = TunnelConfig::new(vec![stun_addr], heartbeat_config(heartbeat_addr));
    config.reachability_timeout = None;
    let (tunnel, _listener) = reservation.establish(config).await.unwrap();
    let stopped = timeout(DEADLINE, tunnel.wait_stopped()).await.unwrap();
    assert_eq!(stopped.stop_reason, Some(TunnelStop::HeartbeatExhausted));
    assert_eq!(stopped.consecutive_failures, 2);
    assert!(stopped.last_success_at_ms > 0);
    assert!(!stopped.valid);
    tunnel.shutdown().await.unwrap();
    timeout(DEADLINE, heartbeat_fixture).await.unwrap().unwrap();
    stun_fixture.await.unwrap();
}

#[tokio::test]
async fn verifies_the_reserved_listener_actually_received_the_probe() {
    let reservation = TunnelListener::bind(LOOPBACK).unwrap();
    let unrelated = TcpListener::bind(LOOPBACK).await.unwrap();
    let wrong_addr = unrelated.local_addr().unwrap();
    let unrelated_fixture = tokio::spawn(async move {
        let (stream, _) = unrelated.accept().await.unwrap();
        let mut stream = BufReader::new(stream);
        assert!(
            request_line(&mut stream)
                .await
                .unwrap()
                .starts_with("GET /pbh-autostun/")
        );
        stream
            .get_mut()
            .write_all(b"HTTP/1.1 204 No Content\r\n\r\n")
            .await
            .unwrap();
    });
    let stun = TcpListener::bind(LOOPBACK).await.unwrap();
    let stun_addr = stun.local_addr().unwrap();
    let stun_fixture = tokio::spawn(async move {
        let (mut stream, _) = stun.accept().await.unwrap();
        let request = read_binding(&mut stream).await;
        stream
            .write_all(&success(&request, wrong_addr))
            .await
            .unwrap();
    });
    let mut config = TunnelConfig::new(vec![stun_addr], heartbeat_config(wrong_addr));
    config.reachability_timeout = Some(Duration::from_millis(220));
    let result = timeout(DEADLINE, reservation.establish(config))
        .await
        .unwrap();
    assert!(matches!(result, Err(TunnelError::ReachabilityTimeout)));
    unrelated_fixture.await.unwrap();
    stun_fixture.await.unwrap();
}

fn heartbeat_config(remote: SocketAddr) -> HeartbeatConfig {
    let mut config = HeartbeatConfig::new(remote, "loopback.test");
    config.first_delay = Duration::from_millis(20);
    config.interval = Duration::from_millis(100);
    config.timeout = Duration::from_millis(70);
    config.max_failures = NonZeroUsize::new(2).unwrap();
    config
}

async fn read_binding(stream: &mut TcpStream) -> [u8; 20] {
    let mut request = [0; 20];
    stream.read_exact(&mut request).await.unwrap();
    assert_eq!(&request[..8], &[0, 1, 0, 0, 0x21, 0x12, 0xa4, 0x42]);
    request
}

fn success(request: &[u8; 20], mapped: SocketAddr) -> Vec<u8> {
    let SocketAddr::V4(mapped) = mapped else {
        unreachable!()
    };
    let mut reply = vec![1, 1, 0, 12];
    reply.extend_from_slice(&request[4..]);
    reply.extend_from_slice(&[0, 32, 0, 8, 0, 1]);
    reply.extend_from_slice(&(mapped.port() ^ 0x2112).to_be_bytes());
    reply.extend(
        mapped
            .ip()
            .octets()
            .into_iter()
            .zip(&request[4..8])
            .map(|(ip, key)| ip ^ key),
    );
    reply
}

async fn request_line(stream: &mut BufReader<TcpStream>) -> Option<String> {
    let mut first = String::new();
    if stream.read_line(&mut first).await.ok()? == 0 {
        return None;
    }
    loop {
        let mut line = String::new();
        if stream.read_line(&mut line).await.ok()? == 0 {
            return None;
        }
        if line == "\r\n" {
            break;
        }
    }
    Some(first.trim_end().to_owned())
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
