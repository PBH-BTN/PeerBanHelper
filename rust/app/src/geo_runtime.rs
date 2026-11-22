//! Share a consistent GeoIP database snapshot for each scan wave and HTTP request.

use std::fs;
use std::path::Path;
use std::sync::Arc;

use pbh_geoip::update::{
    Credentials, DatabaseSource, DatabaseUpdater, ProgressPhase, UpdateOptions, UpdateProgress,
    UpdateStatus,
};
use pbh_geoip::{Database, GeoIp};
use pbh_network::ProxyPolicy;
use serde::Deserialize;
use serde_json::{Value, json};
use tokio::sync::watch;

use crate::background_tasks::{BackgroundTasks, BarType, Task};

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct GeoConfig {
    auto_update: bool,
    database_city: Option<String>,
    database_asn: Option<String>,
    database_geocn: Option<String>,
    account_id: Option<String>,
    license_key: Option<String>,
}

impl GeoConfig {
    fn update_options(&self) -> UpdateOptions {
        let defaults = UpdateOptions::default();
        UpdateOptions {
            auto_update: self.auto_update,
            city: self
                .database_city
                .clone()
                .map_or(defaults.city, DatabaseSource::new),
            asn: self
                .database_asn
                .clone()
                .map_or(defaults.asn, DatabaseSource::new),
            geocn: self
                .database_geocn
                .clone()
                .map_or(defaults.geocn, DatabaseSource::new),
            credentials: self
                .account_id
                .clone()
                .zip(self.license_key.clone())
                .filter(|(account_id, license_key)| {
                    !account_id.is_empty() && !license_key.is_empty()
                })
                .map(|(account_id, license_key)| Credentials {
                    account_id,
                    license_key,
                }),
        }
    }
}

#[derive(Clone)]
pub(crate) struct GeoRuntime(watch::Receiver<Option<Arc<GeoIp>>>);

impl GeoRuntime {
    pub(crate) fn start(
        data_dir: &Path,
        locale: &str,
        config: &GeoConfig,
        refresh: bool,
        tasks: &BackgroundTasks,
        proxy: &ProxyPolicy,
    ) -> Self {
        if disabled(data_dir) {
            return Self(watch::channel(None).1);
        }
        let (snapshot, receiver) = watch::channel(Some(open(data_dir, locale)));
        if refresh {
            let tasks = tasks.clone();
            let proxy = proxy.clone();
            let data_dir = data_dir.to_owned();
            let locale = locale.to_owned();
            let options = config.update_options();
            // This blocking startup job can take minutes while a mirror is unavailable.
            // A dedicated thread lets normal service shutdown complete without waiting for it.
            let worker = std::thread::Builder::new()
                .name("peerbanhelper-geoip".into())
                .spawn(move || {
                    let updater = match DatabaseUpdater::with_proxy(&proxy) {
                        Ok(updater) => updater,
                        Err(error) => {
                            crate::journal::log_warning!("GeoIP updater: {error}");
                            return;
                        }
                    };
                    let mut installed = false;
                    let mut current = None;
                    for result in updater.refresh_with_progress(&data_dir, &options, |event| {
                        update_task(&tasks, &mut current, event);
                    }) {
                        match result.status {
                            UpdateStatus::Current => {}
                            UpdateStatus::Installed(_) => installed = true,
                            UpdateStatus::Unavailable { error, .. } => {
                                crate::journal::log_warning!(
                                    "GeoIP {}: {error}",
                                    result.database.filename()
                                );
                            }
                        }
                    }
                    if installed {
                        let _ = snapshot.send(Some(open(&data_dir, &locale)));
                    }
                });
            if let Err(error) = worker {
                crate::journal::log_warning!("GeoIP updater: {error}");
            }
        }
        Self(receiver)
    }

    pub(crate) fn current(&self) -> Option<Arc<GeoIp>> {
        self.0.borrow().clone()
    }
}

struct DownloadTask {
    database: Database,
    task: Task,
    bar_type: BarType,
    current: i64,
    max: i64,
    message: Option<Value>,
}

fn update_task(tasks: &BackgroundTasks, active: &mut Option<DownloadTask>, update: UpdateProgress) {
    let database = update.database;
    if matches!(update.phase, ProgressPhase::Started) {
        *active = Some(DownloadTask {
            database,
            task: tasks.begin(json!({"key":"IPDB_DOWNLOAD_MMDB"})),
            bar_type: BarType::Indeterminate,
            current: 0,
            max: 0,
            message: None,
        });
        return;
    }
    let Some(task) = active.as_mut().filter(|task| task.database == database) else {
        return;
    };
    match update.phase {
        ProgressPhase::Requesting { url } => {
            task.current = 0;
            task.message = Some(json!({"key":"IPDB_DOWNLOAD_MMDB_DESCRIPTION","params":[url]}));
        }
        ProgressPhase::Transfer {
            decoded_bytes,
            compressed_total,
        } => {
            task.current = i64::try_from(decoded_bytes).unwrap_or(i64::MAX);
            task.max =
                compressed_total.map_or(-1, |value| i64::try_from(value).unwrap_or(i64::MAX));
            task.bar_type = if task.max > 0 {
                BarType::Determinate
            } else {
                BarType::Indeterminate
            };
        }
        ProgressPhase::Validating => task.bar_type = BarType::Indeterminate,
        ProgressPhase::Finished { error } => {
            if let Some(task) = active.take() {
                match error {
                    Some(error) => task.task.fail(Some(json!({
                        "key":"IPDB_DOWNLOAD_MMDB_FAILED_DESCRIPTION","params":[error],
                    }))),
                    None => task.task.complete(),
                }
            }
            return;
        }
        ProgressPhase::Started => return,
    }
    task.task
        .progress(task.bar_type, task.current, task.max, task.message.clone());
}

fn open(data_dir: &Path, locale: &str) -> Arc<GeoIp> {
    let geoip = GeoIp::open(data_dir, locale);
    for error in geoip.load_errors() {
        crate::journal::log_warning!("GeoIP database: {error}");
    }
    Arc::new(geoip)
}

fn disabled(data_dir: &Path) -> bool {
    let flags = fs::read_to_string(data_dir.join("flags.properties")).unwrap_or_default();
    flags
        .lines()
        .filter_map(|line| line.split_once('='))
        .any(|(key, value)| {
            key.trim() == "pbh.forceDisableIPDB" && value.trim().eq_ignore_ascii_case("true")
        })
        || std::env::var("PBH_FORCEDISABLEIPDB")
            .is_ok_and(|value| value.eq_ignore_ascii_case("true"))
}
