//! Drive the BTN configuration and rule polling independently of downloader scans.

use std::collections::{BTreeMap, BTreeSet};
use std::future::Future;
use std::sync::{Arc, Mutex as StateLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::Result;
use pbh_btn::reports::LegacyPeer;
use pbh_btn::{
    BtnClient, Heartbeat, InterfaceHeartbeat, IpListCache, Negotiated, OnlineRules,
    PollingSchedule, Reconfigure, ReconfigureResult, Refresh, RuleSnapshot,
};
use pbh_storage::BanRecord;
use serde_json::{Value, json};
use tokio::sync::{Mutex, RwLock, mpsc, oneshot, watch};
use tokio::task::{JoinHandle, JoinSet};
use uuid::Uuid;

use crate::background_tasks::{BackgroundTasks, Task};
use crate::ban_api::BanLanguage;
use crate::btn_reports::{legacy_ban, send_ban_history, send_peer_history, send_swarm};
use crate::peer_recording::SwarmRecorder;
use crate::storage::Database;

const RECONNECT_INTERVAL: Duration = Duration::from_secs(600);
const CONFIGURATION_TASK: &str = "BTN_CONFIGURE_SYNC_SERVER";
const RECONFIGURE_TASK: &str = "BTN_ABILITY_RECONFIGURE_SYNC_SERVER";

#[derive(Clone, Copy, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) enum Ability {
    Allowlist,
    Denylist,
    Rules,
    Heartbeat,
    Reconfigure,
    IpQuery,
    BanHistory,
    PeerHistory,
    Swarm,
    LegacyPeers,
    LegacyBans,
}

impl Ability {
    fn task_title(self) -> Option<&'static str> {
        match self {
            Self::Allowlist => Some("BTN_ABILITY_ALLOW_LIST_SYNC_SERVER"),
            Self::Denylist => Some("BTN_ABILITY_DENY_LIST_SYNC_SERVER"),
            Self::Rules => Some("BTN_ABILITY_RULEES_SYNC_SERVER"),
            Self::Heartbeat => Some("BTN_ABILITY_HEARTBEAT_SYNC_SERVER"),
            Self::BanHistory => Some("BTN_ABILITY_SUBMIT_BANS_SYNC_SERVER"),
            Self::PeerHistory => Some("BTN_ABILITY_SUBMIT_HISTORY_SYNC_SERVER"),
            Self::Swarm => Some("BTN_ABILITY_SUBMIT_SWARM_SYNC_SERVER"),
            Self::Reconfigure | Self::IpQuery | Self::LegacyPeers | Self::LegacyBans => None,
        }
    }

    fn task_fails(self, error: &anyhow::Error) -> bool {
        match self {
            Self::BanHistory | Self::Swarm => true,
            // Java handles request failures inside the history task. Local persistence and
            // encoding failures escape the callback, as with the other modern reports.
            Self::PeerHistory => error
                .downcast_ref::<pbh_btn::Error>()
                .is_none_or(|error| matches!(error, pbh_btn::Error::ReportEncoding(_))),
            _ => false,
        }
    }
}

#[derive(Clone)]
pub(crate) enum ConfigResult {
    Connected { submit: bool },
    HttpError(u16),
    Incompatible { min: u32, max: u32, actual: u32 },
    Failed(String),
}

#[derive(Clone)]
pub(crate) struct AbilityResult {
    pub(crate) success: bool,
    pub(crate) message: Value,
    pub(crate) at: i64,
    heartbeat: Option<HeartbeatDetail>,
}

#[derive(Clone)]
enum HeartbeatDetail {
    Scheduled,
    Default(Option<String>),
    DefaultFailure(Option<u16>),
    Interfaces(Vec<String>),
}

impl AbilityResult {
    pub(crate) fn new(success: bool, key: &'static str, params: Value) -> Self {
        Self {
            success,
            message: json!({"key":key,"params":params}),
            at: now_ms().unwrap_or_default(),
            heartbeat: None,
        }
    }

    fn ready() -> Self {
        Self::new(true, "BTN_STAND_BY", Value::Null)
    }

    pub(crate) fn failure(ability: Ability, source: Option<&pbh_btn::Error>) -> Self {
        if matches!(ability, Ability::Heartbeat) {
            // Java marks the default-interface heartbeat attempt complete even on failure.
            let mut result = Self::new(true, "BTN_HEARTBEAT_FAILED", Value::Null);
            let status = match source {
                Some(pbh_btn::Error::Upstream { status, .. }) => Some(*status),
                _ => None,
            };
            result.heartbeat = Some(HeartbeatDetail::DefaultFailure(status));
            return result;
        }
        match source {
            Some(pbh_btn::Error::Upstream { status, .. }) => {
                Self::new(false, "BTN_HTTP_ERROR", json!([status, ""]))
            }
            Some(error) if matches!(ability, Ability::Reconfigure) => {
                Self::new(false, "BTN_HTTP_ERROR", json!([-1, error.to_string()]))
            }
            Some(error) => Self::new(false, "BTN_UNKNOWN_ERROR", json!([error.to_string()])),
            None => Self::new(false, "BTN_UNKNOWN_ERROR", json!(["BTN operation failed"])),
        }
    }

    pub(crate) fn heartbeat_description(&self, demo_mode: bool) -> String {
        match &self.heartbeat {
            Some(HeartbeatDetail::Scheduled) => "Scheduled".into(),
            Some(HeartbeatDetail::Default(Some(_))) if demo_mode => "default -> <REDACTED>".into(),
            Some(HeartbeatDetail::Default(Some(address))) => format!("default -> {address}"),
            Some(HeartbeatDetail::Default(None)) => "default -> No External IP returned".into(),
            Some(HeartbeatDetail::DefaultFailure(Some(status))) => {
                format!("default -> Failed: {status}")
            }
            Some(HeartbeatDetail::DefaultFailure(None)) => "default -> Failed".into(),
            Some(HeartbeatDetail::Interfaces(entries)) => {
                let mut text = String::new();
                for entry in entries {
                    text.push_str(if demo_mode { "<REDACTED>" } else { entry });
                    text.push_str("  \n");
                }
                text
            }
            None => "No information".into(),
        }
    }
}

