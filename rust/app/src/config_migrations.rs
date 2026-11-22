//! Versioned YAML upgrades from the Java distribution.

use std::collections::BTreeMap;

use anyhow::{Context, Result, anyhow};
use serde::Serialize;
use serde_yaml_ng::{Mapping, Value};
use uuid::Uuid;

pub(crate) const MAIN_VERSION: i64 = 48;
pub(crate) const PROFILE_VERSION: i64 = 42;
const MAIN_DEFAULTS: &str = include_str!("../../../src/main/resources/config.yml");
const PROFILE_DEFAULTS: &str = include_str!("../../../src/main/resources/profile.yml");

#[derive(Debug, PartialEq, Eq)]
pub(crate) struct MigrationReport {
    pub before: i64,
    pub after: i64,
    pub changed: bool,
    pub downloader_id_changes: BTreeMap<String, String>,
}

pub(crate) fn migrate_main(config: &mut Value) -> Result<MigrationReport> {
    let synology =
        std::env::var("PBH_PKG_SYNOLOGY").is_ok_and(|flag| flag.eq_ignore_ascii_case("true"));
    migrate_main_for_installation(config, synology)
}

pub(crate) fn migrate_main_for_installation(
    config: &mut Value,
    synology: bool,
) -> Result<MigrationReport> {
    migrate(
        config,
        MAIN_VERSION,
        MAIN_DEFAULTS,
        |config, defaults, report| {
            if get(config, "installation-id")
                .and_then(Value::as_str)
                .is_none_or(|id| id.trim().is_empty())
            {
                set(config, "installation-id", Uuid::new_v4().to_string());
            }
            if report.before < MAIN_VERSION {
                for version in (report.before.max(0) + 1)..=MAIN_VERSION {
                    if version == 37 && synology {
                        each_section(config, "client", |client| {
                            if let Some(endpoint) = get(client, "endpoint")
                                .and_then(Value::as_str)
                                .and_then(synology_endpoint)
                            {
                                set(client, "endpoint", endpoint);
                            }
                        });
                    } else {
                        apply_main(config, defaults, report, version);
                    }
                }
                remove(config, "btn.allow-script-execute");
            }
            Ok(())
        },
    )
}

fn synology_endpoint(endpoint: &str) -> Option<String> {
    let url = reqwest::Url::parse(endpoint).ok()?;
    if !url.host_str()?.starts_with("172.") {
        return None;
    }
    let (scheme, remaining) = endpoint.split_once("://")?;
    let authority_end = remaining.find(['/', '?', '#']).unwrap_or(remaining.len());
    let (authority, suffix) = remaining.split_at(authority_end);
    let host = authority.rsplit('@').next()?;
    let port = host.rfind(':').map_or("", |index| &host[index..]);
    let path = suffix.split(['?', '#']).next()?;
    Some(format!("{scheme}://127.0.0.1{port}{path}"))
}

pub(crate) fn migrate_profile(config: &mut Value) -> Result<MigrationReport> {
    migrate(
        config,
        PROFILE_VERSION,
        PROFILE_DEFAULTS,
        |config, defaults, report| {
            if report.before < PROFILE_VERSION {
                for version in (report.before.max(0) + 1)..=PROFILE_VERSION {
                    apply_profile(config, defaults, version)
                        .with_context(|| format!("upgrade profile to version {version}"))?;
                }
                remove(config, "module.expression-engine");
            }
            Ok(())
        },
    )
}

fn migrate(
    config: &mut Value,
    latest: i64,
    defaults: &str,
    apply: impl FnOnce(&mut Value, &Value, &mut MigrationReport) -> Result<()>,
) -> Result<MigrationReport> {
    let root = config
        .as_mapping()
        .context("configuration must be a YAML mapping")?;
    let before = match root.get("config-version") {
        None => 0,
        Some(Value::Number(number)) => number
            .as_i64()
            .context("config-version must be an integer")?,
        Some(_) => return Err(anyhow!("config-version must be an integer")),
    };
    let mut report = MigrationReport {
        before,
        after: before,
        changed: false,
        downloader_id_changes: BTreeMap::new(),
    };
    if before > latest {
        return Ok(report);
    }
    let mut upgraded = config.clone();
    let defaults =
        serde_yaml_ng::from_str(defaults).context("read bundled YAML migration defaults")?;
    apply(&mut upgraded, &defaults, &mut report)?;
    if before < latest {
        set(&mut upgraded, "config-version", latest);
        report.after = latest;
    }
    report.changed = *config != upgraded;
    if report.changed {
        *config = upgraded;
    }
    Ok(report)
}

