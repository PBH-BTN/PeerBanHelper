//! Native BiglyBT PBH plugin protocol 2 adapter.

mod bans;
mod operations;
mod snapshot;

use std::time::{Duration, Instant};

use pbh_core::AddressPolicy;
use pbh_network::ProxyPolicy;
use reqwest::header::{AUTHORIZATION, CONTENT_TYPE, HeaderValue};
use reqwest::{Client, Method, RequestBuilder, Response, StatusCode, Url};
use serde::Deserialize;
use serde::de::DeserializeOwned;
use serde_json::json;
use thiserror::Error;
use tokio::sync::Mutex;

pub use bans::BanStatus;
use bans::SyncState;
pub use operations::{SpeedLimits, TrafficStatistics};
pub use snapshot::{Peer, Torrent};

const AUTH_FAILURE_LIMIT: u8 = 15;
const AUTH_BACKOFF: Duration = Duration::from_secs(30 * 60);

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub struct Config {
    pub name: Option<String>,
    pub endpoint: String,
    pub token: String,
    pub increment_ban: bool,
    pub verify_ssl: bool,
    pub ignore_private: bool,
    pub paused: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            name: None,
            endpoint: String::new(),
            token: String::new(),
            increment_ban: true,
            verify_ssl: true,
            ignore_private: false,
            paused: false,
        }
    }
}

#[derive(Debug, Error)]
pub enum Error {
    #[error("BiglyBT endpoint must be an HTTP(S) URL without credentials, query or fragment")]
    InvalidEndpoint,
    #[error("BiglyBT token cannot be represented as an HTTP header")]
    InvalidToken,
    #[error("BiglyBT is paused")]
    Paused,
    #[error("BiglyBT rejected authentication")]
    Authentication,
    #[error("BiglyBT authentication is temporarily paused after repeated failures")]
    LoginBackoff,
    #[error("BiglyBT requires adapter protocol 2 (plugin 2.1.0 or later)")]
    UnsupportedProtocol,
    #[error("BiglyBT IP filtering is disabled")]
    FilterDisabled,
    #[error("BiglyBT {operation} returned HTTP {status}")]
    Status {
        operation: &'static str,
        status: StatusCode,
    },
    #[error("BiglyBT {operation} request failed")]
    Request {
        operation: &'static str,
        #[source]
        source: reqwest::Error,
    },
    #[error("a read-only BiglyBT connection cannot modify the downloader")]
    ReadOnly,
}

pub struct BiglyBt {
    config: Config,
    client: Client,
    endpoint: Url,
    authorization: HeaderValue,
    auth: Mutex<AuthState>,
    bans: Mutex<SyncState>,
    read_only: bool,
    address_policy: AddressPolicy,
}

#[derive(Default)]
struct AuthState {
    failures: u8,
    retry_at: Option<Instant>,
}

