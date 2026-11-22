use std::collections::HashSet;
use std::io;
use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4};
use std::time::Duration;

use reqwest::Url;
use socket2::{Domain, Protocol, Socket, Type};
use tokio::net::UdpSocket;
use tokio::time::{Instant, sleep_until};

use super::endpoint_on;

const SEARCH_TYPES: [&str; 5] = [
    "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
    "urn:schemas-upnp-org:device:InternetGatewayDevice:2",
    "urn:schemas-upnp-org:service:WANIPConnection:1",
    "urn:schemas-upnp-org:service:WANIPConnection:2",
    "urn:schemas-upnp-org:service:WANPPPConnection:1",
];
const MAX_ANNOUNCEMENTS: usize = 32;

pub(super) struct Announcement {
    pub location: Url,
    pub responder: Ipv4Addr,
}

pub(super) async fn search(
    interface: Ipv4Addr,
    destination: SocketAddrV4,
    wait: Duration,
) -> io::Result<Vec<Announcement>> {
    let multicast = destination.ip().is_multicast();
    let socket = Socket::new(Domain::IPV4, Type::DGRAM, Some(Protocol::UDP))?;
    socket.bind(&SocketAddrV4::new(interface, 0).into())?;
    if multicast {
        socket.set_multicast_if_v4(&interface)?;
        socket.set_multicast_ttl_v4(2)?;
    }
    socket.set_nonblocking(true)?;
    let socket = UdpSocket::from_std(socket.into())?;
    let start = Instant::now();
    let deadline = start + wait;
    let mut resend = Some(start + wait / 2);
    let mut buffer = [0; 8192];
    let mut replies = Vec::new();
    let mut seen = HashSet::new();
    send(&socket, destination, multicast).await?;
    while Instant::now() < deadline && replies.len() < MAX_ANNOUNCEMENTS {
        tokio::select! {
            () = sleep_until(deadline) => break,
            () = sleep_until(resend.unwrap_or(deadline)), if resend.is_some() => {
                send(&socket, destination, multicast).await?;
                resend = None;
            }
            received = socket.recv_from(&mut buffer) => {
                let (length, sender) = received?;
                let SocketAddr::V4(sender) = sender else { continue; };
                if !multicast && sender.ip() != destination.ip() {
                    continue;
                }
                let Some(reply) = parse(&buffer[..length], *sender.ip()) else { continue; };
                if seen.insert(reply.location.clone()) {
                    replies.push(reply);
                }
            }
        }
    }
    Ok(replies)
}

async fn send(socket: &UdpSocket, destination: SocketAddrV4, multicast: bool) -> io::Result<()> {
    let mx = if multicast { "MX: 2\r\n" } else { "" };
    for target in SEARCH_TYPES {
        let request = format!(
            "M-SEARCH * HTTP/1.1\r\nHOST: {destination}\r\nMAN: \"ssdp:discover\"\r\n{mx}ST: {target}\r\n\r\n"
        );
        socket.send_to(request.as_bytes(), destination).await?;
    }
    Ok(())
}

fn parse(bytes: &[u8], responder: Ipv4Addr) -> Option<Announcement> {
    let mut lines = std::str::from_utf8(bytes).ok()?.lines();
    let mut status = lines.next()?.split_ascii_whitespace();
    if !matches!(status.next(), Some("HTTP/1.0" | "HTTP/1.1")) || status.next()? != "200" {
        return None;
    }
    let mut location = None;
    let mut target = None;
    for line in lines.take_while(|line| !line.is_empty()) {
        let (name, value) = line.split_once(':')?;
        let value = value.trim();
        if name.eq_ignore_ascii_case("LOCATION") && location.replace(value).is_some() {
            return None;
        }
        if name.eq_ignore_ascii_case("ST") && target.replace(value).is_some() {
            return None;
        }
    }
    let target = target?;
    if !SEARCH_TYPES
        .iter()
        .any(|known| known.eq_ignore_ascii_case(target))
    {
        return None;
    }
    let location = Url::parse(location?).ok()?;
    endpoint_on(&location, responder).then_some(Announcement {
        location,
        responder,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_only_search_replies_with_one_pinned_description() {
        for (status, location, duplicate, expected) in [
            (
                "HTTP/1.1 200 OK",
                "http://127.0.0.1:5000/root.xml",
                "",
                true,
            ),
            (
                "HTTP/1.0 200 OK",
                "http://127.0.0.1:5000/root.xml",
                "",
                true,
            ),
            (
                "NOTIFY * HTTP/1.1",
                "http://127.0.0.1:5000/root.xml",
                "",
                false,
            ),
            (
                "HTTP/1.1 404 NOT FOUND",
                "http://127.0.0.1:5000/root.xml",
                "",
                false,
            ),
            (
                "HTTP/1.1 200 OK",
                "http://127.0.0.2:5000/root.xml",
                "",
                false,
            ),
            (
                "HTTP/1.1 200 OK",
                "http://localhost:5000/root.xml",
                "",
                false,
            ),
            (
                "HTTP/1.1 200 OK",
                "http://name:secret@127.0.0.1:5000/root.xml",
                "",
                false,
            ),
            (
                "HTTP/1.1 200 OK",
                "http://127.0.0.1:5000/root.xml",
                "LOCATION: http://127.0.0.1:5000/other.xml\r\n",
                false,
            ),
        ] {
            let packet = format!(
                "{status}\r\nLoCAtIoN: {location}\r\nST: {}\r\n{duplicate}\r\n",
                SEARCH_TYPES[0]
            );
            assert_eq!(
                parse(packet.as_bytes(), Ipv4Addr::LOCALHOST).is_some(),
                expected,
                "{packet}"
            );
        }
    }
}