async fn heartbeat_result(heartbeat: &Heartbeat) -> Result<AbilityResult> {
    if !heartbeat.multi_interface {
        let address = heartbeat.send_default().await?;
        let mut result = AbilityResult::new(true, "BTN_HEARTBEAT_SUCCESS", Value::Null);
        result.heartbeat = Some(HeartbeatDetail::Default(address));
        return Ok(result);
    }
    let attempts = match heartbeat.send_multi_interface().await {
        Ok(attempts) => attempts,
        Err(error) => {
            crate::journal::log_warning!("BTN heartbeat: {error}");
            Vec::new()
        }
    };
    Ok(multi_heartbeat_result(attempts))
}

fn multi_heartbeat_result(attempts: Vec<InterfaceHeartbeat>) -> AbilityResult {
    let mut last_by_address = BTreeMap::new();
    let mut success = false;
    for attempt in attempts {
        let summary = match attempt.reply {
            Ok(Some(address)) => {
                success = true;
                address
            }
            Ok(None) => "Failed: No external IP returned".into(),
            Err(pbh_btn::Error::Upstream { status, .. }) => format!("Failed: {status}"),
            Err(error) => format!("Failed: {error}"),
        };
        last_by_address.insert(attempt.address, summary);
    }
    let mut unique = BTreeSet::new();
    let entries = last_by_address
        .into_values()
        .filter(|summary| unique.insert(summary.clone()))
        .collect();
    let key = if success {
        "BTN_HEARTBEAT_SUCCESS"
    } else {
        "BTN_HEARTBEAT_FAILED"
    };
    let mut result = AbilityResult::new(success, key, Value::Null);
    result.heartbeat = Some(HeartbeatDetail::Interfaces(entries));
    result
}

#[cfg(test)]
#[test]
fn heartbeat_result_summarizes_unique_results_and_redacts_demo_addresses() {
    use pbh_btn::HeartbeatRoute;

    let make_attempt = |address: &str, reply, route| InterfaceHeartbeat {
        address: address.into(),
        route,
        reply,
    };
    let result = multi_heartbeat_result(vec![
        make_attempt(
            "127.0.0.1",
            Ok(Some("192.0.2.1".into())),
            HeartbeatRoute::Direct,
        ),
        make_attempt("127.0.0.1", Ok(None), HeartbeatRoute::ConfiguredProxy),
        make_attempt(
            "127.0.0.2",
            Ok(Some("192.0.2.1".into())),
            HeartbeatRoute::Direct,
        ),
        make_attempt(
            "127.0.0.3",
            Ok(Some("192.0.2.1".into())),
            HeartbeatRoute::Direct,
        ),
    ]);
    assert!(result.success);
    assert_eq!(
        result.heartbeat_description(false),
        "Failed: No external IP returned  \n192.0.2.1  \n"
    );
    assert_eq!(
        result.heartbeat_description(true),
        "<REDACTED>  \n<REDACTED>  \n"
    );
    let failed = multi_heartbeat_result(Vec::new());
    assert!(!failed.success);
    assert_eq!(failed.heartbeat_description(false), "");
}

#[cfg(test)]
#[tokio::test]
async fn negotiated_multi_heartbeat_enumerates_local_interfaces() {
    use axum::routing::{get, post};
    use axum::{Json, Router};
    use tokio::net::TcpListener;

    let addresses = Arc::new(StateLock::new(BTreeMap::<String, usize>::new()));
    let observed = addresses.clone();
    let router = Router::new()
        .route("/config", get(|| async {
            Json(json!({"min_protocol_version":20,"max_protocol_version":21,"ability":{
                "heartbeat":{"endpoint":"/heartbeat","interval":10000,"random_initial_delay":0,"multi_if":true}
            }}))
        }))
        .route("/heartbeat", post(move |Json(body): Json<Value>| {
            let address = body["ifaddr"].as_str().unwrap().to_owned();
            *observed.lock().unwrap().entry(address).or_default() += 1;
            async { Json(json!({"external_ip":"192.0.2.5"})) }
        }));
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let config = pbh_btn::Config {
        enabled: true,
        config_url: format!("http://{}/config", listener.local_addr().unwrap()),
        ..pbh_btn::Config::default()
    };
    let task = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
    let heartbeat = BtnClient::with_timeout(config, "fixture", Duration::from_secs(2))
        .unwrap()
        .negotiate()
        .await
        .unwrap()
        .heartbeat
        .unwrap();
    let result = heartbeat_result(&heartbeat).await.unwrap();
    assert!(result.success);
    assert!(
        result
            .heartbeat_description(false)
            .contains("192.0.2.5  \n")
    );
    let seen = addresses.lock().unwrap();
    assert_eq!(seen.get("127.0.0.1"), Some(&2));
    assert!(!seen.contains_key("default"));
    task.abort();
}

#[derive(Clone, Default)]
pub(crate) struct BtnStatus(Arc<StateLock<BtnStatusSnapshot>>);

#[derive(Clone, Default)]
pub(crate) struct BtnStatusSnapshot {
    pub(crate) config: Option<ConfigResult>,
    pub(crate) negotiated: Option<Arc<Negotiated>>,
    pub(crate) abilities: BTreeMap<Ability, AbilityResult>,
}

impl BtnStatus {
    pub(crate) fn snapshot(&self) -> BtnStatusSnapshot {
        self.0
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .clone()
    }

    pub(crate) fn negotiated(&self) -> Option<Arc<Negotiated>> {
        self.0
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .negotiated
            .clone()
    }

    pub(crate) fn negotiation_failed(&self, error: &pbh_btn::Error) {
        let result = match error {
            pbh_btn::Error::Upstream { status, .. } => ConfigResult::HttpError(*status),
            pbh_btn::Error::IncompatibleProtocol { min, max, actual } => {
                ConfigResult::Incompatible {
                    min: *min,
                    max: *max,
                    actual: *actual,
                }
            }
            error => ConfigResult::Failed(error.to_string()),
        };
        self.0
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .config = Some(result);
    }

