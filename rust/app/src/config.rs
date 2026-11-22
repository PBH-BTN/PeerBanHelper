//! Configuration keys come from the Java distribution's config.yml and profile.yml.

use std::collections::BTreeMap;
use std::fs;
use std::io::{ErrorKind, Write};
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use pbh_core::AddressPolicy;
use pbh_core::{
    AddressRule, IdleConfig, IpBlacklist, MultiDialConfig, PcbConfig, ProtectionMode, RangeBan,
    StringRules,
};
use pbh_network::{ProxyConfig, ProxyPolicy};
use serde::Deserialize;
use serde_yaml_ng::Value;

use crate::config_migrations::{self, MigrationReport};
use crate::geo_runtime::GeoConfig;
use crate::nat_status::{AutoStunConfig, StunServers};
use crate::peer_recording::RecordingPolicy;
use crate::session_recording::SessionPolicy;
use crate::subscriptions::SubscriptionPolicy;
use crate::traffic::TrafficPolicy;

#[derive(Deserialize, Default)]
#[serde(default)]
pub(crate) struct MainConfig {
    #[serde(rename = "config-version")]
    config_version: Option<u32>,
    pub(crate) proxy: ProxyConfig,
    pub(crate) client: BTreeMap<String, DownloaderConfig>,
    pub(crate) database: DatabaseConfig,
    pub(crate) persist: PersistenceConfig,
    pub(crate) server: ServerConfig,
    pub(crate) privacy: PrivacyConfig,
    pub(crate) logger: LoggerConfig,
    pub(crate) lookup: LookupConfig,
    pub(crate) resolvers: crate::dns::ResolverConfig,
    pub(crate) performance: PerformanceConfig,
    pub(crate) language: Option<String>,
    pub(crate) btn: pbh_btn::Config,
    #[serde(rename = "ip-database")]
    pub(crate) geoip: GeoConfig,
    #[serde(rename = "auto-stun")]
    pub(crate) auto_stun: AutoStunConfig,
    pub(crate) stun: StunServers,
    #[serde(rename = "ip-remapping")]
    address_remapping: AddressRemapping,
    #[serde(rename = "banlist-remapping")]
    banlist_remapping: BanlistRemapping,
    #[serde(rename = "installation-id")]
    pub(crate) installation_id: Option<String>,
}

#[derive(Deserialize, Default)]
#[serde(default)]
struct AddressRemapping {
    teredo: bool,
    nat64: Nat64Config,
}

#[derive(Deserialize)]
#[serde(default)]
struct Nat64Config {
    enabled: bool,
    prefix: Vec<String>,
}

impl Default for Nat64Config {
    fn default() -> Self {
        Self {
            enabled: true,
            prefix: vec!["64:ff9b::/96".into()],
        }
    }
}

#[derive(Deserialize)]
#[serde(default)]
struct BanlistRemapping {
    ipv4: PrefixConfig,
    ipv6: PrefixConfig,
}

impl Default for BanlistRemapping {
    fn default() -> Self {
        Self {
            ipv4: PrefixConfig {
                enabled: false,
                remap_range: Some(30),
            },
            ipv6: PrefixConfig {
                enabled: true,
                remap_range: Some(52),
            },
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "kebab-case")]
struct PrefixConfig {
    enabled: bool,
    remap_range: Option<u8>,
}

impl MainConfig {
    pub(crate) fn proxy_policy(&self) -> Result<ProxyPolicy> {
        let mut config = self.proxy.clone();
        if self.config_version.is_some_and(|version| version < 35) {
            config.setting = match config.setting {
                2 => 1,
                3 => 2,
                _ => 0,
            };
        }
        Ok(ProxyPolicy::new(&config)?)
    }

    pub(crate) fn downloader_proxy_policy(&self, data_dir: &Path) -> Result<ProxyPolicy> {
        let properties = fs::read_to_string(data_dir.join("flags.properties")).unwrap_or_default();
        let bypass = properties
            .lines()
            .filter_map(|line| line.split_once('='))
            .find_map(|(key, value)| {
                (key.trim() == "pbh.downloader.bypassproxy")
                    .then(|| value.trim().eq_ignore_ascii_case("true"))
            })
            .or_else(|| {
                std::env::var("PBH_DOWNLOADER_BYPASSPROXY")
                    .ok()
                    .map(|value| value.eq_ignore_ascii_case("true"))
            });
        if bypass.unwrap_or(true) {
            Ok(ProxyPolicy::default())
        } else {
            self.proxy_policy()
        }
    }

