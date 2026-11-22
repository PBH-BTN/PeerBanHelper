//! Shared networking for PeerBanHelper's native clients.

pub mod discovery;

use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};
use std::sync::Arc;

use fancy_regex::Regex;
use reqwest::{ClientBuilder, Proxy, Url};
use serde::Deserialize;
use thiserror::Error;

#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub struct ProxyConfig {
    pub setting: i32,
    pub host: String,
    pub port: i32,
    pub non_proxy_hosts: String,
}

impl Default for ProxyConfig {
    fn default() -> Self {
        Self {
            setting: 0,
            host: "127.0.0.1".to_owned(),
            port: 7890,
            non_proxy_hosts: String::new(),
        }
    }
}

#[derive(Debug, Error, Eq, PartialEq)]
pub enum ProxyError {
    #[error(
        "configured proxy must have a hostname or IP address and a nonzero port without credentials"
    )]
    InvalidAddress,
}

/// Explicit PBH proxy selection. The default disables system and environment proxies.
#[derive(Clone, Default)]
pub struct ProxyPolicy(Option<Arc<ExplicitProxy>>);

struct ExplicitProxy {
    destination: Url,
    bypasses: Vec<Regex>,
    invalid_bypasses: usize,
}

impl ProxyPolicy {
    pub fn enabled(&self) -> bool {
        self.0.is_some()
    }

    pub fn new(config: &ProxyConfig) -> Result<Self, ProxyError> {
        let scheme = match config.setting {
            1 => "http",
            2 => "socks5h",
            _ => return Ok(Self::default()),
        };
        let destination = proxy_url(scheme, &config.host, config.port)?;
        let mut bypasses = Vec::new();
        let mut invalid_bypasses = 0;
        for raw in config
            .non_proxy_hosts
            .split('|')
            .filter(|pattern| !pattern.is_empty())
        {
            let pattern = raw.replace('*', ".*").replace('?', ".?");
            match Regex::new(&format!("\\A(?:{pattern})\\z")) {
                Ok(pattern) => bypasses.push(pattern),
                Err(_) => invalid_bypasses += 1,
            }
        }
        Ok(Self(Some(Arc::new(ExplicitProxy {
            destination,
            bypasses,
            invalid_bypasses,
        }))))
    }

    /// Applies PBH's selector without falling back to environment or system proxies.
    pub fn apply(&self, builder: ClientBuilder) -> ClientBuilder {
        let builder = builder.no_proxy();
        match self.selector() {
            Some(proxy) => builder.proxy(proxy),
            None => builder,
        }
    }

    /// Applies the same selector to blocking clients used on dedicated workers.
    pub fn apply_blocking(
        &self,
        builder: reqwest::blocking::ClientBuilder,
    ) -> reqwest::blocking::ClientBuilder {
        let builder = builder.no_proxy();
        match self.selector() {
            Some(proxy) => builder.proxy(proxy),
            None => builder,
        }
    }

    /// The selected proxy for this request, or `None` for a direct route.
    pub fn proxy_for(&self, target: &Url) -> Option<&Url> {
        self.0.as_ref()?.for_url(target)
    }

    /// Java logs but skips malformed host expressions. Reports their count without exposing input.
    pub fn invalid_bypass_count(&self) -> usize {
        self.0.as_ref().map_or(0, |proxy| proxy.invalid_bypasses)
    }

    fn selector(&self) -> Option<Proxy> {
        let selection = Arc::clone(self.0.as_ref()?);
        Some(Proxy::custom(move |target| {
            selection.for_url(target).cloned()
        }))
    }
}

impl ExplicitProxy {
    fn for_url(&self, target: &Url) -> Option<&Url> {
        let host = target.host_str()?;
        if self
            .bypasses
            .iter()
            .any(|rule| rule.is_match(host).unwrap_or(false))
        {
            return None;
        }
        // Java URI retains brackets around IPv6 hosts. Its IPv4/IPv6 literal checker declines those
        // bracketed strings, so IPv6 exemptions come from the configured patterns.
        if host.parse::<Ipv4Addr>().is_ok_and(java_local_ipv4) {
            return None;
        }
        Some(&self.destination)
    }
}

fn proxy_url(scheme: &str, host: &str, port: i32) -> Result<Url, ProxyError> {
    let port = u16::try_from(port)
        .ok()
        .filter(|&port| port != 0)
        .ok_or(ProxyError::InvalidAddress)?;
    let host = host.trim();
    let ip = if let Some(bracketed) = host
        .strip_prefix('[')
        .and_then(|text| text.strip_suffix(']'))
    {
        bracketed.parse::<Ipv6Addr>().ok().map(IpAddr::from)
    } else {
        host.parse::<IpAddr>().ok()
    };
    if let Some(ip) = ip {
        let authority = SocketAddr::new(ip, port);
        return Url::parse(&format!("{scheme}://{authority}"))
            .map_err(|_| ProxyError::InvalidAddress);
    }
    if host.is_empty()
        || host.contains([':', '/', '\\', '[', ']', '%', '@', '?', '#'])
        || host.chars().any(|ch| ch.is_whitespace() || ch.is_control())
    {
        return Err(ProxyError::InvalidAddress);
    }
    // Parse the bare host with the HTTP URL rules for consistent DNS name handling and IDNA.
    let domain = Url::parse(&format!("http://{host}")).map_err(|_| ProxyError::InvalidAddress)?;
    let host = domain.host_str().ok_or(ProxyError::InvalidAddress)?;
    Url::parse(&format!("{scheme}://{host}:{port}")).map_err(|_| ProxyError::InvalidAddress)
}

fn java_local_ipv4(ip: Ipv4Addr) -> bool {
    if ip.is_private() || ip.is_link_local() || ip.is_unspecified() {
        return true;
    }
    match ip.octets() {
        [224, 0, 0, _] | [239, _, _, _] => true,
        [232, second, third, _] => second != 0 || third != 0,
        _ => false,
    }
}
