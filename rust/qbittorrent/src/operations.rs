use std::collections::BTreeMap;

use semver::{Prerelease, Version};
use serde::Deserialize;
use serde::de::DeserializeOwned;
use serde_json::json;

use crate::{Error, Qbittorrent};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize)]
pub struct TrafficStatistics {
    #[serde(rename = "alltime_ul", default)]
    pub uploaded: i64,
    #[serde(rename = "alltime_dl", default)]
    pub downloaded: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize)]
pub struct SpeedLimits {
    #[serde(rename = "up_limit")]
    pub upload: i64,
    #[serde(rename = "dl_limit")]
    pub download: i64,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct TrackerTier {
    pub tier: i32,
    pub urls: Vec<String>,
}

impl Qbittorrent {
    /// Returns the negotiated range-ban capability for the current session.
    pub async fn supports_range_bans(&self) -> bool {
        self.login
            .lock()
            .await
            .version
            .as_ref()
            .is_some_and(|version| {
                let mut alpha = Version::new(5, 3, 0);
                alpha.pre = Prerelease::new("alpha1").expect("valid prerelease");
                let mut beta = Version::new(5, 2, 0);
                beta.pre = Prerelease::new("beta1").expect("valid prerelease");
                version.cmp_precedence(&alpha).is_ge() || version.cmp_precedence(&beta).is_eq()
            })
    }

    pub async fn traffic_statistics(&self) -> Result<TrafficStatistics, Error> {
        #[derive(Deserialize)]
        struct MainData {
            server_state: TrafficStatistics,
        }

        let data: MainData = self
            .json(self.get("sync/maindata"), "traffic statistics")
            .await?;
        if data.server_state.uploaded == 0 && data.server_state.downloaded == 0 {
            return Err(Error::TrafficCountersUnavailable);
        }
        Ok(data.server_state)
    }

    pub async fn speed_limits(&self) -> Result<SpeedLimits, Error> {
        self.preferences().await
    }

    pub async fn set_speed_limits(&self, limits: SpeedLimits) -> Result<(), Error> {
        let upload = limits.upload.max(0);
        let download = limits.download.max(0);
        self.set_preferences(json!({
            "up_limit": upload,
            "dl_limit": download,
            "alt_up_limit": upload,
            "alt_dl_limit": download,
            "limit_utp_rate": true,
            "limit_lan_peers": true,
            "scheduler_enabled": false,
        }))
        .await
    }

    pub async fn listen_port(&self) -> Result<u16, Error> {
        #[derive(Deserialize)]
        struct ListenPort {
            listen_port: u16,
        }

        self.preferences::<ListenPort>()
            .await
            .map(|preferences| preferences.listen_port)
    }

    pub async fn set_listen_port(&self, port: u16) -> Result<(), Error> {
        self.set_preferences(json!({"listen_port": port})).await
    }

    pub async fn trackers(&self, hash: &str) -> Result<Vec<TrackerTier>, Error> {
        #[derive(Deserialize)]
        struct TrackerResponse {
            tier: i32,
            url: String,
        }

        let request = self.get("torrents/trackers").query(&[("hash", hash)]);
        let response: Vec<TrackerResponse> = self.json(request, "torrent trackers").await?;
        let mut tiers = BTreeMap::<i32, Vec<String>>::new();
        for tracker in response {
            // The client returns DHT/PeX/LSD pseudo-trackers with a "**" prefix.
            if !tracker.url.starts_with("**") {
                tiers.entry(tracker.tier).or_default().push(tracker.url);
            }
        }
        Ok(tiers
            .into_iter()
            .map(|(tier, urls)| TrackerTier { tier, urls })
            .collect())
    }

    /// Replaces all tracker URLs following the legacy remove-then-add order.
    /// If adding fails after removing, the caller receives the error and can
    /// repeat the request using the desired tracker list.
    pub async fn replace_trackers(&self, hash: &str, desired: &[TrackerTier]) -> Result<(), Error> {
        self.ensure_writable()?;
        let existing = self.trackers(hash).await?;
        let removals = flatten_trackers(&existing, "|");
        let additions = flatten_trackers(desired, "\n");
        if !removals.is_empty() {
            let request = self
                .post("torrents/removeTrackers")
                .form(&[("hash", hash), ("urls", &removals)]);
            self.execute(request, "remove torrent trackers").await?;
        }
        if !additions.is_empty() {
            let request = self
                .post("torrents/addTrackers")
                .form(&[("hash", hash), ("urls", &additions)]);
            self.execute(request, "add torrent trackers").await?;
        }
        Ok(())
    }

    pub(crate) async fn preferences<T: DeserializeOwned>(&self) -> Result<T, Error> {
        self.json(self.get("app/preferences"), "application preferences")
            .await
    }
}

fn flatten_trackers(tiers: &[TrackerTier], separator: &str) -> String {
    tiers
        .iter()
        .flat_map(|tier| tier.urls.iter().map(String::as_str))
        .collect::<Vec<_>>()
        .join(separator)
}