impl BiglyBt {
    pub fn new(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, false, &ProxyPolicy::default())
    }

    /// Read-only mode probes the plugin and its ban status without updating
    /// connector metadata, downloader settings or the ban list.
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
        endpoint
            .path_segments_mut()
            .map_err(|_| Error::InvalidEndpoint)?
            .pop_if_empty()
            .push("");
        let mut authorization = HeaderValue::from_str(&format!("Bearer {}", config.token))
            .map_err(|_| Error::InvalidToken)?;
        authorization.set_sensitive(true);
        let client = proxy
            .apply(Client::builder())
            .redirect(reqwest::redirect::Policy::none())
            .connect_timeout(Duration::from_secs(15))
            .timeout(Duration::from_secs(30))
            .danger_accept_invalid_certs(!config.verify_ssl)
            .user_agent(concat!("PeerBanHelper/", env!("CARGO_PKG_VERSION")))
            .build()
            .map_err(|error| request_error("build HTTP client", error))?;
        Ok(Self {
            config,
            client,
            endpoint,
            authorization,
            auth: Mutex::default(),
            bans: Mutex::default(),
            read_only,
            address_policy: AddressPolicy::default(),
        })
    }

    pub async fn login(&self) -> Result<(), Error> {
        #[derive(Deserialize)]
        struct Metadata {
            protocol: Option<u32>,
        }

        if self.config.paused {
            return Err(Error::Paused);
        }
        let mut auth = self.auth.lock().await;
        if auth.retry_at.is_some_and(|until| Instant::now() < until) {
            return Err(Error::LoginBackoff);
        }
        let metadata: Metadata = match self.get("metadata", "metadata").await {
            Ok(metadata) => metadata,
            Err(Error::Status {
                status: StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN,
                ..
            }) => {
                auth.failures += 1;
                if auth.failures >= AUTH_FAILURE_LIMIT {
                    auth.retry_at = Some(Instant::now() + AUTH_BACKOFF);
                    auth.failures = 0;
                }
                return Err(Error::Authentication);
            }
            Err(error) => return Err(error),
        };
        if metadata.protocol != Some(2) {
            return Err(Error::UnsupportedProtocol);
        }
        self.recover_bans().await?;
        if !self.read_only {
            let announcement = self.request(Method::POST, "setconnector").json(&json!({
                "software": "PeerBanHelper",
                "version": env!("CARGO_PKG_VERSION"),
                "abbrev": option_env!("PBH_BUILD_GIT_ABBREV").unwrap_or_default(),
            }));
            tokio::spawn(async move {
                // The connector label is advisory, and the Java adapter does not
                // block scanning if the plugin can't record it.
                let _ = announcement.send().await;
            });
        }
        auth.failures = 0;
        auth.retry_at = None;
        Ok(())
    }

    fn request(&self, method: Method, path: &str) -> RequestBuilder {
        let url = self.endpoint.join(path).expect("fixed plugin API path");
        self.request_url(method, url)
    }

    fn torrent_request(&self, method: Method, hash: &str, operation: &str) -> RequestBuilder {
        let mut url = self.endpoint.clone();
        url.path_segments_mut()
            .expect("HTTP(S) endpoint")
            .pop_if_empty()
            .extend(["download", hash, operation]);
        self.request_url(method, url)
    }

    fn request_url(&self, method: Method, url: Url) -> RequestBuilder {
        self.client
            .request(method, url)
            .header(AUTHORIZATION, &self.authorization)
            .header(CONTENT_TYPE, "application/json")
    }

    async fn get<T: DeserializeOwned>(
        &self,
        path: &str,
        operation: &'static str,
    ) -> Result<T, Error> {
        self.get_json(self.request(Method::GET, path), operation)
            .await
    }

    async fn get_json<T: DeserializeOwned>(
        &self,
        request: RequestBuilder,
        operation: &'static str,
    ) -> Result<T, Error> {
        let response = self.send(request, operation).await?;
        decode(check_status(response, operation)?, operation).await
    }

    async fn send(
        &self,
        request: RequestBuilder,
        operation: &'static str,
    ) -> Result<Response, Error> {
        request
            .send()
            .await
            .map_err(|error| request_error(operation, error))
    }

    fn ensure_writable(&self) -> Result<(), Error> {
        if self.read_only {
            return Err(Error::ReadOnly);
        }
        Ok(())
    }
}

fn check_status(response: Response, operation: &'static str) -> Result<Response, Error> {
    if response.status().is_success() {
        Ok(response)
    } else {
        Err(Error::Status {
            operation,
            status: response.status(),
        })
    }
}

async fn decode<T: DeserializeOwned>(
    response: Response,
    operation: &'static str,
) -> Result<T, Error> {
    response
        .json()
        .await
        .map_err(|error| request_error(operation, error))
}

fn request_error(operation: &'static str, error: reqwest::Error) -> Error {
    Error::Request {
        operation,
        source: error.without_url(),
    }
}
