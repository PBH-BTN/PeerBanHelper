use std::io;
use std::net::SocketAddr;
use std::time::Duration;

use socket2::{Domain, Protocol, SockRef, Socket, Type};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpSocket, TcpStream};
use tokio::time::{Instant, timeout, timeout_at};

use crate::stun::HEADER_SIZE;
use crate::{BindingRequest, ChangeRequest, PacketError, StunVersion};

use super::{BindingError, HeartbeatConfig, TcpMapping};

const LISTENER_BACKLOG: i32 = 128;
const HTTP_HEADER_LIMIT: u64 = 8192;
const INCOMING_PROBE_READ_TIMEOUT: Duration = Duration::from_millis(300);

pub(super) fn listen(local: SocketAddr) -> io::Result<TcpListener> {
    let socket = shared_socket(local)?;
    socket.listen(LISTENER_BACKLOG)?;
    TcpListener::from_std(socket.into())
}

async fn connect(local: SocketAddr, remote: SocketAddr) -> io::Result<TcpStream> {
    let socket = TcpSocket::from_std_stream(shared_socket(local)?.into());
    socket.connect(remote).await
}

fn shared_socket(local: SocketAddr) -> io::Result<Socket> {
    let socket = Socket::new(
        Domain::for_address(local),
        Type::STREAM,
        Some(Protocol::TCP),
    )?;
    socket.set_reuse_address(true)?;
    #[cfg(all(
        unix,
        not(any(
            target_os = "solaris",
            target_os = "illumos",
            target_os = "cygwin",
            target_os = "nuttx",
            target_os = "wasi"
        ))
    ))]
    socket.set_reuse_port(true)?;
    if local.is_ipv6() {
        socket.set_only_v6(true)?;
    }
    socket.set_nonblocking(true)?;
    socket.bind(&local.into())?;
    Ok(socket)
}

pub(super) async fn binding(
    local: SocketAddr,
    server: SocketAddr,
    limit: Duration,
) -> Result<TcpMapping, BindingError> {
    let deadline = Instant::now() + limit;
    let mut stream = timeout_at(deadline, connect(local, server))
        .await
        .map_err(|_| BindingError::Timeout)??;
    let local = stream.local_addr()?;
    let result = timeout_at(deadline, exchange_binding(&mut stream))
        .await
        .unwrap_or(Err(BindingError::Timeout));
    let mapped = match result {
        Ok(mapped)
            if mapped.port() != 0
                && !mapped.ip().is_unspecified()
                && !mapped.ip().is_multicast() =>
        {
            mapped
        }
        Ok(_) => {
            reset(&stream);
            return Err(BindingError::InvalidMapping);
        }
        Err(error) => {
            reset(&stream);
            return Err(error);
        }
    };
    Ok(TcpMapping {
        local,
        mapped,
        server,
    })
}

async fn exchange_binding(stream: &mut TcpStream) -> Result<SocketAddr, BindingError> {
    let request = BindingRequest::new(StunVersion::Modern, ChangeRequest::None)?;
    stream.write_all(&request.encode()).await?;
    let mut header = [0; HEADER_SIZE];
    stream.read_exact(&mut header).await?;
    let size = usize::from(u16::from_be_bytes([header[2], header[3]]));
    if !size.is_multiple_of(4) {
        return Err(PacketError::Malformed.into());
    }
    let mut packet = vec![0; HEADER_SIZE + size];
    packet[..HEADER_SIZE].copy_from_slice(&header);
    stream.read_exact(&mut packet[HEADER_SIZE..]).await?;
    Ok(request.decode_response(&packet)?.mapped)
}

