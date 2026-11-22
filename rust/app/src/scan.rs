use std::collections::{BTreeMap, BTreeSet};
use std::fs::{self, OpenOptions};
use std::net::{IpAddr, SocketAddr};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{Context, Result, bail};
use pbh_core::{Action, AddressPolicy, normalize_ip};
use pbh_geoip::GeoIp;
use pbh_storage::{
    AlertDeduplication, AlertLevel, BanHistoryEntry, BanRecord, HistoryTorrent, NewAlert,
};
use pbh_traversal::ConnectionMappings;
use serde::Serialize;
use serde_json::{Value, json};
use tokio::sync::{Mutex, RwLock, mpsc, watch};
use uuid::Uuid;

use crate::auto_tunnel::AutoTunnels;
use crate::ban_api::{BanLanguage, ManualBans};
use crate::btn_reports::{java_utc_datetime, legacy_peer};
use crate::btn_runtime::{BtnResources, BtnRuntime, BtnStatus};
use crate::config::{MainConfig, Policies, Profile, read_yaml};
use crate::dashboard::{Counters, Dashboard, LiveDownloader, peer_json, torrent_json};
use crate::downloader::{AddedBan, Downloader, Peer, Torrent};
use crate::geo_runtime::GeoRuntime;
use crate::nat_status::AutoStunControl;
use crate::pcb::{PcbSample, PcbWave};
use crate::peer_recording::{Recorder, SwarmBatch, SwarmRecorder};
use crate::policy::{
    RuleHit, RuleRuntime, btn_hit, idle_hit, multi_dial_hit, select_rule, subscription_hit,
};
use crate::session_recording::SessionRecorder;
use crate::storage::Database;
use crate::subscriptions::{Feed, Subscriptions};
use crate::traffic::{self, Notifications, TrafficMonitor};
use crate::web::{self, ScanState};

const DAY_MS: i64 = 86_400_000;
const ALERT_RETENTION_MS: i64 = 30 * DAY_MS;

#[derive(Serialize)]
struct Match {
    downloader: String,
    torrent: String,
    address: IpAddr,
    port: u16,
    duration_ms: i64,
    rule: Value,
    reason: Value,
}

#[derive(Default, Serialize)]
struct Report {
    torrents: usize,
    peers: usize,
    ignored: usize,
    matches: Vec<Match>,
    expired: Vec<IpAddr>,
    allowlisted: Vec<IpAddr>,
    failures: Vec<String>,
}

struct PendingBan {
    record: BanRecord,
    downloader: String,
    torrent: Torrent,
    peer: Peer,
    start_ms: i64,
    end_ms: i64,
    matched: RuleHit,
    geoip: Option<Value>,
}

struct BanWaveState {
    database: Arc<Mutex<Database>>,
    persist_bans: bool,
    active: BTreeMap<IpAddr, BanRecord>,
    web: Option<ScanState>,
    tunnel_bans: Arc<std::sync::RwLock<BTreeSet<IpAddr>>>,
    tunnels: AutoTunnels,
}

struct ScanRound<'a> {
    policies: &'a Policies,
    previous_bans: &'a [(IpAddr, bool)],
    btn: pbh_btn::RuleSnapshot,
    subscriptions: Vec<Arc<Feed>>,
    rules: &'a mut RuleRuntime,
    pcb: &'a mut PcbWave,
    database: Option<&'a Arc<Mutex<Database>>>,
    now: i64,
    live: Option<&'a mut BTreeMap<String, LiveDownloader>>,
    recorder: Option<&'a mut Recorder>,
    swarm: Option<&'a mut SwarmBatch>,
    geoip: Option<&'a GeoIp>,
    legacy_peers: Option<&'a mut Vec<pbh_btn::reports::LegacyPeer>>,
    sessions: Option<&'a mut SessionRecorder>,
    day_start: i64,
    mappings: &'a ConnectionMappings,
    address_policy: &'a AddressPolicy,
}

impl PendingBan {
    fn history(&self) -> BanHistoryEntry<'_> {
        BanHistoryEntry {
            address: self.record.address,
            ban_at_ms: self.start_ms,
            unban_at_ms: self.end_ms,
            port: self.peer.port,
            peer_id: self.peer.peer_id.as_deref(),
            peer_client_name: self.peer.client_name.as_deref(),
            peer_uploaded: Some(self.peer.uploaded),
            peer_downloaded: Some(self.peer.downloaded),
            peer_progress: self.peer.progress,
            torrent: HistoryTorrent {
                info_hash: &self.torrent.hash,
                name: &self.torrent.name,
                size: self.torrent.size,
                private: Some(self.torrent.private),
                progress: self.torrent.progress,
            },
            module_name: self.matched.module,
            rule: &self.matched.rule,
            description: &self.matched.reason,
            flags: self.peer.flags.as_deref(),
            downloader: &self.downloader,
            structured_data: Some(&self.matched.details),
            peer_geoip: self.geoip.as_ref(),
        }
    }
}

