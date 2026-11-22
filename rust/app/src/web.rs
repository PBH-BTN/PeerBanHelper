use std::collections::{BTreeMap, HashMap};
use std::fs;
use std::io::{ErrorKind, Write};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex as StateMutex};
use std::time::Duration;

use anyhow::{Context, Result};
use axum::extract::State;
use axum::http::{HeaderMap, StatusCode, header};
use axum::routing::get;
use axum::{Router, middleware};
use pbh_core::AddressPolicy;
use pbh_storage::BanRecord;
use pbh_web::{BuildVersion, Manifest, SetupConfig, WebConfig, WebSessions};
use serde::Deserialize;
use serde_json::Value;
use tokio::net::TcpListener;
use tokio::sync::{Mutex, RwLock, mpsc, watch};

use crate::background_tasks::{self, BackgroundTasks};
use crate::config::{MainConfig, ServerConfig, config_dir, read_main, read_yaml};
use crate::dashboard::{self, Dashboard};
use crate::downloader::Client;
use crate::general_api;
use crate::geo_runtime::GeoRuntime;
use crate::modules::Modules;
use crate::nat_status::AutoStunControl;
use crate::storage::Database;
use crate::subscriptions::Subscriptions;
use crate::{
    alert_api, ban_api, btn_api, chart_api, downloader_api, history_api, journal, lab_api,
    metric_charts, nat_status, plus_api, push_api, subscription_api, swarm_api, tracker_api,
};

#[derive(Clone)]
pub(crate) struct ScanState {
    pub(crate) modules: Modules,
    pub(crate) reachability: watch::Receiver<crate::reachability::Reachability>,
    pub(crate) background_tasks: BackgroundTasks,
    pub(crate) reload: watch::Sender<bool>,
    pub(crate) closing_http: watch::Receiver<bool>,
    pub(crate) bans: Arc<RwLock<Vec<BanRecord>>>,
    pub(crate) database: Arc<Mutex<Database>>,
    pub(crate) paused: watch::Sender<bool>,
    pub(crate) subscriptions: Option<Subscriptions>,
    pub(crate) btn_negotiated: watch::Receiver<Option<Arc<pbh_btn::Negotiated>>>,
    pub(crate) btn_status: crate::btn_runtime::BtnStatus,
    pub(crate) clients: BTreeMap<String, Arc<Client>>,
    pub(crate) downloader_info: Arc<HashMap<String, Value>>,
    pub(crate) dashboard: Dashboard,
    pub(crate) address_policy: AddressPolicy,
    pub(crate) geoip: GeoRuntime,
    pub(crate) swarm_enabled: bool,
    pub(crate) auto_stun: AutoStunControl,
    pub(crate) manual_bans: mpsc::Sender<ban_api::ManualBans>,
    pub(crate) can_add_manual_bans: bool,
    pub(crate) demo_mode: bool,
}

pub(crate) struct BoundServer {
    socket: std::net::TcpListener,
    sessions: StateMutex<(String, WebSessions)>,
}

impl BoundServer {
    pub(crate) async fn bind(server: &ServerConfig) -> Result<Self> {
        let listener = TcpListener::bind((server.address.as_str(), server.http))
            .await
            .context("bind PeerBanHelper HTTP server")?;
        Ok(Self {
            socket: listener.into_std()?,
            sessions: StateMutex::new((server.token.clone(), WebSessions::default())),
        })
    }

    pub(crate) fn listener(&self) -> Result<TcpListener> {
        Ok(TcpListener::from_std(self.socket.try_clone()?)?)
    }

    fn sessions(&self, token: &str) -> WebSessions {
        let mut state = self
            .sessions
            .lock()
            .expect("web session state lock poisoned");
        if state.0 != token {
            *state = (token.to_owned(), WebSessions::default());
        }
        state.1.clone()
    }
}

pub(crate) fn configured_server(path: &Path) -> Result<ServerConfig> {
    #[derive(Deserialize)]
    struct Configured {
        #[serde(default)]
        server: ServerConfig,
    }
    let mut server = read_yaml::<Configured>(path)?.server;
    server.apply_environment()?;
    Ok(server)
}

