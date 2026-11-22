use std::collections::HashSet;
use std::net::IpAddr;

use serde::Deserialize;
use serde_json::{Value, json};

use pbh_core::AddressPolicy;

use crate::{Aria2Next, Error, wire::scalar};

const TASK_FIELDS: &[&str] = &[
    "gid",
    "status",
    "totalLength",
    "completedLength",
    "uploadLength",
    "bitfield",
    "downloadSpeed",
    "uploadSpeed",
    "infoHash",
    "numSeeders",
    "seeder",
    "pieceLength",
    "numPieces",
    "connections",
    "errorCode",
    "errorMessage",
    "followedBy",
    "following",
    "belongsTo",
    "dir",
    "bittorrent",
    "verifiedLength",
    "verifyIntegrityPending",
    "files",
];
const QUEUE_PAGE_SIZE: usize = 100;

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
    pub trackers: Vec<Vec<String>>,
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
#[serde(rename_all = "camelCase")]
struct TaskResponse {
    gid: String,
    info_hash: Option<String>,
    #[serde(default, deserialize_with = "scalar")]
    total_length: i64,
    #[serde(default, deserialize_with = "scalar")]
    completed_length: i64,
    #[serde(default, deserialize_with = "scalar")]
    upload_speed: i64,
    #[serde(default, deserialize_with = "scalar")]
    download_speed: i64,
    bittorrent: Option<BitTorrent>,
    files: Option<Vec<DownloadFile>>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct BitTorrent {
    #[serde(default)]
    announce_list: Vec<Vec<String>>,
    info: Option<Info>,
}

#[derive(Deserialize)]
struct Info {
    name: String,
}

#[derive(Deserialize)]
struct DownloadFile {
    path: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct PeerResponse {
    ip: String,
    #[serde(default, deserialize_with = "scalar")]
    port: u16,
    peer_client_name: Option<String>,
    peer_id: Option<String>,
    #[serde(default, deserialize_with = "scalar")]
    download_speed: i64,
    #[serde(default, deserialize_with = "scalar")]
    upload_speed: i64,
    #[serde(default, deserialize_with = "scalar")]
    downloaded: i64,
    #[serde(default, deserialize_with = "scalar")]
    uploaded: i64,
    #[serde(default, deserialize_with = "scalar")]
    progress: f64,
    #[serde(default, deserialize_with = "scalar")]
    am_choking: bool,
    #[serde(default, deserialize_with = "scalar")]
    am_interested: bool,
    #[serde(default, deserialize_with = "scalar")]
    peer_choking: bool,
    #[serde(default, deserialize_with = "scalar")]
    peer_interested: bool,
    #[serde(default, deserialize_with = "scalar")]
    incoming: bool,
    #[serde(default, deserialize_with = "scalar")]
    snubbed: bool,
    #[serde(default, deserialize_with = "scalar")]
    handshaking: bool,
}

impl Aria2Next {
    pub async fn active_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.tasks("aria2.tellActive", vec![json!(TASK_FIELDS)])
            .await
            .map(only_torrents)
    }

    pub async fn all_torrents(&self) -> Result<Vec<Torrent>, Error> {
        let mut torrents = self.active_torrents().await?;
        for method in ["aria2.tellWaiting", "aria2.tellStopped"] {
            let queue = match self.tasks(method, vec![json!(TASK_FIELDS)]).await {
                Ok(queue) => only_torrents(queue),
                Err(Error::Rejected {
                    code: -32602 | 1, ..
                }) => self.paged_torrents(method).await?,
                Err(error) => return Err(error),
            };
            torrents.extend(queue);
        }
        Ok(torrents)
    }

    async fn paged_torrents(&self, method: &'static str) -> Result<Vec<Torrent>, Error> {
        let mut torrents = Vec::new();
        let mut seen = HashSet::new();
        for offset in (0..).step_by(QUEUE_PAGE_SIZE) {
            let mut page = self
                .tasks(
                    method,
                    vec![json!(offset), json!(QUEUE_PAGE_SIZE), json!(TASK_FIELDS)],
                )
                .await?;
            let complete = page.len() < QUEUE_PAGE_SIZE;
            page.retain(|task| seen.insert(task.gid.clone()));
            if page.is_empty() {
                break;
            }
            torrents.extend(only_torrents(page));
            if complete {
                break;
            }
        }
        Ok(torrents)
    }