struct RunOptions<'a> {
    data_dir: &'a Path,
    once: bool,
    apply: bool,
    webui_dir: Option<PathBuf>,
    tray: Option<&'a pbh_desktop::Tray>,
    listener: Option<web::BoundServer>,
}

struct ScanControl {
    pause: watch::Sender<bool>,
    manual_bans: mpsc::Sender<ManualBans>,
    manual_commands: mpsc::Receiver<ManualBans>,
    stop: tokio::task::JoinHandle<std::io::Result<()>>,
    eco_qos: bool,
    counters: Arc<RwLock<Counters>>,
}

pub(crate) async fn run(
    data_dir: &Path,
    once: bool,
    apply: bool,
    webui_dir: Option<PathBuf>,
    tray: Option<&pbh_desktop::Tray>,
    mut listener: Option<web::BoundServer>,
) -> Result<()> {
    let (pause, _) = watch::channel(false);
    let (manual_bans, manual_commands) = mpsc::channel(32);
    let config_path = crate::config::config_dir(data_dir).join("config.yml");
    if listener.is_none() && webui_dir.is_some() {
        listener = Some(web::BoundServer::bind(&web::configured_server(&config_path)?).await?);
    }
    let _writer_lock = if apply {
        let persist = data_dir.join("persist");
        fs::create_dir_all(&persist).context("create persist directory")?;
        let lock = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .open(persist.join("pbh-rust-writer.lock"))?;
        lock.try_lock()
            .context("lock PeerBanHelper Rust data directory for writing")?;
        crate::journal::open_file(data_dir);
        crate::config::upgrade_installed_configs(data_dir)?;
        Some(lock)
    } else {
        None
    };
    let boot = crate::config::read_main(&config_path)?;
    if listener.is_none()
        && apply
        && boot
            .client
            .values()
            .any(|client| client.kind == "transmission")
    {
        listener = Some(web::BoundServer::bind(&boot.server).await?);
    }
    let options = RunOptions {
        data_dir,
        once,
        apply,
        webui_dir,
        tray,
        listener,
    };
    let mut control = ScanControl {
        pause,
        manual_bans,
        manual_commands,
        stop: tokio::spawn(crate::shutdown::signal()),
        eco_qos: false,
        counters: Arc::default(),
    };
    let result = async {
        let mut previous_bans = None;
        loop {
            let next = run_epoch(&options, &boot, &mut control, previous_bans).await?;
            let Some(bans) = next else {
                return Ok(());
            };
            previous_bans = Some(bans);
        }
    }
    .await;
    control.stop.abort();
    result
}

