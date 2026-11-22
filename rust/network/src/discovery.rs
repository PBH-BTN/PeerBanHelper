//! Find supported downloaders among this host's existing TCP listeners.

use std::collections::BTreeSet;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::time::Duration;

use futures_util::{StreamExt, stream};
use reqwest::{Client, Url, header, redirect};
use serde::Serialize;
use tokio::sync::Mutex;

const MAX_CONCURRENT_PROBES: usize = 64;
const MAX_BODY: usize = 512 * 1024;
const PROBE_TIMEOUT: Duration = Duration::from_secs(8);
static ACTIVE_SCAN: Mutex<()> = Mutex::const_new(());

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd, Serialize)]
pub struct DiscoveredDownloader {
    pub host: String,
    pub port: u16,
    #[serde(rename = "type")]
    pub kind: DownloaderKind,
    pub pid: u32,
    pub status: DiscoveryStatus,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum DownloaderKind {
    BitComet,
    BiglyBt,
    Qbittorrent,
    Transmission,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd, Serialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum DiscoveryStatus {
    New,
    Added,
}

/// Inspect listeners reported by the local OS. There is no caller-supplied probe target.
pub async fn discover(configured_endpoints: Vec<String>) -> Vec<DiscoveredDownloader> {
    let _scan = ACTIVE_SCAN.lock().await;
    let listeners = tokio::task::spawn_blocking(local_tcp_listeners)
        .await
        .unwrap_or_default();
    if listeners.is_empty() {
        return Vec::new();
    }
    let ports = stream::iter(configured_endpoints.into_iter().map(configured_local_port))
        .buffer_unordered(MAX_CONCURRENT_PROBES)
        .filter_map(std::future::ready)
        .collect::<BTreeSet<_>>()
        .await;
    let sockets = listeners
        .into_iter()
        .map(|(address, pid)| (probe_address(address), pid))
        .collect();
    probe_listeners(sockets, &ports).await
}

#[cfg(any(
    target_os = "windows",
    target_os = "linux",
    target_os = "macos",
    target_os = "freebsd",
    target_os = "openbsd",
    target_os = "netbsd"
))]
fn local_tcp_listeners() -> BTreeSet<(SocketAddr, u32)> {
    use listeners::{Protocol, SocketState};

    listeners::get_all()
        .unwrap_or_default()
        .into_iter()
        .filter(|entry| entry.protocol == Protocol::TCP && entry.state == SocketState::Listen)
        .filter(|entry| entry.socket.port() != 0)
        .map(|entry| (entry.socket, entry.process.pid))
        .collect()
}

#[cfg(not(any(
    target_os = "windows",
    target_os = "linux",
    target_os = "macos",
    target_os = "freebsd",
    target_os = "openbsd",
    target_os = "netbsd"
)))]
fn local_tcp_listeners() -> BTreeSet<(SocketAddr, u32)> {
    BTreeSet::new()
}

fn probe_address(address: SocketAddr) -> SocketAddr {
    let ip = address.ip().to_canonical();
    let ip = if ip.is_unspecified() {
        Ipv4Addr::LOCALHOST.into()
    } else {
        ip
    };
    SocketAddr::new(ip, address.port())
}

async fn configured_local_port(endpoint: String) -> Option<u16> {
    let endpoint = Url::parse(&endpoint).ok()?;
    let port = endpoint.port_or_known_default()?;
    let host = endpoint.host_str()?.trim_matches(['[', ']']);
    let is_local = match host.parse::<IpAddr>() {
        Ok(address) => local_site_address(address),
        Err(_) => tokio::time::timeout(PROBE_TIMEOUT, tokio::net::lookup_host((host, port)))
            .await
            .ok()?
            .ok()?
            .next()
            .is_some_and(|address| local_site_address(address.ip())),
    };
    is_local.then_some(port)
}

fn local_site_address(ip: IpAddr) -> bool {
    let ip = ip.to_canonical();
    if ip.is_loopback() || ip.is_unspecified() {
        return true;
    }
    match ip {
        IpAddr::V4(ip) => ip.is_private(),
        IpAddr::V6(ip) => ip.segments()[0] & 0xffc0 == 0xfec0,
    }
}