    fn connected(&self, negotiated: Arc<Negotiated>) {
        let report = &negotiated.reports;
        let rules = negotiated.rule_snapshot();
        let abilities =
            [
                (Ability::Allowlist, negotiated.ip_allowlist.is_some()),
                (Ability::Denylist, negotiated.ip_denylist.is_some()),
                (Ability::Rules, negotiated.rules.is_some()),
                (Ability::Heartbeat, negotiated.heartbeat.is_some()),
                (Ability::Reconfigure, negotiated.reconfigure.is_some()),
                (Ability::IpQuery, negotiated.ip_query.is_some()),
                (Ability::BanHistory, report.ban_history.is_some()),
                (Ability::PeerHistory, report.peer_history.is_some()),
                (Ability::Swarm, report.swarm.is_some()),
                (Ability::LegacyPeers, report.legacy_peers.is_some()),
                (Ability::LegacyBans, report.legacy_bans.is_some()),
            ]
            .into_iter()
            .filter(|(_, present)| *present)
            .map(|(ability, _)| {
                let status =
                    match ability {
                        Ability::Allowlist => rules.ip_allowlist.as_ref().map_or_else(
                            AbilityResult::ready,
                            |snapshot| {
                                AbilityResult::new(
                                    true,
                                    "BTN_ABILITY_IP_ALLOWLIST_LOADED_FROM_CACHE",
                                    list_details(snapshot),
                                )
                            },
                        ),
                        Ability::Denylist => rules.ip_denylist.as_ref().map_or_else(
                            AbilityResult::ready,
                            |snapshot| {
                                AbilityResult::new(
                                    true,
                                    "BTN_ABILITY_IP_DENYLIST_LOADED_FROM_CACHE",
                                    list_details(snapshot),
                                )
                            },
                        ),
                        Ability::Rules if rules.rules.is_some() => {
                            AbilityResult::new(true, "BTN_RULES_LOADED_FROM_CACHE", Value::Null)
                        }
                        Ability::BanHistory
                        | Ability::PeerHistory
                        | Ability::Swarm
                        | Ability::LegacyPeers
                        | Ability::LegacyBans => {
                            AbilityResult::new(true, "BTN_NO_CONTENT_REPORTED_YET", Value::Null)
                        }
                        Ability::Heartbeat => {
                            let mut status = AbilityResult::ready();
                            status.heartbeat = Some(HeartbeatDetail::Scheduled);
                            status
                        }
                        _ => AbilityResult::ready(),
                    };
                (ability, status)
            })
            .collect();
        *self.0.lock().unwrap_or_else(|error| error.into_inner()) = BtnStatusSnapshot {
            config: Some(ConfigResult::Connected {
                submit: negotiated.submissions_allowed,
            }),
            negotiated: Some(negotiated),
            abilities,
        };
    }

    pub(crate) fn observe(&self, ability: Ability, result: AbilityResult) {
        if let Some(previous) = self
            .0
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .abilities
            .get_mut(&ability)
        {
            *previous = result;
        }
    }
}

pub(crate) fn list_details(snapshot: &pbh_btn::IpListSnapshot) -> Value {
    json!([snapshot.cache().revision.as_deref(), snapshot.len()])
}

pub(crate) struct BtnRuntime {
    state: watch::Receiver<Option<Arc<Negotiated>>>,
    task: JoinHandle<()>,
    legacy: Arc<LegacySnapshots>,
}

pub(crate) struct BtnResources {
    pub(crate) database: Option<Arc<Mutex<Database>>>,
    pub(crate) swarm: Option<Arc<Mutex<SwarmRecorder>>>,
    pub(crate) language: BanLanguage,
    pub(crate) background_tasks: BackgroundTasks,
}

struct LegacySnapshots {
    language: BanLanguage,
    peers: RwLock<Arc<Vec<LegacyPeer>>>,
    bans: RwLock<Arc<Vec<BanRecord>>>,
    ban_cursor: Mutex<Option<LegacyBanCursor>>,
}

struct LegacyBanCursor {
    cutoff_ms: i64,
    confirmed: BTreeSet<String>,
}

impl BtnRuntime {
    pub(crate) fn start(
        client: Option<BtnClient>,
        apply: bool,
        resources: BtnResources,
        sender: watch::Sender<Option<Arc<Negotiated>>>,
        status: BtnStatus,
    ) -> Option<Self> {
        let client = client?;
        let state = sender.subscribe();
        let legacy = Arc::new(LegacySnapshots {
            language: resources.language,
            peers: RwLock::default(),
            bans: RwLock::default(),
            ban_cursor: Mutex::default(),
        });
        let task = tokio::spawn(run(
            client,
            sender,
            apply,
            resources,
            legacy.clone(),
            status,
        ));
        Some(Self {
            state,
            task,
            legacy,
        })
    }

    pub(crate) fn snapshot(&self) -> RuleSnapshot {
        self.state
            .borrow()
            .as_ref()
            .map_or_else(RuleSnapshot::default, |state| state.rule_snapshot())
    }

    pub(crate) fn wants_legacy_peers(&self) -> bool {
        self.state
            .borrow()
            .as_ref()
            .is_some_and(|config| config.reports.legacy_peers.is_some())
    }

    pub(crate) fn wants_legacy_bans(&self) -> bool {
        self.state
            .borrow()
            .as_ref()
            .is_some_and(|config| config.reports.legacy_bans.is_some())
    }

    pub(crate) async fn replace_legacy_peers(&self, peers: Vec<LegacyPeer>) {
        *self.legacy.peers.write().await = Arc::new(peers);
    }

    pub(crate) async fn replace_legacy_bans(&self, bans: Vec<BanRecord>) {
        *self.legacy.bans.write().await = Arc::new(bans);
    }

    pub(crate) async fn shutdown(mut self) {
        self.task.abort();
        let _ = (&mut self.task).await;
    }
}

