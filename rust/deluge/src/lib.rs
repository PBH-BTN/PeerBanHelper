//! Native Deluge Web JSON-RPC adapter.

mod snapshot;

use std::net::IpAddr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

use pbh_core::AddressPolicy;
use pbh_network::ProxyPolicy;
use reqwest::{Client, StatusCode, Url};
use serde::Deserialize;
use serde::de::DeserializeOwned;
use serde_json::{Value, json};
use thiserror::Error;
use tokio::sync::Mutex;

pub use snapshot::{Peer, Torrent};

const METHODS: [&str; 4] = [
    "peerbanhelperadapter.replace_blocklist",
    "peerbanhelperadapter.unban_ips",
    "peerbanhelperadapter.get_active_torrents_info",
    "peerbanhelperadapter.ban_ips",
];
const LOGIN_ATTEMPTS: u8 = 15;
const LOGIN_BACKOFF: Duration = Duration::from_secs(30 * 60);

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub struct Config {
    pub name: Option<String>,
    pub endpoint: String,
    pub password: String,
    pub rpc_url: String,
    pub verify_ssl: bool,
    pub increment_ban: bool,
    pub ignore_private: bool,
    pub paused: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            name: None,
            endpoint: String::new(),
            password: String::new(),
            rpc_url: "/json".into(),
            verify_ssl: true,
            increment_ban: true,
            ignore_private: false,
            paused: false,
        }
    }
}

#[derive(Debug, Error)]
pub enum Error {
    #[error("Deluge endpoint and RPC path must form an HTTP(S) URL without credentials")]
    InvalidEndpoint,
    #[error("Deluge is paused")]
    Paused,
    #[error("Deluge rejected authentication")]
    Authentication,
    #[error("Deluge authentication is temporarily paused after repeated failures")]
    LoginBackoff,
    #[error("Deluge is missing required PeerBanHelper plugin method {method}")]
    MissingMethod { method: &'static str },
    #[error("Deluge {method} returned HTTP {status}")]
    Status {
        method: &'static str,
        status: StatusCode,
    },
    #[error("Deluge {method} request failed")]
    Request {
        method: &'static str,
        #[source]
        source: reqwest::Error,
    },
    #[error("Deluge {method} returned an RPC error (code {code:?})")]
    Rejected {
        method: &'static str,
        code: Option<i64>,
    },
    #[error("Deluge {method} returned invalid data")]
    InvalidResult { method: &'static str },
    #[error("a read-only Deluge connection cannot modify the downloader")]
    ReadOnly,
    #[error("Deluge returned a peer with an invalid IP address")]
    InvalidPeerAddress,
    #[error("Deluge returned a peer with an invalid hex ID")]
    InvalidPeerId,
}

pub struct Deluge {
    config: Config,
    endpoint: Url,
    client: Client,
    request_id: AtomicU64,
    login: Mutex<LoginState>,
    read_only: bool,
    address_policy: AddressPolicy,
}

#[derive(Default)]
struct LoginState {
    failures: u8,
    retry_at: Option<Instant>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize)]
pub struct TrafficStatistics {
    #[serde(rename = "total_payload_upload")]
    pub uploaded: i64,
    #[serde(rename = "total_payload_download")]
    pub downloaded: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SpeedLimits {
    pub upload: i64,
    pub download: i64,
}

impl Deluge {
    pub fn new(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, false, &ProxyPolicy::default())
    }

    /// Enables login, snapshots and statistics without modifying downloader settings or bans.
    pub fn read_only(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, true, &ProxyPolicy::default())
    }

    pub fn with_address_policy(mut self, policy: AddressPolicy) -> Self {
        self.address_policy = policy;
        self
    }

    pub fn connect_with_proxy(
        config: Config,
        read_only: bool,
        proxy: &ProxyPolicy,
    ) -> Result<Self, Error> {
        let base = Url::parse(config.endpoint.trim()).map_err(|_| Error::InvalidEndpoint)?;
        if !safe_endpoint(&base) || base.query().is_some() || !config.rpc_url.starts_with('/') {
            return Err(Error::InvalidEndpoint);
        }
        let endpoint = Url::parse(&format!(
            "{}{}",
            base.as_str().trim_end_matches('/'),
            config.rpc_url
        ))
        .map_err(|_| Error::InvalidEndpoint)?;
        if !safe_endpoint(&endpoint) {
            return Err(Error::InvalidEndpoint);
        }
        let client = proxy
            .apply(Client::builder())
            .cookie_store(true)
            .redirect(reqwest::redirect::Policy::none())
            .connect_timeout(Duration::from_secs(15))
            .timeout(Duration::from_secs(30))
            .danger_accept_invalid_certs(!config.verify_ssl)
            .user_agent(concat!("PeerBanHelper/", env!("CARGO_PKG_VERSION")))
            .build()
            .map_err(|error| request_error("build HTTP client", error))?;
        Ok(Self {
            config,
            endpoint,
            client,
            request_id: AtomicU64::new(0),
            login: Mutex::default(),
            read_only,
            address_policy: AddressPolicy::default(),
        })
    }