    async fn tasks(
        &self,
        method: &'static str,
        params: Vec<Value>,
    ) -> Result<Vec<TaskResponse>, Error> {
        self.rpc(method, params).await
    }

    pub async fn peers(&self, gid: &str) -> Result<Vec<Peer>, Error> {
        let peers: Vec<PeerResponse> = self.rpc("aria2.getPeers", vec![json!(gid)]).await?;
        peers
            .into_iter()
            .map(|peer| peer.into_peer(&self.address_policy))
            .collect()
    }
}

fn only_torrents(tasks: Vec<TaskResponse>) -> Vec<Torrent> {
    tasks
        .into_iter()
        .filter_map(TaskResponse::into_torrent)
        .collect()
}

impl TaskResponse {
    fn into_torrent(self) -> Option<Torrent> {
        let bittorrent = self.bittorrent?;
        let name = if let Some(info) = bittorrent.info {
            info.name
        } else if let Some(file) = self.files.and_then(|files| files.into_iter().next()) {
            file.path
        } else {
            format!("<METADATA> {}", self.gid)
        };
        Some(Torrent {
            id: self.gid,
            hash: self.info_hash.unwrap_or_default(),
            name,
            size: self.total_length,
            completed_size: self.completed_length,
            progress: if self.total_length > 0 {
                self.completed_length as f64 / self.total_length as f64
            } else {
                0.0
            },
            private: false,
            upload_speed: self.upload_speed,
            download_speed: self.download_speed,
            trackers: bittorrent.announce_list,
        })
    }
}

impl PeerResponse {
    fn into_peer(self, policy: &AddressPolicy) -> Result<Peer, Error> {
        let ip = self.ip.parse().map_err(|_| Error::InvalidPeerAddress)?;
        let (ip, port) = policy.translate_peer(ip, self.port);
        let peer_id = decode_id(self.peer_id.as_deref().unwrap_or_default())?;
        let flags = self.flags();
        Ok(Peer {
            ip,
            port,
            raw_address: self.ip,
            client_name: Some(self.peer_client_name.unwrap_or_default()),
            peer_id: Some(peer_id),
            download_speed: self.download_speed,
            upload_speed: self.upload_speed,
            downloaded: self.downloaded,
            uploaded: self.uploaded,
            progress: self.progress,
            flags: Some(flags),
            handshaking: self.handshaking,
        })
    }

    fn flags(&self) -> String {
        let download = transfer_flag(self.am_interested, self.peer_choking, ["d", "D", "K"]);
        let upload = transfer_flag(self.peer_interested, self.am_choking, ["u", "U", "?"]);
        [
            download,
            upload,
            self.snubbed.then_some("S"),
            self.incoming.then_some("I"),
        ]
        .into_iter()
        .flatten()
        .collect::<Vec<_>>()
        .join(" ")
    }
}

fn transfer_flag(interested: bool, choked: bool, flags: [&str; 3]) -> Option<&str> {
    match (interested, choked) {
        (true, true) => Some(flags[0]),
        (true, false) => Some(flags[1]),
        (false, false) => Some(flags[2]),
        (false, true) => None,
    }
}

fn decode_id(input: &str) -> Result<String, Error> {
    let mut output = String::with_capacity(input.len());
    let mut characters = input.chars();
    while let Some(ch) = characters.next() {
        match ch {
            '%' => {
                let high = characters
                    .next()
                    .and_then(|ch| ch.to_digit(16))
                    .ok_or(Error::InvalidPeerId)?;
                let low = characters
                    .next()
                    .and_then(|ch| ch.to_digit(16))
                    .ok_or(Error::InvalidPeerId)?;
                output.push(char::from((high * 16 + low) as u8));
            }
            '+' => output.push(' '),
            _ => output.push(ch),
        }
    }
    Ok(output)
}
