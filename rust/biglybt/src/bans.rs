use std::collections::HashSet;
use std::net::IpAddr;

use reqwest::{Method, StatusCode};
use serde::{Deserialize, Serialize};

use crate::{BiglyBt, Error};

const BAN_PATH: &str = "v2/bans";

#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BanStatus {
    pub instance: String,
    pub revision: u64,
    pub filter_enabled: bool,
}

#[derive(Default)]
pub(crate) struct SyncState {
    desired: Option<Vec<String>>,
    confirmed: Option<BanStatus>,
}

#[derive(Serialize)]
struct BanUpdate<'a> {
    #[serde(skip_serializing_if = "Option::is_none")]
    instance: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    revision: Option<u64>,
    added: &'a [String],
    removed: &'a [String],
}

impl BiglyBt {
    pub async fn ban_status(&self) -> Result<BanStatus, Error> {
        self.get(BAN_PATH, "ban status").await
    }

    /// Reconciles the complete desired set using a revisioned delta when possible.
    pub async fn sync_bans(&self, full: &[IpAddr], force_full: bool) -> Result<(), Error> {
        self.ensure_writable()?;
        let mut next = self.address_policy.ban_rules(full, true);
        next.sort_unstable();
        let mut state = self.bans.lock().await;
        let previous = state.desired.replace(next);
        let confirmed = state.confirmed.take();
        let desired = state
            .desired
            .as_deref()
            .expect("desired list was just installed");

        let status = if self.config.increment_ban
            && !force_full
            && let (Some(previous), Some(base)) = (previous, confirmed)
        {
            let added = difference(desired, &previous);
            let removed = difference(&previous, desired);
            let update = BanUpdate {
                instance: Some(&base.instance),
                revision: Some(base.revision),
                added: &added,
                removed: &removed,
            };
            match self.update(Method::PATCH, update).await {
                Err(Error::Status {
                    status: StatusCode::CONFLICT,
                    ..
                }) => self.replace(desired).await?,
                result => result?,
            }
        } else {
            self.replace(desired).await?
        };
        let filter_enabled = status.filter_enabled;
        state.confirmed = Some(status);
        if !filter_enabled {
            return Err(Error::FilterDisabled);
        }
        Ok(())
    }

    pub(crate) async fn recover_bans(&self) -> Result<BanStatus, Error> {
        let mut state = self.bans.lock().await;
        let current = self.ban_status().await?;
        let drifted = state.confirmed.as_ref().is_none_or(|confirmed| {
            confirmed.instance != current.instance || confirmed.revision != current.revision
        });
        if !self.read_only && drifted && state.desired.is_some() {
            state.confirmed = None;
            let desired = state.desired.as_deref().expect("checked desired list");
            let status = self.replace(desired).await?;
            state.confirmed = Some(status.clone());
            return Ok(status);
        }
        Ok(current)
    }

    async fn replace(&self, desired: &[String]) -> Result<BanStatus, Error> {
        self.update(
            Method::PUT,
            BanUpdate {
                instance: None,
                revision: None,
                added: desired,
                removed: &[],
            },
        )
        .await
    }

    async fn update(&self, method: Method, update: BanUpdate<'_>) -> Result<BanStatus, Error> {
        self.get_json(
            self.request(method, BAN_PATH).json(&update),
            "ban synchronization",
        )
        .await
    }
}

fn difference(current: &[String], previous: &[String]) -> Vec<String> {
    let previous: HashSet<&str> = previous.iter().map(String::as_str).collect();
    current
        .iter()
        .filter(|item| !previous.contains(item.as_str()))
        .cloned()
        .collect()
}
