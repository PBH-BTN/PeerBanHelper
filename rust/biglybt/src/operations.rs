use reqwest::header::{CONTENT_TYPE, HeaderMap, HeaderValue};
use reqwest::{Method, RequestBuilder};
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::{BiglyBt, Error, check_status};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize)]
pub struct TrafficStatistics {
    #[serde(rename = "overallDataBytesSent")]
    pub uploaded: i64,
    #[serde(rename = "overallDataBytesReceived")]
    pub downloaded: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct SpeedLimits {
    pub upload: i64,
    pub download: i64,
}

impl BiglyBt {
    pub async fn traffic_statistics(&self) -> Result<TrafficStatistics, Error> {
        self.get("statistics", "traffic statistics").await
    }

    pub async fn speed_limits(&self) -> Result<SpeedLimits, Error> {
        self.get("speedlimiter", "speed limits").await
    }

    pub async fn set_speed_limits(&self, limits: SpeedLimits) -> Result<(), Error> {
        self.write(
            self.request(Method::POST, "speedlimiter").json(&limits),
            "set speed limits",
        )
        .await
    }

    pub async fn listen_port(&self) -> Result<u16, Error> {
        #[derive(Deserialize)]
        struct ListenPort {
            port: u16,
        }

        self.get::<ListenPort>("listenport", "listen port")
            .await
            .map(|response| response.port)
    }

    pub async fn set_listen_port(&self, port: u16) -> Result<(), Error> {
        self.write(
            self.request(Method::POST, "listenport")
                .json(&json!({"port": port})),
            "set listen port",
        )
        .await
    }

    pub async fn replace_trackers(
        &self,
        hash: &str,
        trackers: &[Vec<String>],
    ) -> Result<(), Error> {
        let tiers = trackers
            .iter()
            .map(|tracker| tracker.join("\n"))
            .collect::<Vec<_>>()
            .join("\n\n");
        let headers =
            HeaderMap::from_iter([(CONTENT_TYPE, HeaderValue::from_static("text/plain"))]);
        let request = self
            .torrent_request(Method::PATCH, hash, "trackers")
            .headers(headers)
            .body(tiers.trim().to_owned());
        self.write(request, "set torrent trackers").await
    }

    async fn write(&self, request: RequestBuilder, operation: &'static str) -> Result<(), Error> {
        self.ensure_writable()?;
        check_status(self.send(request, operation).await?, operation)?;
        Ok(())
    }
}