fn get<'a>(config: &'a Value, path: &str) -> Option<&'a Value> {
    path.split('.')
        .try_fold(config, |node, key| node.as_mapping()?.get(key))
}

fn get_mut<'a>(config: &'a mut Value, path: &str) -> Option<&'a mut Value> {
    path.split('.')
        .try_fold(config, |node, key| node.as_mapping_mut()?.get_mut(key))
}

fn set(config: &mut Value, path: &str, value: impl Into<Value>) {
    let mut parts = path.split('.').peekable();
    let mut node = config;
    while let Some(part) = parts.next() {
        if !node.is_mapping() {
            *node = Value::Mapping(Mapping::new());
        }
        let map = node.as_mapping_mut().expect("section is a mapping");
        if parts.peek().is_none() {
            map.insert(Value::from(part), value.into());
            return;
        }
        node = map
            .entry(Value::from(part))
            .or_insert_with(|| Value::Mapping(Mapping::new()));
    }
}

fn remove(config: &mut Value, path: &str) -> Option<Value> {
    let (section, key) = if let Some((section, key)) = path.rsplit_once('.') {
        (get_mut(config, section)?, key)
    } else {
        (config, path)
    };
    section.as_mapping_mut()?.remove(key)
}

fn copy_path(config: &mut Value, target: &str, source: &Value, source_path: &str) {
    if let Some(value) = get(source, source_path).filter(|value| !value.is_null()) {
        set(config, target, value.clone());
    } else {
        remove(config, target);
    }
}

fn copy_defaults(config: &mut Value, defaults: &Value, paths: &[&str]) {
    for path in paths {
        copy_path(config, path, defaults, path);
    }
}

fn rename(config: &mut Value, from: &str, to: &str) {
    if let Some(value) = remove(config, from) {
        set(config, to, value);
    }
}

fn each_section(config: &mut Value, path: &str, mut callback: impl FnMut(&mut Value)) {
    if let Some(sections) = get_mut(config, path).and_then(Value::as_mapping_mut) {
        for value in sections.values_mut().filter(|value| value.is_mapping()) {
            callback(value);
        }
    }
}

fn setting_is(config: &Value, path: &str, expected: i64) -> bool {
    get(config, path).and_then(Value::as_i64) == Some(expected)
}