pub(crate) async fn run(data_dir: &Path, webui_dir: PathBuf) -> Result<()> {
    let config_path = create_defaults(&config_dir(data_dir))?;
    let config = read_main(&config_path)?;
    let bound = BoundServer::bind(&config.server).await?;
    let listener = bound.listener()?;
    let router = prepare(
        &bound,
        &config,
        data_dir,
        webui_dir,
        None,
        Some(SetupConfig { config_path }),
        None,
    )?;
    pbh_web::serve(listener, router, async {
        if let Err(error) = crate::shutdown::signal().await {
            crate::journal::log_warning!("failed to await shutdown: {error}");
        }
    })
    .await?;
    Ok(())
}

pub(crate) async fn wait_for_setup(
    data_dir: &Path,
    webui_dir: PathBuf,
    tray: Option<&pbh_desktop::Tray>,
) -> Result<Option<BoundServer>> {
    let config_path = create_defaults(&config_dir(data_dir))?;
    let server = configured_server(&config_path)?;
    let bound = BoundServer::bind(&server).await?;
    if !server.token.trim().is_empty() {
        return Ok(Some(bound));
    }
    let listener = bound.listener()?;
    let config = read_main(&config_path)?;
    let router = prepare(
        &bound,
        &config,
        data_dir,
        webui_dir,
        None,
        Some(SetupConfig {
            config_path: config_path.clone(),
        }),
        tray,
    )?;
    let (ready, initialized) = tokio::sync::oneshot::channel();
    pbh_web::serve(listener, router, async move {
        tokio::select! {
            _ = crate::shutdown::signal() => {},
            _ = wait_for_token(config_path) => {
                let _ = ready.send(());
            },
        }
    })
    .await?;
    Ok(initialized.await.is_ok().then_some(bound))
}

async fn wait_for_token(config_path: PathBuf) {
    loop {
        tokio::time::sleep(Duration::from_millis(250)).await;
        if configured_server(&config_path).is_ok_and(|server| !server.token.trim().is_empty()) {
            return;
        }
    }
}

pub(crate) fn create_defaults(config_dir: &Path) -> Result<PathBuf> {
    fs::create_dir_all(config_dir)?;
    let config_path = config_dir.join("config.yml");
    create_config(&config_path, config_template().as_bytes())?;
    create_config(
        &config_dir.join("profile.yml"),
        profile_template().as_bytes(),
    )?;
    Ok(config_path)
}

fn config_template() -> String {
    let shared = include_str!("../../../src/main/resources/config.yml");
    without_template_section(
        shared,
        "  # 是否允许 PeerBanHelper 接收来自 BTN 服务器的 Aviator 脚本",
        "# GeoIP/ASN 数据库",
    )
    .replace("  # h2 - 嵌入式 H2 数据库\n", "")
    .replace(
        "  # 1 = 使用系统代理 - Use system proxy",
        "  # 1 = 使用 HTTP 代理 - HTTP proxy",
    )
    .replace(
        "  # 2 = 使用 HTTP(s) 代理 - Use HTTP(s) proxy",
        "  # 2 = 使用 SOCKS5 代理 - SOCKS5 proxy",
    )
}

fn profile_template() -> String {
    without_template_section(
        include_str!("../../../src/main/resources/profile.yml"),
        "  # 规则引擎，支持 AviatorScript 语言",
        "  # 订阅规则",
    )
}

fn without_template_section(input: &str, opening: &str, next: &str) -> String {
    let input = input.replace("\r\n", "\n");
    if let Some((head, body)) = input.split_once(opening)
        && let Some((_, rest)) = body.split_once(next)
    {
        return format!("{head}{next}{rest}");
    }
    input
}