async fn run_epoch(
    options: &RunOptions<'_>,
    boot: &MainConfig,
    control: &mut ScanControl,
    previous_bans: Option<BTreeMap<IpAddr, BanRecord>>,
) -> Result<Option<BTreeMap<IpAddr, BanRecord>>> {
    let RunOptions {
        data_dir,
        once,
        apply,
        tray,
        ref webui_dir,
        ref listener,
    } = *options;
    let config_dir = crate::config::config_dir(data_dir);
    let mut config = crate::config::read_main(&config_dir.join("config.yml"))?;
    config.database.clone_from(&boot.database);
    config.server.address.clone_from(&boot.server.address);
    config.server.http = boot.server.http;
    configure_eco_qos(
        &mut control.eco_qos,
        apply && !once && config.performance.windows_ecoqos_api,
    );
    let profile: Profile = read_yaml(&config_dir.join("profile.yml"))?;
    let policies = profile.policies(apply)?;
    let address_policy = config.address_policy()?;
    let proxy = config.proxy_policy()?;
    let reachability = crate::reachability::Monitor::start(&proxy, apply && !once)?;
    let dns = crate::dns::Resolver::new(&config.resolvers, &proxy)?;
    let downloader_proxy = config.downloader_proxy_policy(data_dir)?;
    let (reload, mut requested_reload) = watch::channel(false);
    let (close_http, closing_http) = watch::channel(false);

    let mut downloaders = Vec::new();
    for (id, downloader) in &config.client {
        let proxy = if downloader.kind == "transmission" {
            &proxy
        } else {
            &downloader_proxy
        };
        if let Some(downloader) = Downloader::configure_with_proxy(
            downloader,
            id,
            Duration::from_millis(profile.check_interval),
            &config.server.prefix,
            apply,
            &address_policy,
            proxy,
        )? {
            downloaders.push(downloader);
        }
    }

    let mut store = if apply {
        Some(Database::open(&config.database, &data_dir.join("persist")).await?)
    } else {
        None
    };

    let language = config.language.as_deref().unwrap_or("default");
    let background_tasks = crate::background_tasks::BackgroundTasks::new(language);
    let geoip = GeoRuntime::start(
        data_dir,
        language,
        &config.geoip,
        apply && !once,
        &background_tasks,
        &proxy,
    );
    let restarting = previous_bans.is_some();
    let mut active = previous_bans.unwrap_or_default();
    if !restarting
        && let Some(store) = &mut store
        && config.persist.banlist
    {
        for ban in store.load_bans().await? {
            ban_end(&ban)?;
            active.insert(ban.address, ban);
        }
    }
    let mut web_server = None;
    let (btn_updates, btn_negotiated) = watch::channel(None);
    let btn_status = BtnStatus::default();
    let mut paused = control.pause.subscribe();
    let mappings = ConnectionMappings::default();
    let tunnel_bans = Arc::new(std::sync::RwLock::new(active.keys().copied().collect()));
    let mut tunnels = AutoTunnels::start(
        config
            .auto_stun
            .tunnel_options(&config.stun, apply && !once),
        &downloaders,
        mappings.clone(),
        tunnel_bans.clone(),
    );
    let auto_stun = AutoStunControl::new(config.auto_stun.clone(), tunnels.clone());
    let mut tunnel_settings = auto_stun.settings();
    let database = store.map(|database| Arc::new(Mutex::new(database)));
    let subscriptions = policies
        .subscriptions
        .as_ref()
        .map(|policy| {
            Subscriptions::start(
                policy,
                data_dir,
                database.clone(),
                background_tasks.clone(),
                &proxy,
            )
        })
        .transpose()?;
    let mut state = if let Some(database) = database {
        let swarm_enabled = policies.swarm_flush_ms.is_some();
        if swarm_enabled && !restarting {
            database.lock().await.reset_tracked_swarm().await?;
        }
        let initial_list = active.keys().copied().collect::<Vec<_>>();
        let web = if let Some(bound) = listener {
            let scan = ScanState {
                modules: crate::modules::Modules::new(Some(&profile)),
                reachability: reachability.snapshot(),
                background_tasks: background_tasks.clone(),
                reload,
                closing_http: closing_http.clone(),
                bans: Arc::new(RwLock::new(active.values().cloned().collect())),
                database: database.clone(),
                paused: control.pause.clone(),
                subscriptions: subscriptions.clone(),
                btn_negotiated,
                btn_status: btn_status.clone(),
                clients: downloaders
                    .iter()
                    .map(|downloader| (downloader.id.clone(), downloader.client.clone()))
                    .collect(),
                dashboard: Dashboard::new(&config, dns, control.counters.clone()),
                address_policy: address_policy.clone(),
                geoip: geoip.clone(),
                swarm_enabled,
                auto_stun: auto_stun.clone(),
                manual_bans: control.manual_bans.clone(),
                can_add_manual_bans: !downloaders.is_empty(),
                demo_mode: std::env::var("PBH_DEMOMODE")
                    .is_ok_and(|value| value.eq_ignore_ascii_case("true")),
                downloader_info: Arc::new(
                    config
                        .client
                        .iter()
                        .map(|(id, downloader)| {
                            let name = downloader
                                .settings
                                .get("name")
                                .and_then(serde_yaml_ng::Value::as_str)
                                .unwrap_or(id);
                            let kind = if downloader.kind == "deluge" {
                                "raccoonfink/deluge"
                            } else {
                                downloader.kind.as_str()
                            };
                            (id.clone(), json!({"id": id, "name": name, "type": kind}))
                        })
                        .collect(),
                ),
            };
            let listener = bound.listener()?;
            let app = web::prepare(
                bound,
                &config,
                data_dir,
                webui_dir.clone().unwrap_or_else(|| "webui/dist".into()),
                Some(scan.clone()),
                None,
                tray,
            )?;
            web_server = Some(tokio::spawn(pbh_web::serve(listener, app, async move {
                let mut closing = closing_http;
                let _ = closing.wait_for(|closing| *closing).await;
            })));
            Some(scan)
        } else {
            None
        };
        for downloader in &mut downloaders {
            let result = sync_downloader(downloader, &initial_list, &[], &[], true).await;
            downloader.needs_full_list = result.is_err();
            if let Err(error) = result {
                crate::journal::log_warning!(
                    "{}: initial ban-list reconciliation: {error:#}",
                    downloader.id
                );
            }
        }
        Some(BanWaveState {
            database,
            persist_bans: config.persist.banlist,
            active,
            web,
            tunnel_bans: tunnel_bans.clone(),
            tunnels: tunnels.clone(),
        })
    } else {
        None
    };

    let mut rules = RuleRuntime::default();
    let mut pcb = PcbWave::default();
    let mut recorder = policies
        .peer_recording
        .as_ref()
        .filter(|_| apply)
        .map(|_| Recorder::default());
    let swarm = (policies.swarm_flush_ms.is_some() && state.is_some())
        .then(|| Arc::new(Mutex::new(SwarmRecorder::default())));
    let mut sessions = policies
        .session_analysis
        .as_ref()
        .filter(|_| apply)
        .map(|_| SessionRecorder::default());
    let mut traffic_monitor = None;
    if let (Some(policy), Some(state)) = (&policies.traffic, &state) {
        let notifications = Notifications {
            config_path: config_dir.join("config.yml"),
            language: BanLanguage::from_locale(config.language.as_deref().unwrap_or_default()),
            proxy: proxy.clone(),
        };
        if once {
            traffic::sample_once(&downloaders, &state.database, policy, &notifications).await;
        } else {
            traffic_monitor = Some(TrafficMonitor::start(
                &downloaders,
                state.database.clone(),
                policy.clone(),
                notifications,
            ));
        }
    }
    let btn_client = config
        .btn
        .enabled
        .then(|| {
            crate::btn_api::create_client(
                config.btn.clone(),
                config.installation_id.as_deref(),
                &proxy,
            )
        })
        .and_then(|client| match client {
            Ok(client) => Some(client),
            Err(error) => {
                btn_status.negotiation_failed(&error);
                crate::journal::log_warning!("BTN configuration: {error}");
                None
            }
        });
    let btn = BtnRuntime::start(
        btn_client,
        apply,
        BtnResources {
            database: state.as_ref().map(|state| state.database.clone()),
            swarm: swarm.clone(),
            language: BanLanguage::from_locale(language),
            background_tasks: background_tasks.clone(),
        },
        btn_updates,
        btn_status,
    );
    let mut next_cleanup = 0;
    let mut restart_tunnels = false;
    let mut reloading = false;
    loop {
        if std::mem::take(&mut restart_tunnels) || tunnel_settings.has_changed().unwrap_or(false) {
            let settings = tunnel_settings.borrow_and_update().clone();
            tunnels.shutdown().await;
            tunnels = AutoTunnels::start(
                settings.tunnel_options(&config.stun, apply && !once),
                &downloaders,
                mappings.clone(),
                tunnel_bans.clone(),
            );
            auto_stun.replace_tunnels(tunnels.clone());
            if let Some(state) = &mut state {
                state.tunnels = tunnels.clone();
            }
        }
        if *paused.borrow() {
            tokio::select! {
                _ = paused.changed() => continue,
                result = tunnel_settings.changed() => {
                    restart_tunnels = result.is_ok();
                    continue;
                },
                result = &mut control.stop => {
                    result??;
                    break;
                },
                Ok(()) = requested_reload.changed() => {
                    reloading = true;
                    break;
                },
                result = wait_for_http_server(web_server.as_mut()) => {
                    tunnels.shutdown().await;
                    return result.map(|()| None);
                },
            }
        }
        let mut report = Report::default();
        let now = i64::try_from(SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis())?;
        if let Some(state) = &state
            && now >= next_cleanup
        {
            let mut database = state.database.lock().await;
            if let Err(error) = database
                .cleanup_alerts(now.saturating_sub(ALERT_RETENTION_MS))
                .await
            {
                crate::journal::log_warning!("Alert history cleanup: {error:#}");
            }
            let days = config.persist.ban_logs_keep_days;
            if days > 0
                && let Err(error) = database
                    .cleanup_ban_history(now.saturating_sub(days.saturating_mul(DAY_MS)))
                    .await
            {
                crate::journal::log_warning!("Ban history cleanup: {error:#}");
            }
            next_cleanup = now.saturating_add(DAY_MS);
        }
        let btn_snapshot = btn
            .as_ref()
            .map_or_else(pbh_btn::RuleSnapshot::default, BtnRuntime::snapshot);
        let mut previous_bans = Vec::new();
        if policies.range_ban.is_some()
            && let Some(state) = &state
        {
            for ban in state.active.values() {
                if now <= ban_end(ban)? && !btn_allows(&btn_snapshot, ban.address) {
                    previous_bans.push((
                        ban.address,
                        ban.metadata["banForDisconnect"].as_bool().unwrap_or(false),
                    ));
                }
            }
        }
        if let Some(policy) = &policies.progress {
            pcb.prepare(policy, state.as_ref().map(|state| &state.database), now)
                .await?;
            if let Some(state) = &state {
                remove_expired_pcb_entries(state, now, &btn_snapshot).await?;
            }
        }
        let mut pending = Vec::new();
        let mut swarm_batch = swarm.as_ref().map(|_| SwarmBatch::new());
        let mut legacy_peers = btn
            .as_ref()
            .filter(|btn| apply && btn.wants_legacy_peers())
            .map(|_| Vec::new());
        let mut live = state
            .as_ref()
            .and_then(|state| state.web.as_ref())
            .map(|_| BTreeMap::new());
        let geography = geoip.current();
        let mut round = ScanRound {
            policies: &policies,
            previous_bans: &previous_bans,
            btn: btn_snapshot.clone(),
            subscriptions: match &subscriptions {
                Some(subscriptions) => subscriptions.snapshot().await,
                None => Vec::new(),
            },
            rules: &mut rules,
            pcb: &mut pcb,
            database: state.as_ref().map(|state| &state.database),
            now,
            live: live.as_mut(),
            recorder: recorder.as_mut(),
            swarm: swarm_batch.as_mut(),
            geoip: geography.as_deref(),
            legacy_peers: legacy_peers.as_mut(),
            sessions: sessions.as_mut(),
            day_start: crate::dashboard::start_of_day(
                time::OffsetDateTime::from_unix_timestamp_nanos(i128::from(now) * 1_000_000)?,
            ),
            mappings: &mappings,
            address_policy: &address_policy,
        };
        for downloader in &mut downloaders {
            if let Err(error) =
                collect_matches(downloader, &mut round, &mut report, &mut pending).await
            {
                downloader.needs_full_list = true;
                report
                    .failures
                    .push(format!("{}: {error:#}", downloader.id));
            }
        }

        if let Some(state) = &mut state {
            pcb.flush(&state.database).await?;
            if let (Some(recorder), Some(policy)) = (&mut recorder, &policies.peer_recording) {
                recorder
                    .maintain(policy, &state.database, now, once)
                    .await?;
            }
            if let (Some(sessions), Some(policy)) = (&mut sessions, &policies.session_analysis)
                && let Err(error) = sessions.maintain(policy, &state.database, now, once).await
            {
                crate::journal::log_warning!("Session analysis maintenance: {error:#}");
            }
            if let (Some(swarm), Some(batch), Some(interval)) =
                (&swarm, swarm_batch, policies.swarm_flush_ms)
            {
                let mut swarm = swarm.lock().await;
                swarm.append(batch);
                swarm.maintain(interval, &state.database, now, once).await?;
            }
            let commands = std::iter::from_fn(|| control.manual_commands.try_recv().ok())
                .take(32)
                .collect();
            let manual = manual_ban_changes(commands, &downloaders, &mut pending, now);
            apply_wave(
                state,
                &mut downloaders,
                &pending,
                now,
                &btn_snapshot,
                &mut report,
                manual,
            )
            .await?;
            if let Some(btn) = &btn {
                if let Some(peers) = legacy_peers {
                    btn.replace_legacy_peers(peers).await;
                }
                if btn.wants_legacy_bans() {
                    btn.replace_legacy_bans(state.active.values().cloned().collect())
                        .await;
                }
            }
            if let Some(web) = &state.web
                && let Some(mut live) = live
            {
                for failure in &report.failures {
                    if let Some((id, message)) = failure.split_once(": ")
                        && web.downloader_info.contains_key(id)
                    {
                        live.entry(id.to_owned())
                            .or_default()
                            .fail(message.to_owned());
                    }
                }
                *web.dashboard.live.write().await = live;
            }
            if let Some(tray) = tray {
                let failed = report
                    .failures
                    .iter()
                    .filter_map(|error| error.split_once(": ").map(|(id, _)| id))
                    .collect::<BTreeSet<_>>();
                let banned_ips = state.active.len() as u64;
                tray.set_status(Some(pbh_desktop::TrayStatus {
                    banned_peers: banned_ips,
                    banned_ips,
                    healthy_downloaders: downloaders.len().saturating_sub(failed.len()) as u64,
                    total_downloaders: config.client.len() as u64,
                }));
            }
        }

        if apply && !once && !config.logger.hide_finish_log && !downloaders.is_empty() {
            crate::journal::info(format_args!(
                "Scan checked {} torrents and {} peers on {} downloaders",
                report.torrents,
                report.peers,
                downloaders.len()
            ));
        }
        println!("{}", serde_json::to_string(&report)?);
        if once {
            if !report.failures.is_empty() {
                bail!("scan completed with downloader failures");
            }
            return Ok(None);
        }
        tokio::select! {
            _ = tokio::time::sleep(Duration::from_millis(profile.check_interval)) => {},
            _ = paused.changed() => {},
            result = tunnel_settings.changed() => restart_tunnels = result.is_ok(),
            result = &mut control.stop => {
                result??;
                break;
            },
            Ok(()) = requested_reload.changed() => {
                reloading = true;
                break;
            },
            result = wait_for_http_server(web_server.as_mut()) => {
                tunnels.shutdown().await;
                return result.map(|()| None);
            },
        }
    }
    background_tasks.shutdown();
    close_http.send_replace(true);
    if let Some(mut server) = web_server
        && tokio::time::timeout(Duration::from_secs(3), &mut server)
            .await
            .is_err()
    {
        server.abort();
        let _ = server.await;
    }
    if let Some(btn) = btn {
        btn.shutdown().await;
    }
    if let Some(subscriptions) = subscriptions {
        subscriptions.shutdown().await;
    }
    tunnels.shutdown().await;
    if let Some(monitor) = traffic_monitor {
        monitor.stop().await?;
    }
    let now = i64::try_from(SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis())?;
    if let (Some(recorder), Some(state)) = (&mut recorder, &state) {
        recorder.flush(&state.database, now).await?;
    }
    if let (Some(swarm), Some(state)) = (&swarm, &state) {
        swarm.lock().await.flush(&state.database, now).await?;
    }
    if let (Some(sessions), Some(policy), Some(state)) =
        (&mut sessions, &policies.session_analysis, &state)
    {
        sessions
            .maintain(policy, &state.database, now, true)
            .await?;
    }
    Ok(reloading.then(|| state.map_or_else(BTreeMap::new, |state| state.active)))
}

