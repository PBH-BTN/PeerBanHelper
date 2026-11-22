use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::num::NonZeroU8;
use std::time::Duration;

use pbh_traversal::{ConnectionMappings, NatType, RetryPolicy, StunVersion, UdpStunClient};
use tokio::net::UdpSocket;

const EXTERNAL: SocketAddr = SocketAddr::new(IpAddr::V4(Ipv4Addr::new(192, 0, 2, 44)), 4500);
const OPEN: SocketAddr = SocketAddr::new(IpAddr::V4(Ipv4Addr::LOCALHOST), 4500);
const LOOPBACK: SocketAddr = SocketAddr::new(IpAddr::V4(Ipv4Addr::LOCALHOST), 0);

#[tokio::test]
async fn repeated_binding_discards_mismatched_sender_transaction_and_origin() {
    let server = UdpSocket::bind(LOOPBACK).await.unwrap();
    let other = UdpSocket::bind(LOOPBACK).await.unwrap();
    let source = server.local_addr().unwrap();
    let task = tokio::spawn(async move {
        let mut packet = [0; 64];
        let (length, peer) = server.recv_from(&mut packet).await.unwrap();
        assert_eq!(length, 20);
        assert_eq!(&packet[..8], &[0, 1, 0, 0, 0x21, 0x12, 0xa4, 0x42]);
        let request = packet[..length].to_vec();
        let correct = reply(&request, EXTERNAL, source, None);
        other.send_to(&correct, peer).await.unwrap();
        let mut mismatched = correct.clone();
        mismatched[19] ^= 1;
        server.send_to(&mismatched, peer).await.unwrap();
        server.send_to(&correct[..19], peer).await.unwrap();
        let wrong_origin = reply(&request, EXTERNAL, other.local_addr().unwrap(), None);
        server.send_to(&wrong_origin, peer).await.unwrap();

        let (length, retry_peer) = server.recv_from(&mut packet).await.unwrap();
        assert_eq!(retry_peer, peer);
        assert_eq!(&packet[..length], request);
        server.send_to(&correct, peer).await.unwrap();
    });
    let mut client = UdpStunClient::bind(LOOPBACK, test_retry()).await.unwrap();
    let result = client.binding(source, StunVersion::Modern).await.unwrap();
    assert_eq!(result.mapped, EXTERNAL);
    assert_eq!(result.origin, Some(source));
    task.await.unwrap();
}

#[tokio::test]
async fn classifies_legacy_nat_and_filtering_on_one_udp_socket() {
    for expected in [
        NatType::OpenInternet,
        NatType::SymmetricUdpFirewall,
        NatType::FullCone,
        NatType::Symmetric,
        NatType::RestrictedCone,
        NatType::PortRestrictedCone,
        NatType::Unknown,
        NatType::UdpBlocked,
    ] {
        let primary = UdpSocket::bind(LOOPBACK).await.unwrap();
        let secondary = UdpSocket::bind(LOOPBACK).await.unwrap();
        let server = primary.local_addr().unwrap();
        let alternate = secondary.local_addr().unwrap();
        let mut client = UdpStunClient::bind(LOOPBACK, test_retry()).await.unwrap();
        let client_addr = client.local_addr().unwrap();
        let mapped = if matches!(
            expected,
            NatType::OpenInternet | NatType::SymmetricUdpFirewall
        ) {
            OPEN
        } else {
            EXTERNAL
        };
        let fixture = tokio::spawn(async move {
            let mut first = [0; 64];
            let mut second = [0; 64];
            loop {
                let (is_primary, request, peer) = tokio::select! {
                    result = primary.recv_from(&mut first) => {
                        let (length, peer) = result.unwrap();
                        (true, &first[..length], peer)
                    }
                    result = secondary.recv_from(&mut second) => {
                        let (length, peer) = result.unwrap();
                        (false, &second[..length], peer)
                    }
                };
                assert_eq!(peer, client_addr);
                assert_eq!(&request[4..8], &[0; 4]);
                let change = request.get(27).copied().unwrap_or_default();
                let (response_socket, mapping) = match (is_primary, change, expected) {
                    (true, 0, NatType::UdpBlocked) => continue,
                    (true, 0, _) => (&primary, mapped),
                    (true, 6, NatType::OpenInternet | NatType::FullCone) => (&secondary, mapped),
                    (true, 6, NatType::Unknown) => (&primary, mapped),
                    (true, 6, _) => continue,
                    (false, 0, NatType::Symmetric) => {
                        (&secondary, SocketAddr::new(mapped.ip(), 4501))
                    }
                    (false, 0, _) => (&secondary, mapped),
                    (false, 2, NatType::RestrictedCone) => (&primary, mapped),
                    (false, 2, NatType::PortRestrictedCone) => continue,
                    _ => panic!("unexpected STUN request"),
                };
                let origin = response_socket.local_addr().unwrap();
                let response = reply(request, mapping, origin, Some(alternate));
                response_socket.send_to(&response, peer).await.unwrap();
            }
        });
        let result = client
            .probe(server, Ipv4Addr::LOCALHOST.into(), StunVersion::Legacy)
            .await
            .unwrap();
        assert_eq!(result.nat_type, expected);
        assert_eq!(
            result.mapped,
            (expected != NatType::UdpBlocked).then_some(mapped)
        );
        fixture.abort();
    }
}

#[test]
fn translations_use_the_live_source_socket_and_remove_released_entries() {
    let mappings = ConnectionMappings::default();
    let socket = "127.0.0.2:6001".parse().unwrap();
    let mapped_socket = "[::ffff:127.0.0.2]:6001".parse().unwrap();
    let original = "[2001:db8::1234]:51413".parse().unwrap();
    let mapping = mappings.register(socket, original).unwrap();
    assert!(mappings.register(mapped_socket, EXTERNAL).is_err());
    assert_eq!(mappings.translate(mapped_socket), Some(original));
    assert_eq!(mappings.translate("127.0.0.2:6002".parse().unwrap()), None);
    drop(mapping);
    assert_eq!(mappings.translate(mapped_socket), None);
    let _replacement = mappings.register(mapped_socket, EXTERNAL).unwrap();
    assert_eq!(mappings.translate(socket), Some(EXTERNAL));
}

fn test_retry() -> RetryPolicy {
    RetryPolicy {
        attempts: NonZeroU8::new(2).unwrap(),
        attempt_timeout: Duration::from_millis(65),
        total_timeout: Duration::from_secs(2),
    }
}

fn reply(
    request: &[u8],
    mapped: SocketAddr,
    origin: SocketAddr,
    other: Option<SocketAddr>,
) -> Vec<u8> {
    let modern = request[4] == 0x21;
    let mut result = vec![1, 1, 0, 0];
    result.extend_from_slice(&request[4..20]);
    address_attribute(&mut result, 1, mapped);
    address_attribute(&mut result, if modern { 0x802b } else { 4 }, origin);
    if let Some(other) = other {
        address_attribute(&mut result, if modern { 0x802c } else { 5 }, other);
    }
    let length = u16::try_from(result.len() - 20).unwrap();
    result[2..4].copy_from_slice(&length.to_be_bytes());
    result
}

fn address_attribute(result: &mut Vec<u8>, kind: u16, address: SocketAddr) {
    let SocketAddr::V4(address) = address else {
        panic!("expected IPv4 fixture address");
    };
    result.extend_from_slice(&kind.to_be_bytes());
    result.extend_from_slice(&[0, 8, 0, 1]);
    result.extend_from_slice(&address.port().to_be_bytes());
    result.extend_from_slice(&address.ip().octets());
}
