//! Enabled module names and configuration keys exposed by the Java API.

use std::sync::Arc;

use pbh_web::Module;

use crate::config::Profile;

const PACKAGE: &str = "com.ghostchu.peerbanhelper.module.impl";

#[derive(Clone)]
pub(crate) struct Modules(Arc<[Entry]>);

#[derive(Clone, Copy)]
enum Availability {
    Setup,
    Scan,
    Profile,
}

#[derive(Clone, Copy)]
struct Entry {
    group: &'static str,
    class: &'static str,
    config: &'static str,
    name: &'static str,
    availability: Availability,
}

impl Entry {
    fn class_name(self) -> String {
        format!("{PACKAGE}.{}.{}", self.group, self.class)
    }
}

impl Modules {
    pub(crate) fn new(profile: Option<&Profile>) -> Self {
        Self(
            ENTRIES
                .iter()
                .copied()
                .filter(|entry| match entry.availability {
                    Availability::Setup => true,
                    Availability::Scan => profile.is_some(),
                    Availability::Profile => {
                        profile.is_some_and(|profile| profile.module_enabled(entry.config))
                    }
                })
                .collect(),
        )
    }

    pub(crate) fn manifest(&self) -> Vec<Module> {
        self.0
            .iter()
            .map(|entry| Module {
                class_name: entry.class_name(),
                config_name: entry.config.into(),
            })
            .collect()
    }

    pub(crate) fn contains(&self, query: &str) -> bool {
        self.0.iter().any(|entry| {
            [entry.class, entry.config, entry.name]
                .iter()
                .any(|candidate| candidate.eq_ignore_ascii_case(query))
                || entry.class_name().eq_ignore_ascii_case(query)
        })
    }
}

const fn entry(
    group: &'static str,
    class: &'static str,
    config: &'static str,
    name: &'static str,
    availability: Availability,
) -> Entry {
    Entry {
        group,
        class,
        config,
        name,
        availability,
    }
}

const fn controller(class: &'static str, config: &'static str, name: &'static str) -> Entry {
    entry("webapi", class, config, name, Availability::Scan)
}

const fn rule(class: &'static str, config: &'static str, name: &'static str) -> Entry {
    entry("rule", class, config, name, Availability::Profile)
}

const fn monitor(class: &'static str, config: &'static str, name: &'static str) -> Entry {
    entry("monitor", class, config, name, Availability::Profile)
}

const fn setup(class: &'static str, config: &'static str, name: &'static str) -> Entry {
    entry("webapi", class, config, name, Availability::Setup)
}

// Keep registration order from PeerBanHelper.registerModules().
const ENTRIES: &[Entry] = &[
    controller("PBHGeneralController", "webapi-general", "WebAPI - General"),
    rule("IPBlackList", "ip-address-blocker", "IP Blacklist"),
    rule("PeerIdBlacklist", "peer-id-blacklist", "PeerId Blacklist"),
    rule(
        "ClientNameBlacklist",
        "client-name-blacklist",
        "ClientName Blacklist",
    ),
    rule(
        "ProgressCheatBlocker",
        "progress-cheat-blocker",
        "Progress Cheat Blocker",
    ),
    rule(
        "MultiDialingBlocker",
        "multi-dialing-blocker",
        "Multi Dialing Blocker",
    ),
    rule("AutoRangeBan", "auto-range-ban", "Auto Range Ban"),
    rule("BtnNetworkOnline", "btn", "BTN Network Online Rules"),
    controller(
        "BlockListController",
        "webapi-downloader-cidr-blocklist",
        "WebAPI - Downloader CIDR Blocklist",
    ),
    rule(
        "IPBlackRuleList",
        "ip-address-blocker-rules",
        "IP Blacklist Rule List",
    ),
    controller(
        "PBHBackgroundTaskController",
        "webapi-background-tasks",
        "WebAPI - Background Tasks",
    ),
    controller("PBHMetricsController", "webapi-metrics", "WebAPI - Metrics"),
    controller(
        "PBHBanController",
        "webapi-downloader",
        "WebAPI - PBH Ban API",
    ),
    setup(
        "PBHMetadataController",
        "webapi-metadata",
        "WebAPI - Metadata",
    ),
    controller(
        "PBHDownloaderController",
        "webapi-downloader",
        "WebAPI - Downloader API",
    ),
    controller(
        "RuleSubController",
        "webapi-rule-subscription",
        "WebAPI - Rule Subscription",
    ),
    setup(
        "PBHAuthenticateController",
        "webapi-authenticate",
        "WebAPI - Authenticate",
    ),
    monitor(
        "ActiveMonitoringModule",
        "active-monitoring",
        "Active Monitoring",
    ),
    setup(
        "PBHPlusController",
        "webapi-pbh-plus",
        "WebAPI - PBH Plus Interface",
    ),
    setup(
        "PBHOOBEController",
        "webapi-oobe",
        "WebAPI - OOBE Interface",
    ),
    controller("PBHChartController", "webapi-charts", "WebAPI - Charts"),
    controller(
        "PBHTorrentController",
        "torrent-controller",
        "Torrent Controller",
    ),
    controller("PBHPeerController", "peer-controller", "Peer Controller"),
    controller("PBHAlertController", "webapi-alerts", "WebAPI - Alerts"),
    controller("PBHLogsController", "webapi-logs", "WebAPI - Logs"),
    controller("PBHPushController", "webapi-push", "WebAPI - Push"),
    controller(
        "PBHLabController",
        "webapi-laboratory",
        "WebAPI - Laboratory",
    ),
    controller(
        "PBHEasterEggController",
        "webapi-easteregg",
        "WebAPI - Access from :9898/api/egg",
    ),
    controller(
        "PBHUtilitiesController",
        "webapi-utilities",
        "WebAPI - Utilities",
    ),
    monitor(
        "SwarmTrackingModule",
        "peer-analyse-service.swarm-tracking",
        "Swarm Tracking Module",
    ),
    controller(
        "PBHAutoStunController",
        "webeapi-autostun",
        "WebAPI - AutoSTUN",
    ),
    rule(
        "IdleConnectionDosProtection",
        "idle-connection-dos-protection",
        "Idle Connection DoS Protection",
    ),
    monitor(
        "SessionAnalyseServiceModule",
        "peer-analyse-service.session-analyse",
        "Session Analyse Service",
    ),
    monitor(
        "PeerRecordingServiceModule",
        "peer-analyse-service.peer-recording",
        "Peer Recording Service",
    ),
    rule("AntiVampire", "anti-vampire", "Anti Vampire"),
    controller("PBHBtnController", "webapi-btn", "[WebAPI] BTN Network"),
];
