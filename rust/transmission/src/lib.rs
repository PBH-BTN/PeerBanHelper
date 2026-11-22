//! Native Transmission downloader and legacy RPC transport.

mod blocklist;
mod operations;
mod rpc;
mod snapshot;

use std::time::{SystemTime, UNIX_EPOCH};

use pbh_core::AddressPolicy;
use pbh_network::ProxyPolicy;
use reqwest::{StatusCode, Url};
use serde::Deserialize;
use serde_json::json;
use thiserror::Error;

pub use blocklist::{
    BLOCKLIST_PATH, render_p2p_blocklist, render_p2p_blocklist_networks,
    render_p2p_blocklist_networks_with_policy,
};
pub use operations::{SpeedLimits, TrafficStatistics};
use rpc::Rpc;
pub use snapshot::{Peer, Torrent};

const BLOCKLIST_UPDATE_FAILED: &str =
    "http://peerbanhelper-blocklist-update-failed.com/check-peerbanhelper-webui-prefix-settings";

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub struct Config {
    pub name: Option<String>,
    pub endpoint: String,
    pub username: String,
    pub password: String,
    pub verify_ssl: bool,
    pub rpc_url: String,
    pub ignore_private: bool,
    pub paused: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            name: None,
            endpoint: String::new(),
            username: String::new(),
            password: String::new(),
            verify_ssl: true,
            rpc_url: "/transmission/rpc".to_owned(),
            ignore_private: false,
            paused: false,
        }
    }
}