pub(super) async fn verify_reachable(
    listener: &TcpListener,
    external: SocketAddr,
) -> io::Result<()> {
    let mut random = [0_u8; 16];
    getrandom::fill(&mut random)
        .map_err(|_| io::Error::other("cannot generate AutoSTUN probe token"))?;
    let path = format!("/pbh-autostun/{:032x}", u128::from_be_bytes(random));
    let outgoing = async {
        let mut remote = BufReader::new(TcpStream::connect(external).await?);
        let request =
            format!("GET {path} HTTP/1.1\r\nHost: {external}\r\nConnection: close\r\n\r\n");
        remote.get_mut().write_all(request.as_bytes()).await?;
        let response = read_head(&mut remote).await?;
        if response.status() == Some(204) {
            Ok(())
        } else {
            Err(invalid_head())
        }
    };
    let incoming = async {
        let expected = format!("GET {path} HTTP/1.1");
        loop {
            let (peer, _) = listener.accept().await?;
            let mut peer = BufReader::new(peer);
            if let Ok(Ok(request)) =
                timeout(INCOMING_PROBE_READ_TIMEOUT, read_head(&mut peer)).await
                && request.start == expected
            {
                peer.get_mut()
                    .write_all(b"HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
                    .await?;
                return Ok::<_, io::Error>(());
            }
        }
    };
    tokio::try_join!(outgoing, incoming)?;
    Ok(())
}

pub(super) fn valid_host_header(host: &str) -> bool {
    !host.is_empty()
        && host.len() <= 253
        && host.bytes().all(|byte| {
            byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'.' | b'[' | b']' | b':')
        })
}

pub(super) async fn heartbeat(
    local: SocketAddr,
    config: &HeartbeatConfig,
    stream: &mut Option<BufReader<TcpStream>>,
) -> io::Result<()> {
    if stream.is_none() {
        *stream = Some(BufReader::new(connect(local, config.remote).await?));
    }
    let connection = stream.as_mut().expect("heartbeat socket connected");
    let request = format!(
        "HEAD / HTTP/1.1\r\nHost: {}\r\nUser-Agent: PeerBanHelper-NAT-Keeper/1.0\r\nConnection: keep-alive\r\n\r\n",
        config.host_header
    );
    connection.get_mut().write_all(request.as_bytes()).await?;
    loop {
        let response = read_head(connection).await?;
        let status = response.status().ok_or_else(invalid_head)?;
        if status >= 200 {
            if response.close {
                reset(connection.get_ref());
                *stream = None;
            }
            return Ok(());
        }
        if status == 101 {
            return Err(invalid_head());
        }
    }
}

pub(super) fn reset(socket: &TcpStream) {
    let _ = SockRef::from(socket).set_linger(Some(Duration::ZERO));
}

struct HttpHead {
    start: String,
    close: bool,
}

impl HttpHead {
    fn status(&self) -> Option<u16> {
        let mut words = self.start.split_ascii_whitespace();
        if !matches!(words.next(), Some("HTTP/1.0" | "HTTP/1.1")) {
            return None;
        }
        words
            .next()?
            .parse()
            .ok()
            .filter(|status| (100..=599).contains(status))
    }
}

async fn read_head(stream: &mut BufReader<TcpStream>) -> io::Result<HttpHead> {
    let mut limited = stream.take(HTTP_HEADER_LIMIT);
    let mut start = String::new();
    if limited.read_line(&mut start).await? == 0 || !start.ends_with('\n') {
        return Err(invalid_head());
    }
    let mut close = start.starts_with("HTTP/1.0 ");
    loop {
        let mut line = Vec::new();
        if limited.read_until(b'\n', &mut line).await? == 0 || !line.ends_with(b"\n") {
            return Err(invalid_head());
        }
        if line == b"\r\n" || line == b"\n" {
            break;
        }
        if let Ok(line) = std::str::from_utf8(&line)
            && let Some((name, value)) = line.split_once(':')
            && name.eq_ignore_ascii_case("connection")
        {
            if value
                .split(',')
                .any(|token| token.trim().eq_ignore_ascii_case("close"))
            {
                close = true;
            } else if value
                .split(',')
                .any(|token| token.trim().eq_ignore_ascii_case("keep-alive"))
            {
                close = false;
            }
        }
    }
    start.truncate(start.trim_end_matches(['\r', '\n']).len());
    Ok(HttpHead { start, close })
}

fn invalid_head() -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, "invalid AutoSTUN HTTP header")
}