fn configure_eco_qos(current: &mut bool, enabled: bool) {
    let mut target = enabled && cfg!(windows);
    if std::env::var("PBH_PLATFORM_ECOQOS_API")
        .is_ok_and(|value| !value.eq_ignore_ascii_case("true"))
    {
        target = false;
    }
    if *current == target {
        return;
    }
    #[cfg(windows)]
    if let Err(error) = pbh_desktop::process::set_eco_qos(target) {
        crate::journal::log_warning!("Windows EcoQoS: {error}");
        return;
    }
    *current = target;
}

async fn wait_for_http_server(
    server: Option<&mut tokio::task::JoinHandle<std::io::Result<()>>>,
) -> Result<()> {
    if let Some(server) = server {
        return Ok(server.await??);
    }
    std::future::pending().await
}

async fn collect_matches(
    downloader: &Downloader,
    round: &mut ScanRound<'_>,
    report: &mut Report,
    pending: &mut Vec<PendingBan>,
) -> Result<()> {
    let now = round.now;
    let policies = round.policies;
    let mut snapshot = downloader.client.snapshot().await?;
    for peer in snapshot.torrents.iter_mut().flat_map(|(_, peers)| peers) {
        let raw_ip = peer.raw_address.parse().unwrap_or(peer.ip);
        let source = SocketAddr::new(raw_ip, peer.port);
        if let Some(translated) = round.mappings.translate(source) {
            peer.forwarded_from = Some(source);
            (peer.ip, peer.port) = round
                .address_policy
                .translate_peer(translated.ip(), translated.port());
        }
    }
    if let Some(live) = round.live.as_deref_mut() {
        live.insert(
            downloader.id.clone(),
            LiveDownloader::from_snapshot(&snapshot, round.geoip),
        );
    }
    report.torrents += snapshot.torrents.len();
    report.failures.extend(
        snapshot
            .failures
            .into_iter()
            .map(|error| format!("{}: {error}", downloader.id)),
    );
    for (torrent, peers) in snapshot.torrents {
        if policies.idle.is_some() {
            round.rules.idle.observe_batch(
                now,
                peers.iter().map(|peer| SocketAddr::new(peer.ip, peer.port)),
            );
        }
        report.peers += peers.len();
        for peer in peers {
            if let (Some(sessions), Some(database)) =
                (round.sessions.as_deref_mut(), round.database)
                && let Err(error) = sessions
                    .observe(downloader, &torrent, &peer, round.day_start, database)
                    .await
            {
                crate::journal::log_warning!("Record peer session: {error:#}");
            }
            if let Some(snapshot) = round.legacy_peers.as_deref_mut() {
                snapshot.push(legacy_peer(&torrent, &peer));
            }
            let geography = round.geoip.map(|lookup| lookup.lookup(peer.ip));
            if let Some(recorder) = round.recorder.as_deref_mut() {
                recorder.observe(downloader, &torrent, &peer, now, geography.as_ref());
            }
            if let Some(swarm) = round.swarm.as_deref_mut() {
                SwarmRecorder::capture(swarm, downloader, &torrent, &peer, now);
            }
            if policies.ignored.iter().any(|rule| rule.contains(peer.ip)) {
                report.ignored += 1;
                continue;
            }
            let progress = if let Some(policy) = &policies.progress {
                let sample = PcbSample {
                    downloader,
                    torrent: &torrent,
                    peer: &peer,
                    now,
                };
                match round.pcb.evaluate(policy, sample, round.database).await {
                    Ok(hit) => hit,
                    Err(error) => {
                        report.failures.push(format!(
                            "{}: progress-cheat-blocker: {error}",
                            downloader.id
                        ));
                        None
                    }
                }
            } else {
                None
            };
            let hits = [
                idle_hit(policies, &mut round.rules.idle, &torrent, &peer, now),
                multi_dial_hit(policies, &mut round.rules.multi_dial, &torrent, &peer, now),
                progress,
                btn_hit(policies, &round.btn, &peer, &mut report.failures),
                subscription_hit(policies, &round.subscriptions, &peer),
            ];
            if let Some(rule) = select_rule(
                policies,
                &torrent,
                &peer,
                geography.as_ref(),
                round.previous_bans,
                hits,
                &mut report.failures,
            ) {
                if rule.action == Action::Skip {
                    report.ignored += 1;
                    continue;
                }
                if rule.action == Action::NoAction {
                    continue;
                }
                let ban = pending_ban(
                    downloader,
                    &torrent,
                    peer,
                    rule,
                    now,
                    geography.map(|location| json!(location)),
                );
                report.matches.push(Match {
                    downloader: downloader.id.clone(),
                    torrent: torrent.hash.clone(),
                    address: ban.record.address,
                    port: ban.peer.port,
                    duration_ms: ban.matched.duration_ms,
                    rule: ban.matched.rule.clone(),
                    reason: ban.matched.reason.clone(),
                });
                pending.push(ban);
            }
        }
    }
    Ok(())
}