fn apply_main(config: &mut Value, defaults: &Value, report: &mut MigrationReport, version: i64) {
    match version {
        1 | 3 | 4 => each_section(config, "client", |client| {
            let kind = get(client, "type")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_ascii_lowercase();
            match (version, kind.as_str()) {
                (1, "qbittorrent") => set(client, "increment-ban", true),
                (3, "transmission") => set(client, "rpc-url", "/transmission/rpc"),
                (4, "qbittorrent") => set(client, "increment-ban", false),
                _ => {}
            }
        }),
        2 => set(config, "persist.banlist", true),
        5 => set(config, "lookup.dns-reverse-lookup", false),
        6 => {
            set(config, "ip-database.account-id", "");
            set(config, "ip-database.license-key", "");
            set(config, "ip-database.database-city", "GeoLite2-City");
            set(config, "ip-database.database-asn", "GeoLite2-ASN");
            set(config, "ip-database.auto-update", true);
        }
        7 => {
            remove(config, "threads");
        }
        8 => set(config, "server.token", Uuid::new_v4().to_string()),
        9 => {
            remove(config, "firewall-integration");
            set(config, "firewall-integration.windows-adv-firewall", true);
        }
        10 => set(config, "language", "default"),
        11 => set(config, "server.allow-cors", false),
        12 => set(config, "server.external-webui", false),
        13 => {
            set(config, "proxy.setting", 0);
            set(config, "proxy.host", "127.0.0.1");
            set(config, "proxy.port", 7890);
        }
        14 => set(config, "proxy.non-proxy-host", "127.0.0.1|localhost"),
        15 => set(config, "pbh-plus-key", ""),
        16 => {
            let legacy = remove(config, "proxy.non-proxy-host");
            if get(config, "proxy.non-proxy-hosts").is_none_or(Value::is_null) {
                let default = "localhost|127.*|192.168.*|10.*|172.16.*|172.17.*|172.18.*|172.19.*|172.20.*|172.21.*|172.22.*|172.23.*|172.24.*|172.25.*|172.26.*|172.27.*|172.28.*|172.29.*|172.30.*|172.31.*";
                set(
                    config,
                    "proxy.non-proxy-hosts",
                    legacy.unwrap_or_else(|| Value::from(default)),
                );
            }
        }
        17 => set(config, "performance.windows-ecoqos-api", true),
        18 => {
            remove(config, "ip-database.account-id");
            remove(config, "ip-database.license-key");
        }
        19 => set(config, "privacy.error-reporting", true),
        21 => copy_defaults(config, defaults, &["push-notification"]),
        22 => {
            remove(config, "privacy");
        }
        24 => {
            set(config, "decentralized.enabled", false);
            set(config, "decentralized.kubo-rpc", "/ip4/127.0.0.1/tcp/5001");
            set(config, "decentralized.features.publish-banlist", 3_600_000);
        }
        25 => each_section(config, "push-notification", |provider| {
            remove(provider, "enabled");
            rename(provider, "send-key", "sendkey");
            rename(provider, "chat-id", "chatid");
        }),
        26 => migrate_smtp(config),
        27 => set(config, "persist.vacuum-interval-days", 60),
        28 => migrate_btn_address(config),
        30 => set(config, "gui", "auto"),
        31 => {
            remove(config, "banlist-invoker");
        }
        32 => assign_downloader_ids(config, report),
        35 => {
            if let Some(mode) = get(config, "proxy.setting").and_then(Value::as_i64) {
                let replacement = match mode {
                    1 => 0,
                    2 => 1,
                    3 => 2,
                    _ => mode,
                };
                set(config, "proxy.setting", replacement);
            }
        }
        36 => copy_defaults(config, defaults, &["stun", "auto-stun"]),
        38 => {
            let key = get(config, "pbh-plus-key")
                .and_then(Value::as_str)
                .filter(|key| !key.trim().is_empty());
            let keys = key.into_iter().map(Value::from).collect::<Vec<_>>();
            set(config, "pbh-plus-key", Value::Sequence(keys));
        }
        40 => copy_defaults(config, defaults, &["banlist-remapping"]),
        41 => copy_defaults(config, defaults, &["privacy.analytics"]),
        // Keep a database connection already selected by an older installation.
        42 if get(config, "database").is_none_or(Value::is_null) => {
            copy_defaults(config, defaults, &["database"])
        }
        43 => {
            if let Some(id) = get(config, "database.type")
                .and_then(Value::as_i64)
                .filter(|id| *id != -1)
            {
                let kind = match id {
                    1 => "h2",
                    2 | 10 => "mysql",
                    3 | 11 => "postgresql",
                    _ => "sqlite",
                };
                set(config, "database.type", kind);
            }
        }
        44 => {
            let available = get(config, "stun.availableTest")
                .and_then(Value::as_bool)
                .or_else(|| get(defaults, "stun.availableTest").and_then(Value::as_bool))
                .unwrap_or_default();
            set(config, "stun.available-test", available);
            remove(config, "stun.availableTest");
        }
        45 if setting_is(config, "banlist-remapping.ipv6.remap-range", 60) => {
            set(config, "banlist-remapping.ipv6.remap-range", 52);
        }
        46 => set(config, "ip-database.database-geocn", "GeoCN"),
        47 => set(config, "ip-remapping.teredo", false),
        48 => copy_defaults(config, defaults, &["ip-remapping.nat64"]),
        // 33–34 removed obsolete on-disk artifacts in Java. No files are deleted here.
        _ => {}
    }
}