impl Drop for BtnRuntime {
    fn drop(&mut self) {
        self.task.abort();
    }
}

struct Reconfiguration {
    task: Task,
    finished: oneshot::Sender<()>,
}

fn complete_reconfiguration(pending: Option<Reconfiguration>, status: &BtnStatus) {
    if let Some(pending) = pending {
        status.observe(Ability::Reconfigure, AbilityResult::ready());
        pending.task.complete();
        let _ = pending.finished.send(());
    }
}

async fn check_reconfiguration(
    reconfigure: Reconfigure,
    reload: mpsc::Sender<Reconfiguration>,
    background_tasks: BackgroundTasks,
    status: BtnStatus,
) {
    let task = background_tasks.begin(json!({"key":RECONFIGURE_TASK}));
    match reconfigure.check().await {
        Ok(ReconfigureResult::Unchanged) => task.complete(),
        Ok(ReconfigureResult::DisabledByServer) => {
            status.observe(
                Ability::Reconfigure,
                AbilityResult::new(true, "BTN_RECONFIGURE_DISABLED_BY_SERVER", Value::Null),
            );
            task.complete();
        }
        Ok(ReconfigureResult::Changed { .. }) => {
            status.observe(
                Ability::Reconfigure,
                AbilityResult::new(true, "BTN_RECONFIGURE_PREPARE_RECONFIGURE", Value::Null),
            );
            let (finished, done) = oneshot::channel();
            // Keep the enclosing task alive while the coordinator negotiates and replaces polls.
            match reload.send(Reconfiguration { task, finished }).await {
                Ok(()) => {
                    let _ = done.await;
                }
                Err(error) => error.0.task.complete(),
            }
        }
        Err(error) => {
            status.observe(
                Ability::Reconfigure,
                AbilityResult::failure(Ability::Reconfigure, Some(&error)),
            );
            crate::journal::log_warning!("BTN reconfiguration: {error}");
            task.complete();
        }
    }
}

async fn run(
    client: BtnClient,
    state: watch::Sender<Option<Arc<Negotiated>>>,
    apply: bool,
    resources: BtnResources,
    legacy: Arc<LegacySnapshots>,
    status: BtnStatus,
) {
    let BtnResources {
        database,
        swarm,
        background_tasks,
        ..
    } = resources;
    let mut tasks = JoinSet::new();
    let (reload, mut reloading) = mpsc::channel(1);
    let mut pending = None;
    loop {
        let negotiation_ms = now_ms().unwrap_or_default();
        let configuration = background_tasks.begin(json!({"key":CONFIGURATION_TASK}));
        let negotiated = match client.negotiate().await {
            Ok(negotiated) => Arc::new(negotiated),
            Err(error) => {
                status.negotiation_failed(&error);
                crate::journal::log_warning!("BTN negotiation: {error}");
                configuration.complete();
                complete_reconfiguration(pending.take(), &status);
                tokio::select! {
                    _ = tokio::time::sleep(RECONNECT_INTERVAL) => {},
                    request = reloading.recv() => pending = request,
                    _ = state.closed() => return,
                }
                continue;
            }
        };
        tasks.shutdown().await;
        restore_caches(&negotiated, database.as_ref()).await;
        status.connected(negotiated.clone());
        state.send_replace(Some(negotiated.clone()));
        configuration.complete();
        complete_reconfiguration(pending.take(), &status);
        for (ability, list, updated, unchanged) in [
            (
                Ability::Allowlist,
                &negotiated.ip_allowlist,
                "BTN_ABILITY_IP_ALLOWLIST_LOADED_FROM_REMOTE",
                "BTN_ABILITY_IP_ALLOWLIST_LOADED_FROM_REMOTE_NO_CHANGES",
            ),
            (
                Ability::Denylist,
                &negotiated.ip_denylist,
                "BTN_ABILITY_IP_DENYLIST_LOADED_FROM_REMOTE",
                "BTN_ABILITY_IP_DENYLIST_LOADED_FROM_REMOTE_NO_CHANGES",
            ),
        ] {
            let Some(list) = list else { continue };
            let list = list.clone();
            let database = database.clone();
            poll(
                &mut tasks,
                list.schedule,
                &status,
                ability,
                &background_tasks,
                move || {
                    let list = list.clone();
                    let database = database.clone();
                    async move {
                        let refreshed = list.refresh().await?;
                        let snapshot = list.snapshot();
                        if refreshed == Refresh::Updated
                            && let Some(snapshot) = snapshot.as_ref()
                        {
                            let (version, body) = list.cache_keys();
                            persist(
                                database.as_ref(),
                                &[
                                    (version, snapshot.cache().revision.as_deref()),
                                    (body, Some(&snapshot.cache().body)),
                                ],
                            )
                            .await;
                        }
                        let key = if refreshed == Refresh::Updated {
                            updated
                        } else {
                            unchanged
                        };
                        let details = snapshot
                            .as_deref()
                            .map_or_else(|| json!(["initial", 0]), list_details);
                        Ok(Some(AbilityResult::new(true, key, details)))
                    }
                },
            );
        }
        if let Some(rules) = negotiated.rules.clone() {
            let database = database.clone();
            poll(
                &mut tasks,
                rules.schedule,
                &status,
                Ability::Rules,
                &background_tasks,
                move || {
                    let rules = rules.clone();
                    let database = database.clone();
                    async move {
                        let refreshed = rules.refresh().await?;
                        if let Some(snapshot) = rules.snapshot() {
                            if refreshed == Refresh::Updated {
                                persist(
                                    database.as_ref(),
                                    &[(OnlineRules::CACHE_KEY, Some(snapshot.cache_json()))],
                                )
                                .await;
                            }
                            return Ok(Some(AbilityResult::new(
                                true,
                                "BTN_RULES_LOADED_FROM_REMOTE",
                                json!([snapshot.version()]),
                            )));
                        }
                        Ok(None)
                    }
                },
            );
        }
        if apply && let Some(heartbeat) = negotiated.heartbeat.clone() {
            poll(
                &mut tasks,
                heartbeat.schedule,
                &status,
                Ability::Heartbeat,
                &background_tasks,
                move || {
                    let heartbeat = heartbeat.clone();
                    async move { heartbeat_result(&heartbeat).await.map(Some) }
                },
            );
        }
        if let Some(reconfigure) = negotiated.reconfigure.clone() {
            let reload = reload.clone();
            let task_registry = background_tasks.clone();
            let check_status = status.clone();
            poll_after(
                &mut tasks,
                reconfigure.schedule,
                reconfigure.schedule.interval,
                &status,
                Ability::Reconfigure,
                None,
                move || {
                    let reconfigure = reconfigure.clone();
                    let reload = reload.clone();
                    let task_registry = task_registry.clone();
                    let status = check_status.clone();
                    async move {
                        check_reconfiguration(reconfigure, reload, task_registry, status).await;
                        Ok(None)
                    }
                },
            );
        }
        if let Some(database) = &database
            && apply
        {
            if let Some(report) = negotiated.reports.ban_history.clone() {
                let database = database.clone();
                poll(
                    &mut tasks,
                    report.schedule,
                    &status,
                    Ability::BanHistory,
                    &background_tasks,
                    move || {
                        let database = database.clone();
                        let report = report.clone();
                        async move {
                            send_ban_history(&report, &database)
                                .await
                                .map(|count| Some(reported_data(count)))
                        }
                    },
                );
            }
            if let Some(report) = negotiated.reports.peer_history.clone() {
                let database = database.clone();
                poll(
                    &mut tasks,
                    report.schedule,
                    &status,
                    Ability::PeerHistory,
                    &background_tasks,
                    move || {
                        let database = database.clone();
                        let report = report.clone();
                        async move { send_peer_history(&report, &database).await.map(reported) }
                    },
                );
            }
            if let Some(report) = negotiated.reports.swarm.clone() {
                let database = database.clone();
                let swarm = swarm.clone();
                poll(
                    &mut tasks,
                    report.schedule,
                    &status,
                    Ability::Swarm,
                    &background_tasks,
                    move || {
                        let database = database.clone();
                        let report = report.clone();
                        let swarm = swarm.clone();
                        async move {
                            send_swarm(&report, &database, swarm.as_deref())
                                .await
                                .map(|count| Some(reported_data(count)))
                        }
                    },
                );
            }
        }
        if apply {
            start_legacy_reports(&mut tasks, &negotiated, &legacy, &status, negotiation_ms).await;
        }
        tokio::select! {
            request = reloading.recv() => pending = request,
            _ = state.closed() => return,
        }
    }
}

