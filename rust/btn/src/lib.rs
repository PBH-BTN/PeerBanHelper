//! Native BTN negotiation, rules, IP query, and reporting client.

mod counts;
mod heartbeat;
mod ip_list;
mod ip_query;
mod pow;
mod reconfigure;
pub mod reports;
mod rule_source;
mod rules;

use std::collections::BTreeSet;
use std::sync::Arc;
use std::time::Duration;

use reqwest::header::{AUTHORIZATION, CONTENT_TYPE, HeaderMap, HeaderValue};
use reqwest::{Client, RequestBuilder, Response, StatusCode, Url};
use serde::Deserialize;
use serde_json::{Map, Value};
use sha2::{Digest, Sha256};
use thiserror::Error;
use tokio::sync::Semaphore;

pub use heartbeat::{Heartbeat, HeartbeatRoute, InterfaceHeartbeat};
pub use ip_list::{IpList, IpListCache, IpListSnapshot};
pub use ip_query::{CreateComment, IpQuery, UpdateComment};
pub use reconfigure::{Reconfigure, ReconfigureResult};
pub use reports::Reports;
pub use rule_source::Refresh;
pub use rules::{
    BtnRuleMatch, IdentityMatch, OnlineRules, OnlineRulesSnapshot, PeerRuleInput, RuleSnapshot,
    RuleSource,
};

pub const PROTOCOL_VERSION: u32 = 21;
pub const DEFAULT_COMMENT_PAGE_SIZE: i32 = 5;
pub const MAX_COMMENT_COUNT_BATCH: usize = 50;
const DEFAULT_CONFIG_URL: &str = "https://sparkle.ghostchu.com/ping/config";
const DEFAULT_TIMEOUT: Duration = Duration::from_secs(60);

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub struct Config {
    pub enabled: bool,
    pub submit: bool,
    pub config_url: String,
    pub app_id: String,
    pub app_secret: String,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            enabled: false,
            submit: true,
            config_url: DEFAULT_CONFIG_URL.into(),
            app_id: "example-app-id".into(),
            app_secret: "example-app-secret".into(),
        }
    }
}

