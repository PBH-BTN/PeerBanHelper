use std::net::{IpAddr, SocketAddr};
use std::sync::Arc;

use pbh_btn::{BtnRuleMatch, PeerRuleInput, RuleSnapshot, RuleSource};
use pbh_core::{
    Action, BlacklistMatch, IdleConnections, IdleObservation, MultiDialing, XunleiBan, xunlei_ban,
};
use pbh_geoip::GeoData;
use serde_json::{Value, json};

use crate::config::{GeoBlacklist, Policies, StringPolicy};
use crate::downloader::{Peer, Torrent};
use crate::subscriptions::Feed;

const IP_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.IPBlackList";
const PEER_ID_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.PeerIdBlacklist";
const CLIENT_NAME_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.ClientNameBlacklist";
const ANTI_VAMPIRE_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.AntiVampire";
const RANGE_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.AutoRangeBan";
const IDLE_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.IdleConnectionDosProtection";
const MULTIDIAL_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.MultiDialingBlocker";
const BTN_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.BtnNetworkOnline";
const SUBSCRIPTION_MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.IPBlackRuleList";

#[derive(Default)]
pub(crate) struct RuleRuntime {
    pub(crate) idle: IdleConnections,
    pub(crate) multi_dial: MultiDialing,
}

pub(crate) struct RuleHit {
    pub(crate) module: &'static str,
    pub(crate) action: Action,
    pub(crate) duration_ms: i64,
    pub(crate) rule: Value,
    pub(crate) reason: Value,
    pub(crate) details: Value,
}

pub(crate) fn select_rule(
    policies: &Policies,
    torrent: &Torrent,
    peer: &Peer,
    geography: Option<&GeoData>,
    previous_bans: &[(IpAddr, bool)],
    stateful_hits: [Option<RuleHit>; 5],
    failures: &mut Vec<String>,
) -> Option<RuleHit> {
    let handshaking = peer.is_handshaking();
    let ip = policies
        .ip_blacklist
        .check(peer.ip, peer.port, handshaking)
        .map(|matched| ip_hit(matched, policies.ip_ban_duration_ms))
        .or_else(|| {
            if handshaking {
                return None;
            }
            geo_hit(
                &policies.geo_blacklist,
                geography?,
                policies.ip_ban_duration_ms,
            )
        });
    let peer_id = string_hit(
        &policies.peer_id,
        PEER_ID_MODULE,
        peer.peer_id.as_deref(),
        handshaking,
        failures,
    );
    let client_name = string_hit(
        &policies.client_name,
        CLIENT_NAME_MODULE,
        peer.client_name.as_deref(),
        handshaking,
        failures,
    );
    let anti_vampire = policies.xunlei_ban_duration_ms.and_then(|duration_ms| {
        let seeding = torrent.progress >= 1.0;
        let result = xunlei_ban(
            peer.peer_id.as_deref(),
            peer.client_name.as_deref(),
            seeding,
        )?;
        let (reason, version) = match result {
            XunleiBan::OtherVersion => (
                "MODULE_ANTI_VAMPIRE_DESCRIPTION_XUNLEI_NON_0019",
                "non-0019",
            ),
            XunleiBan::Version0019Seeding => (
                "MODULE_ANTI_VAMPIRE_DESCRIPTION_XUNLEI_0019_SEEDING",
                "0019",
            ),
        };
        Some(RuleHit {
            module: ANTI_VAMPIRE_MODULE,
            action: Action::Ban,
            duration_ms,
            rule: json!({"key": "MODULE_ANTI_VAMPIRE_TITLE", "params": []}),
            reason: json!({"key": reason, "params": []}),
            details: json!({"xunleiType": version, "seeding": seeding}),
        })
    });
    let range = policies.range_ban.and_then(|(rule, duration_ms)| {
        let related = rule.related_ban(peer.ip, handshaking, previous_bans)?;
        let prefix = related.network.prefix_len();
        let family = if peer.ip.to_canonical().is_ipv4() { "IPv4" } else { "IPv6" };
        let kind = format!("{family}/{prefix}");
        Some(RuleHit {
            module: RANGE_MODULE,
            action: Action::Ban,
            duration_ms,
            rule: json!({"key": kind, "params": []}),
            reason: json!({"key": "ARB_BANNED", "params": [peer.ip.to_canonical().to_string(), related.address.to_string(), related.network.to_string(), kind]}),
            details: json!({"relatedBannedAddress": related.address.to_string()}),
        })
    });
    let [timeout, multi_dial, progress, btn, subscription] = stateful_hits;
    let mut hit = [
        peer_id,
        client_name,
        progress,
        ip,
        range,
        btn,
        subscription,
        multi_dial,
        timeout,
        anti_vampire,
    ]
    .into_iter()
    .flatten()
    .max_by_key(|hit| (hit.action, hit.duration_ms))?;
    if hit.duration_ms <= 0 {
        hit.duration_ms = policies.global_ban_duration_ms;
    }
    Some(hit)
}