async fn start_legacy_reports(
    tasks: &mut JoinSet<()>,
    negotiated: &Negotiated,
    legacy: &Arc<LegacySnapshots>,
    status: &BtnStatus,
    negotiation_ms: i64,
) {
    if let Some(report) = negotiated.reports.legacy_peers.clone() {
        let legacy = legacy.clone();
        poll_after(
            tasks,
            report.schedule,
            report.schedule.interval,
            status,
            Ability::LegacyPeers,
            None,
            move || {
                let legacy = legacy.clone();
                let report = report.clone();
                async move {
                    let now = now_ms()?;
                    let peers = legacy.peers.read().await.clone();
                    let count = report.send(now, &peers).await?;
                    Ok(legacy_reported(count))
                }
            },
        );
    }
    if let Some(report) = negotiated.reports.legacy_bans.clone() {
        // Include events created in the same millisecond as negotiation started.
        legacy
            .ban_cursor
            .lock()
            .await
            .get_or_insert_with(|| LegacyBanCursor {
                cutoff_ms: negotiation_ms.saturating_sub(1),
                confirmed: BTreeSet::new(),
            });
        let legacy = legacy.clone();
        poll_after(
            tasks,
            report.schedule,
            report.schedule.interval,
            status,
            Ability::LegacyBans,
            None,
            move || {
                let legacy = legacy.clone();
                let report = report.clone();
                async move {
                    let now = now_ms()?;
                    let bans = legacy.bans.read().await.clone();
                    let bans = bans
                        .iter()
                        .filter_map(|ban| legacy_ban(ban, legacy.language))
                        .collect::<Vec<_>>();
                    let mut cursor = legacy.ban_cursor.lock().await;
                    let Some(cursor) = cursor.as_mut() else {
                        return Ok(None);
                    };
                    {
                        let active: BTreeSet<_> = bans
                            .iter()
                            .map(|event| event.ban.ban_unique_id.as_str())
                            .collect();
                        cursor.confirmed.retain(|id| active.contains(id.as_str()));
                    }
                    let pending: Vec<_> = bans
                        .into_iter()
                        .filter(|event| !cursor.confirmed.contains(&event.ban.ban_unique_id))
                        .collect();
                    let confirmed = report
                        .send_acknowledged(now, cursor.cutoff_ms, &pending)
                        .await?;
                    let count = confirmed.len();
                    cursor
                        .confirmed
                        .extend(confirmed.into_iter().map(str::to_owned));
                    Ok(legacy_reported(count))
                }
            },
        );
    }
}

fn reported(count: usize) -> Option<AbilityResult> {
    (count != 0).then(|| reported_data(count))
}

fn reported_data(count: usize) -> AbilityResult {
    AbilityResult::new(true, "BTN_REPORTED_DATA", json!([count]))
}

fn legacy_reported(count: usize) -> Option<AbilityResult> {
    reported(count).or_else(|| {
        Some(AbilityResult::new(
            true,
            "BTN_LAST_REPORT_EMPTY",
            Value::Null,
        ))
    })
}

fn now_ms() -> Result<i64> {
    Ok(i64::try_from(
        SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis(),
    )?)
}