#[derive(Debug, Error)]
pub enum Error {
    #[error("BTN is not enabled")]
    Disabled,
    #[error("invalid BTN URL for {0}")]
    InvalidUrl(&'static str),
    #[error("invalid BTN authentication headers")]
    InvalidCredentials,
    #[error("BTN protocol {actual} is outside upstream range {min}–{max}")]
    IncompatibleProtocol { min: u32, max: u32, actual: u32 },
    #[error("BTN did not advertise the {0} endpoint")]
    EndpointUnavailable(&'static str),
    #[error("BTN {0} response was invalid")]
    InvalidResponse(&'static str),
    #[error("unable to enumerate local BTN heartbeat addresses")]
    HeartbeatInterfaces(#[source] std::io::Error),
    #[error("BTN heartbeat source and destination address families differ")]
    HeartbeatAddressFamily,
    #[error("BTN {operation} HTTP request failed")]
    Transport {
        operation: &'static str,
        #[source]
        source: reqwest::Error,
    },
    #[error("BTN upstream returned HTTP {status}")]
    Upstream {
        status: u16,
        message: Option<String>,
    },
    #[error("expected between 1 and 50 IP addresses")]
    InvalidCommentCountBatch,
    #[error("BTN proof-of-work challenge could not be completed: {0}")]
    Proof(&'static str),
    #[error("BTN {0} report could not be encoded")]
    ReportEncoding(&'static str),
}

#[derive(Clone)]
pub struct BtnClient {
    base: Url,
    http: Client,
    proxy: pbh_network::ProxyPolicy,
    headers: HeaderMap,
    timeout: Duration,
    authorization: HeaderValue,
    iframe_identity: IframeIdentity,
    submit: bool,
    pow_slots: Arc<Semaphore>,
}

#[derive(Clone)]
struct IframeIdentity {
    app_id: String,
    app_secret: String,
    installation_id: String,
    hardware_id: String,
}

pub struct Negotiated {
    pub min_protocol_version: u32,
    pub max_protocol_version: u32,
    pub uses_legacy_protocol: bool,
    pub submissions_allowed: bool,
    pub advertised_abilities: BTreeSet<String>,
    pub ip_query: Option<IpQuery>,
    pub heartbeat: Option<Heartbeat>,
    pub reconfigure: Option<Reconfigure>,
    pub ip_allowlist: Option<IpList>,
    pub ip_denylist: Option<IpList>,
    pub rules: Option<OnlineRules>,
    pub reports: Reports,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PollingSchedule {
    pub interval: Duration,
    pub random_initial_delay: Duration,
}

#[derive(Deserialize)]
struct PollingConfig {
    interval: u64,
    random_initial_delay: u64,
}

impl PollingConfig {
    fn parse(self, ability: &'static str) -> Result<PollingSchedule, Error> {
        if self.interval == 0 {
            return Err(Error::InvalidResponse(ability));
        }
        Ok(PollingSchedule {
            interval: Duration::from_millis(self.interval),
            random_initial_delay: Duration::from_millis(self.random_initial_delay),
        })
    }
}

#[derive(Deserialize)]
struct ProtocolConfig {
    min_protocol_version: u32,
    max_protocol_version: u32,
    ability: Map<String, Value>,
    proof_of_work_captcha: Option<ProofEndpoint>,
}

#[derive(Deserialize)]
struct ProofEndpoint {
    endpoint: String,
}

impl BtnClient {
    pub fn new(config: Config, installation_id: &str) -> Result<Self, Error> {
        Self::with_timeout(config, installation_id, DEFAULT_TIMEOUT)
    }

    pub fn with_proxy(
        config: Config,
        installation_id: &str,
        proxy: &pbh_network::ProxyPolicy,
    ) -> Result<Self, Error> {
        Self::with_proxy_identity(config, Some(installation_id), None, proxy)
    }

    /// Use the original installation ID and the unmodified platform identity when available.
    pub fn with_proxy_identity(
        config: Config,
        installation_id: Option<&str>,
        raw_hardware_identity: Option<&str>,
        proxy: &pbh_network::ProxyPolicy,
    ) -> Result<Self, Error> {
        Self::connect(
            config,
            installation_id,
            raw_hardware_identity,
            DEFAULT_TIMEOUT,
            proxy,
        )
    }

    pub fn with_timeout(
        config: Config,
        installation_id: &str,
        timeout: Duration,
    ) -> Result<Self, Error> {
        Self::connect(
            config,
            Some(installation_id),
            None,
            timeout,
            &pbh_network::ProxyPolicy::default(),
        )
    }

    fn connect(
        config: Config,
        installation_id: Option<&str>,
        raw_hardware_identity: Option<&str>,
        timeout: Duration,
        proxy: &pbh_network::ProxyPolicy,
    ) -> Result<Self, Error> {
        if !config.enabled {
            return Err(Error::Disabled);
        }
        let base =
            Url::parse(&config.config_url).map_err(|_| Error::InvalidUrl("configuration"))?;
        let base = checked_url(base, "configuration")?;
        let mut headers = HeaderMap::new();
        let user_agent = concat!(
            "PeerBanHelper/",
            env!("CARGO_PKG_VERSION"),
            " BTN-Protocol/3.1 BTN-Protocol-Version/21"
        );
        headers.insert(
            reqwest::header::USER_AGENT,
            HeaderValue::from_static(user_agent),
        );
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));
        for name in ["btn-appid", "x-btn-appid"] {
            headers.insert(name, sensitive(&config.app_id)?);
        }
        for name in ["btn-appsecret", "x-btn-appsecret"] {
            headers.insert(name, sensitive(&config.app_secret)?);
        }
        let authorization = sensitive(&format!("Bearer {}@{}", config.app_id, config.app_secret))?;
        headers.insert("authentication", authorization.clone());
        if config.app_id.trim().is_empty()
            || config.app_id == "example-app-id"
            || config.app_secret.trim().is_empty()
            || config.app_secret == "example-app-secret"
        {
            headers.insert(
                "x-btn-installationid",
                sensitive(installation_id.unwrap_or_default())?,
            );
        }
        let hardware_hash = raw_hardware_identity.map_or_else(
            || {
                Sha256::digest(format!(
                    "IID-{}",
                    installation_id.unwrap_or("failed-to-retrieve")
                ))
            },
            Sha256::digest,
        );
        let http = proxy
            .apply(http_builder(timeout))
            .default_headers(headers.clone())
            .build()
            .map_err(|source| transport("initialize", source))?;
        Ok(Self {
            base,
            http,
            proxy: proxy.clone(),
            headers,
            timeout,
            authorization,
            iframe_identity: IframeIdentity {
                app_id: config.app_id,
                app_secret: config.app_secret,
                installation_id: installation_id.unwrap_or_default().to_owned(),
                hardware_id: format!("{hardware_hash:x}"),
            },
            submit: config.submit,
            pow_slots: Arc::new(Semaphore::new(1)),
        })
    }

    /// Set a hardware ID already hashed to the format of Java's BTN provider.
    pub fn with_hardware_id(mut self, hardware_id: String) -> Self {
        self.iframe_identity.hardware_id = hardware_id;
        self
    }

    pub async fn negotiate(&self) -> Result<Negotiated, Error> {
        let response = self
            .send(self.http.get(self.base.clone()), "configuration")
            .await?;
        let config: ProtocolConfig = json(response, "configuration").await?;
        let min = config.min_protocol_version;
        let max = config.max_protocol_version;
        if !(min..=max).contains(&PROTOCOL_VERSION) {
            return Err(Error::IncompatibleProtocol {
                min,
                max,
                actual: PROTOCOL_VERSION,
            });
        }
        let proof_endpoint = config
            .proof_of_work_captcha
            .as_ref()
            .map(|proof| self.endpoint(&proof.endpoint, "proof-of-work"))
            .transpose()?;
        let ip_query = config
            .ability
            .get("ip_query")
            .map(|ability| IpQuery::from_config(self.clone(), ability, proof_endpoint.clone()))
            .transpose()?;
        let heartbeat = config
            .ability
            .get("heartbeat")
            .map(|ability| Heartbeat::from_config(self.clone(), ability, proof_endpoint.clone()))
            .transpose()?;
        let reconfigure = config
            .ability
            .get("reconfigure")
            .map(|ability| Reconfigure::from_config(self.clone(), ability))
            .transpose()?;
        let uses_legacy_protocol = min < 20;
        let ip_allowlist = (!uses_legacy_protocol)
            .then(|| config.ability.get("ip_allowlist"))
            .flatten()
            .map(|ability| {
                IpList::from_config(
                    self.clone(),
                    ability,
                    proof_endpoint.clone(),
                    ip_list::ListKind::Allow,
                )
            })
            .transpose()?;
        let ip_denylist = (!uses_legacy_protocol)
            .then(|| config.ability.get("ip_denylist"))
            .flatten()
            .map(|ability| {
                IpList::from_config(
                    self.clone(),
                    ability,
                    proof_endpoint.clone(),
                    ip_list::ListKind::Deny,
                )
            })
            .transpose()?;
        let rule_ability = if uses_legacy_protocol {
            "rules"
        } else {
            "rule_peer_identity"
        };
        let rules = config
            .ability
            .get(rule_ability)
            .map(|ability| OnlineRules::from_config(self.clone(), ability, proof_endpoint.clone()))
            .transpose()?;
        let reports = Reports::from_config(
            self,
            &config.ability,
            uses_legacy_protocol,
            proof_endpoint.as_ref(),
        )?;
        Ok(Negotiated {
            min_protocol_version: min,
            max_protocol_version: max,
            uses_legacy_protocol,
            submissions_allowed: self.submit,
            advertised_abilities: config.ability.into_iter().map(|(name, _)| name).collect(),
            ip_query,
            heartbeat,
            reconfigure,
            ip_allowlist,
            ip_denylist,
            rules,
            reports,
        })
    }

    fn endpoint(&self, target: &str, label: &'static str) -> Result<Url, Error> {
        if target.trim().is_empty() {
            return Err(Error::InvalidUrl(label));
        }
        let url = self
            .base
            .join(target)
            .map_err(|_| Error::InvalidUrl(label))?;
        checked_url(url, label)
    }

    async fn send(
        &self,
        builder: RequestBuilder,
        operation: &'static str,
    ) -> Result<Response, Error> {
        check_response(self.send_with_auth_retry(builder, operation).await?).await
    }

    async fn send_with_auth_retry(
        &self,
        builder: RequestBuilder,
        operation: &'static str,
    ) -> Result<Response, Error> {
        let retry = builder.try_clone();
        let response = builder
            .send()
            .await
            .map_err(|source| transport(operation, source))?;
        let response = if response.status() == StatusCode::UNAUTHORIZED
            && let Some(retry) = retry
        {
            drop(response);
            retry
                .header(AUTHORIZATION, self.authorization.clone())
                .send()
                .await
                .map_err(|source| transport(operation, source))?
        } else {
            response
        };
        Ok(response)
    }
}

fn http_builder(timeout: Duration) -> reqwest::ClientBuilder {
    Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(timeout)
}

async fn check_response(response: Response) -> Result<Response, Error> {
    if response.status().is_success() {
        return Ok(response);
    }
    let status = response.status().as_u16();
    let message = if matches!(status, 400 | 409 | 429) {
        response.json::<Value>().await.ok().and_then(|value| {
            value
                .get("message")
                .and_then(Value::as_str)
                .map(str::to_owned)
        })
    } else {
        None
    };
    Err(Error::Upstream { status, message })
}

fn checked_url(url: Url, label: &'static str) -> Result<Url, Error> {
    if matches!(url.scheme(), "http" | "https")
        && url.has_host()
        && url.username().is_empty()
        && url.password().is_none()
        && url.fragment().is_none()
    {
        Ok(url)
    } else {
        Err(Error::InvalidUrl(label))
    }
}

fn sensitive(value: &str) -> Result<HeaderValue, Error> {
    let mut header = HeaderValue::from_str(value).map_err(|_| Error::InvalidCredentials)?;
    header.set_sensitive(true);
    Ok(header)
}

async fn json<T: serde::de::DeserializeOwned>(
    response: Response,
    operation: &'static str,
) -> Result<T, Error> {
    response.json().await.map_err(|source| {
        if source.is_decode() {
            Error::InvalidResponse(operation)
        } else {
            transport(operation, source)
        }
    })
}

fn transport(operation: &'static str, source: reqwest::Error) -> Error {
    Error::Transport {
        operation,
        source: source.without_url(),
    }
}
