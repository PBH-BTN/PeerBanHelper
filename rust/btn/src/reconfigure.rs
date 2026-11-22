use serde::Deserialize;
use serde_json::{Map, Value};

use crate::{BtnClient, Error, PollingConfig, PollingSchedule, json};

const ABILITY: &str = "reconfigure";

#[derive(Clone)]
pub struct Reconfigure {
    pub schedule: PollingSchedule,
    pub version: String,
    client: BtnClient,
}

#[derive(Debug, Eq, PartialEq)]
pub enum ReconfigureResult {
    Unchanged,
    Changed { version: String },
    DisabledByServer,
}

#[derive(Deserialize)]
struct Config {
    #[serde(flatten)]
    timing: PollingConfig,
    version: String,
}

#[derive(Deserialize)]
struct Reply {
    ability: Map<String, Value>,
}

impl Reconfigure {
    pub(crate) fn from_config(client: BtnClient, ability: &Value) -> Result<Self, Error> {
        let config: Config =
            serde_json::from_value(ability.clone()).map_err(|_| Error::InvalidResponse(ABILITY))?;
        Ok(Self {
            schedule: config.timing.parse(ABILITY)?,
            version: config.version,
            client,
        })
    }

    /// Check the revision; the runtime can replace negotiated abilities on change.
    pub async fn check(&self) -> Result<ReconfigureResult, Error> {
        let request = self.client.http.get(self.client.base.clone());
        let response = self.client.send(request, ABILITY).await?;
        let mut reply: Reply = json(response, ABILITY).await?;
        let Some(ability) = reply.ability.remove(ABILITY) else {
            return Ok(ReconfigureResult::DisabledByServer);
        };
        let version = ability
            .get("version")
            .and_then(Value::as_str)
            .ok_or(Error::InvalidResponse(ABILITY))?;
        if version == self.version {
            Ok(ReconfigureResult::Unchanged)
        } else {
            Ok(ReconfigureResult::Changed {
                version: version.to_owned(),
            })
        }
    }
}