async fn probe_listeners(
    listeners: BTreeSet<(SocketAddr, u32)>,
    configured_ports: &BTreeSet<u16>,
) -> Vec<DiscoveredDownloader> {
    let client = Client::builder()
        .no_proxy()
        .timeout(PROBE_TIMEOUT)
        .redirect(redirect::Policy::custom(|attempt| {
            let Some(origin) = attempt.previous().first() else {
                return attempt.stop();
            };
            let target = attempt.url();
            if attempt.previous().len() >= 10
                || !matches!(target.scheme(), "http" | "https")
                || target.host() != origin.host()
                || target.port_or_known_default() != origin.port_or_known_default()
            {
                return attempt.stop();
            }
            attempt.follow()
        }))
        .user_agent(concat!(
            "PeerBanHelper-DownloaderDiscovery/1.0 PeerBanHelper/",
            env!("CARGO_PKG_VERSION")
        ))
        .build();
    let Ok(client) = client else {
        return Vec::new();
    };
    let entries = stream::iter(listeners.into_iter().map(|(address, pid)| {
        let client = &client;
        async move {
            let kind =
                fingerprint(client.get(format!("http://{address}/")).send().await.ok()?).await?;
            let host = match address.ip() {
                IpAddr::V4(host) => host.to_string(),
                IpAddr::V6(host) => format!("[{host}]"),
            };
            let port = address.port();
            let status = if configured_ports.contains(&port) {
                DiscoveryStatus::Added
            } else {
                DiscoveryStatus::New
            };
            Some(DiscoveredDownloader {
                host,
                port,
                kind,
                pid,
                status,
            })
        }
    }))
    .buffer_unordered(MAX_CONCURRENT_PROBES)
    .filter_map(std::future::ready)
    .collect::<BTreeSet<_>>()
    .await;
    entries.into_iter().collect()
}

async fn fingerprint(mut response: reqwest::Response) -> Option<DownloaderKind> {
    if header_contains(&response, header::SERVER, "Transmission") {
        return Some(DownloaderKind::Transmission);
    }
    if header_contains(&response, header::SERVER, "PeerBanHelper-BiglyBT-Adapter") {
        return Some(DownloaderKind::BiglyBt);
    }
    if header_contains(&response, header::WWW_AUTHENTICATE, "BitComet Remote Login") {
        return Some(DownloaderKind::BitComet);
    }

    let mut bytes = Vec::new();
    while bytes.len() < MAX_BODY {
        let Some(chunk) = response.chunk().await.ok()? else {
            break;
        };
        bytes.extend_from_slice(&chunk[..chunk.len().min(MAX_BODY - bytes.len())]);
    }
    let html = String::from_utf8_lossy(&bytes);
    if html.contains("BitComet Remote Access") || html.contains("BitComet WebUI") {
        Some(DownloaderKind::BitComet)
    } else if html.contains("qBittorrent WebUI") {
        Some(DownloaderKind::Qbittorrent)
    } else {
        None
    }
}

