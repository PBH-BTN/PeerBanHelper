//! Native BitComet WebUI adapter.

mod credential;
mod operations;
mod snapshot;

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

pub use operations::{SpeedLimits, TrafficStatistics};
pub use snapshot::{Peer, Torrent};

const GET_IP_FILTER: &str = "api/config/ipfilter/get";
const SET_IP_FILTER: &str = "api/config/ipfilter/set";
const AUTH_FAILURE_LIMIT: u8 = 15;
const AUTH_BACKOFF: Duration = Duration::from_secs(30 * 60);

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub struct Config {
    pub name: Option<String>,
    pub endpoint: String,
    pub username: String,
    pub password: String,
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
            username: String::new(),
            password: String::new(),
            increment_ban: true,
            verify_ssl: true,
            ignore_private: false,
            paused: false,
        }
    }
}

#[derive(Debug, Error)]
pub enum Error {
    #[error("BitComet endpoint must be an HTTP(S) URL without credentials, query or fragment")]
    InvalidEndpoint,
    #[error("BitComet is paused")]
    Paused,
    #[error("BitComet rejected authentication")]
    Authentication,
    #[error("BitComet authentication is temporarily paused after repeated failures")]
    LoginBackoff,
    #[error("BitComet version 2.18 or later is required")]
    UnsupportedVersion,
    #[error("BitComet returned an invalid application version")]
    InvalidVersion,
    #[error("BitComet has no active device session")]
    NotLoggedIn,
    #[error("unable to prepare BitComet's encrypted login request")]
    Credential,
    #[error("BitComet {operation} returned HTTP {status}")]
    Status {
        operation: &'static str,
        status: StatusCode,
    },
    #[error("BitComet {operation} request failed")]
    Request {
        operation: &'static str,
        #[source]
        source: reqwest::Error,
    },
    #[error("BitComet {operation} rejected the request")]
    Rejected { operation: &'static str },
    #[error("BitComet {operation} returned invalid data")]
    InvalidResult { operation: &'static str },
    #[error("a read-only BitComet connection cannot modify downloader configuration or bans")]
    ReadOnly,
}

pub struct BitComet {
    config: Config,
    client: Client,
    endpoint: Url,
    auth: Mutex<AuthState>,
    read_only: bool,
    address_policy: AddressPolicy,
}

#[derive(Default)]
struct AuthState {
    session: Option<Session>,
    failures: u8,
    retry_at: Option<Instant>,
}

#[derive(Clone)]
struct Session {
    authorization: HeaderValue,
    version: (u32, u32),
}

#[derive(Deserialize)]
struct FilterResponse {
    ip_filter_config: FilterConfig,
}

#[derive(Deserialize)]
struct FilterConfig {
    #[serde(default)]
    enable_ipfilter: bool,
    ipfilter_mode: Option<String>,
}

impl BitComet {
    pub fn new(config: Config) -> Result<Self, Error> {
        Self::connect_with_proxy(config, false, &ProxyPolicy::default())
    }

    /// Acquires a device token to read state. Configuration and ban writes are disabled.
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
        let client = proxy
            .apply(Client::builder())
            .redirect(reqwest::redirect::Policy::none())
            .connect_timeout(Duration::from_secs(10))
            .timeout(Duration::from_secs(30))
            .danger_accept_invalid_certs(!config.verify_ssl)
            .user_agent("PeerBanHelper BitComet Adapter")
            .build()
            .map_err(|error| request_error("build HTTP client", error))?;
        Ok(Self {
            config,
            client,
            endpoint,
            auth: Mutex::default(),
            read_only,
            address_policy: AddressPolicy::default(),
        })
    }

    pub async fn login(&self) -> Result<(), Error> {
        if self.config.paused {
            return Err(Error::Paused);
        }
        let mut state = self.auth.lock().await;
        if state.retry_at.is_some_and(|until| Instant::now() < until) {
            return Err(Error::LoginBackoff);
        }
        if let Some(session) = &state.session {
            match self.read_filter(session).await {
                Ok(_) => return Ok(()),
                Err(Error::Status {
                    status: StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN,
                    ..
                }) => {
                    state.session = None;
                }
                Err(error) => return Err(error),
            }
        }
        let session = match self.authenticate().await {
            Ok(session) => session,
            Err(Error::Authentication) => {
                state.failures += 1;
                if state.failures >= AUTH_FAILURE_LIMIT {
                    state.retry_at = Some(Instant::now() + AUTH_BACKOFF);
                    state.failures = 0;
                }
                return Err(Error::Authentication);
            }
            Err(error) => return Err(error),
        };
        let filter = self.read_filter(&session).await?;
        if !self.read_only
            && (!filter.enable_ipfilter || filter.ipfilter_mode.as_deref() != Some("blacklist"))
        {
            self.post_ok(
                SET_IP_FILTER,
                Some(&session.authorization),
                json!({
                    "ip_filter_config": {"enable_ipfilter": true, "ipfilter_mode": "blacklist"},
                }),
            )
            .await?;
        }
        state.session = Some(session);
        state.failures = 0;
        state.retry_at = None;
        Ok(())
    }

    async fn authenticate(&self) -> Result<Session, Error> {
        #[derive(Deserialize)]
        struct Invite {
            invite_token: String,
            version: String,
        }

        #[derive(Deserialize)]
        struct DeviceToken {
            device_token: String,
            version: String,
        }

        let operation = "api/webui/login";
        let authentication = credential::encode(&self.config.username, &self.config.password)?;
        let response = self
            .post(
                operation,
                None,
                json!({
                    "authentication": authentication, "client_id": credential::CLIENT_ID,
                }),
            )
            .await?;
        if response["error_code"]
            .as_str()
            .is_some_and(|code| code.eq_ignore_ascii_case("PASSWORD_ERROR"))
        {
            return Err(Error::Authentication);
        }
        let invite: Invite = decode(ensure_ok(response, operation)?, operation)?;
        if parse_version(&invite.version)? < (2, 18) {
            return Err(Error::UnsupportedVersion);
        }
        let operation = "api/device_token/get";
        let invite_header = bearer(&invite.invite_token, operation)?;
        let device: DeviceToken = self
            .post_get(
                operation,
                Some(&invite_header),
                json!({
                    "invite_token": invite.invite_token,
                    "device_id": credential::CLIENT_ID,
                    "device_name": "PeerBanHelper - BitComet Adapter",
                    "platform": "webui",
                }),
            )
            .await?;
        let version = parse_version(&device.version)?;
        Ok(Session {
            authorization: bearer(&device.device_token, operation)?,
            version,
        })
    }

    async fn session(&self) -> Result<Session, Error> {
        self.auth
            .lock()
            .await
            .session
            .clone()
            .ok_or(Error::NotLoggedIn)
    }

    async fn read_filter(&self, session: &Session) -> Result<FilterConfig, Error> {
        self.post_get::<FilterResponse>(GET_IP_FILTER, Some(&session.authorization), json!({}))
            .await
            .map(|filter| filter.ip_filter_config)
    }

    async fn post(
        &self,
        operation: &'static str,
        authorization: Option<&HeaderValue>,
        payload: Value,
    ) -> Result<Value, Error> {
        let url = self
            .endpoint
            .join(operation)
            .expect("fixed BitComet endpoint");
        let request = self
            .client
            .post(url)
            .header("Client-Type", "BitComet WebUI")
            .json(&payload);
        let request = if let Some(authorization) = authorization {
            request.header(AUTHORIZATION, authorization)
        } else {
            request
        };
        let response = request
            .send()
            .await
            .map_err(|error| request_error(operation, error))?;
        if !response.status().is_success() {
            return Err(Error::Status {
                operation,
                status: response.status(),
            });
        }
        let bytes = response
            .bytes()
            .await
            .map_err(|error| request_error(operation, error))?;
        if bytes.is_empty() {
            return Ok(Value::Null);
        }
        serde_json::from_slice(&bytes).map_err(|_| Error::InvalidResult { operation })
    }

    async fn post_get<T: DeserializeOwned>(
        &self,
        operation: &'static str,
        authorization: Option<&HeaderValue>,
        payload: Value,
    ) -> Result<T, Error> {
        decode(
            self.post_ok(operation, authorization, payload).await?,
            operation,
        )
    }

    async fn post_ok(
        &self,
        operation: &'static str,
        authorization: Option<&HeaderValue>,
        payload: Value,
    ) -> Result<Value, Error> {
        ensure_ok(
            self.post(operation, authorization, payload).await?,
            operation,
        )
    }

    fn ensure_writable(&self) -> Result<(), Error> {
        if self.read_only {
            return Err(Error::ReadOnly);
        }
        Ok(())
    }
}

fn bearer(token: &str, operation: &'static str) -> Result<HeaderValue, Error> {
    if token.is_empty() {
        return Err(Error::InvalidResult { operation });
    }
    let mut bearer = HeaderValue::from_str(&format!("Bearer {token}"))
        .map_err(|_| Error::InvalidResult { operation })?;
    bearer.set_sensitive(true);
    Ok(bearer)
}

fn ensure_ok(value: Value, operation: &'static str) -> Result<Value, Error> {
    if value
        .get("error_code")
        .and_then(Value::as_str)
        .is_some_and(|code| !code.eq_ignore_ascii_case("ok"))
    {
        return Err(Error::Rejected { operation });
    }
    Ok(value)
}

fn decode<T: DeserializeOwned>(value: Value, operation: &'static str) -> Result<T, Error> {
    serde_json::from_value(value).map_err(|_| Error::InvalidResult { operation })
}

fn parse_version(version: &str) -> Result<(u32, u32), Error> {
    let mut parts = version.trim().trim_start_matches('v').split('.');
    let major = parts
        .next()
        .and_then(|part| part.parse().ok())
        .ok_or(Error::InvalidVersion)?;
    let minor = parts
        .next()
        .and_then(|part| part.parse().ok())
        .ok_or(Error::InvalidVersion)?;
    Ok((major, minor))
}

fn request_error(operation: &'static str, error: reqwest::Error) -> Error {
    Error::Request {
        operation,
        source: error.without_url(),
    }
}