async fn restore_caches(negotiated: &Negotiated, database: Option<&Arc<Mutex<Database>>>) {
    let Some(database) = database else {
        return;
    };
    let mut keys = vec![OnlineRules::CACHE_KEY];
    let lists = [&negotiated.ip_allowlist, &negotiated.ip_denylist];
    for list in lists.into_iter().flatten() {
        let (version, body) = list.cache_keys();
        keys.extend([version, body]);
    }
    let cache = match database.lock().await.read_metadata(&keys).await {
        Ok(cache) => cache,
        Err(error) => {
            crate::journal::log_warning!("Read BTN cache: {error}");
            return;
        }
    };
    for list in lists.into_iter().flatten() {
        let (version, body) = list.cache_keys();
        if let Some(body) = cache.get(body).and_then(Option::as_ref) {
            list.restore_cache(IpListCache {
                revision: cache.get(version).cloned().flatten(),
                body: body.clone(),
            });
        }
    }
    if let Some(rules) = &negotiated.rules
        && let Some(body) = cache.get(OnlineRules::CACHE_KEY).and_then(Option::as_ref)
        && let Err(error) = rules.restore_cache(body)
    {
        crate::journal::log_warning!("Restore BTN rules: {error}");
    }
}

async fn persist(database: Option<&Arc<Mutex<Database>>>, values: &[(&str, Option<&str>)]) {
    if let Some(database) = database
        && let Err(error) = database.lock().await.write_metadata(values).await
    {
        crate::journal::log_warning!("Persist BTN cache: {error}");
    }
}

fn poll<F, Work>(
    tasks: &mut JoinSet<()>,
    schedule: PollingSchedule,
    status: &BtnStatus,
    ability: Ability,
    background_tasks: &BackgroundTasks,
    work: F,
) where
    F: Fn() -> Work + Send + 'static,
    Work: Future<Output = Result<Option<AbilityResult>>> + Send + 'static,
{
    let background_task = ability
        .task_title()
        .map(|title| (background_tasks.clone(), title));
    poll_after(
        tasks,
        schedule,
        Duration::ZERO,
        status,
        ability,
        background_task,
        work,
    );
}