    pub(crate) fn address_policy(&self) -> Result<AddressPolicy> {
        let nat64 = &self.address_remapping.nat64;
        let nat64_prefixes = if nat64.enabled {
            nat64
                .prefix
                .iter()
                .filter_map(|prefix| match prefix.parse() {
                    Ok(prefix) => Some(prefix),
                    Err(_) => {
                        crate::journal::log_warning!("Ignoring invalid NAT64 prefix {prefix}");
                        None
                    }
                })
                .collect()
        } else {
            Vec::new()
        };
        let prefix = |settings: &PrefixConfig, maximum, default, family| -> Result<Option<u8>> {
            let prefix = settings.remap_range.unwrap_or(default);
            if settings.enabled && prefix > maximum {
                bail!("invalid {family} banlist remapping prefix");
            }
            Ok(settings.enabled.then_some(prefix))
        };
        Ok(AddressPolicy {
            nat64_prefixes,
            translate_teredo_peers: self.address_remapping.teredo,
            ipv4_ban_prefix: prefix(&self.banlist_remapping.ipv4, 32, 30, "IPv4")?,
            ipv6_ban_prefix: prefix(&self.banlist_remapping.ipv6, 128, 52, "IPv6")?,
        })
    }
}

#[derive(Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct ServerConfig {
    pub(crate) address: String,
    pub(crate) http: u16,
    pub(crate) token: String,
    pub(crate) prefix: String,
    pub(crate) allow_cors: bool,
}

impl ServerConfig {
    pub(crate) fn apply_environment(&mut self) -> Result<()> {
        if let Ok(address) = std::env::var("PBH_SERVERADDRESS") {
            self.address = address;
        }
        if let Ok(port) = std::env::var("PBH_PORT") {
            self.http = port.parse().context("PBH_PORT must be a valid port")?;
        }
        if let Ok(token) = std::env::var("PBH_APITOKEN")
            && !token.trim().is_empty()
        {
            self.token = token;
        }
        Ok(())
    }
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            address: "0.0.0.0".into(),
            http: 9898,
            token: String::new(),
            prefix: "http://127.0.0.1:9898".into(),
            allow_cors: false,
        }
    }
}

#[derive(Deserialize, Default)]
#[serde(default)]
pub(crate) struct PrivacyConfig {
    pub(crate) analytics: bool,
}

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct LoggerConfig {
    pub(crate) hide_finish_log: bool,
}

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct LookupConfig {
    pub(crate) dns_reverse_lookup: bool,
}

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct PerformanceConfig {
    pub(crate) windows_ecoqos_api: bool,
}

#[derive(Deserialize)]
pub(crate) struct DownloaderConfig {
    #[serde(rename = "type")]
    pub(crate) kind: String,
    #[serde(flatten)]
    pub(crate) settings: Value,
}

#[derive(Clone, Deserialize, PartialEq)]
#[serde(default)]
pub(crate) struct DatabaseConfig {
    #[serde(rename = "type")]
    pub(crate) kind: String,
    pub(crate) host: String,
    pub(crate) port: Option<u16>,
    pub(crate) username: String,
    pub(crate) password: String,
    pub(crate) database: String,
}

impl Default for DatabaseConfig {
    fn default() -> Self {
        Self {
            kind: "sqlite".into(),
            host: "localhost".into(),
            port: None,
            username: "peerbanhelper".into(),
            password: String::new(),
            database: "peerbanhelper".into(),
        }
    }
}

#[derive(Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct PersistenceConfig {
    pub(crate) banlist: bool,
    pub(crate) ban_logs_keep_days: i64,
}

impl Default for PersistenceConfig {
    fn default() -> Self {
        Self {
            banlist: true,
            ban_logs_keep_days: 0,
        }
    }
}

#[derive(Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct Profile {
    pub(crate) check_interval: u64,
    pub(crate) ban_duration: i64,
    ignore_peers_from_addresses: Vec<String>,
    module: BTreeMap<String, Value>,
}

impl Default for Profile {
    fn default() -> Self {
        Self {
            check_interval: 5_000,
            ban_duration: 1_209_600_000,
            ignore_peers_from_addresses: Vec::new(),
            module: BTreeMap::new(),
        }
    }
}

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "kebab-case")]
struct IpBlacklistConfig {
    enabled: bool,
    ban_duration: Value,
    ips: Vec<String>,
    ports: Vec<u16>,
}

