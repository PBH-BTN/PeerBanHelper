//! Sample the downloaders' lifetime counters and maintain the hourly journal.

use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result};
use pbh_storage::{AlertDeduplication, AlertLevel, NewAlert, TrafficSample};
use serde::Deserialize;
use serde_json::json;
use time::{OffsetDateTime, Time, UtcOffset};
use tokio::sync::{Mutex, oneshot};
use tokio::task::JoinHandle;

use crate::ban_api::BanLanguage;
use crate::downloader::{Client, Downloader};
use crate::push::read_providers;
use crate::storage::Database;

const HOUR_SECONDS: i64 = 3_600;
const WINDOW_SECONDS: i64 = 24 * HOUR_SECONDS;

pub(crate) struct Notifications {
    pub(crate) config_path: PathBuf,
    pub(crate) language: BanLanguage,
    pub(crate) proxy: pbh_network::ProxyPolicy,
}

#[derive(Clone, Default, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct TrafficPolicy {
    traffic_monitoring: DailyThreshold,
    traffic_sliding_capping: SlidingWindow,
}

#[derive(Clone, Copy, Default, Deserialize)]
#[serde(default)]
struct DailyThreshold {
    daily: i64,
}

#[derive(Clone, Copy, Default, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
struct SlidingWindow {
    enabled: bool,
    daily_max_allowed_upload_traffic: i64,
    min_speed: i64,
    max_speed: i64,
}

impl SlidingWindow {
    fn upload_limit(self, old_speed: i64, uploaded: i64) -> Result<i64> {
        let threshold = self.daily_max_allowed_upload_traffic;
        if uploaded >= threshold {
            if old_speed <= self.min_speed {
                return Ok(self.min_speed);
            }
            let factor = if threshold > 0 {
                uploaded as f64 / threshold as f64
            } else {
                1.0
            };
            return Ok(self.min_speed.max((old_speed as f64 / factor) as i64));
        }
        if self.max_speed > 0 && old_speed >= self.max_speed {
            return Ok(self.max_speed);
        }
        let increase = (threshold.wrapping_sub(uploaded) as f64 / (WINDOW_SECONDS * 1_000) as f64
            * 1_000.0) as i64;
        Ok(self.max_speed.min(
            old_speed
                .checked_add(increase)
                .context("traffic limiter overflow")?,
        ))
    }
}

pub(crate) struct TrafficMonitor {
    stop: oneshot::Sender<()>,
    task: JoinHandle<()>,
}

impl TrafficMonitor {
    pub(crate) fn start(
        downloaders: &[Downloader],
        database: Arc<Mutex<Database>>,
        policy: TrafficPolicy,
        notifications: Notifications,
    ) -> Self {
        let clients = downloaders
            .iter()
            .map(|downloader| (downloader.id.clone(), downloader.client.clone()))
            .collect::<Vec<_>>();
        let (stop, mut stopped) = oneshot::channel();
        let task = tokio::spawn(async move {
            let mut interval = tokio::time::interval(Duration::from_secs(60));
            interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
            loop {
                tokio::select! {
                    _ = interval.tick() => update_traffic(&clients, &database, &policy, &notifications).await,
                    result = &mut stopped => {
                        if result.is_ok() {
                            update_traffic(&clients, &database, &policy, &notifications).await;
                        }
                        return;
                    }
                }
            }
        });
        Self { stop, task }
    }

    pub(crate) async fn stop(self) -> Result<()> {
        let _ = self.stop.send(());
        Ok(self.task.await?)
    }
}

pub(crate) async fn sample_once(
    downloaders: &[Downloader],
    database: &Mutex<Database>,
    policy: &TrafficPolicy,
    notifications: &Notifications,
) {
    let clients = downloaders
        .iter()
        .map(|downloader| (downloader.id.clone(), downloader.client.clone()))
        .collect::<Vec<_>>();
    update_traffic(&clients, database, policy, notifications).await;
}

async fn update_traffic(
    clients: &[(String, Arc<Client>)],
    database: &Mutex<Database>,
    policy: &TrafficPolicy,
    notifications: &Notifications,
) {
    for (id, client) in clients {
        match statistics(client).await {
            Ok((uploaded, downloaded)) => {
                let sample = TrafficSample {
                    timestamp_ms: hour_start(OffsetDateTime::now_utc()),
                    downloader: id,
                    uploaded,
                    downloaded,
                    protocol_uploaded: 0,
                    protocol_downloaded: 0,
                };
                if let Err(error) = database
                    .lock()
                    .await
                    .upsert_traffic_samples(&[sample])
                    .await
                {
                    crate::journal::log_warning!("Persist traffic sample for {id}: {error:#}");
                }
            }
            Err(error) => crate::journal::log_warning!("Traffic sample for {id}: {error:#}"),
        }
    }

    let now = OffsetDateTime::now_utc();
    if policy.traffic_monitoring.daily > 0
        && let Err(error) = check_daily_threshold(
            database,
            policy.traffic_monitoring.daily,
            now,
            notifications,
        )
        .await
    {
        crate::journal::log_warning!("Daily traffic threshold: {error:#}");
    }
    let sliding = policy.traffic_sliding_capping;
    if sliding.enabled {
        let end_ms = now.unix_timestamp_nanos() as i64 / 1_000_000;
        let hours = database
            .lock()
            .await
            .traffic_hours(None, end_ms - WINDOW_SECONDS * 1_000, end_ms)
            .await;
        match hours {
            Ok(hours) => {
                let uploaded = hours
                    .iter()
                    .fold(0_i64, |sum, hour| sum.wrapping_add(hour.uploaded));
                for (id, client) in clients {
                    if let Err(error) = set_limit(client, sliding, uploaded).await {
                        crate::journal::log_warning!("Traffic limit for {id}: {error:#}");
                    }
                }
            }
            Err(error) => crate::journal::log_warning!("Read rolling traffic window: {error:#}"),
        }
    }
}