async fn remove_expired_pcb_entries(
    state: &BanWaveState,
    now: i64,
    btn: &pbh_btn::RuleSnapshot,
) -> Result<()> {
    for ban in state.active.values() {
        if (now > ban_end(ban)? || btn_allows(btn, ban.address))
            && let Some(torrent_id) = ban.metadata["torrent"]["id"].as_str()
        {
            state
                .database
                .lock()
                .await
                .delete_pcb_address(torrent_id, ban.address)
                .await?;
        }
    }
    Ok(())
}

fn btn_allows(snapshot: &pbh_btn::RuleSnapshot, address: IpAddr) -> bool {
    btn_allow_comment(snapshot, address).is_some()
}

fn btn_allow_comment(snapshot: &pbh_btn::RuleSnapshot, address: IpAddr) -> Option<&str> {
    snapshot.ip_allowlist.as_ref()?.matching_comment(address)
}

fn pending_ban(
    downloader: &Downloader,
    torrent: &Torrent,
    peer: Peer,
    matched: RuleHit,
    now: i64,
    geoip: Option<Value>,
) -> PendingBan {
    let address = normalize_ip(peer.ip);
    let end_ms = now.saturating_add(matched.duration_ms);
    let disconnect_only = matched.action == Action::BanForDisconnect;
    // JSON field names mirror Java BanMetadata, TorrentWrapper and PeerWrapper.
    let metadata = json!({
        "context": matched.module,
        "randomId": Uuid::new_v4().simple().to_string(),
        "banAt": now,
        "unbanAt": end_ms,
        "banForDisconnect": disconnect_only,
        "excludeFromReport": disconnect_only,
        "excludeFromDisplay": disconnect_only,
        "reverseLookup": "N/A",
        "rule": matched.rule,
        "description": matched.reason,
        "structuredData": matched.details,
        "downloader": {"id": downloader.id, "name": downloader.name, "type": downloader.kind},
        "torrent": torrent_json(torrent),
        "peer": peer_json(&peer),
    });
    PendingBan {
        record: BanRecord { address, metadata },
        downloader: downloader.id.clone(),
        torrent: torrent.clone(),
        peer,
        start_ms: now,
        end_ms,
        matched,
        geoip,
    }
}