pub(crate) struct Policies {
    pub(crate) ignored: Vec<AddressRule>,
    pub(crate) ip_blacklist: IpBlacklist,
    pub(crate) geo_blacklist: GeoBlacklist,
    pub(crate) ip_ban_duration_ms: i64,
    pub(crate) global_ban_duration_ms: i64,
    pub(crate) peer_id: StringPolicy,
    pub(crate) client_name: StringPolicy,
    pub(crate) xunlei_ban_duration_ms: Option<i64>,
    pub(crate) range_ban: Option<(RangeBan, i64)>,
    pub(crate) idle: Option<(IdleConfig, i64)>,
    pub(crate) multi_dial: Option<(MultiDialConfig, i64)>,
    pub(crate) progress: Option<ProgressPolicy>,
    pub(crate) btn_ban_duration: Option<i64>,
    pub(crate) subscriptions: Option<SubscriptionPolicy>,
    pub(crate) peer_recording: Option<RecordingPolicy>,
    pub(crate) swarm_flush_ms: Option<i64>,
    pub(crate) session_analysis: Option<SessionPolicy>,
    pub(crate) traffic: Option<TrafficPolicy>,
}

pub(crate) struct ProgressPolicy {
    pub(crate) config: PcbConfig,
    pub(crate) ipv4_prefix: u8,
    pub(crate) ipv6_prefix: u8,
    pub(crate) persist_ms: i64,
}

#[derive(Default)]
pub(crate) struct GeoBlacklist {
    pub(crate) asns: Vec<i64>,
    pub(crate) regions: Vec<String>,
    pub(crate) cities: Vec<String>,
    pub(crate) network_types: Vec<String>,
}

#[derive(Default)]
pub(crate) struct StringPolicy {
    pub(crate) rules: StringRules,
    pub(crate) ban_duration_ms: i64,
}

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "kebab-case")]
struct StringPolicyConfig {
    ban_duration: Value,
    banned_peer_id: Vec<String>,
    banned_client_name: Vec<String>,
}

impl Profile {
    pub(crate) fn module_enabled(&self, config_name: &str) -> bool {
        let mut segments = config_name.split('.');
        segments
            .next()
            .and_then(|first| self.module.get(first))
            .and_then(|module| segments.try_fold(module, |parent, segment| parent.get(segment)))
            .is_some_and(enabled)
    }

    pub(crate) fn policies(&self, apply: bool) -> Result<Policies> {
        if self.check_interval == 0 || self.ban_duration <= 0 {
            bail!("check-interval and global ban-duration must be positive in the preview");
        }
        if apply {
            let unsupported: Vec<_> = self
                .module
                .iter()
                .filter(|(name, value)| {
                    !matches!(
                        name.as_str(),
                        "ip-address-blocker"
                            | "peer-id-blacklist"
                            | "client-name-blacklist"
                            | "anti-vampire"
                            | "auto-range-ban"
                            | "idle-connection-dos-protection"
                            | "multi-dialing-blocker"
                            | "progress-cheat-blocker"
                            | "btn"
                            | "ip-address-blocker-rules"
                            | "expression-engine"
                            | "active-probing-removed"
                            | "active-monitoring"
                    ) && enabled(value)
                })
                .map(|(name, _)| name.as_str())
                .collect();
            if !unsupported.is_empty() {
                bail!(
                    "enabled modules outside the preview: {}",
                    unsupported.join(", ")
                );
            }
        }

        let mut ip_blacklist = IpBlacklist::default();
        let mut geo_blacklist = GeoBlacklist::default();
        let mut ip_ban_duration_ms = 0;
        if let Some(module) = self.module.get("ip-address-blocker") {
            let config: IpBlacklistConfig = serde_yaml_ng::from_value(module.clone())
                .context("invalid ip-address-blocker settings")?;
            if config.enabled {
                geo_blacklist = GeoBlacklist::from_settings(module);
                ip_blacklist.addresses =
                    parse_address_rules(&config.ips, "ip-address-blocker.ips")?;
                ip_blacklist.ports = config.ports.into_iter().collect();
                ip_ban_duration_ms = config.ban_duration.as_i64().unwrap_or(0);
            }
        }

        Ok(Policies {
            ignored: parse_address_rules(&self.ignore_peers_from_addresses, "ignored peers")?,
            ip_blacklist,
            geo_blacklist,
            ip_ban_duration_ms,
            global_ban_duration_ms: self.ban_duration,
            peer_id: self.string_policy("peer-id-blacklist", |config| config.banned_peer_id)?,
            client_name: self
                .string_policy("client-name-blacklist", |config| config.banned_client_name)?,
            xunlei_ban_duration_ms: self
                .module
                .get("anti-vampire")
                .filter(|value| enabled(value) && enabled(&value["presets"]["xunlei"]))
                .map(|value| value["ban-duration"].as_i64().unwrap_or(0)),
            range_ban: self.range_ban()?,
            idle: self.idle_policy(),
            multi_dial: self.multi_dial_policy()?,
            progress: self.progress_policy()?,
            btn_ban_duration: self
                .module
                .get("btn")
                .filter(|value| enabled(value))
                .map(|value| value["ban-duration"].as_i64().unwrap_or(0)),
            subscriptions: self
                .module
                .get("ip-address-blocker-rules")
                .filter(|value| enabled(value))
                .map(|value| serde_yaml_ng::from_value(value.clone()))
                .transpose()?,
            peer_recording: self
                .module
                .get("peer-analyse-service")
                .and_then(|value| value.get("peer-recording"))
                .filter(|value| enabled(value))
                .map(|value| serde_yaml_ng::from_value(value.clone()))
                .transpose()?,
            traffic: self
                .module
                .get("active-monitoring")
                .filter(|value| enabled(value))
                .map(|value| serde_yaml_ng::from_value(value.clone()))
                .transpose()?,
            swarm_flush_ms: self
                .module
                .get("peer-analyse-service")
                .and_then(|value| value.get("swarm-tracking"))
                .filter(|value| enabled(value))
                .map(|value| value["data-flush-interval"].as_i64().unwrap_or(3_600_000)),
            session_analysis: self
                .module
                .get("peer-analyse-service")
                .and_then(|value| value.get("session-analyse"))
                .filter(|value| enabled(value))
                .map(|value| serde_yaml_ng::from_value(value.clone()))
                .transpose()?,
        })
    }