    pub async fn login(&self) -> Result<(), Error> {
        if self.config.paused {
            return Err(Error::Paused);
        }
        let mut state = self.login.lock().await;
        if state.retry_at.is_some_and(|until| Instant::now() < until) {
            return Err(Error::LoginBackoff);
        }
        let authenticated: bool = self
            .get("auth.login", json!([&self.config.password]))
            .await?;
        if !authenticated {
            state.failures += 1;
            if state.failures >= LOGIN_ATTEMPTS {
                state.retry_at = Some(Instant::now() + LOGIN_BACKOFF);
                state.failures = 0;
            }
            return Err(Error::Authentication);
        }
        let advertised: Vec<String> = self.get("system.listMethods", json!([])).await?;
        for method in METHODS {
            if !advertised.iter().any(|name| name == method) {
                return Err(Error::MissingMethod { method });
            }
        }
        state.failures = 0;
        state.retry_at = None;
        Ok(())
    }

    pub async fn active_torrents(&self) -> Result<Vec<Torrent>, Error> {
        let snapshots: Vec<snapshot::TorrentResponse> = self
            .get("peerbanhelperadapter.get_active_torrents_info", json!([]))
            .await?;
        snapshots
            .into_iter()
            .map(|torrent| torrent.into_snapshot(&self.address_policy))
            .collect()
    }

    /// Matches the legacy Deluge adapter, which uses the active snapshot for both calls.
    pub async fn all_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.active_torrents().await
    }

    pub async fn banned_ips(&self) -> Result<Vec<String>, Error> {
        #[derive(Deserialize)]
        struct Blocklist {
            ips: Vec<String>,
        }

        self.get::<Blocklist>("peerbanhelperadapter.get_blocklist", json!([]))
            .await
            .map(|blocklist| blocklist.ips)
    }

    pub async fn add_bans(&self, added: &[IpAddr]) -> Result<(), Error> {
        self.set_bans("peerbanhelperadapter.ban_ips", added).await
    }

    pub async fn replace_bans(&self, full: &[IpAddr]) -> Result<(), Error> {
        self.set_bans("peerbanhelperadapter.replace_blocklist", full)
            .await
    }

    pub async fn sync_bans(
        &self,
        full: &[IpAddr],
        added: &[IpAddr],
        has_removals: bool,
        force_full: bool,
    ) -> Result<(), Error> {
        if self.config.increment_ban && !has_removals && !force_full {
            self.add_bans(added).await
        } else {
            self.replace_bans(full).await
        }
    }

    async fn set_bans(&self, method: &'static str, addresses: &[IpAddr]) -> Result<(), Error> {
        let addresses = self.address_policy.ban_rules(addresses, true);
        self.write(method, json!([addresses])).await
    }

    pub async fn traffic_statistics(&self) -> Result<TrafficStatistics, Error> {
        self.get("peerbanhelperadapter.get_session_totals", json!([]))
            .await
    }

    pub async fn speed_limits(&self) -> Result<SpeedLimits, Error> {
        #[derive(Deserialize)]
        struct DelugeLimits {
            max_upload_speed: f64,
            max_download_speed: f64,
        }

        let limits: DelugeLimits = self.get("core.get_config", json!([])).await?;
        Ok(SpeedLimits {
            upload: (limits.max_upload_speed as i64).wrapping_mul(1024),
            download: (limits.max_download_speed as i64).wrapping_mul(1024),
        })
    }

    pub async fn set_speed_limits(&self, limits: SpeedLimits) -> Result<(), Error> {
        self.write(
            "core.set_config",
            json!([{
                "max_upload_speed": limits.upload.max(0) / 1024,
                "max_download_speed": limits.download.max(0) / 1024,
            }]),
        )
        .await
    }

    async fn get<T: DeserializeOwned>(
        &self,
        method: &'static str,
        params: Value,
    ) -> Result<T, Error> {
        let result = self.rpc(method, params).await?;
        serde_json::from_value(result).map_err(|_| Error::InvalidResult { method })
    }

    async fn write(&self, method: &'static str, params: Value) -> Result<(), Error> {
        if self.read_only {
            return Err(Error::ReadOnly);
        }
        self.rpc(method, params).await.map(|_| ())
    }

    async fn rpc(&self, method: &'static str, params: Value) -> Result<Value, Error> {
        #[derive(Deserialize)]
        struct Reply {
            id: u64,
            #[serde(default)]
            result: Value,
            error: Option<Value>,
        }

        let id = self.request_id.fetch_add(1, Ordering::Relaxed);
        let response = self
            .client
            .post(self.endpoint.clone())
            .json(&json!({"id": id, "method": method, "params": params}))
            .send()
            .await
            .map_err(|error| request_error(method, error))?;
        if !response.status().is_success() {
            return Err(Error::Status {
                method,
                status: response.status(),
            });
        }
        let reply: Reply = response
            .json()
            .await
            .map_err(|error| request_error(method, error))?;
        if reply.id != id {
            return Err(Error::InvalidResult { method });
        }
        if let Some(error) = reply.error {
            return Err(Error::Rejected {
                method,
                code: error.get("code").and_then(Value::as_i64),
            });
        }
        Ok(reply.result)
    }
}

fn safe_endpoint(url: &Url) -> bool {
    matches!(url.scheme(), "http" | "https")
        && url.has_host()
        && url.username().is_empty()
        && url.password().is_none()
        && url.fragment().is_none()
}

fn request_error(method: &'static str, error: reqwest::Error) -> Error {
    Error::Request {
        method,
        source: error.without_url(),
    }
}