#[derive(Default)]
struct ManualRemovals {
    addresses: Vec<IpAddr>,
    force_full: bool,
}

fn manual_ban_changes(
    commands: Vec<ManualBans>,
    downloaders: &[Downloader],
    pending: &mut Vec<PendingBan>,
    now: i64,
) -> ManualRemovals {
    let mut removals = ManualRemovals::default();
    let mut additions = Vec::new();
    for command in commands {
        match command {
            ManualBans::Add(addresses) => additions.extend(addresses),
            ManualBans::Remove { addresses, full } => {
                let addresses = addresses
                    .into_iter()
                    .collect::<std::collections::BTreeSet<_>>();
                additions.retain(|(address, _)| !addresses.contains(address));
                removals.addresses.extend(addresses);
                removals.force_full |= full;
            }
        }
    }
    if let Some(downloader) = downloaders.first() {
        pending.extend(
            additions
                .into_iter()
                .map(|(address, raw)| manual_ban(downloader, address, raw, now)),
        );
    }
    removals
}

fn manual_ban(downloader: &Downloader, address: IpAddr, raw: String, now: i64) -> PendingBan {
    const HASH: &str = "00000000000000000000";
    const NAME: &str = "User Operation";
    let torrent = Torrent {
        id: HASH.into(),
        hash: HASH.into(),
        name: NAME.into(),
        size: 0,
        completed_size: 0,
        progress: 0.0,
        private: false,
        upload_speed: 0,
        download_speed: 0,
    };
    let peer = Peer {
        ip: address,
        port: 0,
        raw_address: raw,
        forwarded_from: None,
        client_name: Some(NAME.into()),
        peer_id: Some("-USROPS-".into()),
        download_speed: 0,
        upload_speed: 0,
        downloaded: 0,
        uploaded: 0,
        progress: 0.0,
        flags: None,
        handshaking: false,
    };
    let rule = RuleHit {
        module: "com.ghostchu.peerbanhelper.DownloaderServerImpl",
        action: Action::Ban,
        duration_ms: 100 * 365 * 24 * 60 * 60 * 1_000,
        rule: json!({"key": "PEER_BAN_USER_OPERATE_TITLE", "params": []}),
        reason: json!({"key": "PEER_BAN_USER_OPERATE_DESCRIPTION", "params": [address]}),
        details: json!({"type": "manually", "ip": peer_json(&peer)["address"]}),
    };
    pending_ban(downloader, &torrent, peer, rule, now, None)
}

