//! qBittorrent Web API adapter.

mod config;
mod operations;
mod snapshot;

use std::collections::{HashMap, HashSet};
use std::net::IpAddr;
use std::time::{Duration, Instant};

use futures_util::{StreamExt, stream};
use pbh_network::ProxyPolicy;
use reqwest::header::{AUTHORIZATION, REFERER};
use reqwest::{Client, RequestBuilder, Response, StatusCode, Url};
use semver::Version;
use serde::Deserialize;
use serde_json::{Value, json};
use thiserror::Error;
use tokio::sync::Mutex;

pub use config::{BasicAuth, Config};
pub use operations::{SpeedLimits, TrackerTier, TrafficStatistics};
pub use pbh_core::AddressPolicy;
pub use snapshot::{Peer, Torrent};

use snapshot::{PeersResponse, TorrentProperties, TorrentResponse};

const TORRENT_PAGE_SIZE: usize = 100;
const PROPERTY_REQUESTS_IN_FLIGHT: usize = 16;
const LOGIN_FAILURE_LIMIT: u8 = 15;
const LOGIN_BACKOFF: Duration = Duration::from_secs(30 * 60);

#[derive(Debug, Error)]
pub enum Error {
    #[error("qBittorrent endpoint must be an HTTP(S) URL without credentials, query or fragment")]
    InvalidEndpoint,
    #[error("qBittorrent is paused")]
    Paused,
    #[error("qBittorrent rejected authentication")]
    Authentication,
    #[error("qBittorrent authentication is temporarily paused after repeated failures")]
    LoginBackoff,
    #[error("qBittorrent reported an invalid application version")]
    InvalidVersion,
    #[error("qBittorrent {actual} is older than required version {required}")]
    IncompatibleVersion { actual: Version, required: Version },
    #[error("qBittorrent Enhanced Edition has shadow banning disabled")]
    ShadowBanDisabled,
    #[error("qBittorrent traffic counters are not ready")]
    TrafficCountersUnavailable,
    #[error("qBittorrent returned a peer with an invalid IP address")]
    InvalidPeerAddress,
    #[error("qBittorrent incremental ban contains an invalid peer key")]
    InvalidBanPeer,
    #[error("a read-only qBittorrent connection cannot modify the downloader")]
    ReadOnly,
    #[error("qBittorrent {operation} returned HTTP {status}")]
    Status {
        operation: &'static str,
        status: StatusCode,
    },
    #[error("qBittorrent {operation} request failed")]
    Request {
        operation: &'static str,
        #[source]
        source: reqwest::Error,
    },
}

pub struct Qbittorrent {
    config: Config,
    client: Client,
    endpoint: Url,
    referer: String,
    enhanced: bool,
    read_only: bool,
    login: Mutex<LoginState>,
    address_policy: AddressPolicy,
    properties: Mutex<HashMap<String, CachedProperties>>,
    properties_ttl: Duration,
}

#[derive(Default)]
struct LoginState {
    version: Option<Version>,
    shadow_ban_enabled: bool,
    failures: u8,
    retry_at: Option<Instant>,
}

struct CachedProperties {
    last_access: Instant,
    completed: i64,
    value: TorrentProperties,
}

