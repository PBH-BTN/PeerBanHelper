use std::net::IpAddr;
use std::sync::Arc;

use ipnet::IpNet;
use pbh_core::{AddressRule, PrefixMap, as_network, covering_network, parse_address};
use reqwest::Url;
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::rule_source::{CachedSnapshot, Refresh, RuleEndpoint};
use crate::{BtnClient, Error, PollingSchedule, transport};

#[derive(Clone, Copy)]
pub(crate) enum ListKind {
    Allow,
    Deny,
}

impl ListKind {
    fn proof_type(self) -> &'static str {
        match self {
            Self::Allow => "ip_allowlist",
            Self::Deny => "ip_denylist",
        }
    }

    fn metadata_keys(self) -> (&'static str, &'static str) {
        match self {
            Self::Allow => (
                "btn.ability.ip_allowlist.cache.version",
                "btn.ability.ip_allowlist.cache.value",
            ),
            Self::Deny => (
                "btn.ability.ip_denylist.cache.version",
                "btn.ability.ip_denylist.cache.value",
            ),
        }
    }
}

#[derive(Clone)]
pub struct IpList {
    pub schedule: PollingSchedule,
    kind: ListKind,
    source: RuleEndpoint,
    cache: Arc<CachedSnapshot<IpListSnapshot>>,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct IpListCache {
    pub revision: Option<String>,
    pub body: String,
}

pub struct IpListSnapshot {
    cache: IpListCache,
    entries: PrefixMap<String>,
}

impl IpList {
    pub(crate) fn from_config(
        client: BtnClient,
        ability: &Value,
        proof_endpoint: Option<Url>,
        kind: ListKind,
    ) -> Result<Self, Error> {
        let source = RuleEndpoint::from_config(client, ability, proof_endpoint, kind.proof_type())?;
        Ok(Self {
            schedule: source.schedule,
            kind,
            source,
            cache: Arc::default(),
        })
    }

    /// Java metadata rows used for the revision and raw rule list, respectively.
    pub fn cache_keys(&self) -> (&'static str, &'static str) {
        self.kind.metadata_keys()
    }

    /// Restore a persisted list at startup. Never replace an active snapshot.
    pub fn restore_cache(&self, cache: IpListCache) -> bool {
        self.cache.seed(IpListSnapshot::parse(cache))
    }

    pub fn snapshot(&self) -> Option<Arc<IpListSnapshot>> {
        self.cache.read()
    }

    pub async fn refresh(&self) -> Result<Refresh, Error> {
        let _guard = self.cache.refresh.lock().await;
        let current = self.snapshot();
        let revision = current
            .as_ref()
            .and_then(|current| current.cache.revision.as_deref());
        let Some(response) = self.source.fetch(revision).await? else {
            return Ok(Refresh::Unchanged);
        };
        let revision = response
            .headers()
            .get("x-btn-contentversion")
            .and_then(|value| value.to_str().ok())
            .unwrap_or("unknown")
            .to_owned();
        let body = response
            .text()
            .await
            .map_err(|error| transport(self.kind.proof_type(), error))?;
        self.cache.update(IpListSnapshot::parse(IpListCache {
            revision: Some(revision),
            body,
        }));
        Ok(Refresh::Updated)
    }
}

impl IpListSnapshot {
    fn parse(cache: IpListCache) -> Self {
        let mut entries = PrefixMap::default();
        let mut preceding = Vec::new();
        for line in cache.body.lines() {
            if line.trim().is_empty() {
                continue;
            }
            if let Some(comment) = line.strip_prefix('#') {
                preceding.push(comment);
                continue;
            }
            if let Some((network, comment)) = parse_line(line, &preceding) {
                entries.insert(network, comment);
            }
            preceding.clear();
        }
        Self { cache, entries }
    }

    pub fn cache(&self) -> &IpListCache {
        &self.cache
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// Return the first (broadest) containing node, as Java IPMatcher does.
    pub fn matching_comment(&self, address: IpAddr) -> Option<&str> {
        self.entries
            .find(address)
            .map(|(_, comment)| comment.as_str())
    }
}

fn parse_line(line: &str, preceding: &[&str]) -> Option<(IpNet, String)> {
    if line.contains(',') {
        let mut fields = line.split(',');
        let start = parse_address(fields.next()?.trim())?;
        let end = parse_address(fields.next()?.trim())?;
        // Java parses the level as written. An invalid level skips this entry.
        if fields.next()?.parse::<i32>().ok()? >= 128 {
            return None;
        }
        let (AddressRule::Host(start), AddressRule::Host(end)) = (start, end) else {
            return None;
        };
        let comment = fields
            .next()
            .filter(|text| !text.is_empty())
            .map(str::to_owned)
            .unwrap_or_else(|| preceding.join("\n"));
        return Some((covering_network(start, end)?, comment));
    }
    let (address, comment) = match line.split_once('#') {
        Some((address, comment)) => (address, comment.to_owned()),
        None => (line, preceding.join("\n")),
    };
    Some((as_network(parse_address(address.trim())?)?, comment))
}
