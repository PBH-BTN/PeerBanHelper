use std::collections::BTreeSet;
use std::io;
use std::net::{IpAddr, Ipv4Addr};
use std::sync::Arc;
use std::time::Duration;

use reqwest::dns::{Addrs, Name, Resolve, Resolving};
use reqwest::{Client, Response, StatusCode, Url};
use serde::Deserialize;
use serde_json::{Value, json};
use tokio::task::JoinSet;
use tokio::time::{Instant, timeout_at};

use crate::{
    BtnClient, Error, PollingConfig, PollingSchedule, check_response, http_builder, json, transport,
};

const ABILITY: &str = "heartbeat";
const MULTI_INTERFACE_TIMEOUT: Duration = Duration::from_secs(30);

#[derive(Clone)]
pub struct Heartbeat {
    pub schedule: PollingSchedule,
    pub multi_interface: bool,
    authenticated: BtnClient,
    anonymous: Client,
    endpoint: Url,
    proof_endpoint: Option<Url>,
    require_pow: bool,
}

/// Both routes are attempted for each address. The configured policy may bypass its proxy.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum HeartbeatRoute {
    Direct,
    ConfiguredProxy,
}

#[derive(Debug)]
pub struct InterfaceHeartbeat {
    pub address: String,
    pub route: HeartbeatRoute,
    pub reply: Result<Option<String>, Error>,
}

#[derive(Deserialize)]
struct Config {
    #[serde(flatten)]
    timing: PollingConfig,
    endpoint: String,
    multi_if: bool,
    #[serde(default)]
    pow_captcha: bool,
}

#[derive(Deserialize)]
struct Reply {
    external_ip: Option<String>,
}

impl Heartbeat {
    pub(crate) fn from_config(
        client: BtnClient,
        ability: &Value,
        proof_endpoint: Option<Url>,
    ) -> Result<Self, Error> {
        let config: Config =
            serde_json::from_value(ability.clone()).map_err(|_| Error::InvalidResponse(ABILITY))?;
        Ok(Self {
            schedule: config.timing.parse(ABILITY)?,
            multi_interface: config.multi_if,
            endpoint: client.endpoint(&config.endpoint, ABILITY)?,
            anonymous: client
                .proxy
                .apply(http_builder(client.timeout))
                .build()
                .map_err(|source| transport(ABILITY, source))?,
            authenticated: client,
            proof_endpoint,
            require_pow: config.pow_captcha,
        })
    }

    /// Send the report for the system default interface without BTN credentials.
    pub async fn send_default(&self) -> Result<Option<String>, Error> {
        let request = self
            .anonymous
            .post(self.endpoint.clone())
            .json(&json!({"ifaddr":"default"}));
        let request = self
            .authenticated
            .with_proof(
                request,
                self.require_pow,
                self.proof_endpoint.as_ref(),
                ABILITY,
            )
            .await?;
        let response = request
            .send()
            .await
            .map_err(|source| transport(ABILITY, source))?;
        read_reply(check_response(response).await?).await
    }

    /// Bind an authenticated heartbeat to the selected local address.
    pub async fn send_for_interface(&self, source: IpAddr) -> Result<Option<String>, Error> {
        self.send_bound(source, &address_text(source), HeartbeatRoute::Direct)
            .await
    }

    /// Enumerate currently configured unicast addresses for the negotiated multi-interface mode.
    pub async fn send_multi_interface(&self) -> Result<Vec<InterfaceHeartbeat>, Error> {
        let interfaces = tokio::task::spawn_blocking(if_addrs::get_if_addrs)
            .await
            .map_err(|error| Error::HeartbeatInterfaces(io::Error::other(error)))?
            .map_err(Error::HeartbeatInterfaces)?;
        Ok(self
            .send_for_interfaces(interfaces.into_iter().map(|interface| interface.ip()))
            .await)
    }