fn migrate_smtp(config: &mut Value) {
    if let Some(providers) = get_mut(config, "push-notification").and_then(Value::as_mapping_mut) {
        providers
            .retain(|_, provider| get(provider, "enabled").and_then(Value::as_bool) != Some(true));
        for provider in providers
            .values_mut()
            .filter(|provider| provider.is_mapping())
        {
            if get(provider, "type").and_then(Value::as_str) == Some("smtp") {
                let encryption = if get(provider, "ssl")
                    .and_then(Value::as_bool)
                    .unwrap_or_default()
                {
                    "STARTTLS"
                } else {
                    "NONE"
                };
                set(provider, "auth", true);
                set(provider, "encryption", encryption);
                remove(provider, "ssl");
                set(provider, "sendPartial", true);
            }
        }
    }
}

fn migrate_btn_address(config: &mut Value) {
    let outdated = get(config, "btn.config-url")
        .and_then(Value::as_str)
        .and_then(|url| {
            url.strip_prefix("https://")
                .or_else(|| url.strip_prefix("http://"))
        })
        .and_then(|url| url.strip_suffix(".ghostchu-services.top/ping/config"))
        .is_some_and(|name| matches!(name, "btn-prod" | "btn-dev" | "btn-dev2" | "sparkle"));
    if outdated {
        set(
            config,
            "btn.config-url",
            "https://sparkle.pbh-btn.com/ping/config",
        );
    }
}

fn assign_downloader_ids(config: &mut Value, report: &mut MigrationReport) {
    let Some(clients) = get_mut(config, "client").and_then(Value::as_mapping_mut) else {
        return;
    };
    let old = std::mem::take(clients);
    for (key, mut client) in old {
        let Some(name) = key.as_str() else {
            continue;
        };
        if !client.is_mapping() {
            continue;
        }
        let id = Uuid::new_v4().to_string();
        set(&mut client, "name", name);
        clients.insert(Value::from(id.as_str()), client);
        report.downloader_id_changes.insert(name.to_owned(), id);
    }
}

const PEER_IDS: &str = "module.peer-id-blacklist.banned-peer-id";
const EXCLUDED_PEER_IDS: &str = "module.peer-id-blacklist.exclude-peer-id";
const CLIENT_NAMES: &str = "module.client-name-blacklist.banned-client-name";
const EXCLUDED_CLIENT_NAMES: &str = "module.client-name-blacklist.exclude-client-name";
const SUB_RULES: &str = "module.ip-address-blocker-rules.rules";
const NET_TYPES: &str = "module.ip-address-blocker.net-type";
const NET_TYPE_NAMES: [(&str, &str); 9] = [
    ("wideband", "wideband"),
    ("base-station", "baseStation"),
    (
        "government-and-enterprise-line",
        "governmentAndEnterpriseLine",
    ),
    ("business-platform", "businessPlatform"),
    ("backbone-network", "backboneNetwork"),
    ("ip-private-network", "ipPrivateNetwork"),
    ("internet-cafe", "internetCafe"),
    ("iot", "iot"),
    ("datacenter", "dataCenter"),
];

