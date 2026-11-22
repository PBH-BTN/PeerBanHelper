use std::collections::HashSet;
use std::net::{IpAddr, SocketAddr};

use futures_util::{StreamExt, TryStreamExt, stream};
use reqwest::StatusCode;
use serde::{Deserialize, Deserializer, de};
use serde_json::json;

use pbh_core::AddressPolicy;

use crate::{BitComet, Error, Session};

const TASKS_PER_PAGE: usize = 50;
const SUMMARY_CONCURRENCY: usize = 3;
const PEER_GROUPS: [&str; 2] = ["peers_connected", "ltseeds_connected"];

#[derive(Clone, Debug, PartialEq)]
pub struct Torrent {
    pub id: String,
    pub hash: String,
    pub name: String,
    pub size: i64,
    pub completed_size: i64,
    pub progress: f64,
    pub private: bool,
    pub upload_speed: i64,
    pub download_speed: i64,
}

#[derive(Clone, Debug, PartialEq)]
pub struct Peer {
    pub ip: IpAddr,
    pub port: u16,
    pub raw_address: String,
    pub client_name: Option<String>,
    pub peer_id: Option<String>,
    pub download_speed: i64,
    pub upload_speed: i64,
    pub downloaded: i64,
    pub uploaded: i64,
    pub progress: f64,
    pub flags: Option<String>,
    pub handshaking: bool,
}

#[derive(Deserialize)]
struct Page {
    tasks: Option<Vec<TaskKey>>,
}

#[derive(Deserialize)]
struct TaskKey {
    #[serde(deserialize_with = "task_id")]
    task_id: i64,
    #[serde(rename = "type")]
    kind: String,
}

#[derive(Deserialize)]
struct TaskSummary {
    task: TaskData,
    task_detail: TaskDetail,
    task_status: TaskStatus,
}

#[derive(Deserialize)]
struct TaskData {
    #[serde(deserialize_with = "task_id")]
    task_id: i64,
    task_name: String,
    selected_downloaded_size: i64,
    download_rate: i64,
    upload_rate: i64,
}

#[derive(Deserialize)]
struct TaskDetail {
    infohash: Option<String>,
    infohash_v2: Option<String>,
    total_size: i64,
    torrent_private: Option<bool>,
}

#[derive(Deserialize)]
struct TaskStatus {
    download_permillage: i32,
}

#[derive(Deserialize)]
struct PeerList {
    peers: Option<Vec<WirePeer>>,
}

#[derive(Default, Deserialize)]
#[serde(default)]
struct WirePeer {
    ip: String,
    client_type: Option<String>,
    remote_port: u16,
    permillage: i32,
    dl_rate: i64,
    up_rate: i64,
    dl_size: Option<i64>,
    up_size: Option<i64>,
    peer_id: Option<String>,
    group: String,
}

impl BitComet {
    pub async fn active_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.torrents("ACTIVE", !self.config.ignore_private).await
    }

    pub async fn all_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.torrents("ALL", true).await
    }

    async fn torrents(
        &self,
        state_group: &str,
        include_private: bool,
    ) -> Result<Vec<Torrent>, Error> {
        let session = self.session().await?;
        let mut seen = HashSet::new();
        let mut torrents = Vec::new();
        for start in (0..).step_by(TASKS_PER_PAGE) {
            let page: Page = self.post_get("api_v2/task_list/get", Some(&session.authorization), json!({
                "state_group": state_group, "sort_key": "", "sort_order": "unsorted",
                "tag_filter": "ALL", "task_type": "ALL", "keyword": "", "query_mode": "stateless",
                "validate_task_ids": [], "start": start, "limit": TASKS_PER_PAGE,
            })).await?;
            let mut tasks = page.tasks.unwrap_or_default();
            let last_page = tasks.len() < TASKS_PER_PAGE;
            tasks.retain(|task| seen.insert(task.task_id));
            if tasks.is_empty() {
                break;
            }
            let summaries: Vec<Option<Torrent>> =
                stream::iter(tasks.into_iter().filter(|task| task.kind == "BT"))
                    .map(|task| self.task_summary(&session, task.task_id))
                    .buffered(SUMMARY_CONCURRENCY)
                    .try_collect()
                    .await?;
            torrents.extend(
                summaries
                    .into_iter()
                    .flatten()
                    .filter(|torrent| include_private || !torrent.private),
            );
            if last_page {
                break;
            }
        }
        Ok(torrents)
    }

    async fn task_summary(&self, session: &Session, id: i64) -> Result<Option<Torrent>, Error> {
        let response: Result<TaskSummary, _> = self
            .post_get(
                "api/task/summary/get",
                Some(&session.authorization),
                json!({
                    "task_id": id.to_string(),
                }),
            )
            .await;
        match response {
            Ok(summary) => Ok(Some(summary.into())),
            Err(Error::Status {
                status: StatusCode::NOT_FOUND,
                ..
            }) => Ok(None),
            Err(error) => Err(error),
        }
    }

    pub async fn peers(&self, task_id: &str) -> Result<Vec<Peer>, Error> {
        let session = self.session().await?;
        let response: PeerList = self
            .post_get(
                "api/task/peers/get",
                Some(&session.authorization),
                json!({
                    "groups": PEER_GROUPS, "task_id": task_id, "max_count": 0,
                }),
            )
            .await?;
        Ok(response
            .peers
            .unwrap_or_default()
            .into_iter()
            .filter(|peer| PEER_GROUPS.contains(&peer.group.as_str()))
            .filter_map(|peer| peer.into_peer(&self.address_policy))
            .collect())
    }

    pub async fn trackers(&self, task_id: &str) -> Result<Vec<Vec<String>>, Error> {
        #[derive(Deserialize)]
        struct TrackerList {
            trackers: Vec<Tracker>,
        }

        #[derive(Deserialize)]
        struct Tracker {
            name: String,
        }

        let session = self.session().await?;
        let response: TrackerList = self
            .post_get(
                "api/task/trackers/get",
                Some(&session.authorization),
                json!({
                    "task_id": task_id, "max_count": i32::MAX.to_string(),
                }),
            )
            .await?;
        Ok(response
            .trackers
            .into_iter()
            .filter(|tracker| {
                ["http", "udp", "ws"]
                    .iter()
                    .any(|scheme| tracker.name.starts_with(scheme))
            })
            .map(|tracker| vec![tracker.name])
            .collect())
    }
}

