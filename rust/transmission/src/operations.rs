use serde::Deserialize;
use serde_json::json;

use crate::{Error, Transmission};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrafficStatistics {
    pub uploaded_bytes: i64,
    pub downloaded_bytes: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SpeedLimits {
    pub upload: i64,
    pub download: i64,
}

impl Transmission {
    pub async fn traffic_statistics(&self) -> Result<TrafficStatistics, Error> {
        #[derive(Deserialize)]
        #[serde(rename_all = "kebab-case")]
        struct SessionStatistics {
            cumulative_stats: TrafficStatistics,
        }

        self.rpc
            .read::<SessionStatistics>("session-stats", json!({}))
            .await
            .map(|stats| stats.cumulative_stats)
    }

    pub async fn speed_limits(&self) -> Result<SpeedLimits, Error> {
        #[derive(Deserialize)]
        #[serde(rename_all = "kebab-case")]
        struct Limits {
            speed_limit_down: i32,
            speed_limit_up: i32,
            speed_limit_down_enabled: bool,
            speed_limit_up_enabled: bool,
        }

        let limits: Limits = self
            .rpc
            .read(
                "session-get",
                json!({"fields": [
                    "speed-limit-down", "speed-limit-up",
                    "speed-limit-down-enabled", "speed-limit-up-enabled",
                ]}),
            )
            .await?;
        Ok(SpeedLimits {
            upload: if limits.speed_limit_up_enabled {
                i64::from(limits.speed_limit_up) * 1024
            } else {
                0
            },
            download: if limits.speed_limit_down_enabled {
                i64::from(limits.speed_limit_down) * 1024
            } else {
                0
            },
        })
    }

    pub async fn set_speed_limits(&self, limits: SpeedLimits) -> Result<(), Error> {
        self.ensure_writable()?;
        let (upload, upload_enabled) = encode_limit(limits.upload);
        let (download, download_enabled) = encode_limit(limits.download);
        self.rpc
            .write(
                "session-set",
                json!({
                    "alt-speed-enabled": false,
                    "alt-speed-time-enabled": false,
                    "speed-limit-down": download,
                    "speed-limit-up": upload,
                    "speed-limit-down-enabled": download_enabled,
                    "speed-limit-up-enabled": upload_enabled,
                }),
            )
            .await
    }

    pub async fn listen_port(&self) -> Result<u16, Error> {
        #[derive(Deserialize)]
        #[serde(rename_all = "kebab-case")]
        struct PeerPort {
            peer_port: u16,
        }

        self.rpc
            .read::<PeerPort>("session-get", json!({"fields": ["peer-port"]}))
            .await
            .map(|prefs| prefs.peer_port)
    }

    pub async fn set_listen_port(&self, port: u16) -> Result<(), Error> {
        self.ensure_writable()?;
        self.rpc
            .write(
                "session-set",
                json!({"peer-port-random-on-start": false, "peer-port": port}),
            )
            .await
    }

    pub async fn replace_trackers(&self, id: i64, tiers: &[Vec<String>]) -> Result<(), Error> {
        self.ensure_writable()?;
        let tracker_list = tiers
            .iter()
            .map(|tier| tier.join("\n"))
            .collect::<Vec<_>>()
            .join("\n\n");
        self.rpc
            .write(
                "torrent-set",
                json!({"ids": [id], "trackerList": tracker_list}),
            )
            .await
    }
}

fn encode_limit(bytes: i64) -> (i32, bool) {
    if bytes <= 0 {
        (1, false)
    } else {
        ((bytes / 1024) as i32, true)
    }
}