    /// Probe explicit source addresses and return completed attempts in completion order.
    pub async fn send_for_interfaces(
        &self,
        addresses: impl IntoIterator<Item = IpAddr>,
    ) -> Vec<InterfaceHeartbeat> {
        let addresses: BTreeSet<_> = addresses
            .into_iter()
            .map(|source| source.to_canonical())
            .filter(|source| suitable_source(*source))
            .collect();
        let mut tasks = JoinSet::new();
        for source in addresses {
            let address = address_text(source);
            for route in [HeartbeatRoute::ConfiguredProxy, HeartbeatRoute::Direct] {
                let heartbeat = self.clone();
                let address = address.clone();
                tasks.spawn(async move {
                    let reply = heartbeat.send_bound(source, &address, route).await;
                    InterfaceHeartbeat {
                        address,
                        route,
                        reply,
                    }
                });
            }
        }
        let end = Instant::now() + MULTI_INTERFACE_TIMEOUT;
        let mut replies = Vec::new();
        while let Ok(Some(attempt)) = timeout_at(end, tasks.join_next()).await {
            if let Ok(attempt) = attempt {
                replies.push(attempt);
            }
        }
        replies
    }

    async fn send_bound(
        &self,
        source: IpAddr,
        address: &str,
        route: HeartbeatRoute,
    ) -> Result<Option<String>, Error> {
        let policy = &self.authenticated.proxy;
        let connection = match route {
            HeartbeatRoute::Direct => &self.endpoint,
            HeartbeatRoute::ConfiguredProxy => {
                policy.proxy_for(&self.endpoint).unwrap_or(&self.endpoint)
            }
        };
        // Hyper's literal-IP path skips the DNS resolver. Reject a wrong-family destination
        // there too, so the connector cannot fall back to an unspecified local address.
        if connection
            .host_str()
            .and_then(|host| host.trim_matches(['[', ']']).parse::<IpAddr>().ok())
            .is_some_and(|target| target.is_ipv4() != source.is_ipv4())
        {
            return Err(Error::HeartbeatAddressFamily);
        }
        let builder = http_builder(self.authenticated.timeout);
        let builder = match route {
            HeartbeatRoute::Direct => builder.no_proxy(),
            HeartbeatRoute::ConfiguredProxy => policy.apply(builder),
        };
        let client = builder
            .local_address(source)
            .dns_resolver(Arc::new(SourceFamily {
                ipv4: source.is_ipv4(),
            }))
            .default_headers(self.authenticated.headers.clone())
            .build()
            .map_err(|source| transport(ABILITY, source))?;
        let request = client
            .post(self.endpoint.clone())
            .json(&json!({"ifaddr":address}));
        let request = self
            .authenticated
            .with_queued_proof(
                request,
                self.require_pow,
                self.proof_endpoint.as_ref(),
                ABILITY,
            )
            .await?;
        read_reply(self.authenticated.send(request, ABILITY).await?).await
    }
}

fn suitable_source(source: IpAddr) -> bool {
    !source.is_unspecified()
        && !source.is_multicast()
        && match source {
            IpAddr::V4(ip) => ip != Ipv4Addr::BROADCAST,
            // Scoped addresses need an interface identifier, which the Java literal parser drops.
            IpAddr::V6(ip) => !ip.is_unicast_link_local(),
        }
}

fn address_text(address: IpAddr) -> String {
    match address {
        IpAddr::V4(address) => address.to_string(),
        IpAddr::V6(address) => address
            .segments()
            .map(|segment| format!("{segment:x}"))
            .join(":"),
    }
}

struct SourceFamily {
    ipv4: bool,
}

impl Resolve for SourceFamily {
    fn resolve(&self, name: Name) -> Resolving {
        let ipv4 = self.ipv4;
        Box::pin(async move {
            let addresses: Vec<_> = tokio::net::lookup_host((name.as_str(), 0))
                .await?
                .filter(|address| address.is_ipv4() == ipv4)
                .collect();
            if addresses.is_empty() {
                return Err(io::Error::new(
                    io::ErrorKind::AddrNotAvailable,
                    "no address in the configured source family",
                )
                .into());
            }
            Ok(Box::new(addresses.into_iter()) as Addrs)
        })
    }
}

async fn read_reply(response: Response) -> Result<Option<String>, Error> {
    if response.status() == StatusCode::NO_CONTENT {
        return Ok(None);
    }
    let reply: Option<Reply> = json(response, ABILITY).await?;
    Ok(reply.and_then(|reply| reply.external_ip))
}