fn geo_hit(policy: &GeoBlacklist, geography: &GeoData, duration_ms: i64) -> Option<RuleHit> {
    if policy.asns.is_empty() && policy.regions.is_empty() {
        return None;
    }
    let mut matched = None;
    if let Some(asn) = geography.asn.as_ref().and_then(|value| value.number)
        && policy.asns.contains(&asn)
    {
        matched = Some((
            "asn",
            json!(asn),
            asn.to_string(),
            "IP_BLACKLIST_ASN_RULE",
            "MODULE_IBL_MATCH_ASN",
        ));
    }
    if matched.is_none()
        && let Some(iso) = geography
            .country
            .as_ref()
            .and_then(|value| value.iso.as_ref())
        && policy.regions.contains(iso)
    {
        matched = Some((
            "iso",
            json!(iso),
            iso.clone(),
            "IP_BLACKLIST_REGION_RULE",
            "MODULE_IBL_MATCH_REGION",
        ));
    }
    if matched.is_none()
        && let Some(network) = geography
            .network
            .as_ref()
            .and_then(|value| value.net_type.as_ref())
    {
        let key = match network.as_str() {
            "宽带" => "wideband",
            "基站" => "basestation",
            "政企专线" | "专线" => "governmentandenterpriseline",
            "业务平台" => "businessplatform",
            "骨干网" => "backbonenetwork",
            "IP 专网" | "IP专网" => "ipprivatenetwork",
            "网吧" => "internetcafe",
            "物联网" => "iot",
            "数据中心" | "IDC" => "datacenter",
            _ => "",
        };
        if !key.is_empty() && policy.network_types.iter().any(|value| value == key) {
            matched = Some((
                "netTypes",
                json!(network),
                network.clone(),
                "IP_BLACKLIST_NETTYPE_RULE",
                "MODULE_IBL_MATCH_IP",
            ));
        }
    }
    if matched.is_none()
        && let Some(city) = geography
            .city
            .as_ref()
            .and_then(|value| value.name.as_ref())
        && let Some(pattern) = policy
            .cities
            .iter()
            .find(|pattern| city.contains(pattern.as_str()))
    {
        matched = Some((
            "city",
            json!(pattern),
            pattern.clone(),
            "IP_BLACKLIST_CITY_RULE",
            "MODULE_IBL_MATCH_IP",
        ));
    }
    let (kind, value, text, rule, reason) = matched?;
    Some(RuleHit {
        module: IP_MODULE,
        action: Action::Ban,
        duration_ms,
        rule: json!({"key": rule, "params": [text]}),
        reason: json!({"key": reason, "params": [text]}),
        details: json!({"type": kind, "rule": value}),
    })
}

pub(crate) fn subscription_hit(
    policies: &Policies,
    feeds: &[Arc<Feed>],
    peer: &Peer,
) -> Option<RuleHit> {
    let policy = policies.subscriptions.as_ref()?;
    if peer.is_handshaking() {
        return None;
    }
    let (feed, comment) = feeds
        .iter()
        .find_map(|feed| feed.match_ip(peer.ip).map(|comment| (&feed.name, comment)))?;
    Some(RuleHit {
        module: SUBSCRIPTION_MODULE,
        action: Action::Ban,
        duration_ms: policy.ban_duration.as_i64().unwrap_or(0),
        rule: json!({"key": feed, "params": []}),
        reason: json!({"key": "MODULE_IBL_MATCH_IP_RULE", "params": [feed, peer.ip.to_string(), comment]}),
        details: json!({"ruleName": feed}),
    })
}

pub(crate) fn btn_hit(
    policies: &Policies,
    snapshot: &RuleSnapshot,
    peer: &Peer,
    failures: &mut Vec<String>,
) -> Option<RuleHit> {
    let duration_ms = policies.btn_ban_duration?;
    let input = PeerRuleInput {
        address: peer.ip,
        port: peer.port,
        peer_id: peer.peer_id.as_deref(),
        client_name: peer.client_name.as_deref(),
        handshaking: peer.is_handshaking(),
    };
    let result = match snapshot.check_peer(input) {
        Ok(result) => result?,
        Err(error) => {
            failures.push(format!("BTN rule: {error}"));
            return None;
        }
    };
    let action = result.action();
    let (rule, reason, details) = match result {
        BtnRuleMatch::AllowIp { comment } => (
            json!({"key": "BTN_ABILITY_IP_ALLOWLIST_RULE", "params": [comment]}),
            json!({"key": "BTN_ABILITY_IP_ALLOWLIST_HIT", "params": [comment]}),
            json!({"type": "ipAllowList", "matchedValue": comment}),
        ),
        BtnRuleMatch::DenyIp { comment } => (
            json!({"key": "BTN_ABILITY_IP_DENYLIST_RULE", "params": [comment]}),
            json!({"key": "BTN_ABILITY_IP_DENYLIST_HIT", "params": [comment]}),
            json!({"type": "ip"}),
        ),
        BtnRuleMatch::Identity(hit) => {
            let (source, kind) = match hit.source {
                RuleSource::PeerId => ("PeerId", "peerId"),
                RuleSource::ClientName => ("ClientName", "clientName"),
                RuleSource::Ip => ("IP", "ip"),
                RuleSource::Port => ("Port", "port"),
            };
            let name = hit.matcher_name.unwrap_or_else(|| json!(hit.category));
            let value = if hit.source == RuleSource::Ip {
                json!(peer.ip.to_string())
            } else {
                name.clone()
            };
            (
                json!({"key": "BTN_BTN_RULE", "params": [hit.category, name]}),
                json!({"key": "MODULE_BTN_BAN", "params": [source, hit.category, value]}),
                json!({"type": kind, "category": hit.category, "rule": hit.metadata}),
            )
        }
    };
    Some(RuleHit {
        module: BTN_MODULE,
        action,
        duration_ms: if action == Action::Skip {
            0
        } else {
            duration_ms
        },
        rule,
        reason,
        details,
    })
}