    fn range_ban(&self) -> Result<Option<(RangeBan, i64)>> {
        let Some(value) = self
            .module
            .get("auto-range-ban")
            .filter(|value| enabled(value))
        else {
            return Ok(None);
        };
        let prefix = |key: &str| u8::try_from(value[key].as_i64().unwrap_or(0));
        let range = RangeBan::new(prefix("ipv4")?, prefix("ipv6")?)
            .context("invalid auto-range-ban prefix")?;
        let duration = value["ban-duration"].as_i64().unwrap_or(0);
        Ok(Some((range, duration)))
    }

    fn idle_policy(&self) -> Option<(IdleConfig, i64)> {
        let settings = self
            .module
            .get("idle-connection-dos-protection")
            .filter(|value| enabled(value))?;
        let ignore_interested =
            std::env::var("PBH_MODULE_IDLE_CONNECTION_DOS_PROTECTION_IGNORE_IF_ANY_INTERESTED")
                .map(|value| value.eq_ignore_ascii_case("true"))
                .unwrap_or(true);
        Some((
            IdleConfig {
                mode: ProtectionMode::from(settings["protect-mode"].as_i64().unwrap_or(0)),
                max_duration_ms: settings["max-allowed-idle-time"].as_i64().unwrap_or(0),
                speed_threshold: settings["idle-speed-threshold"].as_i64().unwrap_or(0),
                min_status_change_percentage: settings["min-status-change-percentage"]
                    .as_f64()
                    .unwrap_or(0.0),
                reset_on_status_change: settings["reset-on-status-change"]
                    .as_bool()
                    .unwrap_or(false),
                ignore_interested,
            },
            settings["ban-duration"].as_i64().unwrap_or(0),
        ))
    }

    fn multi_dial_policy(&self) -> Result<Option<(MultiDialConfig, i64)>> {
        let Some(settings) = self
            .module
            .get("multi-dialing-blocker")
            .filter(|value| enabled(value))
        else {
            return Ok(None);
        };
        let get = |key: &str| settings[key].as_i64().unwrap_or(0);
        let ipv4_prefix = u8::try_from(get("subnet-mask-length"))?;
        let ipv6_prefix = u8::try_from(get("subnet-mask-v6-length"))?;
        RangeBan::new(ipv4_prefix, ipv6_prefix).context("invalid multi-dialing prefix")?;
        Ok(Some((
            MultiDialConfig {
                ipv4_prefix,
                ipv6_prefix,
                ipv4_limit: get("tolerate-num-ipv4"),
                ipv6_limit: get("tolerate-num-ipv6"),
                lifespan_ms: get("cache-lifespan").saturating_mul(1000),
                hunting_ms: settings["keep-hunting"]
                    .as_bool()
                    .unwrap_or(false)
                    .then(|| get("keep-hunting-time").saturating_mul(1000)),
            },
            get("ban-duration"),
        )))
    }