fn apply_profile(config: &mut Value, defaults: &Value, version: i64) -> Result<()> {
    match version {
        1 => {
            set(config, EXCLUDED_PEER_IDS, Value::Sequence(Vec::new()));
            set(config, EXCLUDED_CLIENT_NAMES, Value::Sequence(Vec::new()));
        }
        2 => {
            migrate_rule_list(config, PEER_IDS, EXCLUDED_PEER_IDS)?;
            migrate_rule_list(config, CLIENT_NAMES, EXCLUDED_CLIENT_NAMES)?;
            rename(
                config,
                "module.active-probing",
                "module.active-probing-removed",
            );
        }
        3 => {
            set(config, "module.multi-dialing-blocker.enabled", false);
            set(
                config,
                "module.multi-dialing-blocker.subnet-mask-length",
                24,
            );
            set(
                config,
                "module.multi-dialing-blocker.subnet-mask-v6-length",
                64,
            );
            set(config, "module.multi-dialing-blocker.tolerate-num", 3);
            set(
                config,
                "module.multi-dialing-blocker.cache-lifespan",
                86_400,
            );
            set(config, "module.multi-dialing-blocker.keep-hunting", true);
            set(
                config,
                "module.multi-dialing-blocker.keep-hunting-time",
                2_592_000,
            );
        }
        4 => {
            set(
                config,
                "module.ip-address-blocker.asns",
                Value::Sequence(Vec::new()),
            );
            set(
                config,
                "module.ip-address-blocker.regions",
                Value::Sequence(Vec::new()),
            );
        }
        5 => {
            set(config, "module.ip-address-blocker-rules.enabled", false);
            set(
                config,
                "module.ip-address-blocker-rules.check-interval",
                86_400_000,
            );
            set(
                config,
                "module.ip-address-blocker-rules.rules.example-rule.enabled",
                false,
            );
            set(
                config,
                "module.ip-address-blocker-rules.rules.example-rule.name",
                "Example",
            );
            set(
                config,
                "module.ip-address-blocker-rules.rules.example-rule.url",
                "https://example.com/example.txt",
            );
        }
        7 => {
            set(
                config,
                "module.progress-cheat-blocker.ipv4-prefix-length",
                32,
            );
            set(
                config,
                "module.progress-cheat-blocker.ipv6-prefix-length",
                64,
            );
        }
        8 => {
            let addresses = [
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16",
                "fc00::/7",
                "fd00::/8",
                "100.64.0.0/10",
                "169.254.0.0/16",
                "127.0.0.0/8",
                "fe80::/10",
            ];
            set(
                config,
                "ignore-peers-from-addresses",
                Value::Sequence(addresses.into_iter().map(Value::from).collect()),
            );
        }
        9 => {
            append_rules(config, PEER_IDS, &[("STARTS_WITH", "-xm")]);
            append_rules(config, CLIENT_NAMES, &[("STARTS_WITH", "xm/torrent")]);
        }
        10 => each_section(config, "module", |module| {
            set(module, "ban-duration", "default")
        }),
        11 => {
            retain_rules(config, PEER_IDS, |rule| !rule.contains("-xl0019"));
            retain_rules(config, CLIENT_NAMES, |rule| {
                !rule.contains("xunlei 0019") && !rule.contains("xunlei 0.0.1.9")
            });
        }
        12 => set(
            config,
            "module.progress-cheat-blocker.ban-duration",
            "default",
        ),
        14 => {
            set(config, "module.active-monitoring.enabled", false);
            set(
                config,
                "module.active-monitoring.data-retention-time",
                5_184_000_000i64,
            );
            set(
                config,
                "module.active-monitoring.data-cleanup-interval",
                604_800_000,
            );
            for (key, _) in NET_TYPE_NAMES {
                set(config, &format!("{NET_TYPES}.{key}"), false);
            }
            set(config, "module.progress-cheat-blocker.enable-persist", true);
            set(
                config,
                "module.progress-cheat-blocker.persist-duration",
                1_209_600_000,
            );
        }
        15 => {
            set(
                config,
                "module.ip-address-blocker.cities",
                Value::Sequence(vec![Value::from("示例海南")]),
            );
            each_section(config, SUB_RULES, |rule| {
                if matches!(
                    get(rule, "url").and_then(Value::as_str),
                    Some(
                        "https://cdn.jsdelivr.net/gh/PBH-BTN/BTN-Collected-Rules@master/combine/all.txt"
                    ) | Some(
                        "https://fastly.jsdelivr.net/gh/PBH-BTN/BTN-Collected-Rules@master/combine/all.txt"
                    )
                ) {
                    set(
                        rule,
                        "url",
                        "https://bcr.pbh-btn.ghorg.ghostchu-services.top/combine/all.txt",
                    );
                }
            });
        }
        16 if setting_is(
            config,
            "module.progress-cheat-blocker.ipv6-prefix-length",
            128,
        ) =>
        {
            set(
                config,
                "module.progress-cheat-blocker.ipv6-prefix-length",
                60,
            );
        }
        17 => {
            append_rules(config, PEER_IDS, &[("CONTAINS", "-rn0.0.0")]);
            append_rules(
                config,
                CLIENT_NAMES,
                &[("CONTAINS", "rain 0.0.0"), ("CONTAINS", "gopeed dev")],
            );
            set(config, "module.active-monitoring.enabled", true);
        }
        18 => set(
            config,
            "module.progress-cheat-blocker.max-wait-duration",
            30_000,
        ),
        19 => {
            set(
                config,
                "module.progress-cheat-blocker.fast-pcb-test-percentage",
                0.1,
            );
            set(
                config,
                "module.progress-cheat-blocker.fast-pcb-test-block-duration",
                15_000,
            );
        }
        20 => {
            set(config, "module.multi-dialing-blocker.tolerate-num-ipv4", 2);
            set(config, "module.multi-dialing-blocker.tolerate-num-ipv6", 5);
            remove(config, "module.multi-dialing-blocker.tolerate-num");
        }
        21 => set(
            config,
            "module.active-monitoring.traffic-monitoring.daily",
            -1,
        ),
        22 if setting_is(config, "module.auto-range-ban.ipv6", 32) => {
            set(config, "module.auto-range-ban.ipv6", 60)
        }
        24 => {
            copy_defaults(config, defaults, &["module.ptr-blacklist"]);
            let path = "module.ip-address-blocker-rules.rules.tor-exit-nodes.url";
            if get(config, path).and_then(Value::as_str)
                == Some(
                    "https://cdn.jsdelivr.net/gh/platformcosmo/Tor-IP-Addresses/tor-exit-nodes.lst",
                )
            {
                set(
                    config,
                    path,
                    "https://cdn.jsdelivr.net/gh/7c/torfilter/lists/txt/torfilter-1d-flat.txt",
                );
            }
        }
        25 => {
            remove(config, "decentralized");
        }
        27 => {
            remove(config, "module.active-monitoring.traffic-capping");
            set(
                config,
                "module.active-monitoring.traffic-sliding-capping.enabled",
                false,
            );
            set(
                config,
                "module.active-monitoring.traffic-sliding-capping.daily-max-allowed-upload-traffic",
                53_687_091_200i64,
            );
            set(
                config,
                "module.active-monitoring.traffic-sliding-capping.max-speed",
                10_485_760,
            );
            set(
                config,
                "module.active-monitoring.traffic-sliding-capping.min-speed",
                0,
            );
        }
        28 => set(config, "module.swarm-tracking.enabled", true),
        29 => {
            if let Some(section) = get(config, NET_TYPES).filter(|section| section.is_mapping()) {
                let types = NET_TYPE_NAMES
                    .into_iter()
                    .filter(|(old, _)| get(section, old).and_then(Value::as_bool) == Some(true))
                    .map(|(_, new)| Value::from(new))
                    .collect();
                set(config, NET_TYPES, Value::Sequence(types));
            }
        }
        30 | 31 => copy_defaults(config, defaults, &["module.idle-connection-dos-protection"]),
        // The current bundle has no top-level swarm settings. Preserve the old values for v34.
        32 if get(defaults, "module.swarm-tracking").is_some() => {
            copy_defaults(config, defaults, &["module.swarm-tracking"])
        }
        34 => {
            copy_defaults(config, defaults, &["module.peer-analyse-service"]);
            rename(
                config,
                "module.active-monitoring.data-retention-time",
                "module.peer-analyse-service.peer-recording.data-retention-time",
            );
            rename(
                config,
                "module.active-monitoring.data-cleanup-interval",
                "module.peer-analyse-service.peer-recording.data-cleanup-interval",
            );
            rename(
                config,
                "module.swarm-tracking.enabled",
                "module.peer-analyse-service.swarm-tracking.enabled",
            );
            remove(config, "module.swarm-tracking");
        }
        35 => {
            retain_rules(config, PEER_IDS, |rule| {
                rule != r#"{"method":"STARTS_WITH","content":"-xl"}"#
            });
            retain_rules(config, CLIENT_NAMES, |rule| {
                !matches!(
                    rule,
                    r#"{"method":"CONTAINS","content":"xunlei"}"#
                        | r#"{"method":"STARTS_WITH","content":"-XL"}"#
                )
            });
        }
        36 => copy_defaults(config, defaults, &["module.anti-vampire"]),
        37 => {
            copy_defaults(
                config,
                defaults,
                &["module.peer-analyse-service.swarm-tracking.data-flush-interval"],
            );
            let path = "module.peer-analyse-service.peer-recording.data-flush-interval";
            if setting_is(config, path, 20_000) {
                copy_defaults(config, defaults, &[path]);
            }
        }
        38 => {
            append_rules(
                config,
                PEER_IDS,
                &[("EQUALS", "Unknown"), ("EQUALS", "未知")],
            );
            append_rules(config, CLIENT_NAMES, &[("STARTS_WITH", "Unknown [")]);
        }
        39 => each_section(config, SUB_RULES, |rule| {
            if let Some(url) = get(rule, "url")
                .and_then(Value::as_str)
                .filter(|url| url.contains("bcr.pbh-btn.ghorg.ghostchu-services.top"))
            {
                let replacement =
                    url.replace("bcr.pbh-btn.ghorg.ghostchu-services.top", "bcr.pbh-btn.com");
                set(rule, "url", replacement);
            }
        }),
        40 => append_rules(config, CLIENT_NAMES, &[("STARTS_WITH", "Gopeed bt-exp")]),
        42 => append_rules(config, CLIENT_NAMES, &[("STARTS_WITH", "Gopeed bt-")]),
        _ => {}
    }
    Ok(())
}

