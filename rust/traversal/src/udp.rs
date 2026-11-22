use std::io;
use std::net::{IpAddr, SocketAddr};
use std::num::NonZeroU8;
use std::time::Duration;

use serde::Serialize;
use thiserror::Error;
use tokio::net::UdpSocket;
use tokio::time::{Instant, timeout, timeout_at};

use crate::{BindingRequest, BindingResponse, ChangeRequest, PacketError, StunVersion};

#[derive(Clone, Copy, Debug)]
pub struct RetryPolicy {
    pub attempts: NonZeroU8,
    pub attempt_timeout: Duration,
    pub total_timeout: Duration,
}

impl Default for RetryPolicy {
    fn default() -> Self {
        Self {
            attempts: NonZeroU8::new(3).expect("nonzero retry count"),
            attempt_timeout: Duration::from_secs(1),
            total_timeout: Duration::from_secs(5),
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Serialize)]
pub enum NatType {
    UdpBlocked,
    OpenInternet,
    SymmetricUdpFirewall,
    FullCone,
    RestrictedCone,
    PortRestrictedCone,
    Symmetric,
    #[default]
    Unknown,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct NatResult {
    pub nat_type: NatType,
    pub mapped: Option<SocketAddr>,
}

#[derive(Debug, Error)]
pub enum Error {
    #[error("STUN UDP I/O failed")]
    Io(#[from] io::Error),
    #[error(transparent)]
    Packet(#[from] PacketError),
    #[error("STUN transaction timed out")]
    Timeout,
}

pub struct UdpStunClient {
    socket: UdpSocket,
    retry: RetryPolicy,
}

impl UdpStunClient {
    pub async fn bind(local: SocketAddr, retry: RetryPolicy) -> Result<Self, Error> {
        let socket = UdpSocket::bind(local).await?;
        Ok(Self { socket, retry })
    }

    pub fn local_addr(&self) -> Result<SocketAddr, Error> {
        Ok(self.socket.local_addr()?)
    }

    /// Resolves the mapping from an ordinary request to this server.
    pub async fn binding(
        &mut self,
        server: SocketAddr,
        version: StunVersion,
    ) -> Result<BindingResponse, Error> {
        let received = timeout(
            self.retry.total_timeout,
            self.exchange(server, None, version, ChangeRequest::None),
        )
        .await
        .map_err(|_| Error::Timeout)??;
        Ok(received.message)
    }

    /// Runs the Java client's NAT tests with a single UDP source port.
    pub async fn probe(
        &mut self,
        server: SocketAddr,
        local_ip: IpAddr,
        version: StunVersion,
    ) -> Result<NatResult, Error> {
        match timeout(
            self.retry.total_timeout,
            self.classify(server, local_ip, version),
        )
        .await
        {
            Ok(result) => result,
            Err(_) => Ok(NatResult {
                nat_type: NatType::Unknown,
                mapped: None,
            }),
        }
    }

    async fn classify(
        &self,
        server: SocketAddr,
        local_ip: IpAddr,
        version: StunVersion,
    ) -> Result<NatResult, Error> {
        let message = match self
            .exchange(server, None, version, ChangeRequest::None)
            .await
        {
            Ok(received) => received.message,
            Err(Error::Timeout) => {
                return Ok(NatResult {
                    nat_type: NatType::UdpBlocked,
                    mapped: None,
                });
            }
            Err(error) => return Err(error),
        };
        let result = |nat_type| NatResult {
            nat_type,
            mapped: Some(message.mapped),
        };
        let is_nat = message.mapped.ip().to_canonical() != local_ip.to_canonical();
        let other = message.other.filter(|other| {
            other != &server
                && other.is_ipv4() == server.is_ipv4()
                && other.port() != 0
                && !other.ip().is_unspecified()
                && !other.ip().is_multicast()
        });
        match self
            .exchange(server, other, version, ChangeRequest::IpAndPort)
            .await
        {
            Ok(received) if received.sender != server => {
                return Ok(result(if is_nat {
                    NatType::FullCone
                } else {
                    NatType::OpenInternet
                }));
            }
            Ok(_) | Err(Error::Packet(PacketError::Server(_))) => {
                return Ok(result(NatType::Unknown));
            }
            Err(Error::Timeout) if !is_nat => return Ok(result(NatType::SymmetricUdpFirewall)),
            Err(Error::Timeout) => {}
            Err(error) => return Err(error),
        }
        let Some(other) = other else {
            return Ok(result(NatType::Unknown));
        };
        match self
            .exchange(other, None, version, ChangeRequest::None)
            .await
        {
            Ok(received) if received.message.mapped != message.mapped => {
                return Ok(result(NatType::Symmetric));
            }
            Ok(_) => {}
            Err(Error::Timeout | Error::Packet(PacketError::Server(_))) => {
                return Ok(result(NatType::Unknown));
            }
            Err(error) => return Err(error),
        }
        match self
            .exchange(other, Some(server), version, ChangeRequest::Port)
            .await
        {
            Ok(received) if received.sender != other => Ok(result(NatType::RestrictedCone)),
            Ok(_) | Err(Error::Packet(PacketError::Server(_))) => Ok(result(NatType::Unknown)),
            Err(Error::Timeout) => Ok(result(NatType::PortRestrictedCone)),
            Err(error) => Err(error),
        }
    }

    async fn exchange(
        &self,
        server: SocketAddr,
        other: Option<SocketAddr>,
        version: StunVersion,
        change: ChangeRequest,
    ) -> Result<Received, Error> {
        let request = BindingRequest::new(version, change)?;
        let encoded = request.encode();
        let mut buffer = vec![0; usize::from(u16::MAX)];
        for _ in 0..self.retry.attempts.get() {
            let deadline = Instant::now() + self.retry.attempt_timeout;
            let Ok(sent) = timeout_at(deadline, self.socket.send_to(&encoded, server)).await else {
                continue;
            };
            sent?;
            while Instant::now() < deadline {
                let Ok(received) = timeout_at(deadline, self.socket.recv_from(&mut buffer)).await
                else {
                    break;
                };
                let (length, sender) = received?;
                if !source_allowed(sender, server, other, change) {
                    continue;
                }
                let message = match request.decode_response(&buffer[..length]) {
                    Ok(message) => message,
                    Err(error @ PacketError::Server(_)) if sender == server => {
                        return Err(error.into());
                    }
                    Err(_) => continue,
                };
                if message.origin.is_some_and(|origin| {
                    origin.ip() != sender.ip() || origin.port() != sender.port()
                }) {
                    continue;
                }
                return Ok(Received { message, sender });
            }
        }
        Err(Error::Timeout)
    }
}

struct Received {
    message: BindingResponse,
    sender: SocketAddr,
}

fn source_allowed(
    sender: SocketAddr,
    server: SocketAddr,
    other: Option<SocketAddr>,
    change: ChangeRequest,
) -> bool {
    if sender == server {
        return true;
    }
    let Some(other) = other else {
        return false;
    };
    match change {
        ChangeRequest::None => false,
        ChangeRequest::Port => sender.ip() == server.ip() && sender.port() == other.port(),
        ChangeRequest::Ip => sender.ip() == other.ip() && sender.port() == server.port(),
        ChangeRequest::IpAndPort => {
            (sender.ip() == server.ip() || sender.ip() == other.ip())
                && (sender.port() == server.port() || sender.port() == other.port())
        }
    }
}