async fn apply_wave(
    state: &mut BanWaveState,
    downloaders: &mut [Downloader],
    pending: &[PendingBan],
    now: i64,
    btn: &pbh_btn::RuleSnapshot,
    report: &mut Report,
    manual: ManualRemovals,
) -> Result<()> {
    let active = &mut state.active;
    let mut allowlist_alerts = Vec::new();
    for (address, ban) in active.iter() {
        if now > ban_end(ban)? {
            report.expired.push(*address);
        } else if let Some(comment) = btn_allow_comment(btn, *address) {
            report.allowlisted.push(*address);
            let ban_at = ban.metadata["banAt"].as_i64().and_then(java_utc_datetime);
            let identifier = format!("btn-allowlist-unbanned-peer-{address}{}", Uuid::new_v4());
            let content = json!({
                "key": "BTN_ABILITY_ALLOW_LIST_UNBAN_ALERT_DESCRIPTION",
                "params": [address, ban_at, ban.metadata["rule"], ban.metadata["description"], comment],
            });
            allowlist_alerts.push((identifier, content));
        }
    }
    let mut removed = report
        .expired
        .iter()
        .chain(&report.allowlisted)
        .copied()
        .collect::<Vec<_>>();
    removed.extend(
        manual
            .addresses
            .into_iter()
            .filter(|address| active.contains_key(address)),
    );
    removed.sort_unstable();
    removed.dedup();
    let unbans = removed
        .iter()
        .filter_map(|address| active.remove(address))
        .filter(|ban| ban.metadata["banForDisconnect"].as_bool() != Some(true))
        .count();

    let duplicate = pending
        .iter()
        .any(|ban| active.contains_key(&ban.record.address));
    for ban in pending {
        active.insert(ban.record.address, ban.record.clone());
    }

    let has_removals = !removed.is_empty();
    if !pending.is_empty() || has_removals {
        let snapshot = state
            .persist_bans
            .then(|| active.values().cloned().collect::<Vec<_>>());
        let history = pending
            .iter()
            .filter(|ban| ban.matched.action == Action::Ban)
            .map(PendingBan::history)
            .collect::<Vec<_>>();
        {
            let mut database = state.database.lock().await;
            database
                .commit_ban_wave(snapshot.as_deref(), &history)
                .await?;
            let title = json!({"key": "BTN_ABILITY_ALLOW_LIST_UNBAN_ALERT_TITLE"});
            for (identifier, content) in &allowlist_alerts {
                let alert = NewAlert {
                    create_at_ms: now,
                    level: AlertLevel::Info,
                    identifier,
                    title: &title,
                    content,
                };
                if let Err(error) = database
                    .publish_alert(&alert, AlertDeduplication::Unread)
                    .await
                {
                    crate::journal::log_warning!("BTN allowlist alert: {error:#}");
                }
            }
        }
        *state
            .tunnel_bans
            .write()
            .expect("AutoSTUN bans lock poisoned") = active.keys().copied().collect();
        state.tunnels.bans_changed();
    }

    let full_list = active.keys().copied().collect::<Vec<_>>();
    if let Some(web) = &state.web {
        let mut counters = web.dashboard.counters.write().await;
        counters.checks += 1;
        counters.unbans += unbans as u64;
        for ban in pending
            .iter()
            .filter(|ban| ban.matched.action == Action::Ban)
        {
            counters.bans += 1;
            counters.wasted_traffic = counters.wasted_traffic.wrapping_add(ban.peer.uploaded);
        }
    }
    if let Some(web) = &state.web
        && (!pending.is_empty() || has_removals)
    {
        *web.bans.write().await = active.values().cloned().collect();
    }
    let added: Vec<_> = pending
        .iter()
        .map(|ban| AddedBan {
            address: ban.record.address,
            raw_address: &ban.peer.raw_address,
        })
        .collect();
    for downloader in downloaders {
        let force_full = downloader.needs_full_list || duplicate || manual.force_full;
        if force_full || has_removals || !added.is_empty() {
            let result =
                sync_downloader(downloader, &full_list, &added, &removed, force_full).await;
            downloader.needs_full_list = result.is_err();
            if let Err(error) = result {
                report
                    .failures
                    .push(format!("{}: {error:#}", downloader.id));
            }
        }
    }
    Ok(())
}

async fn sync_downloader(
    downloader: &mut Downloader,
    full_list: &[IpAddr],
    added: &[AddedBan<'_>],
    removed: &[IpAddr],
    force_full: bool,
) -> Result<()> {
    downloader.pending_removals.extend(removed.iter().copied());
    downloader
        .pending_removals
        .retain(|address| full_list.binary_search(address).is_err());
    let removed = downloader
        .pending_removals
        .iter()
        .copied()
        .collect::<Vec<_>>();
    downloader
        .client
        .sync(full_list, added, &removed, force_full)
        .await
        .context("synchronize ban list")?;
    downloader.pending_removals.clear();
    Ok(())
}

fn ban_end(ban: &BanRecord) -> Result<i64> {
    ban.metadata["unbanAt"]
        .as_i64()
        .with_context(|| format!("invalid unbanAt in persisted ban for {}", ban.address))
}