impl From<TaskSummary> for Torrent {
    fn from(summary: TaskSummary) -> Self {
        Self {
            id: summary.task.task_id.to_string(),
            hash: summary
                .task_detail
                .infohash
                .or(summary.task_detail.infohash_v2)
                .unwrap_or_default(),
            name: summary.task.task_name,
            size: summary.task_detail.total_size,
            completed_size: summary.task.selected_downloaded_size,
            progress: f64::from(summary.task_status.download_permillage) / 1000.0,
            private: summary.task_detail.torrent_private.unwrap_or(false),
            upload_speed: summary.task.upload_rate,
            download_speed: summary.task.download_rate,
        }
    }
}

impl WirePeer {
    fn into_peer(self, policy: &AddressPolicy) -> Option<Peer> {
        let (ip, port, raw_address) = split_address(&self.ip, self.remote_port)?;
        let (ip, port) = policy.translate_peer(ip, port);
        let peer_id = decode_hex(self.peer_id.as_deref().unwrap_or_default())?;
        Some(Peer {
            ip,
            port,
            raw_address,
            client_name: self.client_type,
            peer_id: Some(peer_id),
            download_speed: self.dl_rate,
            upload_speed: self.up_rate,
            downloaded: self.dl_size.unwrap_or(-1),
            uploaded: self.up_size.unwrap_or(-1),
            progress: f64::from(self.permillage) / 1000.0,
            flags: None,
            handshaking: self.dl_rate <= 0 && self.up_rate <= 0,
        })
    }
}

fn split_address(address: &str, fallback_port: u16) -> Option<(IpAddr, u16, String)> {
    let address = address.trim();
    if let Ok(socket) = address.parse::<SocketAddr>() {
        let (wire_host, _) = address.rsplit_once(':')?;
        return Some((socket.ip(), socket.port(), unbracket(wire_host).to_owned()));
    }
    let host = unbracket(address);
    Some((host.parse().ok()?, fallback_port, host.to_owned()))
}

fn unbracket(host: &str) -> &str {
    host.strip_prefix('[')
        .and_then(|host| host.strip_suffix(']'))
        .unwrap_or(host)
}

fn decode_hex(hex: &str) -> Option<String> {
    let padded;
    let hex = if hex.len().is_multiple_of(2) {
        hex
    } else {
        padded = format!("0{hex}");
        &padded
    };
    hex.as_bytes()
        .chunks_exact(2)
        .map(|pair| {
            let hi = char::from(pair[0]).to_digit(16)?;
            let lo = char::from(pair[1]).to_digit(16)?;
            Some(char::from((hi * 16 + lo) as u8))
        })
        .collect()
}

fn task_id<'de, D: Deserializer<'de>>(deserializer: D) -> Result<i64, D::Error> {
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum Id {
        Number(i64),
        Text(String),
    }

    match Id::deserialize(deserializer)? {
        Id::Number(id) => Ok(id),
        Id::Text(id) => id.parse().map_err(de::Error::custom),
    }
}
