mod payload;

use std::collections::BTreeMap;
use std::marker::PhantomData;
use std::time::Duration;

use flate2::Compression;
use flate2::write::GzEncoder;
use reqwest::Url;
use reqwest::header::{CONTENT_ENCODING, CONTENT_TYPE};
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::{BtnClient, Error, PollingConfig, PollingSchedule, check_response};

pub use payload::{
    BanHistory, LegacyBan, LegacyBanEvent, LegacyPeer, PeerHistory, Swarm, TorrentIdentifier,
};

pub const BAN_HISTORY_CURSOR_KEY: &str = "BtnAbilitySubmitBans.cursor";
pub const SWARM_CURSOR_KEY: &str = "BtnAbilitySubmitSwarm.cursor";
pub const PEER_HISTORY_CURSOR_KEY: &str = "btn.submithistory.timestamp";
pub const BAN_HISTORY_PAGE_SIZE: u32 = 100;
pub const SWARM_PAGE_SIZE: u32 = 1_000;
pub const PEER_HISTORY_PAGE_SIZE: u32 = 5_000;
const PEER_HISTORY_MAX_AGE_MS: i64 = 30 * 24 * 60 * 60 * 1000;

/// Existing installations store a scalar epoch-milliseconds peer history cursor.
pub fn peer_history_start(saved: Option<&str>, now_ms: i64) -> i64 {
    saved
        .and_then(|value| value.parse::<i64>().ok())
        .filter(|time| *time >= now_ms.saturating_sub(PEER_HISTORY_MAX_AGE_MS))
        .unwrap_or(now_ms)
}

/// The HTTP acknowledgement is the caller's boundary for advancing persisted cursors.
#[derive(Clone, Default)]
pub struct Reports {
    pub ban_history: Option<Report<BanHistory>>,
    pub swarm: Option<Report<Swarm>>,
    pub peer_history: Option<Report<PeerHistory>>,
    pub legacy_bans: Option<Report<LegacyBanEvent>>,
    pub legacy_peers: Option<Report<LegacyPeer>>,
}

#[derive(Clone)]
pub struct Report<Row> {
    pub schedule: PollingSchedule,
    client: BtnClient,
    endpoint: Url,
    kind: ReportKind,
    proof: Option<Url>,
    row: PhantomData<fn(Row)>,
}

#[derive(Clone, Copy)]
enum ReportKind {
    BanHistory,
    Swarm,
    PeerHistory,
    LegacyBans,
    LegacyPeers,
}

impl ReportKind {
    fn ability(self) -> &'static str {
        match self {
            Self::BanHistory | Self::LegacyBans => "submit_bans",
            Self::Swarm => "submit_swarm",
            Self::PeerHistory => "submit_histories",
            Self::LegacyPeers => "submit_peers",
        }
    }

    fn legacy_snapshot(self) -> bool {
        matches!(self, Self::LegacyBans | Self::LegacyPeers)
    }

    fn proof_kind(self) -> &'static str {
        match self {
            Self::PeerHistory => "submit_history",
            kind => kind.ability(),
        }
    }
}

#[derive(Deserialize)]
struct Config {
    #[serde(flatten)]
    timing: PollingConfig,
    endpoint: String,
    #[serde(default)]
    pow_captcha: bool,
}

impl Reports {
    pub(crate) fn from_config(
        client: &BtnClient,
        abilities: &Map<String, Value>,
        legacy: bool,
        proof_endpoint: Option<&Url>,
    ) -> Result<Self, Error> {
        if !client.submit {
            return Ok(Self::default());
        }
        let peer_history =
            Report::from_config(client, abilities, ReportKind::PeerHistory, proof_endpoint)?;
        let mut reports = Self {
            peer_history,
            ..Self::default()
        };
        if legacy {
            reports.legacy_bans =
                Report::from_config(client, abilities, ReportKind::LegacyBans, None)?;
            reports.legacy_peers =
                Report::from_config(client, abilities, ReportKind::LegacyPeers, None)?;
        } else {
            reports.ban_history =
                Report::from_config(client, abilities, ReportKind::BanHistory, proof_endpoint)?;
            reports.swarm =
                Report::from_config(client, abilities, ReportKind::Swarm, proof_endpoint)?;
        }
        Ok(reports)
    }
}

impl<Row> Report<Row> {
    fn from_config(
        client: &BtnClient,
        abilities: &Map<String, Value>,
        kind: ReportKind,
        proof_endpoint: Option<&Url>,
    ) -> Result<Option<Self>, Error> {
        let ability = kind.ability();
        let Some(value) = abilities.get(ability) else {
            return Ok(None);
        };
        let config: Config =
            serde_json::from_value(value.clone()).map_err(|_| Error::InvalidResponse(ability))?;
        let schedule = config.timing.parse(ability)?;
        let proof = proof_endpoint
            .filter(|_| config.pow_captcha && !kind.legacy_snapshot())
            .cloned();
        Ok(Some(Self {
            schedule,
            client: client.clone(),
            endpoint: client.endpoint(&config.endpoint, ability)?,
            kind,
            proof,
            row: PhantomData,
        }))
    }