impl Qbittorrent {
    pub fn new(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, false, false, &ProxyPolicy::default())
    }

    /// Creates an observer that can authenticate and read snapshots without writing
    /// downloader settings or bans.
    pub fn read_only(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, false, true, &ProxyPolicy::default())
    }

    pub fn enhanced(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, true, false, &ProxyPolicy::default())
    }

    pub fn enhanced_read_only(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, true, true, &ProxyPolicy::default())
    }

    pub fn with_scan_interval(mut self, interval: Duration) -> Self {
        self.properties_ttl = interval.saturating_add(Duration::from_secs(60));
        self
    }

    pub fn with_address_policy(mut self, policy: AddressPolicy) -> Self {
        self.address_policy = policy;
        self
    }

    pub fn connect_with_proxy(
        config: Config,
        enhanced: bool,
        read_only: bool,
        proxy: &ProxyPolicy,
    ) -> Result<Self, Error> {
        let mut endpoint =
            Url::parse(config.endpoint.trim()).map_err(|_| Error::InvalidEndpoint)?;
        if !matches!(endpoint.scheme(), "http" | "https")
            || !endpoint.has_host()
            || !endpoint.username().is_empty()
            || endpoint.password().is_some()
            || endpoint.query().is_some()
            || endpoint.fragment().is_some()
        {
            return Err(Error::InvalidEndpoint);
        }
        let referer = endpoint.origin().ascii_serialization();
        {
            let mut path = endpoint
                .path_segments_mut()
                .map_err(|_| Error::InvalidEndpoint)?;
            path.pop_if_empty().extend(["api", "v2", ""]);
        }
        let client = proxy
            .apply(Client::builder())
            .cookie_store(true)
            .redirect(reqwest::redirect::Policy::none())
            .connect_timeout(Duration::from_secs(10))
            .timeout(Duration::from_secs(30))
            .danger_accept_invalid_certs(!config.verify_ssl)
            .user_agent(concat!("PeerBanHelper/", env!("CARGO_PKG_VERSION")))
            .build()
            .map_err(|err| request_error("build HTTP client", err))?;

        Ok(Self {
            config,
            client,
            endpoint,
            referer,
            enhanced,
            read_only,
            login: Mutex::default(),
            address_policy: AddressPolicy::default(),
            properties: Mutex::default(),
            properties_ttl: Duration::from_secs(65),
        })
    }

    /// Checks whether the existing session works and reauthenticates if it expired.
    pub async fn login(&self) -> Result<(), Error> {
        if self.config.paused {
            return Err(Error::Paused);
        }
        let mut state = self.login.lock().await;
        if state.retry_at.is_some_and(|until| Instant::now() < until) {
            return Err(Error::LoginBackoff);
        }
        let result = self.authenticate(&mut state).await;
        match &result {
            Ok(()) => {
                state.failures = 0;
                state.retry_at = None;
            }
            Err(Error::Authentication) => {
                state.failures += 1;
                if state.failures >= LOGIN_FAILURE_LIMIT {
                    state.retry_at = Some(Instant::now() + LOGIN_BACKOFF);
                    state.failures = 0;
                }
            }
            Err(_) => {
                state.version = None;
            }
        }
        result
    }

    async fn authenticate(&self, state: &mut LoginState) -> Result<(), Error> {
        if !self.is_logged_in().await? {
            state.version = None;
            state.shadow_ban_enabled = false;
            if self.api_key_enabled() {
                return Err(Error::Authentication);
            }
            let request = self
                .client
                .post(self.url("auth/login"))
                .header(REFERER, &self.referer)
                .form(&[
                    ("username", &self.config.username),
                    ("password", &self.config.password),
                ]);
            let response = self.send(request, "login").await?;
            if is_auth_rejection(response.status()) {
                return Err(Error::Authentication);
            }
            check_status(response, "login")?;
            if !self.is_logged_in().await? {
                return Err(Error::Authentication);
            }
        }
        if state.version.is_none() {
            if !self.read_only {
                self.set_preferences(json!({"enable_multi_connections_from_same_ip": false}))
                    .await?;
            }
            let actual = self.read_version().await?;
            let required = if self.api_key_enabled() {
                Version::new(5, 2, 0)
            } else {
                Version::new(4, 5, 0)
            };
            if actual < required {
                return Err(Error::IncompatibleVersion { actual, required });
            }
            state.version = Some(actual);
        }
        if self.uses_shadow_bans() && !state.shadow_ban_enabled {
            #[derive(Deserialize)]
            struct ShadowBanPreference {
                shadow_ban_enabled: Option<bool>,
            }

            let preferences: ShadowBanPreference = self.preferences().await?;
            state.shadow_ban_enabled = preferences.shadow_ban_enabled.unwrap_or(false);
            if !state.shadow_ban_enabled {
                return Err(Error::ShadowBanDisabled);
            }
        }
        Ok(())
    }

    async fn is_logged_in(&self) -> Result<bool, Error> {
        #[derive(Deserialize)]
        struct BuildInfo {
            libtorrent: Option<String>,
        }

        let operation = "build information";
        let response = self.send(self.get("app/buildInfo"), operation).await?;
        if is_auth_rejection(response.status()) {
            return Ok(false);
        }
        let info: Option<BuildInfo> = decode(check_status(response, operation)?, operation).await?;
        Ok(info
            .and_then(|info| info.libtorrent)
            .is_some_and(|version| !version.trim().is_empty()))
    }

    async fn read_version(&self) -> Result<Version, Error> {
        let operation = "application version";
        let response = self.execute(self.get("app/version"), operation).await?;
        let version = response
            .text()
            .await
            .map_err(|err| request_error(operation, err))?;
        parse_version(&version).ok_or(Error::InvalidVersion)
    }

    pub async fn active_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.torrents(true, !self.config.ignore_private).await
    }

    pub async fn all_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.torrents(false, true).await
    }

    async fn torrents(&self, active: bool, include_private: bool) -> Result<Vec<Torrent>, Error> {
        let mut seen_hashes = HashSet::new();
        let mut torrents = Vec::new();
        for offset in (0..).step_by(TORRENT_PAGE_SIZE) {
            let mut request = self
                .get("torrents/info")
                .query(&[("limit", TORRENT_PAGE_SIZE), ("offset", offset)]);
            if active {
                request = request.query(&[("filter", "active")]);
            }
            let page: Option<Vec<TorrentResponse>> = self.json(request, "torrent list").await?;
            let page = page.unwrap_or_default();
            let page_len = page.len();
            let previous_len = torrents.len();
            torrents.extend(
                page.into_iter()
                    .filter(|torrent| seen_hashes.insert(torrent.hash.clone())),
            );
            if page_len < TORRENT_PAGE_SIZE || torrents.len() == previous_len {
                break;
            }
        }
        self.properties
            .lock()
            .await
            .retain(|_, entry| entry.last_access.elapsed() < self.properties_ttl);
        let snapshots: Vec<Torrent> = stream::iter(torrents)
            .map(|torrent| async move {
                let properties = self.torrent_properties(&torrent).await;
                torrent.into_snapshot(properties, self.config.ignore_private)
            })
            .buffered(PROPERTY_REQUESTS_IN_FLIGHT)
            .collect()
            .await;
        Ok(snapshots
            .into_iter()
            .filter(|torrent| include_private || !torrent.private)
            .collect())
    }

    async fn torrent_properties(&self, torrent: &TorrentResponse) -> Option<TorrentProperties> {
        if let Some(cached) = self.properties.lock().await.get_mut(&torrent.hash)
            && cached.completed == torrent.completed
        {
            cached.last_access = Instant::now();
            return Some(cached.value);
        }
        let request = self
            .get("torrents/properties")
            .query(&[("hash", &torrent.hash)]);
        let value = self.json(request, "torrent properties").await.ok()?;
        self.properties.lock().await.insert(
            torrent.hash.clone(),
            CachedProperties {
                last_access: Instant::now(),
                completed: torrent.completed,
                value,
            },
        );
        Some(value)
    }

    pub async fn peers(&self, hash: &str) -> Result<Vec<Peer>, Error> {
        let request = self.get("sync/torrentPeers").query(&[("hash", hash)]);
        let response: PeersResponse = self.json(request, "torrent peers").await?;
        response
            .peers
            .into_iter()
            .filter_map(|(raw_address, data)| {
                data.into_snapshot(raw_address, self.enhanced)
                    .map(|peer| {
                        peer.map(|mut peer| {
                            (peer.ip, peer.port) =
                                self.address_policy.translate_peer(peer.ip, peer.port);
                            peer
                        })
                    })
                    .transpose()
            })
            .collect()
    }

    /// Replaces qBittorrent's entire banned IP preference, allowing removals.
    pub async fn replace_bans(&self, addresses: &[IpAddr]) -> Result<(), Error> {
        let range_support = self.supports_range_bans().await;
        let addresses = self
            .address_policy
            .ban_rules(addresses, range_support)
            .join("\n");
        let key = if self.uses_shadow_bans() {
            "shadow_banned_IPs"
        } else {
            "banned_IPs"
        };
        self.set_preferences(json!({(key): addresses})).await
    }

    /// Adds only the given raw peer keys without changing existing bans.
    pub async fn add_bans(&self, raw_peers: &[&str]) -> Result<(), Error> {
        self.ensure_writable()?;
        if raw_peers.is_empty() {
            return Ok(());
        }
        if raw_peers
            .iter()
            .any(|key| key.trim().is_empty() || key.contains(['|', '\r', '\n']))
        {
            return Err(Error::InvalidBanPeer);
        }
        let method = if self.uses_shadow_bans() {
            "transfer/shadowbanPeers"
        } else {
            "transfer/banPeers"
        };
        let request = self.post(method).form(&[("peers", raw_peers.join("|"))]);
        self.execute(request, "incremental peer ban").await?;
        Ok(())
    }

    /// Uses incremental bans only for pure additions. A failed write is returned
    /// to the caller, which can reconcile using a full replacement on the next round.
    pub async fn sync_bans(
        &self,
        full: &[IpAddr],
        added: &[&str],
        has_removals: bool,
        force_full: bool,
    ) -> Result<(), Error> {
        if self.config.increment_ban && !has_removals && !force_full {
            self.add_bans(added).await
        } else {
            self.replace_bans(full).await
        }
    }

    async fn set_preferences(&self, preferences: Value) -> Result<(), Error> {
        self.ensure_writable()?;
        let request = self
            .post("app/setPreferences")
            .form(&[("json", preferences.to_string())]);
        self.execute(request, "update preferences").await?;
        Ok(())
    }

    fn ensure_writable(&self) -> Result<(), Error> {
        if self.read_only {
            return Err(Error::ReadOnly);
        }
        Ok(())
    }

    fn uses_shadow_bans(&self) -> bool {
        self.enhanced && self.config.use_shadow_ban
    }

    fn url(&self, api_method: &str) -> Url {
        self.endpoint.join(api_method).expect("fixed API path")
    }

    fn get(&self, api_method: &str) -> RequestBuilder {
        self.authorize(self.client.get(self.url(api_method)))
    }

    fn post(&self, api_method: &str) -> RequestBuilder {
        self.authorize(self.client.post(self.url(api_method)))
    }

    fn authorize(&self, request: RequestBuilder) -> RequestBuilder {
        let request = request.header(REFERER, &self.referer);
        if self.api_key_enabled() {
            request.bearer_auth(&self.config.api_key)
        } else {
            request
        }
    }

    fn api_key_enabled(&self) -> bool {
        !self.config.api_key.trim().is_empty()
    }

    async fn json<T: serde::de::DeserializeOwned>(
        &self,
        request: RequestBuilder,
        operation: &'static str,
    ) -> Result<T, Error> {
        decode(self.execute(request, operation).await?, operation).await
    }

    async fn execute(
        &self,
        request: RequestBuilder,
        operation: &'static str,
    ) -> Result<Response, Error> {
        check_status(self.send(request, operation).await?, operation)
    }

    async fn send(
        &self,
        request: RequestBuilder,
        operation: &'static str,
    ) -> Result<Response, Error> {
        let basic_auth = &self.config.basic_auth;
        let request = request
            .build()
            .map_err(|err| request_error(operation, err))?;
        let fallback = if basic_auth.user.is_empty() && basic_auth.pass.is_empty() {
            None
        } else {
            request.try_clone()
        };
        let response = self
            .client
            .execute(request)
            .await
            .map_err(|err| request_error(operation, err))?;
        if response.status() == StatusCode::UNAUTHORIZED
            && let Some(mut fallback) = fallback
        {
            fallback.headers_mut().remove(AUTHORIZATION);
            return RequestBuilder::from_parts(self.client.clone(), fallback)
                .basic_auth(&basic_auth.user, Some(&basic_auth.pass))
                .send()
                .await
                .map_err(|err| request_error(operation, err));
        }
        Ok(response)
    }
}