fn rules(config: &Value, path: &str) -> Vec<Value> {
    get(config, path)
        .and_then(Value::as_sequence)
        .cloned()
        .unwrap_or_default()
}

fn retain_rules(config: &mut Value, path: &str, retain: impl Fn(&str) -> bool) {
    let mut entries = rules(config, path);
    entries.retain(|rule| rule.as_str().is_none_or(&retain));
    set(config, path, Value::Sequence(entries));
}

fn append_rules(config: &mut Value, path: &str, new: &[(&str, &str)]) {
    let mut entries = rules(config, path);
    entries.extend(new.iter().map(|&(method, content)| {
        Value::from(
            serde_json::to_string(&LegacyRule {
                method,
                content: Some(content),
                ..LegacyRule::default()
            })
            .expect("a rule with string properties always serializes"),
        )
    }));
    set(config, path, Value::Sequence(entries));
}

#[derive(Default, Serialize)]
struct LegacyRule<'a> {
    method: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    content: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    min: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    max: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    hit: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    success: Option<&'a str>,
}

fn migrate_rule_list(config: &mut Value, banned: &str, excluded: &str) -> Result<()> {
    let excludes = rules(config, excluded);
    let includes = rules(config, banned);
    let mut converted = Vec::new();
    for (entries, negate) in [(excludes, true), (includes, false)] {
        for entry in entries {
            let Some(raw) = entry.as_str() else {
                continue;
            };
            let normalized = raw.to_lowercase();
            let Some((method, body)) = normalized.split_once('@') else {
                continue;
            };
            let mut rule = LegacyRule::default();
            match method {
                "contains" => rule.method = "CONTAINS",
                "startswith" => rule.method = "STARTS_WITH",
                "endswith" => rule.method = "ENDS_WITH",
                "equals" => rule.method = "EQUALS",
                "regex" => rule.method = "REGEX",
                "length" => rule.method = "LENGTH",
                _ => continue,
            }
            if method == "length" {
                let length = body
                    .parse::<i32>()
                    .context("invalid integer in legacy length rule")?;
                rule.min = Some(length);
                rule.max = Some(length);
            } else {
                rule.content = Some(body);
            }
            if negate {
                match method {
                    "equals" => rule.success = Some("FALSE"),
                    "regex" => rule.success = Some("NEGATIVE"),
                    _ => rule.hit = Some("FALSE"),
                }
            }
            converted.push(Value::from(serde_json::to_string(&rule)?));
        }
    }
    set(config, banned, Value::Sequence(converted));
    remove(config, excluded);
    Ok(())
}