#[derive(Debug, Error)]
pub enum Error {
    #[error("Transmission endpoint and RPC path must form an HTTP(S) URL without credentials")]
    InvalidEndpoint,
    #[error("PBH's blocklist prefix must be an HTTP(S) URL without credentials, query or fragment")]
    InvalidBlocklistPrefix,
    #[error("Transmission is paused")]
    Paused,
    #[error("a read-only Transmission connection cannot modify the downloader")]
    ReadOnly,
    #[error("Transmission reported an invalid application version")]
    InvalidVersion,
    #[error("Transmission version 4.1.0 or later is required")]
    IncompatibleVersion,
    #[error("Transmission rejected authentication")]
    Authentication,
    #[error("Transmission did not provide a session identifier")]
    SessionMissing,
    #[error("Transmission rejected the refreshed session identifier")]
    SessionExpired,
    #[error("Transmission {method} returned HTTP {status}")]
    Status {
        method: &'static str,
        status: StatusCode,
    },
    #[error("Transmission {method} request failed")]
    Request {
        method: &'static str,
        #[source]
        source: reqwest::Error,
    },
    #[error("Transmission {method} did not return an RPC success")]
    Rejected { method: &'static str },
    #[error("Transmission {method} returned incomplete or invalid RPC arguments")]
    InvalidArguments { method: &'static str },
    #[error("Transmission returned a peer with an invalid IP address")]
    InvalidPeerAddress,
    #[error("Transmission returned an invalid peer ID")]
    InvalidPeerId,
}

pub struct Transmission {
    config: Config,
    rpc: Rpc,
    blocklist_url: Url,
    read_only: bool,
    address_policy: AddressPolicy,
}

impl Transmission {
    pub fn new(config: Config, server_prefix: &str) -> Result<Self, Error> {
        Self::connect_with_proxy(config, server_prefix, false, &ProxyPolicy::default())
    }

    /// Keeps all RPC operations read-only, including when login notices that the
    /// server is using an unrelated blocklist.
    pub fn read_only(config: Config, server_prefix: &str) -> Result<Self, Error> {
        Self::connect_with_proxy(config, server_prefix, true, &ProxyPolicy::default())
    }

    pub fn with_address_policy(mut self, policy: AddressPolicy) -> Self {
        self.address_policy = policy;
        self
    }

    pub fn connect_with_proxy(
        config: Config,
        server_prefix: &str,
        read_only: bool,
        proxy: &ProxyPolicy,
    ) -> Result<Self, Error> {
        let endpoint = Url::parse(config.endpoint.trim()).map_err(|_| Error::InvalidEndpoint)?;
        if !is_http_url(&endpoint) || endpoint.query().is_some() || !config.rpc_url.starts_with('/')
        {
            return Err(Error::InvalidEndpoint);
        }
        let rpc_endpoint = Url::parse(&format!(
            "{}{}",
            endpoint.as_str().trim_end_matches('/'),
            config.rpc_url,
        ))
        .map_err(|_| Error::InvalidEndpoint)?;
        if !is_http_url(&rpc_endpoint) {
            return Err(Error::InvalidEndpoint);
        }
        let rpc = Rpc::new(rpc_endpoint, &config, proxy)?;
        let prefix = Url::parse(server_prefix).map_err(|_| Error::InvalidBlocklistPrefix)?;
        if !is_http_url(&prefix) || prefix.query().is_some() {
            return Err(Error::InvalidBlocklistPrefix);
        }
        let blocklist_url = Url::parse(&format!(
            "{}{BLOCKLIST_PATH}",
            prefix.as_str().trim_end_matches('/'),
        ))
        .map_err(|_| Error::InvalidBlocklistPrefix)?;
        Ok(Self {
            config,
            rpc,
            blocklist_url,
            read_only,
            address_policy: AddressPolicy::default(),
        })
    }

    pub async fn login(&self) -> Result<(), Error> {
        if self.config.paused {
            return Err(Error::Paused);
        }
        #[derive(Deserialize)]
        #[serde(rename_all = "kebab-case")]
        struct Session {
            version: String,
            blocklist_enabled: bool,
            blocklist_url: String,
        }

        let session: Session = self.rpc.read("session-get", json!({})).await?;
        let (major, minor) = major_minor(&session.version).ok_or(Error::InvalidVersion)?;
        if (major, minor) < (4, 1) {
            return Err(Error::IncompatibleVersion);
        }
        if !self.read_only
            && (!session.blocklist_enabled
                || !session
                    .blocklist_url
                    .starts_with(self.blocklist_url.as_str()))
        {
            self.set_blocklist_url(self.blocklist_url.clone()).await?;
            if let Err(err) = self.update_blocklist().await {
                // A successful HTTP response with an RPC error is the signal the
                // existing client uses to expose the prefix configuration problem.
                if matches!(err, Error::Rejected { .. }) {
                    let failed_url = Url::parse(BLOCKLIST_UPDATE_FAILED).expect("fixed URL");
                    self.set_blocklist_url(failed_url).await?;
                }
                return Err(err);
            }
        }
        Ok(())
    }

    async fn set_blocklist_url(&self, mut url: Url) -> Result<(), Error> {
        self.ensure_writable()?;
        let milliseconds = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis()
            .to_string();
        url.query_pairs_mut().append_pair("t", &milliseconds);
        self.rpc
            .write(
                "session-set",
                json!({"blocklist-url": url.as_str(), "blocklist-enabled": true}),
            )
            .await
    }

    /// Reloads the PBH-hosted full list. The same operation handles removals.
    pub async fn update_blocklist(&self) -> Result<u32, Error> {
        self.ensure_writable()?;
        #[derive(Deserialize)]
        struct BlocklistResult {
            #[serde(rename = "blocklist-size")]
            size: u32,
        }

        self.rpc
            .read::<BlocklistResult>("blocklist-update", json!({}))
            .await
            .map(|result| result.size)
    }

    fn ensure_writable(&self) -> Result<(), Error> {
        if self.read_only {
            return Err(Error::ReadOnly);
        }
        Ok(())
    }
}

fn is_http_url(url: &Url) -> bool {
    matches!(url.scheme(), "http" | "https")
        && url.has_host()
        && url.username().is_empty()
        && url.password().is_none()
        && url.fragment().is_none()
}

fn major_minor(version: &str) -> Option<(u32, u32)> {
    let mut parts = version.trim().split('.');
    let major = parts.next()?.parse().ok()?;
    let minor = parts.next()?.parse().ok()?;
    Some((major, minor))
}