fn header_contains(response: &reqwest::Response, name: header::HeaderName, text: &str) -> bool {
    response
        .headers()
        .get(name)
        .and_then(|value| value.to_str().ok())
        .is_some_and(|value| value.contains(text))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
    use tokio::net::{TcpListener, TcpStream};
    use tokio::task::JoinHandle;

    #[tokio::test]
    async fn fingerprints_supported_clients_and_marks_configured_ports() {
        let cases = [
            (
                "200 OK",
                "Server: Transmission\r\n",
                "",
                Some(DownloaderKind::Transmission),
            ),
            (
                "200 OK",
                "Server: PeerBanHelper-BiglyBT-Adapter\r\n",
                "",
                Some(DownloaderKind::BiglyBt),
            ),
            (
                "401 Unauthorized",
                "WWW-Authenticate: Basic realm=\"BitComet Remote Login\"\r\n",
                "",
                Some(DownloaderKind::BitComet),
            ),
            (
                "200 OK",
                "",
                "<title>BitComet Remote Access</title>",
                Some(DownloaderKind::BitComet),
            ),
            (
                "200 OK",
                "",
                "<title>BitComet WebUI</title>",
                Some(DownloaderKind::BitComet),
            ),
            (
                "200 OK",
                "",
                "<title>qBittorrent WebUI</title>",
                Some(DownloaderKind::Qbittorrent),
            ),
            ("200 OK", "", "generic router control panel", None),
        ];
        let mut expected = BTreeSet::new();
        let mut sockets = BTreeSet::new();
        let mut tasks = Vec::new();
        let mut configured = BTreeSet::new();
        for (offset, (status, headers, body, kind)) in cases.into_iter().enumerate() {
            let pid = 1000 + u32::try_from(offset).unwrap();
            let (address, task) = fixture(status, headers, body).await;
            let discovery_status = if offset == 0 {
                configured.insert(address.port());
                DiscoveryStatus::Added
            } else {
                DiscoveryStatus::New
            };
            if let Some(kind) = kind {
                expected.insert(DiscoveredDownloader {
                    host: "127.0.0.1".into(),
                    port: address.port(),
                    kind,
                    pid,
                    status: discovery_status,
                });
            }
            sockets.insert((address, pid));
            tasks.push(task);
        }
        let found = probe_listeners(sockets, &configured).await;
        assert_eq!(found, expected.into_iter().collect::<Vec<_>>());
        let row = found
            .iter()
            .find(|entry| entry.kind == DownloaderKind::BiglyBt)
            .unwrap();
        let serialized = serde_json::to_value(row).unwrap();
        assert_eq!(serialized["type"], "biglybt");
        assert_eq!(serialized["status"], "NEW");
        assert_eq!(serialized["pid"], 1001);
        for task in tasks {
            task.await.unwrap();
        }
    }

    #[tokio::test]
    async fn follows_redirects_only_within_the_local_listener() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let server = tokio::spawn(async move {
            let (mut first, _) = listener.accept().await.unwrap();
            assert_eq!(request_line(&mut first).await, "GET / HTTP/1.1");
            respond(first, "302 Found", "Location: /gui\r\n", "").await;
            let (mut next, _) = listener.accept().await.unwrap();
            assert_eq!(request_line(&mut next).await, "GET /gui HTTP/1.1");
            respond(next, "200 OK", "", "qBittorrent WebUI").await;
        });

        let other = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let location = format!(
            "Location: http://{}/out-of-scope\r\n",
            other.local_addr().unwrap()
        );
        let (redirecting, redirect) = fixture("302 Found", &location, "").await;
        let found = probe_listeners(
            BTreeSet::from([(address, 1), (redirecting, 2)]),
            &BTreeSet::new(),
        )
        .await;
        assert_eq!(found.len(), 1);
        assert_eq!(found[0].port, address.port());
        assert!(
            tokio::time::timeout(Duration::from_millis(100), other.accept())
                .await
                .is_err()
        );
        server.await.unwrap();
        redirect.await.unwrap();
    }

    #[tokio::test]
    async fn normalizes_wildcard_listeners_and_marks_only_local_configured_hosts() {
        for wildcard in ["[::]:9000", "0.0.0.0:9000", "[::ffff:127.0.0.1]:9000"] {
            assert_eq!(
                probe_address(wildcard.parse().unwrap()).to_string(),
                "127.0.0.1:9000"
            );
        }
        assert_eq!(
            configured_local_port("http://127.0.0.1:8123/gui".into()).await,
            Some(8123)
        );
        assert_eq!(
            configured_local_port("http://localhost:8700".into()).await,
            Some(8700)
        );
        assert_eq!(
            configured_local_port("https://[::1]:8950".into()).await,
            Some(8950)
        );
        assert_eq!(
            configured_local_port("https://192.0.2.15:8950".into()).await,
            None
        );
    }

    async fn fixture(
        status: &'static str,
        headers: &str,
        body: &'static str,
    ) -> (SocketAddr, JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let headers = headers.to_owned();
        let task = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            assert_eq!(request_line(&mut socket).await, "GET / HTTP/1.1");
            respond(socket, status, &headers, body).await;
        });
        (address, task)
    }

    async fn request_line(socket: &mut TcpStream) -> String {
        let mut reader = BufReader::new(socket);
        let mut request = String::new();
        reader.read_line(&mut request).await.unwrap();
        loop {
            let mut line = String::new();
            if reader.read_line(&mut line).await.unwrap() == 0 || line == "\r\n" {
                break;
            }
        }
        request.trim_end().to_owned()
    }

    async fn respond(mut socket: TcpStream, status: &str, headers: &str, body: &str) {
        let response = format!(
            "HTTP/1.1 {status}\r\n{headers}Content-Length: {}\r\nConnection: close\r\n\r\n{body}",
            body.len()
        );
        socket.write_all(response.as_bytes()).await.unwrap();
    }
}
