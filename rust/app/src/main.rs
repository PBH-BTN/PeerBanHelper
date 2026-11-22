mod alert_api;
mod auto_tunnel;
mod background_tasks;
mod ban_api;
mod btn_api;
mod btn_reports;
mod btn_runtime;
mod chart_api;
mod config;
mod config_migrations;
mod dashboard;
mod dns;
mod downloader;
mod downloader_api;
mod general_api;
mod geo_runtime;
mod history_api;
mod journal;
mod journal_file;
mod lab_api;
mod metric_charts;
mod modules;
mod nat_status;
mod pcb;
mod peer_recording;
mod plus_api;
mod policy;
mod push;
mod push_api;
mod reachability;
mod scan;
mod session_recording;
mod shutdown;
mod storage;
mod subscription_api;
mod subscriptions;
mod swarm_api;
mod tracker_api;
mod traffic;
mod web;

use std::io::{self, BufRead, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use pbh_core::{Decision, select_decision};
use serde::{Deserialize, Serialize};

#[derive(Parser)]
#[command(version, about = "PeerBanHelper Rust migration preview")]
struct Args {
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// Configure on first launch and then run the native service.
    Run {
        /// PeerBanHelper data directory.
        #[arg(long, env = "PBH_DATADIR", default_value_os_t = config::default_data_dir())]
        data_dir: PathBuf,
        /// Built Vue application directory.
        #[arg(long, default_value = "webui/dist")]
        webui_dir: PathBuf,
        /// Show the notification-area controls on supported desktops.
        #[arg(long)]
        tray: bool,
    },
    /// Evaluate the reference merge contract over JSON Lines.
    CompareDecisions,
    #[command(hide = true)]
    InitConfig {
        #[arg(long)]
        data_dir: PathBuf,
    },
    /// Scan configured downloaders and evaluate supported rules.
    Scan {
        /// PeerBanHelper data directory containing config/config.yml and config/profile.yml.
        #[arg(long, env = "PBH_DATADIR", default_value_os_t = config::default_data_dir())]
        data_dir: PathBuf,
        /// Run a single wave and exit.
        #[arg(long)]
        once: bool,
        /// Persist and synchronize bans for a profile containing only supported rules.
        #[arg(long)]
        apply: bool,
        /// Serve the authenticated API and supplied WebUI while applying bans.
        #[arg(long, requires = "apply")]
        webui_dir: Option<PathBuf>,
    },
    /// Run the WebUI HTTP preview with metadata and authentication.
    Serve {
        /// PeerBanHelper data directory containing config/config.yml.
        #[arg(long, env = "PBH_DATADIR", default_value_os_t = config::default_data_dir())]
        data_dir: PathBuf,
        /// Built Vue application directory.
        #[arg(long, default_value = "webui/dist")]
        webui_dir: PathBuf,
    },
}

#[derive(Deserialize)]
struct DecisionInput {
    id: String,
    candidates: Vec<Decision>,
}

#[derive(Serialize)]
struct DecisionOutput {
    id: String,
    value: Option<Decision>,
}

fn compare_decisions(input: impl BufRead, mut output: impl Write) -> Result<()> {
    for (index, line) in input.lines().enumerate() {
        let input = line?;
        let row: DecisionInput = serde_json::from_str(&input)
            .with_context(|| format!("invalid decision fixture on line {}", index + 1))?;
        let row = DecisionOutput {
            id: row.id,
            value: select_decision(row.candidates),
        };
        serde_json::to_writer(&mut output, &row)?;
        writeln!(output)?;
    }
    Ok(())
}

fn main() -> Result<()> {
    let command = Args::parse().command;
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?;
    match command {
        #[cfg(target_os = "macos")]
        Command::Run {
            data_dir,
            webui_dir,
            tray: true,
        } => pbh_desktop::run_macos_tray(tray_labels(&data_dir), move |tray| {
            runtime.block_on(run_service(data_dir, webui_dir, Some(tray)))
        })?,
        #[cfg(all(target_os = "linux", feature = "linux-webview"))]
        Command::Run {
            data_dir,
            webui_dir,
            tray: true,
        } => pbh_desktop::run_linux_tray(tray_labels(&data_dir), move |tray| {
            runtime.block_on(run_service(data_dir, webui_dir, Some(tray)))
        })?,
        command => runtime.block_on(run_command(command)),
    }
}

async fn run_command(command: Command) -> Result<()> {
    match command {
        Command::Run {
            data_dir,
            webui_dir,
            tray,
        } => {
            #[cfg(windows)]
            let tray = tray
                .then(|| pbh_desktop::Tray::start_windows(tray_labels(&data_dir), &data_dir))
                .transpose()?;
            #[cfg(not(windows))]
            let tray = tray
                .then(|| pbh_desktop::Tray::start(tray_labels(&data_dir)))
                .transpose()?;
            run_service(data_dir, webui_dir, tray).await
        }
        Command::CompareDecisions => compare_decisions(io::stdin().lock(), io::stdout().lock()),
        Command::InitConfig { data_dir } => {
            web::create_defaults(&data_dir.join("config")).map(|_| ())
        }
        Command::Scan {
            data_dir,
            once,
            apply,
            webui_dir,
        } => scan::run(&data_dir, once, apply, webui_dir, None, None).await,
        Command::Serve {
            data_dir,
            webui_dir,
        } => web::run(&data_dir, webui_dir).await,
    }
}

fn tray_labels(data_dir: &Path) -> pbh_desktop::TrayLabels {
    let config_path = config::config_dir(data_dir).join("config.yml");
    let yaml = config::read_yaml::<serde_yaml_ng::Value>(&config_path).unwrap_or_default();
    let language =
        ban_api::BanLanguage::from_locale(yaml["language"].as_str().unwrap_or("default"));
    let message = |key| language.render(&serde_json::json!({"key": key}));
    pbh_desktop::TrayLabels {
        title: "PeerBanHelper".to_owned(),
        open_window: message("GUI_MENU_SHOW_WINDOW"),
        open_web_ui: message("GUI_MENU_WEBUI"),
        exit: message("GUI_MENU_QUIT"),
        ban_statistics: message("GUI_MENU_STATS_BANNED"),
        downloader_statistics: message("GUI_MENU_STATS_DOWNLOADER"),
    }
}

async fn run_service(
    data_dir: PathBuf,
    webui_dir: PathBuf,
    tray: Option<pbh_desktop::Tray>,
) -> Result<()> {
    journal::open_file(&data_dir);
    let tray = tray.map(Arc::new);
    let exit_monitor = tray.as_ref().map(|tray| {
        let tray = tray.clone();
        tokio::spawn(async move {
            tray.exit_requested().await;
            shutdown::request();
        })
    });
    let result = async {
        if let Some(bound) =
            web::wait_for_setup(&data_dir, webui_dir.clone(), tray.as_deref()).await?
        {
            scan::run(
                &data_dir,
                false,
                true,
                Some(webui_dir),
                tray.as_deref(),
                Some(bound),
            )
            .await?;
        }
        Ok(())
    }
    .await;
    if let Some(monitor) = exit_monitor {
        monitor.abort();
        let _ = monitor.await;
    }
    result
}
