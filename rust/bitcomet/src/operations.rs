use std::net::IpAddr;

use base64::{Engine, engine::general_purpose::STANDARD};
use serde::Deserialize;
use serde_json::json;

use crate::{BitComet, Error};

const GET_CONNECTION: &str = "api/config/connection/get";
const SET_CONNECTION: &str = "api/config/connection/set";

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct TrafficStatistics {
    pub uploaded: i64,
    pub downloaded: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SpeedLimits {
    pub upload: i64,
    pub download: i64,
}

#[derive(Deserialize)]
struct ConnectionConfig {
    max_upload_speed: i64,
    max_download_speed: i64,
    listen_port_tcp: u16,
}

impl BitComet {
    /// BitComet unbans peers at the task level and replaces the global data file.
    pub async fn sync_bans(&self, full: &[IpAddr], removed: &[IpAddr]) -> Result<(), Error> {
        self.ensure_writable()?;
        let session = self.session().await?;
        let auth = Some(&session.authorization);
        if !removed.is_empty() {
            let ip_list: Vec<_> = removed.iter().map(ToString::to_string).collect();
            self.post_ok(
                "api/task/peers/unban_peers",
                auth,
                json!({
                    "ip_list": ip_list, "unban_range": "unban_peers_in_all_tasks",
                }),
            )
            .await?;
        }
        let content = self.address_policy.ban_rules(full, true).join("\n");
        self.post_ok(
            "api/config/ipfilter/upload",
            auth,
            json!({
                "import_type": "replace", "data_type": "data_file",
                "content_base64": STANDARD.encode(content),
            }),
        )
        .await?;
        Ok(())
    }

    pub async fn supports_traffic_statistics(&self) -> Result<bool, Error> {
        self.session()
            .await
            .map(|session| session.version >= (2, 20))
    }

    pub async fn traffic_statistics(&self) -> Result<TrafficStatistics, Error> {
        #[derive(Deserialize)]
        struct StatisticsResponse {
            value_list: Vec<Statistic>,
        }

        #[derive(Deserialize)]
        struct Statistic {
            token: String,
            value: String,
        }

        let session = self.session().await?;
        let prefix = if session.version >= (2, 21) { "" } else { "$" };
        let token_list: Vec<_> = [
            "G_TOTAL_DOWNLOAD_AUTO",
            "G_TOTAL_UPLOAD_AUTO",
            "G_SESSION_DOWNLOAD_AUTO",
            "G_SESSION_UPLOAD_AUTO",
            "G_TOTAL_DOWNLOAD_BYTE",
            "G_TOTAL_UPLOAD_BYTE",
            "G_SESSION_DOWNLOAD_BYTE",
            "G_SESSION_UPLOAD_BYTE",
        ]
        .into_iter()
        .map(|name| format!("{prefix}{{{name}}}"))
        .collect();
        let operation = "api/statistics_list/get";
        let response: StatisticsResponse = self
            .post_get(
                operation,
                Some(&session.authorization),
                json!({
                    "token_list": token_list,
                }),
            )
            .await?;

        let mut traffic = TrafficStatistics::default();
        let suffix = if session.version >= (2, 20) {
            "BYTE"
        } else {
            "AUTO"
        };
        let upload_token = format!("{{G_TOTAL_UPLOAD_{suffix}}}");
        let download_token = format!("{{G_TOTAL_DOWNLOAD_{suffix}}}");
        for statistic in response.value_list {
            let token = statistic.token.trim_start_matches('$');
            let target = if token == upload_token {
                &mut traffic.uploaded
            } else if token == download_token {
                &mut traffic.downloaded
            } else {
                continue;
            };
            *target = if suffix == "BYTE" {
                statistic
                    .value
                    .parse()
                    .map_err(|_| Error::InvalidResult { operation })?
            } else {
                human_readable_bytes(&statistic.value)
            };
        }
        Ok(traffic)
    }

    pub async fn speed_limits(&self) -> Result<SpeedLimits, Error> {
        let connection = self.connection_config().await?;
        Ok(SpeedLimits {
            upload: connection.max_upload_speed,
            download: connection.max_download_speed,
        })
    }

    pub async fn set_speed_limits(&self, limits: SpeedLimits) -> Result<(), Error> {
        self.write_connection(json!({
            "max_upload_speed": limits.upload.max(0),
            "max_download_speed": limits.download.max(0),
        }))
        .await
    }

    pub async fn listen_port(&self) -> Result<u16, Error> {
        self.connection_config()
            .await
            .map(|config| config.listen_port_tcp)
    }

    pub async fn set_listen_port(&self, port: u16) -> Result<(), Error> {
        self.write_connection(json!({"enable_listen_tcp": true, "listen_port_tcp": port}))
            .await
    }

    async fn write_connection(&self, config: serde_json::Value) -> Result<(), Error> {
        self.ensure_writable()?;
        let session = self.session().await?;
        self.post_ok(
            SET_CONNECTION,
            Some(&session.authorization),
            json!({
                "connection_config": config,
            }),
        )
        .await?;
        Ok(())
    }

    async fn connection_config(&self) -> Result<ConnectionConfig, Error> {
        #[derive(Deserialize)]
        struct ConnectionResponse {
            connection_config: ConnectionConfig,
        }

        let session = self.session().await?;
        self.post_get::<ConnectionResponse>(GET_CONNECTION, Some(&session.authorization), json!({}))
            .await
            .map(|response| response.connection_config)
    }
}

fn human_readable_bytes(text: &str) -> i64 {
    let Some((quantity, unit)) = text.split_once(' ') else {
        return 0;
    };
    let Ok(quantity) = quantity.parse::<f64>() else {
        return 0;
    };
    let Some(power) = ["B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB"]
        .iter()
        .position(|candidate| unit.eq_ignore_ascii_case(candidate))
    else {
        return 0;
    };
    (quantity * 1024_f64.powi(power as i32)) as i64
}