    /// Add a random delay sampled from `schedule.random_initial_delay` to this minimum.
    pub fn initial_delay_floor(&self) -> Duration {
        if self.kind.legacy_snapshot() {
            self.schedule.interval
        } else {
            Duration::ZERO
        }
    }

    async fn send_payload(&self, payload: impl Serialize) -> Result<(), Error> {
        let ability = self.kind.ability();
        let encoding_error = || Error::ReportEncoding(ability);
        let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
        serde_json::to_writer(&mut encoder, &payload).map_err(|_| encoding_error())?;
        let request = self
            .client
            .http
            .post(self.endpoint.clone())
            .header(CONTENT_TYPE, "application/json")
            .header(CONTENT_ENCODING, "gzip")
            .body(encoder.finish().map_err(|_| encoding_error())?);
        let request = match &self.proof {
            Some(endpoint) => {
                self.client
                    .with_proof(request, true, Some(endpoint), self.kind.proof_kind())
                    .await?
            }
            None => request,
        };
        let response = self.client.send_with_auth_retry(request, ability).await?;
        // Swarm reports historically acknowledge 3xx without requiring a response body.
        // The BTN client never forwards credentials or report payloads to redirect targets.
        if matches!(self.kind, ReportKind::Swarm) && response.status().is_redirection() {
            return Ok(());
        }
        check_response(response).await?;
        Ok(())
    }
}

#[derive(Serialize)]
struct Bans<T> {
    #[serde(skip_serializing_if = "Option::is_none")]
    populate_time: Option<i64>,
    bans: T,
}

#[derive(Serialize)]
struct Swarms<'a> {
    swarms: &'a [Swarm],
}

#[derive(Serialize)]
struct Peers<T> {
    populate_time: i64,
    peers: T,
}

impl Report<BanHistory> {
    /// A page containing only malformed rows is acknowledged with an empty `bans` array.
    pub async fn send(&self, rows: &[BanHistory]) -> Result<usize, Error> {
        self.send_payload(Bans {
            populate_time: None,
            bans: rows,
        })
        .await?;
        Ok(rows.len())
    }
}

impl Report<Swarm> {
    pub async fn send(&self, rows: &[Swarm]) -> Result<usize, Error> {
        if !rows.is_empty() {
            self.send_payload(Swarms { swarms: rows }).await?;
        }
        Ok(rows.len())
    }
}

impl Report<PeerHistory> {
    pub async fn send(&self, now_ms: i64, rows: &[PeerHistory]) -> Result<usize, Error> {
        if !rows.is_empty() {
            self.send_payload(Peers {
                populate_time: now_ms,
                peers: rows,
            })
            .await?;
        }
        Ok(rows.len())
    }
}

impl Report<LegacyPeer> {
    pub async fn send(&self, now_ms: i64, rows: &[LegacyPeer]) -> Result<usize, Error> {
        if !rows.is_empty() {
            self.send_payload(Peers {
                populate_time: now_ms,
                peers: rows,
            })
            .await?;
        }
        Ok(rows.len())
    }
}

impl Report<LegacyBanEvent> {
    pub async fn send(
        &self,
        now_ms: i64,
        last_report_ms: i64,
        events: &[LegacyBanEvent],
    ) -> Result<usize, Error> {
        Ok(self
            .send_acknowledged(now_ms, last_report_ms, events)
            .await?
            .len())
    }

    /// Return the IDs actually submitted in an acknowledged batch.
    pub async fn send_acknowledged<'a>(
        &self,
        now_ms: i64,
        last_report_ms: i64,
        events: &'a [LegacyBanEvent],
    ) -> Result<Vec<&'a str>, Error> {
        let mut unique = BTreeMap::new();
        for event in events {
            let identity = serde_json::to_vec(&event.ban.peer)
                .map_err(|_| Error::ReportEncoding(self.kind.ability()))?;
            unique.insert(identity, event);
        }
        let bans = unique
            .into_values()
            .filter_map(|event| {
                (event.ban_at > last_report_ms && !event.disconnect && !event.exclude_from_report)
                    .then_some(&event.ban)
            })
            .collect::<Vec<_>>();
        let acknowledged = bans.iter().map(|ban| ban.ban_unique_id.as_str()).collect();
        if !bans.is_empty() {
            self.send_payload(Bans {
                populate_time: Some(now_ms),
                bans,
            })
            .await?;
        }
        Ok(acknowledged)
    }
}