async fn decode<T: serde::de::DeserializeOwned>(
    response: Response,
    operation: &'static str,
) -> Result<T, Error> {
    response
        .json()
        .await
        .map_err(|err| request_error(operation, err))
}

fn check_status(response: Response, operation: &'static str) -> Result<Response, Error> {
    let status = response.status();
    if status.is_success() {
        Ok(response)
    } else {
        Err(Error::Status { operation, status })
    }
}

fn request_error(operation: &'static str, error: reqwest::Error) -> Error {
    Error::Request {
        operation,
        source: error.without_url(),
    }
}

fn is_auth_rejection(status: StatusCode) -> bool {
    matches!(status, StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN)
}

fn parse_version(text: &str) -> Option<Version> {
    let text = text.trim().trim_start_matches('v');
    if let Ok(version) = Version::parse(text) {
        return Some(version);
    }
    let suffix_start = text.find(|ch: char| !(ch.is_ascii_digit() || ch == '.'));
    let (digits, suffix) = text.split_at(suffix_start.unwrap_or(text.len()));
    let mut digits = digits.split('.');
    let major = digits.next()?.parse().ok()?;
    let minor = digits.next()?.parse().ok()?;
    let patch = digits.next().unwrap_or("0").parse().ok()?;
    // qBittorrent builds can include a fourth numeric component or a suffix
    // such as "alpha1" without a separating hyphen.
    let prefix = format!("{major}.{minor}.{patch}");
    let normalized = match suffix.chars().next() {
        None => return Some(Version::new(major, minor, patch)),
        Some('-' | '+') => format!("{prefix}{suffix}"),
        Some(_) => format!("{prefix}-{suffix}"),
    };
    Version::parse(&normalized).ok()
}
