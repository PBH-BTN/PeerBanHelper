use std::collections::{BTreeMap, BTreeSet};
use std::net::IpAddr;
use std::sync::Arc;

use pbh_core::{Action, PrefixMap, StringRules, as_network, parse_address};
use reqwest::Url;
use serde::{Deserialize, Serialize};
use serde_json::{Value, json as value};

use crate::ip_list::{IpList, IpListSnapshot};
use crate::rule_source::{CachedSnapshot, Refresh, RuleEndpoint};
use crate::{BtnClient, Error, Negotiated, PollingSchedule, json};

const ABILITY: &str = "rule_peer_identity";

#[derive(Clone)]
pub struct OnlineRules {
    pub schedule: PollingSchedule,
    source: RuleEndpoint,
    cache: Arc<CachedSnapshot<OnlineRulesSnapshot>>,
}

pub struct OnlineRulesSnapshot {
    version: Option<String>,
    cache: String,
    counts: [usize; 4],
    peer_id: BTreeMap<String, StringRules>,
    client_name: BTreeMap<String, StringRules>,
    ip: BTreeMap<String, PrefixMap<()>>,
    port: BTreeMap<String, BTreeSet<i32>>,
}

#[derive(Default, Deserialize, Serialize)]
#[serde(default)]
struct WireRules {
    version: Option<String>,
    peer_id: BTreeMap<String, Vec<String>>,
    client_name: BTreeMap<String, Vec<String>>,
    ip: BTreeMap<String, Vec<String>>,
    port: BTreeMap<String, Vec<i32>>,
    // Java's legacy cache reader expects a map when its script toggle is on.
    // Emit the empty field while discarding every incoming script payload.
    #[serde(skip_deserializing)]
    script: BTreeMap<String, String>,
}

#[derive(Clone, Copy)]
pub struct PeerRuleInput<'a> {
    pub address: IpAddr,
    pub port: u16,
    pub peer_id: Option<&'a str>,
    pub client_name: Option<&'a str>,
    pub handshaking: bool,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RuleSource {
    PeerId,
    ClientName,
    Ip,
    Port,
}

#[derive(Debug, Eq, PartialEq)]
pub struct IdentityMatch {
    pub source: RuleSource,
    pub category: String,
    pub matcher_name: Option<Value>,
    pub metadata: Option<String>,
}

#[derive(Debug, Eq, PartialEq)]
pub enum BtnRuleMatch {
    AllowIp { comment: String },
    DenyIp { comment: String },
    Identity(IdentityMatch),
}

impl BtnRuleMatch {
    pub fn action(&self) -> Action {
        match self {
            Self::AllowIp { .. } => Action::Skip,
            Self::DenyIp { .. } | Self::Identity(_) => Action::Ban,
        }
    }
}

#[derive(Clone, Default)]
pub struct RuleSnapshot {
    pub ip_allowlist: Option<Arc<IpListSnapshot>>,
    pub ip_denylist: Option<Arc<IpListSnapshot>>,
    pub rules: Option<Arc<OnlineRulesSnapshot>>,
}

impl Negotiated {
    pub fn rule_snapshot(&self) -> RuleSnapshot {
        RuleSnapshot {
            ip_allowlist: self.ip_allowlist.as_ref().and_then(IpList::snapshot),
            ip_denylist: self.ip_denylist.as_ref().and_then(IpList::snapshot),
            rules: self.rules.as_ref().and_then(OnlineRules::snapshot),
        }
    }

    pub fn check_peer(&self, peer: PeerRuleInput<'_>) -> Result<Option<BtnRuleMatch>, Error> {
        self.rule_snapshot().check_peer(peer)
    }
}

impl RuleSnapshot {
    pub fn check_peer(&self, peer: PeerRuleInput<'_>) -> Result<Option<BtnRuleMatch>, Error> {
        if let Some(comment) = self
            .ip_allowlist
            .as_ref()
            .and_then(|list| list.matching_comment(peer.address))
        {
            return Ok(Some(BtnRuleMatch::AllowIp {
                comment: comment.to_owned(),
            }));
        }
        if let Some(comment) = self
            .ip_denylist
            .as_ref()
            .and_then(|list| list.matching_comment(peer.address))
        {
            return Ok(Some(BtnRuleMatch::DenyIp {
                comment: comment.to_owned(),
            }));
        }
        if let Some(rules) = self.rules.as_ref() {
            return rules
                .check_peer(peer)
                .map(|matched| matched.map(BtnRuleMatch::Identity));
        }
        Ok(None)
    }
}

impl OnlineRules {
    pub const CACHE_KEY: &str = "btn.ability.rules.cache";