pub(crate) fn prepare(
    bound: &BoundServer,
    config: &MainConfig,
    data_dir: &Path,
    webui_dir: PathBuf,
    scan: Option<ScanState>,
    setup: Option<SetupConfig>,
    tray: Option<&pbh_desktop::Tray>,
) -> Result<Router> {
    let local = bound.socket.local_addr()?;
    let config_dir = config_dir(data_dir);
    if let Some(tray) = tray {
        tray.set_web_ui(Some(pbh_desktop::WebUi::from_listener(local, None)));
    }
    let demo_mode =
        std::env::var("PBH_DEMOMODE").is_ok_and(|value| value.eq_ignore_ascii_case("true"));
    let proxy = config.proxy_policy()?;
    let btn_status = scan
        .as_ref()
        .map(|scan| scan.btn_status.clone())
        .unwrap_or_default();
    let plus =
        plus_api::PlusRuntime::open(data_dir, &config_dir.join("config.yml"), demo_mode, &proxy)?;
    let modules = scan.as_ref().map_or_else(
        || Modules::new(None).manifest(),
        |scan| scan.modules.manifest(),
    );
    let options = WebConfig {
        token: config.server.token.clone(),
        allow_cors: config.server.allow_cors,
        static_dir: webui_dir.join("index.html").is_file().then_some(webui_dir),
        manifest: Manifest {
            version: BuildVersion {
                version: env!("CARGO_PKG_VERSION").into(),
                os: std::env::consts::OS.into(),
                branch: option_env!("PBH_BUILD_GIT_BRANCH")
                    .unwrap_or_default()
                    .into(),
                commit: option_env!("PBH_BUILD_GIT_COMMIT")
                    .unwrap_or_default()
                    .into(),
                abbrev: option_env!("PBH_BUILD_GIT_ABBREV")
                    .unwrap_or_default()
                    .into(),
            },
            modules,
            installation_id: config
                .installation_id
                .clone()
                .unwrap_or_else(|| "not-initialized".into()),
            analytics: config.privacy.analytics,
        },
    };
    let (public, protected) = if let Some(scan) = scan {
        let public = Router::new()
            .route(pbh_transmission::BLOCKLIST_PATH, get(render_blocklist))
            .with_state(scan.clone());
        let dashboard = dashboard::router(scan.clone());
        let downloader_config = downloader_api::router(scan.clone(), data_dir);
        let metric_charts = metric_charts::router(scan.clone());
        let general = general_api::router(scan.clone(), data_dir, config);
        let background_tasks = background_tasks::router(scan.background_tasks.clone());
        let swarm = if scan.swarm_enabled {
            swarm_api::router(scan.clone())
        } else {
            Router::new()
        };
        let alerts = alert_api::router(scan.clone());
        let licensed = chart_api::router(scan.clone())
            .merge(history_api::plus_router(scan.clone()))
            .route_layer(middleware::from_fn_with_state(
                plus.clone(),
                plus_api::require_basic,
            ));
        let history = history_api::router(scan.clone());
        let push = push_api::router(config_dir.join("config.yml"), scan.demo_mode, proxy.clone());
        let tracker = tracker_api::router(scan.clients.clone());
        let subscriptions = scan
            .subscriptions
            .clone()
            .map_or_else(Router::new, |runtime| {
                subscription_api::router(
                    config_dir.join("profile.yml"),
                    runtime,
                    scan.database.clone(),
                    scan.demo_mode,
                )
            });
        let nat = nat_status::router(
            scan.auto_stun.clone(),
            config.stun.clone(),
            scan.downloader_info.clone(),
            scan.demo_mode,
            scan.geoip.clone(),
            config_dir.join("config.yml"),
        );
        let journal = journal::router(scan.closing_http.clone());
        (
            public,
            ban_api::router(scan)
                .merge(dashboard)
                .merge(downloader_config)
                .merge(metric_charts)
                .merge(general)
                .merge(background_tasks)
                .merge(swarm)
                .merge(alerts)
                .merge(licensed)
                .merge(history)
                .merge(journal)
                .merge(push)
                .merge(tracker)
                .merge(subscriptions)
                .merge(nat),
        )
    } else {
        (Router::new(), Router::new())
    };
    let protected = protected
        .merge(btn_api::router(
            config.btn.clone(),
            config.installation_id.as_deref(),
            &proxy,
            btn_status,
            config.language.as_deref().unwrap_or("default"),
            demo_mode,
        ))
        .merge(plus_api::router(plus));
    let (public, protected) = if setup.is_none() {
        let lab = lab_api::LabRuntime::open(
            data_dir,
            config.installation_id.as_deref(),
            config.language.as_deref().unwrap_or("default"),
            demo_mode,
        )?;
        (
            public.route("/api/egg", get(easter_egg)),
            protected.merge(lab_api::router(lab)),
        )
    } else {
        (public, protected)
    };
    let app = pbh_web::router_with_sessions(
        options,
        public,
        protected,
        setup,
        bound.sessions(&config.server.token),
    );
    journal::info(format_args!(
        "PeerBanHelper HTTP preview listening on {local}"
    ));
    Ok(app)
}

