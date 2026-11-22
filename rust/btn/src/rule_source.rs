use std::sync::{Arc, RwLock};

use reqwest::{Response, StatusCode, Url};
use serde::Deserialize;
use serde_json::Value;
use tokio::sync::Mutex;

use crate::{BtnClient, Error, PollingConfig, PollingSchedule};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Refresh {
    Unchanged,
    Updated,
}

#[derive(Clone)]
pub(crate) struct RuleEndpoint {
    client: BtnClient,
    endpoint: Url,
    proof_endpoint: Option<Url>,
    require_pow: bool,
    kind: &'static str,
    pub(crate) schedule: PollingSchedule,
}

#[derive(Deserialize)]
struct Config {
    #[serde(flatten)]
    timing: PollingConfig,
    endpoint: String,
    #[serde(default)]
    pow_captcha: bool,
}

impl RuleEndpoint {
    pub(crate) fn from_config(
        client: BtnClient,
        ability: &Value,
        proof_endpoint: Option<Url>,
        kind: &'static str,
    ) -> Result<Self, Error> {
        let config: Config =
            serde_json::from_value(ability.clone()).map_err(|_| Error::InvalidResponse(kind))?;
        Ok(Self {
            endpoint: client.endpoint(&config.endpoint, kind)?,
            client,
            proof_endpoint,
            require_pow: config.pow_captcha,
            kind,
            schedule: config.timing.parse(kind)?,
        })
    }

    pub(crate) async fn fetch(&self, revision: Option<&str>) -> Result<Option<Response>, Error> {
        let mut endpoint = self.endpoint.clone();
        endpoint
            .query_pairs_mut()
            .append_pair("rev", revision.unwrap_or("initial"));
        let request = self.client.http.get(endpoint);
        let request = self
            .client
            .with_proof(
                request,
                self.require_pow,
                self.proof_endpoint.as_ref(),
                self.kind,
            )
            .await?;
        let response = self.client.send(request, self.kind).await?;
        Ok((response.status() != StatusCode::NO_CONTENT).then_some(response))
    }
}

pub(crate) struct CachedSnapshot<T> {
    snapshot: RwLock<Option<Arc<T>>>,
    pub(crate) refresh: Mutex<()>,
}

impl<T> Default for CachedSnapshot<T> {
    fn default() -> Self {
        Self {
            snapshot: RwLock::default(),
            refresh: Mutex::new(()),
        }
    }
}

impl<T> CachedSnapshot<T> {
    pub(crate) fn read(&self) -> Option<Arc<T>> {
        self.snapshot
            .read()
            .expect("BTN snapshot read lock")
            .clone()
    }

    pub(crate) fn update(&self, snapshot: T) {
        *self.snapshot.write().expect("BTN snapshot write lock") = Some(Arc::new(snapshot));
    }

    pub(crate) fn seed(&self, snapshot: T) -> bool {
        let mut cached = self.snapshot.write().expect("BTN snapshot write lock");
        if cached.is_some() {
            return false;
        }
        *cached = Some(Arc::new(snapshot));
        true
    }
}