fn poll_after<F, Work>(
    tasks: &mut JoinSet<()>,
    schedule: PollingSchedule,
    delay: Duration,
    status: &BtnStatus,
    ability: Ability,
    background_task: Option<(BackgroundTasks, &'static str)>,
    work: F,
) where
    F: Fn() -> Work + Send + 'static,
    Work: Future<Output = Result<Option<AbilityResult>>> + Send + 'static,
{
    let status = status.clone();
    tasks.spawn(async move {
        let max_ms = schedule.random_initial_delay.as_millis();
        let initial_ms = Uuid::new_v4().as_u128() % max_ms.max(1);
        let jitter = Duration::from_millis(u64::try_from(initial_ms).unwrap_or(u64::MAX));
        tokio::time::sleep(delay.saturating_add(jitter)).await;
        loop {
            let task = background_task
                .as_ref()
                .map(|(tasks, title)| tasks.begin(json!({"key":title})));
            let result = work().await;
            let failed = result
                .as_ref()
                .err()
                .is_some_and(|error| ability.task_fails(error));
            match result {
                Ok(Some(result)) => status.observe(ability, result),
                Ok(None) => {}
                Err(error) => {
                    status.observe(
                        ability,
                        AbilityResult::failure(ability, error.downcast_ref()),
                    );
                    crate::journal::log_warning!("BTN polling: {error}");
                }
            }
            if let Some(task) = task {
                if failed {
                    task.fail(None);
                } else {
                    task.complete();
                }
            }
            tokio::time::sleep(schedule.interval).await;
        }
    });
}

#[cfg(test)]
#[tokio::test]
async fn legacy_reports_keep_new_bans_published_during_an_in_flight_ack() {
    use axum::http::StatusCode;
    use axum::routing::{get, post};
    use axum::{Json, Router};
    use std::sync::atomic::{AtomicUsize, Ordering};
    use tokio::net::TcpListener;
    use tokio::sync::Notify;

    let (reported, mut events) = mpsc::unbounded_channel();
    let release_first = Arc::new(Notify::new());
    let reported_count = Arc::new(AtomicUsize::new(0));
    let router = Router::new()
        .route(
            "/config",
            get(|| async {
                Json(
                    json!({"min_protocol_version":19,"max_protocol_version":21,"ability":{
                        "submit_bans":{"endpoint":"/report","interval":25,"random_initial_delay":0}
                    }}),
                )
            }),
        )
        .route(
            "/report",
            post({
                let release_first = release_first.clone();
                move || {
                    let report = reported_count.fetch_add(1, Ordering::Relaxed);
                    let _ = reported.send(report);
                    let release_first = release_first.clone();
                    async move {
                        if report == 0 {
                            release_first.notified().await;
                        }
                        StatusCode::NO_CONTENT
                    }
                }
            }),
        );
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let config = pbh_btn::Config {
        enabled: true,
        config_url: format!("http://{}/config", listener.local_addr().unwrap()),
        ..pbh_btn::Config::default()
    };
    let server = tokio::spawn(async move { axum::serve(listener, router).await.unwrap() });
    let client = BtnClient::new(config, "fixture").unwrap();
    let (sender, mut negotiated) = watch::channel(None);
    let status = BtnStatus::default();
    let runtime = BtnRuntime::start(
        Some(client),
        true,
        BtnResources {
            database: None,
            swarm: None,
            language: BanLanguage::En,
            background_tasks: BackgroundTasks::default(),
        },
        sender,
        status.clone(),
    )
    .unwrap();
    tokio::time::timeout(Duration::from_secs(3), negotiated.wait_for(Option::is_some))
        .await
        .unwrap()
        .unwrap();
    let at = now_ms().unwrap();
    tokio::time::timeout(Duration::from_secs(3), async {
        loop {
            if status
                .snapshot()
                .abilities
                .get(&Ability::LegacyBans)
                .is_some_and(|result| result.message["key"] == "BTN_LAST_REPORT_EMPTY")
            {
                break;
            }
            tokio::time::sleep(Duration::from_millis(5)).await;
        }
    })
    .await
    .unwrap();

    let ban = |ip: &str, id: &str| BanRecord {
        address: ip.parse().unwrap(),
        metadata: json!({"banAt":at,"unbanAt":at+100000,"randomId":id,
            "torrent":{"hash":"8888888888888888888888888888888888888888"},
            "peer":{"address":{"ip":ip,"port":3000}}}),
    };
    let first = ban("192.0.2.6", "fixture-a");
    let second = ban("192.0.2.7", "fixture-b");
    runtime.replace_legacy_bans(vec![first.clone()]).await;
    assert_eq!(
        tokio::time::timeout(Duration::from_secs(3), events.recv())
            .await
            .unwrap(),
        Some(0)
    );
    runtime.replace_legacy_bans(vec![first, second]).await;
    release_first.notify_one();
    assert_eq!(
        tokio::time::timeout(Duration::from_secs(3), events.recv())
            .await
            .unwrap(),
        Some(1)
    );
    assert!(
        tokio::time::timeout(Duration::from_millis(180), events.recv())
            .await
            .is_err()
    );
    runtime.shutdown().await;
    server.abort();
}

#[cfg(test)]
mod task_tests {
    use super::*;
    use axum::Json;
    use axum::Router;
    use axum::http::StatusCode;
    use axum::response::IntoResponse;
    use axum::routing::{get, post};
    use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
    use tokio::net::TcpListener;
    use tokio::sync::Notify;

    struct TaskStream {
        response: reqwest::Response,
        buffer: Vec<u8>,
    }

    impl TaskStream {
        async fn open(base: &str) -> Self {
            let response = reqwest::Client::builder()
                .no_proxy()
                .build()
                .unwrap()
                .get(format!("{base}/api/tasks/live"))
                .header("accept-language", "en-US")
                .send()
                .await
                .unwrap()
                .error_for_status()
                .unwrap();
            Self {
                response,
                buffer: Vec::new(),
            }
        }

        async fn next_matching(&mut self, predicate: impl Fn(&Value) -> bool) -> Value {
            tokio::time::timeout(Duration::from_secs(5), async {
                loop {
                    if let Some(end) = self.buffer.windows(2).position(|bytes| bytes == b"\n\n") {
                        let frame: Vec<_> = self.buffer.drain(..end + 2).collect();
                        if let Some(data) = frame.strip_prefix(b"data:") {
                            let event: Value = serde_json::from_slice(data).unwrap();
                            let task = &event["task"];
                            if predicate(task) {
                                return task.clone();
                            }
                        }
                    } else {
                        let chunk = self
                            .response
                            .chunk()
                            .await
                            .unwrap()
                            .expect("BTN task stream remains open");
                        self.buffer.extend_from_slice(&chunk);
                    }
                }
            })
            .await
            .expect("expected BTN task event")
        }
    }

    #[tokio::test]
    async fn reconfiguration_task_waits_for_nested_configuration() {
        let entered = Arc::new(Notify::new());
        let release = Arc::new(Notify::new());
        let calls = Arc::new(AtomicUsize::new(0));
        let tasks = BackgroundTasks::default();
        let fixture = Router::new()
            .merge(crate::background_tasks::router(tasks.clone()))
            .route("/config", get({
                let entered = entered.clone();
                let release = release.clone();
                let calls = calls.clone();
                move || {
                    let call = calls.fetch_add(1, Ordering::Relaxed);
                    let entered = entered.clone();
                    let release = release.clone();
                    async move {
                        if call == 1 { return StatusCode::SERVICE_UNAVAILABLE.into_response(); }
                        if call == 3 {
                            entered.notify_one();
                            release.notified().await;
                        }
                        let version = if call == 0 {"1"} else {"2"};
                        Json(json!({"min_protocol_version":20,"max_protocol_version":21,"ability":{
                            "reconfigure":{"version":version,"interval":80,"random_initial_delay":0},
                            "heartbeat":{"endpoint":"/heart","interval":10000,"random_initial_delay":0,"multi_if":false}
                        }})).into_response()
                    }
                }
            }))
            .route("/heart", post(|| async { StatusCode::BAD_GATEWAY }));
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let base = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move { axum::serve(listener, fixture).await.unwrap() });
        let client = BtnClient::new(
            pbh_btn::Config {
                enabled: true,
                config_url: format!("{base}/config"),
                ..pbh_btn::Config::default()
            },
            "fixture",
        )
        .unwrap();
        let (sender, _) = watch::channel(None);
        let status = BtnStatus::default();
        let runtime = BtnRuntime::start(
            Some(client),
            true,
            BtnResources {
                database: None,
                swarm: None,
                language: BanLanguage::En,
                background_tasks: tasks,
            },
            sender,
            status.clone(),
        )
        .unwrap();
        tokio::time::timeout(Duration::from_secs(5), entered.notified())
            .await
            .unwrap();
        assert_eq!(
            status.snapshot().abilities[&Ability::Reconfigure].message["key"],
            "BTN_RECONFIGURE_PREPARE_RECONFIGURE"
        );

        let mut events = TaskStream::open(&base).await;
        let mut running = BTreeMap::new();
        let mut completed = BTreeSet::new();
        while running.len() < 2 || completed.len() < 3 {
            let task = events.next_matching(|_| true).await;
            let title = task["title"].as_str().unwrap().to_owned();
            if task["status"] == "RUNNING" {
                running.insert(title, task["id"].clone());
            } else if task["status"] == "COMPLETED" {
                completed.insert(title);
            }
        }
        let config_title = "[BTN Network] Fetch configuration and initialize";
        let reconfigure_title = "[BTN Network] Check remote configuration version";
        assert!(completed.contains(config_title));
        assert!(completed.contains(reconfigure_title));
        assert!(completed.contains("[BTN Network] Sync heartbeat data"));
        assert_eq!(
            status.snapshot().abilities[&Ability::Heartbeat].message["key"],
            "BTN_HEARTBEAT_FAILED"
        );
        release.notify_one();
        let inner_id = &running[config_title];
        let outer_id = &running[reconfigure_title];
        let first = events
            .next_matching(|task| task["id"] == *inner_id || task["id"] == *outer_id)
            .await;
        assert_eq!(first["id"], *inner_id);
        assert_eq!(first["status"], "COMPLETED");
        let second = events.next_matching(|task| task["id"] == *outer_id).await;
        assert_eq!(second["status"], "COMPLETED");
        runtime.shutdown().await;
        server.abort();
    }

    async fn database_for_reports() -> Database {
        use pbh_storage::{
            BanHistoryEntry, HistoryTorrent, PeerRecordObservation, PeerRecordTorrent, SqliteStore,
            TrackedSwarmObservation,
        };

        let mut database = Database::Sqlite(SqliteStore::open(":memory:").unwrap());
        let now = now_ms().unwrap();
        let address = "192.0.2.1".parse().unwrap();
        let info_hash = "abcdef0123456789abcdef0123456789abcdef01";
        database
            .commit_ban_wave(
                None,
                &[BanHistoryEntry {
                    address,
                    ban_at_ms: now,
                    unban_at_ms: now + 30000,
                    port: 1234,
                    peer_id: None,
                    peer_client_name: None,
                    peer_uploaded: None,
                    peer_downloaded: None,
                    peer_progress: 0.0,
                    torrent: HistoryTorrent {
                        info_hash,
                        name: "fixture",
                        size: 100,
                        private: None,
                        progress: 1.0,
                    },
                    module_name: "fixture",
                    rule: &Value::Null,
                    description: &Value::Null,
                    flags: None,
                    downloader: "fixture",
                    structured_data: None,
                    peer_geoip: None,
                }],
            )
            .await
            .unwrap();
        database
            .upsert_tracked_swarm(&[TrackedSwarmObservation {
                ip: address,
                port: 1234,
                info_hash,
                torrent_is_private: false,
                torrent_size: 100,
                downloader: "fixture",
                downloader_progress: 1.0,
                peer_id: None,
                client_name: None,
                peer_progress: 0.0,
                uploaded: 0,
                upload_speed: 0,
                downloaded: 0,
                download_speed: 0,
                last_flags: None,
                seen_at_ms: now,
            }])
            .await
            .unwrap();
        database
            .upsert_peer_records(&[PeerRecordObservation {
                address,
                port: 1234,
                downloader_id: "fixture",
                peer_id: None,
                client_name: None,
                torrent: PeerRecordTorrent {
                    info_hash,
                    name: "fixture",
                    size: 100,
                    private: None,
                },
                uploaded: 0,
                downloaded: 0,
                upload_speed: 0,
                download_speed: 0,
                last_flags: None,
                observed_at_ms: now,
                peer_geoip: None,
            }])
            .await
            .unwrap();
        database
            .write_metadata(&[(
                pbh_btn::reports::PEER_HISTORY_CURSOR_KEY,
                Some(&(now - 1000).to_string()),
            )])
            .await
            .unwrap();
        database
    }

    #[tokio::test]
    async fn report_task_statuses_follow_the_modern_and_history_error_boundaries() {
        let reject = Arc::new(AtomicBool::new(true));
        let report = {
            let reject = reject.clone();
            move || {
                let reject = reject.load(Ordering::Relaxed);
                async move {
                    if reject {
                        StatusCode::SERVICE_UNAVAILABLE
                    } else {
                        StatusCode::NO_CONTENT
                    }
                }
            }
        };
        let tasks = BackgroundTasks::default();
        let fixture = Router::new()
            .merge(crate::background_tasks::router(tasks.clone()))
            .route("/config", get(|| async {
                Json(json!({"min_protocol_version":20,"max_protocol_version":21,"ability":{
                    "submit_bans":{"endpoint":"/bans","interval":80,"random_initial_delay":0},
                    "submit_swarm":{"endpoint":"/swarm","interval":80,"random_initial_delay":0},
                    "submit_histories":{"endpoint":"/histories","interval":80,"random_initial_delay":0}
                }}))
            }))
            .route("/bans", post(report.clone()))
            .route("/swarm", post(report.clone()))
            .route("/histories", post(report));
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let base = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move { axum::serve(listener, fixture).await.unwrap() });
        let client = BtnClient::new(
            pbh_btn::Config {
                enabled: true,
                submit: true,
                config_url: format!("{base}/config"),
                ..pbh_btn::Config::default()
            },
            "fixture",
        )
        .unwrap();
        let (sender, _) = watch::channel(None);
        let status = BtnStatus::default();
        let runtime = BtnRuntime::start(
            Some(client),
            true,
            BtnResources {
                database: Some(Arc::new(Mutex::new(database_for_reports().await))),
                swarm: None,
                language: BanLanguage::En,
                background_tasks: tasks,
            },
            sender,
            status.clone(),
        )
        .unwrap();

        let mut events = TaskStream::open(&base).await;
        let expected = [
            ("[BTN Network] Submit local ban records", "FAILED"),
            ("[BTN Network] Submit swarm records", "FAILED"),
            ("[BTN Network] Submit history records", "COMPLETED"),
        ];
        let mut seen = BTreeSet::new();
        while seen.len() < expected.len() {
            let task = events
                .next_matching(|task| {
                    expected
                        .iter()
                        .any(|(title, status)| task["title"] == *title && task["status"] == *status)
                })
                .await;
            assert!(task["statusText"].is_null());
            seen.insert(task["title"].as_str().unwrap().to_owned());
        }
        let current = status.snapshot();
        for ability in [Ability::BanHistory, Ability::Swarm, Ability::PeerHistory] {
            assert!(!current.abilities[&ability].success);
        }
        for ability in [Ability::IpQuery, Ability::LegacyBans, Ability::LegacyPeers] {
            assert!(ability.task_title().is_none());
        }
        reject.store(false, Ordering::Relaxed);
        let mut recovered = BTreeSet::new();
        while recovered.len() < expected.len() {
            let task = events
                .next_matching(|task| {
                    task["status"] == "COMPLETED"
                        && expected.iter().any(|(title, _)| task["title"] == *title)
                })
                .await;
            recovered.insert(task["title"].as_str().unwrap().to_owned());
        }
        runtime.shutdown().await;
        server.abort();
    }
}