async fn check_daily_threshold(
    database: &Mutex<Database>,
    limit: i64,
    now: OffsetDateTime,
    notifications: &Notifications,
) -> Result<()> {
    let now = now.to_offset(UtcOffset::local_offset_at(now).unwrap_or(UtcOffset::UTC));
    let start = now.replace_time(Time::MIDNIGHT).unix_timestamp();
    let mut database = database.lock().await;
    let hours = database
        .traffic_hours(None, start * 1_000, (start + WINDOW_SECONDS) * 1_000 - 1)
        .await?;
    let uploaded = hours
        .iter()
        .fold(0_i64, |sum, hour| sum.wrapping_add(hour.uploaded));
    if uploaded >= limit {
        let date = now.date();
        let date_time = format!(
            "{date} {:02}:{:02}:{:02}",
            now.hour(),
            now.minute(),
            now.second()
        );
        let title = json!({"key": "MODULE_AMM_TRAFFIC_MONITORING_TRAFFIC_ALERT_TITLE", "params": [date.to_string()]});
        let content = json!({"key": "MODULE_AMM_TRAFFIC_MONITORING_TRAFFIC_ALERT_DESCRIPTION", "params": [
            date_time, binary_bytes(uploaded), binary_bytes(limit)
        ]});
        let created = database
            .publish_alert(
                &NewAlert {
                    create_at_ms: (now.unix_timestamp_nanos() / 1_000_000) as i64,
                    level: AlertLevel::Warn,
                    identifier: &format!("dataTrafficCapping-{start}"),
                    title: &title,
                    content: &content,
                },
                AlertDeduplication::IncludingRead,
            )
            .await?;
        drop(database);
        if created {
            let title = format!(
                "[PeerBanHelper/WARN] {}",
                notifications.language.render(&title)
            );
            let body = notifications.language.render(&content);
            for provider in read_providers(&notifications.config_path)?.into_values() {
                if let Err(error) = provider
                    .send(&title, &body, AlertLevel::Warn, &notifications.proxy)
                    .await
                {
                    crate::journal::log_warning!("Push channel {}: {error}", provider.kind);
                }
            }
        }
    }
    Ok(())
}

fn binary_bytes(bytes: i64) -> String {
    if bytes < 1_024 {
        return format!("{bytes} B");
    }
    let mut value = bytes;
    let mut unit = 0;
    let mut shift = 40;
    while shift >= 0 && bytes > (0x0fff_cccc_cccc_cccc_i64 >> shift) {
        value >>= 10;
        unit += 1;
        shift -= 10;
    }
    format!(
        "{:.1} {}iB",
        value as f64 / 1_024.0,
        ["K", "M", "G", "T", "P", "E"][unit]
    )
}

fn hour_start(now: OffsetDateTime) -> i64 {
    let offset = i64::from(UtcOffset::local_offset_at(now).map_or(0, UtcOffset::whole_seconds));
    let local_seconds = now.unix_timestamp() + offset;
    (local_seconds.div_euclid(HOUR_SECONDS) * HOUR_SECONDS - offset) * 1_000
}

async fn statistics(client: &Client) -> Result<(i64, i64)> {
    match client {
        Client::Qbittorrent(client) => {
            client.login().await?;
            let stats = client.traffic_statistics().await?;
            Ok((stats.uploaded, stats.downloaded))
        }
        Client::Transmission(client) => {
            client.login().await?;
            let stats = client.traffic_statistics().await?;
            Ok((stats.uploaded_bytes, stats.downloaded_bytes))
        }
        Client::Deluge(client) => {
            client.login().await?;
            let stats = client.traffic_statistics().await?;
            Ok((stats.uploaded, stats.downloaded))
        }
        Client::BiglyBt(client) => {
            client.login().await?;
            let stats = client.traffic_statistics().await?;
            Ok((stats.uploaded, stats.downloaded))
        }
        Client::BitComet(client) => {
            client.login().await?;
            let stats = client.traffic_statistics().await?;
            Ok((stats.uploaded, stats.downloaded))
        }
        Client::Aria2Next(client) => {
            client.login().await?;
            Ok((0, 0))
        }
    }
}

async fn set_limit(client: &Client, window: SlidingWindow, uploaded: i64) -> Result<()> {
    match client {
        Client::Qbittorrent(client) => {
            client.login().await?;
            let mut limits = client.speed_limits().await?;
            limits.upload = window.upload_limit(limits.upload, uploaded)?;
            client.set_speed_limits(limits).await?;
        }
        Client::Transmission(client) => {
            client.login().await?;
            let mut limits = client.speed_limits().await?;
            limits.upload = window.upload_limit(limits.upload, uploaded)?;
            client.set_speed_limits(limits).await?;
        }
        Client::BiglyBt(client) => {
            client.login().await?;
            let mut limits = client.speed_limits().await?;
            limits.upload = window.upload_limit(limits.upload, uploaded)?;
            client.set_speed_limits(limits).await?;
        }
        Client::BitComet(client) => {
            client.login().await?;
            if client.supports_traffic_statistics().await? {
                let mut limits = client.speed_limits().await?;
                limits.upload = window.upload_limit(limits.upload, uploaded)?;
                client.set_speed_limits(limits).await?;
            }
        }
        Client::Deluge(_) | Client::Aria2Next(_) => {}
    }
    Ok(())
}