    fn progress_policy(&self) -> Result<Option<ProgressPolicy>> {
        let Some(settings) = self
            .module
            .get("progress-cheat-blocker")
            .filter(|value| enabled(value))
        else {
            return Ok(None);
        };
        let int = |key: &str| settings[key].as_i64().unwrap_or(0);
        let ratio = |key: &str| settings[key].as_f64().unwrap_or(0.0);
        let ipv4_prefix = u8::try_from(int("ipv4-prefix-length"))?;
        let ipv6_prefix = u8::try_from(int("ipv6-prefix-length"))?;
        RangeBan::new(ipv4_prefix, ipv6_prefix).context("invalid progress-cheat-blocker prefix")?;
        let config = PcbConfig {
            minimum_size: int("minimum-size"),
            block_excessive_clients: settings["block-excessive-clients"]
                .as_bool()
                .unwrap_or(false),
            excessive_threshold: ratio("excessive-threshold"),
            maximum_difference: ratio("maximum-difference"),
            maximum_rewind: ratio("rewind-maximum-difference"),
            max_wait_ms: int("max-wait-duration"),
            fast_test_progress: ratio("fast-pcb-test-percentage"),
            fast_test_duration_ms: int("fast-pcb-test-block-duration"),
            ban_duration_ms: int("ban-duration"),
            disable_completed_excessive: std::env::var_os("PBH_PCB_DISABLE_COMPLETED_EXCESSIVE")
                .is_some(),
        };
        Ok(Some(ProgressPolicy {
            config,
            ipv4_prefix,
            ipv6_prefix,
            persist_ms: int("persist-duration"),
        }))
    }

    fn string_policy(
        &self,
        name: &str,
        list: impl FnOnce(StringPolicyConfig) -> Vec<String>,
    ) -> Result<StringPolicy> {
        let Some(settings) = self.module.get(name).filter(|value| enabled(value)) else {
            return Ok(StringPolicy::default());
        };
        let config: StringPolicyConfig = serde_yaml_ng::from_value(settings.clone())
            .with_context(|| format!("invalid {name} settings"))?;
        let ban_duration_ms = config.ban_duration.as_i64().unwrap_or(0);
        Ok(StringPolicy {
            rules: StringRules::parse(&list(config))
                .with_context(|| format!("invalid {name} rules"))?,
            ban_duration_ms,
        })
    }
}

fn enabled(value: &Value) -> bool {
    value
        .get("enabled")
        .and_then(Value::as_bool)
        .unwrap_or(false)
}

impl GeoBlacklist {
    fn from_settings(module: &Value) -> Self {
        let texts = |key: &str| {
            module[key]
                .as_sequence()
                .into_iter()
                .flatten()
                .filter_map(|value| {
                    value
                        .as_str()
                        .map(str::to_owned)
                        .or_else(|| value.as_i64().map(|number| number.to_string()))
                })
                .collect::<Vec<_>>()
        };
        let network_types = if let Some(mapping) = module["net-type"].as_mapping() {
            mapping
                .iter()
                .filter(|(_, value)| value.as_bool() == Some(true))
                .filter_map(|(key, _)| key.as_str().map(str::to_owned))
                .collect()
        } else {
            texts("net-type")
        };
        Self {
            asns: texts("asns")
                .into_iter()
                .filter_map(|asn| asn.parse().ok())
                .collect(),
            regions: texts("regions"),
            cities: texts("cities"),
            network_types: network_types
                .into_iter()
                .map(|name| network_type_key(&name))
                .collect(),
        }
    }
}

pub(crate) fn network_type_key(value: &str) -> String {
    value.replace('-', "").to_ascii_lowercase()
}

fn parse_address_rules(addresses: &[String], field: &str) -> Result<Vec<AddressRule>> {
    addresses
        .iter()
        .map(|address| address.parse().with_context(|| format!("invalid {field}")))
        .collect()
}

impl DownloaderConfig {
    pub(crate) fn decode<T: serde::de::DeserializeOwned>(&self, id: &str) -> Result<T> {
        serde_yaml_ng::from_value(self.settings.clone())
            .map_err(|_| anyhow::anyhow!("invalid {} settings for downloader {id}", self.kind))
    }
}