pub(crate) fn multi_dial_hit(
    policies: &Policies,
    state: &mut MultiDialing,
    torrent: &Torrent,
    peer: &Peer,
    now: i64,
) -> Option<RuleHit> {
    let (config, duration_ms) = policies.multi_dial.as_ref()?;
    let hit = state.check(config, now, &torrent.id, peer.ip, peer.is_handshaking())?;
    let (rule, reason) = if hit.hunting {
        (
            "MDB_MULTI_HUNTING",
            "MODULE_MDB_MULTI_DIALING_HUNTING_TRIGGERED",
        )
    } else {
        (
            "MDB_MULTI_DIALING_DETECTED",
            "MODULE_MDB_MULTI_DIALING_DETECTED",
        )
    };
    Some(RuleHit {
        module: MULTIDIAL_MODULE,
        action: Action::Ban,
        duration_ms: *duration_ms,
        rule: json!({"key": rule, "params": []}),
        reason: json!({"key": reason, "params": [hit.subnet.to_string(), peer.ip.to_string()]}),
        details: json!({"subnetPeersSize": hit.peer_count, "subnet": format!("{}@{}", torrent.id, hit.subnet)}),
    })
}

pub(crate) fn idle_hit(
    policies: &Policies,
    idle: &mut IdleConnections,
    torrent: &Torrent,
    peer: &Peer,
    now: i64,
) -> Option<RuleHit> {
    let (config, duration_ms) = policies.idle.as_ref()?;
    let address = SocketAddr::new(peer.ip, peer.port);
    let observation = IdleObservation {
        address,
        flags: peer.flags.as_deref(),
        seeding: torrent.progress >= 1.0,
        progress: peer.progress,
        upload_speed: peer.upload_speed,
        download_speed: peer.download_speed,
        uploaded: peer.uploaded,
        downloaded: peer.downloaded,
    };
    let timeout = idle.check(config, now, &observation)?;
    Some(RuleHit {
        module: IDLE_MODULE,
        action: Action::Ban,
        duration_ms: *duration_ms,
        rule: json!({"key": "MODULE_ICDP_RULE_TITLE", "params": []}),
        reason: json!({"key": "MODULE_ICDP_RULE_DESCRIPTION", "params": [address.to_string()]}),
        details: json!({
            "ip": address.to_string(),
            "idle_type": "timeout",
            "idle_start": timeout.start_ms,
            "last_percentage": timeout.progress,
            "last_uploaded": timeout.uploaded,
            "last_downloaded": timeout.downloaded,
            "percentage": peer.progress,
            "uploaded": peer.uploaded,
            "downloaded": peer.downloaded,
            "upload_speed": peer.upload_speed,
            "download_speed": peer.download_speed,
        }),
    })
}

fn string_hit(
    policy: &StringPolicy,
    module: &'static str,
    text: Option<&str>,
    handshaking: bool,
    failures: &mut Vec<String>,
) -> Option<RuleHit> {
    if handshaking && text.is_none_or(|text| text.trim().is_empty()) {
        return None;
    }
    match policy.rules.check(text) {
        Ok(matched) => matched.map(|hit| RuleHit {
            module,
            action: Action::Ban,
            duration_ms: policy.ban_duration_ms,
            rule: hit.name.unwrap_or(Value::Null),
            reason: json!({"key": "MODULE_CNB_MATCH_CLIENT_NAME", "params": [hit.comment]}),
            details: json!({"rule": hit.metadata}),
        }),
        Err(error) => {
            failures.push(format!("{module}: {error}"));
            None
        }
    }
}

fn ip_hit(matched: BlacklistMatch, duration_ms: i64) -> RuleHit {
    let (rule, reason, details) = match matched {
        BlacklistMatch::Port(port) => (
            json!({"key": "IP_BLACKLIST_PORT_RULE", "params": []}),
            json!({"key": "MODULE_IBL_MATCH_PORT", "params": [port.to_string()]}),
            json!({"type": "port", "rule": port}),
        ),
        BlacklistMatch::Address(address) => (
            json!({"key": "IP_BLACKLIST_CIDR_RULE", "params": [address.to_string()]}),
            json!({"key": "MODULE_IBL_MATCH_IP", "params": [address.to_string()]}),
            json!({"type": "ip", "rule": address.to_string()}),
        ),
    };
    RuleHit {
        module: IP_MODULE,
        action: Action::Ban,
        duration_ms,
        rule,
        reason,
        details,
    }
}
