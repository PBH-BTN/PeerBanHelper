//! Periodic HTTP reachability measurements displayed by the settings page.

use std::time::Duration;

use pbh_network::ProxyPolicy;
use reqwest::Client;
use serde::Serialize;
use tokio::sync::watch;
use tokio::task::JoinHandle;

const CHINA: &str = "https://www.qq.com/";
const GLOBAL: &str = "https://www.google.com/generate_204";
const INTERVAL: Duration = Duration::from_secs(3_600);

#[derive(Clone, Copy, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Reachability {
    access_to_china_network: Option<bool>,
    access_to_global_network: Option<bool>,
}

pub(crate) struct Monitor {
    snapshot: watch::Receiver<Reachability>,
    worker: Option<JoinHandle<()>>,
}

impl Monitor {
    pub(crate) fn start(proxy: &ProxyPolicy, enabled: bool) -> reqwest::Result<Self> {
        let (published, snapshot) = watch::channel(Reachability::default());
        let disabled = std::env::var("PBH_DISABLE_NETWORK_PROBES")
            .is_ok_and(|value| value.eq_ignore_ascii_case("true"));
        let worker = if enabled && !disabled {
            let client = proxy
                .apply(Client::builder())
                .timeout(Duration::from_secs(10))
                .build()?;
            Some(tokio::spawn(async move {
                let mut tick = tokio::time::interval(INTERVAL);
                loop {
                    tick.tick().await;
                    let (china, global) =
                        tokio::join!(reachable(&client, CHINA), reachable(&client, GLOBAL));
                    published.send_replace(Reachability {
                        access_to_china_network: Some(china),
                        access_to_global_network: Some(global),
                    });
                }
            }))
        } else {
            None
        };
        Ok(Self { snapshot, worker })
    }

    pub(crate) fn snapshot(&self) -> watch::Receiver<Reachability> {
        self.snapshot.clone()
    }
}

impl Drop for Monitor {
    fn drop(&mut self) {
        if let Some(worker) = &self.worker {
            worker.abort();
        }
    }
}

async fn reachable(client: &Client, url: &str) -> bool {
    client
        .head(url)
        .send()
        .await
        .is_ok_and(|response| response.status().is_success())
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{Router, http::StatusCode, routing::head};
    use tokio::net::TcpListener;

    #[tokio::test]
    async fn measures_head_responses_with_independent_results() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let endpoint = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            let routes = Router::new()
                .route("/ok", head(|| async { StatusCode::NO_CONTENT }))
                .route("/down", head(|| async { StatusCode::SERVICE_UNAVAILABLE }));
            axum::serve(listener, routes).await.unwrap();
        });
        let client = Client::builder().no_proxy().build().unwrap();
        assert!(reachable(&client, &format!("{endpoint}/ok")).await);
        assert!(!reachable(&client, &format!("{endpoint}/down")).await);
        server.abort();
    }
}