pub(crate) fn read_yaml<T: serde::de::DeserializeOwned>(path: &Path) -> Result<T> {
    let file = fs::File::open(path).with_context(|| format!("open {}", path.display()))?;
    // Do not print YAML error snippets. A configuration may contain credentials.
    serde_yaml_ng::from_reader(file).map_err(|error| match error.location() {
        Some(location) => anyhow::anyhow!(
            "invalid configuration in {} at line {}, column {}",
            path.display(),
            location.line(),
            location.column()
        ),
        None => anyhow::anyhow!("invalid configuration in {}", path.display()),
    })
}

pub(crate) fn default_data_dir() -> PathBuf {
    if std::env::var("PBH_USEPLATFORMCONFIGLOCATION")
        .is_ok_and(|value| value.eq_ignore_ascii_case("true"))
    {
        let platform_dir = if cfg!(windows) {
            std::env::var_os("LOCALAPPDATA").map(PathBuf::from)
        } else {
            let folder = if cfg!(target_os = "macos") {
                "Library/Application Support"
            } else {
                ".config"
            };
            std::env::var_os("HOME").map(|home| PathBuf::from(home).join(folder))
        };
        if let Some(platform_dir) = platform_dir {
            return platform_dir.join("PeerBanHelper");
        }
    }
    PathBuf::from("data")
}

pub(crate) fn config_dir(data_dir: &Path) -> PathBuf {
    std::env::var_os("PBH_CONFIGDIR")
        .filter(|value| !value.is_empty())
        .map_or_else(|| data_dir.join("config"), PathBuf::from)
}

pub(crate) fn read_main(path: &Path) -> Result<MainConfig> {
    let mut main: MainConfig = read_yaml(path)?;
    main.server.apply_environment()?;
    Ok(main)
}

pub(crate) fn upgrade_installed_configs(data_dir: &Path) -> Result<()> {
    let config = config_dir(data_dir);
    upgrade_file(
        &config.join("config.yml"),
        config_migrations::MAIN_VERSION,
        config_migrations::migrate_main,
    )?;
    upgrade_file(
        &config.join("profile.yml"),
        config_migrations::PROFILE_VERSION,
        config_migrations::migrate_profile,
    )?;
    Ok(())
}

fn upgrade_file(
    path: &Path,
    latest: i64,
    migrate: impl FnOnce(&mut Value) -> Result<MigrationReport>,
) -> Result<()> {
    let needs_upgrade = |yaml: &Value| {
        yaml.get("config-version")
            .and_then(Value::as_i64)
            .is_some_and(|version| {
                version < latest
                    || (latest == config_migrations::MAIN_VERSION
                        && version == latest
                        && yaml
                            .get("installation-id")
                            .and_then(Value::as_str)
                            .is_none_or(|id| id.trim().is_empty()))
            })
    };
    if !needs_upgrade(&read_yaml::<Value>(path)?) {
        return Ok(());
    }
    let report = pbh_web::config_file::edit(path, |mapping| -> Result<Option<MigrationReport>> {
        let mut yaml = Value::Mapping(mapping.clone());
        if !needs_upgrade(&yaml) {
            return Ok(None);
        }
        let report = migrate(&mut yaml)?;
        let Value::Mapping(upgraded) = yaml else {
            bail!("configuration upgrade requires a mapping");
        };
        if report.changed && report.before < report.after {
            backup_config(path, report.before)?;
        }
        if report.changed {
            *mapping = upgraded;
        }
        Ok(Some(report))
    })
    .map_err(|error| anyhow::anyhow!("upgrade {}: {error}", path.display()))?;
    if let Some(report) = report
        && report.before < report.after
    {
        crate::journal::info(format_args!(
            "Upgraded {} from version {} to {}",
            path.display(),
            report.before,
            report.after
        ));
    }
    Ok(())
}

fn backup_config(path: &Path, version: i64) -> Result<()> {
    let directory = path.parent().context("configuration path has no parent")?;
    let backup = path.with_extension(format!("before-rust-v{version}.yml"));
    if backup.exists() {
        return Ok(());
    }
    let contents = fs::read(path)?;
    let mut file = tempfile::NamedTempFile::new_in(directory)?;
    file.write_all(&contents)?;
    file.as_file().sync_all()?;
    match file.persist_noclobber(backup) {
        Ok(_) => Ok(()),
        Err(error) if error.error.kind() == ErrorKind::AlreadyExists => Ok(()),
        Err(error) => Err(error.error.into()),
    }
}