    pub(crate) fn from_config(
        client: BtnClient,
        ability: &Value,
        proof_endpoint: Option<Url>,
    ) -> Result<Self, Error> {
        let source = RuleEndpoint::from_config(client, ability, proof_endpoint, ABILITY)?;
        Ok(Self {
            schedule: source.schedule,
            source,
            cache: Arc::default(),
        })
    }

    pub fn snapshot(&self) -> Option<Arc<OnlineRulesSnapshot>> {
        self.cache.read()
    }

    /// Read an existing Java metadata row and ignore its Aviator field.
    pub fn restore_cache(&self, body: &str) -> Result<bool, Error> {
        let rules = serde_json::from_str(body).map_err(|_| Error::InvalidResponse(ABILITY))?;
        Ok(self.cache.seed(OnlineRulesSnapshot::parse(rules)?))
    }

    pub async fn refresh(&self) -> Result<Refresh, Error> {
        let _guard = self.cache.refresh.lock().await;
        let current = self.snapshot();
        let version = current.as_ref().and_then(|current| current.version());
        let Some(response) = self.source.fetch(version).await? else {
            return Ok(Refresh::Unchanged);
        };
        let rules: WireRules = json(response, ABILITY).await?;
        self.cache.update(OnlineRulesSnapshot::parse(rules)?);
        Ok(Refresh::Updated)
    }
}

impl OnlineRulesSnapshot {
    fn parse(rules: WireRules) -> Result<Self, Error> {
        let cache = serde_json::to_string(&rules).map_err(|_| Error::InvalidResponse(ABILITY))?;
        let mut ip = BTreeMap::new();
        for (category, ranges) in rules.ip {
            let mut entries = PrefixMap::default();
            for raw in ranges {
                let address = parse_address(&raw)
                    .and_then(as_network)
                    .ok_or(Error::InvalidResponse(ABILITY))?;
                entries.insert(address, ());
            }
            ip.insert(category, entries);
        }
        let counts = [
            ip.values().map(PrefixMap::len).sum(),
            rules.peer_id.values().map(Vec::len).sum(),
            rules.client_name.values().map(Vec::len).sum(),
            rules.port.values().map(Vec::len).sum(),
        ];
        Ok(Self {
            version: rules.version,
            cache,
            counts,
            peer_id: text_groups(rules.peer_id)?,
            client_name: text_groups(rules.client_name)?,
            ip,
            port: rules
                .port
                .into_iter()
                .map(|(name, ports)| (name, ports.into_iter().collect()))
                .collect(),
        })
    }

    pub fn version(&self) -> Option<&str> {
        self.version.as_deref()
    }

    /// Counts in the WebUI's IP, peer ID, client name, and port order.
    pub fn counts(&self) -> [usize; 4] {
        self.counts
    }

    /// Serialize only the supported rule families for the existing metadata key.
    pub fn cache_json(&self) -> &str {
        &self.cache
    }

    pub fn check_peer(&self, peer: PeerRuleInput<'_>) -> Result<Option<IdentityMatch>, Error> {
        if peer.handshaking {
            return Ok(None);
        }
        let peer_id = text_match(&self.peer_id, peer.peer_id, RuleSource::PeerId)?;
        let client_name = text_match(&self.client_name, peer.client_name, RuleSource::ClientName)?;
        let ip = self.ip.iter().find_map(|(name, group)| {
            group.find(peer.address).map(|_| IdentityMatch {
                source: RuleSource::Ip,
                category: name.clone(),
                matcher_name: None,
                metadata: None,
            })
        });
        let port = self
            .port
            .iter()
            .find(|(_, group)| group.contains(&i32::from(peer.port)))
            .map(|(name, _)| IdentityMatch {
                source: RuleSource::Port,
                category: name.clone(),
                matcher_name: Some(value!({"key":"BTN_PORT_RULE","params":[self.version()]})),
                metadata: Some(peer.port.to_string()),
            });
        Ok(port.or(ip).or(client_name).or(peer_id))
    }
}

fn text_groups(raw: BTreeMap<String, Vec<String>>) -> Result<BTreeMap<String, StringRules>, Error> {
    raw.into_iter()
        .map(|(name, rules)| {
            let rules = StringRules::parse(&rules).map_err(|_| Error::InvalidResponse(ABILITY))?;
            Ok((name, rules))
        })
        .collect()
}

fn text_match(
    groups: &BTreeMap<String, StringRules>,
    text: Option<&str>,
    kind: RuleSource,
) -> Result<Option<IdentityMatch>, Error> {
    for (name, rules) in groups {
        if let Some(matched) = rules
            .check(text)
            .map_err(|_| Error::InvalidResponse(ABILITY))?
        {
            return Ok(Some(IdentityMatch {
                source: kind,
                category: name.clone(),
                matcher_name: matched.name,
                metadata: Some(matched.metadata),
            }));
        }
    }
    Ok(None)
}
