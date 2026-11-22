//! Native Aria2 Next JSON-RPC adapter.

mod snapshot;
mod wire;

use std::net::IpAddr;
use std::time::{Duration, Instant};

use pbh_core::AddressPolicy;
use pbh_network::ProxyPolicy;
use reqwest::header::{AUTHORIZATION, HeaderValue};
use reqwest::{Client, StatusCode, Url};
use serde::Deserialize;
use serde::de::DeserializeOwned;
use serde_json::{Value, json};
use thiserror::Error;
use tokio::sync::Mutex;
use uuid::Uuid;

pub use snapshot::{Peer, Torrent};
use wire::scalar;

const AUTH_FAILURE_THRESHOLD: u8 = 15;
const AUTH_BACKOFF: Duration = Duration::from_secs(30 * 60);

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub struct Config {
    pub name: Option<String>,
    pub endpoint: String,
    pub token: String,
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
            verify_ssl: true,
            ignore_private: false,
            paused: false,
        }
    }
}

#[derive(Debug, Error)]
pub enum Error {
    #[error(
        "Aria2 Next endpoint must be an HTTP(S) URL without embedded credentials or a fragment"
    )]
    InvalidEndpoint,
    #[error("Aria2 Next RPC token cannot be represented as an HTTP header")]
    InvalidToken,
    #[error("Aria2 Next is paused")]
    Paused,
    #[error("Aria2 Next rejected authentication")]
    Authentication,
    #[error("Aria2 Next authentication is temporarily paused after repeated failures")]
    LoginBackoff,
    #[error("the configured server did not identify as aria2-next")]
    UnsupportedProduct,
    #[error("Aria2 Next {method} returned HTTP {status}")]
    Status {
        method: &'static str,
        status: StatusCode,
    },
    #[error("Aria2 Next {method} request failed")]
    Request {
        method: &'static str,
        #[source]
        source: reqwest::Error,
    },
    #[error("Aria2 Next {method} returned JSON-RPC error {code}")]
    Rejected { method: &'static str, code: i64 },
    #[error("Aria2 Next {method} returned invalid data")]
    InvalidResult { method: &'static str },
    #[error("Aria2 Next returned a peer with an invalid IP address")]
    InvalidPeerAddress,
    #[error("Aria2 Next returned a peer with an invalid percent-encoded ID")]
    InvalidPeerId,
    #[error("a read-only Aria2 Next connection cannot modify the downloader")]
    ReadOnly,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BlocklistRevision {
    #[serde(default, deserialize_with = "scalar")]
    pub revision: u64,
    #[serde(default, deserialize_with = "scalar")]
    pub rule_count: u64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SpeedLimits {
    pub upload: i64,
    pub download: i64,
}

#[derive(Deserialize)]
#[serde(rename_all = "kebab-case")]
struct GlobalOptions {
    #[serde(default, deserialize_with = "scalar")]
    listen_port: u16,
    #[serde(default, deserialize_with = "scalar")]
    max_overall_upload_limit: i64,
    #[serde(default, deserialize_with = "scalar")]
    max_overall_download_limit: i64,
}

pub struct Aria2Next {
    config: Config,
    client: Client,
    endpoint: Url,
    authorization: HeaderValue,
    auth: Mutex<AuthState>,
    read_only: bool,
    address_policy: AddressPolicy,
}

#[derive(Default)]
struct AuthState {
    failures: u8,
    retry_at: Option<Instant>,
}

impl Aria2Next {
    pub fn new(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, false, &ProxyPolicy::default())
    }

    /// Allows RPC queries while preventing blocklist and global-option writes.
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
        let endpoint = Url::parse(config.endpoint.trim().trim_end_matches('/'))
            .map_err(|_| Error::InvalidEndpoint)?;
        if !matches!(endpoint.scheme(), "http" | "https")
            || !endpoint.has_host()
            || !endpoint.username().is_empty()
            || endpoint.password().is_some()
            || endpoint.fragment().is_some()
        {
            return Err(Error::InvalidEndpoint);
        }
        let mut authorization = HeaderValue::from_str(&format!("token:{}", config.token))
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
            read_only,
            address_policy: AddressPolicy::default(),
        })
    }

    /// Probes the product before enabling Aria2 Next-specific operations.
    pub async fn login(&self) -> Result<(), Error> {
        #[derive(Deserialize)]
        struct Version {
            product: Option<String>,
            version: String,
        }

        if self.config.paused {
            return Err(Error::Paused);
        }
        let mut state = self.auth.lock().await;
        if state.retry_at.is_some_and(|until| Instant::now() < until) {
            return Err(Error::LoginBackoff);
        }
        let version: Version = match self.rpc("aria2.getVersion", Vec::new()).await {
            Ok(version) => version,
            Err(Error::Status {
                status: StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN,
                ..
            }) => {
                state.failures += 1;
                if state.failures >= AUTH_FAILURE_THRESHOLD {
                    state.retry_at = Some(Instant::now() + AUTH_BACKOFF);
                    state.failures = 0;
                }
                return Err(Error::Authentication);
            }
            Err(error) => return Err(error),
        };
        if version.product.as_deref() != Some("aria2-next") {
            return Err(Error::UnsupportedProduct);
        }
        if version.version.trim().is_empty() {
            return Err(Error::InvalidResult {
                method: "aria2.getVersion",
            });
        }
        state.failures = 0;
        state.retry_at = None;
        Ok(())
    }

    /// Aria2 Next's legacy adapter always replaces the complete blocklist.
    pub async fn replace_bans(&self, full: &[IpAddr]) -> Result<BlocklistRevision, Error> {
        self.ensure_writable()?;
        let rules = self.address_policy.ban_rules(full, true);
        self.rpc("aria2.setBtPeerBlocklist", vec![json!(rules)])
            .await
    }

    pub async fn speed_limits(&self) -> Result<SpeedLimits, Error> {
        let options = self.global_options().await?;
        Ok(SpeedLimits {
            upload: options.max_overall_upload_limit,
            download: options.max_overall_download_limit,
        })
    }

    pub async fn set_speed_limits(&self, limits: SpeedLimits) -> Result<(), Error> {
        self.set_options(json!({
            "max-overall-upload-limit": limits.upload.max(0),
            "max-overall-download-limit": limits.download.max(0),
        }))
        .await
    }

    pub async fn listen_port(&self) -> Result<u16, Error> {
        self.global_options()
            .await
            .map(|options| options.listen_port)
    }

    pub async fn set_listen_port(&self, port: u16) -> Result<(), Error> {
        self.set_options(json!({"listen-port": port})).await
    }

    async fn global_options(&self) -> Result<GlobalOptions, Error> {
        self.rpc("aria2.getGlobalOption", Vec::new()).await
    }

    async fn set_options(&self, options: Value) -> Result<(), Error> {
        self.ensure_writable()?;
        let method = "aria2.changeGlobalOption";
        let result: String = self.rpc(method, vec![options]).await?;
        if result.eq_ignore_ascii_case("OK") {
            Ok(())
        } else {
            Err(Error::InvalidResult { method })
        }
    }

    fn ensure_writable(&self) -> Result<(), Error> {
        if self.read_only {
            return Err(Error::ReadOnly);
        }
        Ok(())
    }

    async fn rpc<T: DeserializeOwned>(
        &self,
        method: &'static str,
        mut params: Vec<Value>,
    ) -> Result<T, Error> {
        #[derive(Deserialize)]
        struct Response {
            id: Option<String>,
            error: Option<RpcError>,
            #[serde(default)]
            result: Value,
        }

        #[derive(Deserialize)]
        struct RpcError {
            code: i64,
        }

        if !self.config.token.is_empty() {
            params.insert(0, json!(format!("token:{}", self.config.token)));
        }
        let id = Uuid::new_v4().to_string();
        let response = self
            .client
            .post(self.endpoint.clone())
            .header(AUTHORIZATION, &self.authorization)
            .json(&json!({"jsonrpc": "2.0", "id": id, "method": method, "params": params}))
            .send()
            .await
            .map_err(|error| request_error(method, error))?;
        if !response.status().is_success() {
            return Err(Error::Status {
                method,
                status: response.status(),
            });
        }
        let response: Response = response
            .json()
            .await
            .map_err(|error| request_error(method, error))?;
        if let Some(error) = response.error {
            return Err(Error::Rejected {
                method,
                code: error.code,
            });
        }
        if response.id.as_deref() != Some(&id) {
            return Err(Error::InvalidResult { method });
        }
        serde_json::from_value(response.result).map_err(|_| Error::InvalidResult { method })
    }
}

fn request_error(method: &'static str, error: reqwest::Error) -> Error {
    Error::Request {
        method,
        source: error.without_url(),
    }
}