async fn easter_egg() -> (StatusCode, [(header::HeaderName, &'static str); 1]) {
    const LINKS: [&str; 22] = [
        "https://www.bilibili.com/video/BV1S3UaYvE5o",
        "https://www.bilibili.com/video/BV1QQ4y1d7Rd",
        "https://www.bilibili.com/video/BV1by4y1F7nS",
        "https://www.bilibili.com/video/BV1ztzBY1EPy",
        "https://www.bilibili.com/video/BV15Z4y1Q7mz",
        "https://www.bilibili.com/video/BV1GJ411x7h7",
        "https://www.bilibili.com/video/BV1a14y187U5",
        "https://www.bilibili.com/video/BV1na411Q7qH",
        "https://music.163.com/song?id=2647873613&uct2=U2FsdGVkX1+Uch9IV1F1N/gLpHilPuUZQXy0xDGsKrM=",
        "https://music.163.com/song?id=2165896793&uct2=U2FsdGVkX19w55MRVVy7ZG3Yf9d6QL+CIwgE8jPc4N0=",
        "https://www.youtube.com/watch?v=tSLI6PjAfns",
        "https://www.bilibili.com/video/BV1Nb41117L3",
        "https://www.bilibili.com/video/BV1js411A71E",
        "https://store.steampowered.com/app/365450/Hacknet/",
        "https://store.steampowered.com/app/753640/Outer_Wilds/",
        "https://store.steampowered.com/app/739630/Phasmophobia/",
        "https://store.steampowered.com/app/3590/Plants_vs_Zombies_GOTY_Edition/",
        "https://store.steampowered.com/app/333600/NEKOPARA_Vol_1/",
        "https://store.steampowered.com/app/391540/Undertale/",
        "https://store.steampowered.com/app/646570/Slay_the_Spire/",
        "https://store.steampowered.com/app/457140/_/",
        "https://www.bilibili.com/video/BV1nB3u6tEHD",
    ];
    let index = (uuid::Uuid::new_v4().as_u128() % LINKS.len() as u128) as usize;
    (StatusCode::FOUND, [(header::LOCATION, LINKS[index])])
}

fn create_config(path: &Path, contents: &[u8]) -> Result<()> {
    if path.exists() {
        return Ok(());
    }
    let directory = path
        .parent()
        .context("configuration path lacks parent directory")?;
    let mut file = tempfile::NamedTempFile::new_in(directory)?;
    file.write_all(contents)?;
    file.as_file().sync_all()?;
    match file.persist_noclobber(path) {
        Ok(_) => Ok(()),
        Err(error) if error.error.kind() == ErrorKind::AlreadyExists => Ok(()),
        Err(error) => Err(error.error).with_context(|| format!("create {}", path.display())),
    }
}

async fn render_blocklist(State(scan): State<ScanState>, headers: HeaderMap) -> String {
    let user_agent = headers
        .get(header::USER_AGENT)
        .and_then(|value| value.to_str().ok());
    let snapshot = scan
        .bans
        .read()
        .await
        .iter()
        .map(|record| ipnet::IpNet::from(record.address))
        .collect::<Vec<_>>();
    pbh_transmission::render_p2p_blocklist_networks_with_policy(
        &snapshot,
        user_agent,
        &scan.address_policy,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn native_templates_keep_settings_and_remove_retired_script_sections() {
        let config = config_template();
        let yaml: serde_yaml_ng::Value = serde_yaml_ng::from_str(&config).unwrap();
        assert!(yaml["btn"].get("allow-script-execute").is_none());
        assert_eq!(yaml["database"]["type"].as_str(), Some("sqlite"));
        assert!(config.contains("2 = 使用 SOCKS5 代理"));
        let profile = profile_template();
        let yaml: serde_yaml_ng::Value = serde_yaml_ng::from_str(&profile).unwrap();
        assert!(yaml["module"].get("expression-engine").is_none());
        assert!(yaml["module"].get("ip-address-blocker-rules").is_some());
        let profile: crate::config::Profile = serde_yaml_ng::from_str(&profile).unwrap();
        profile.policies(true).unwrap();
    }
}
