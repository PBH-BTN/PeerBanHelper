use std::net::IpAddr;

use base64::Engine;
use base64::alphabet;
use base64::engine::{DecodePaddingMode, GeneralPurpose, GeneralPurposeConfig};
use pbh_core::AddressPolicy;
use serde::Deserialize;
use serde_json::json;

use crate::{Error, Transmission};

const PEER_ID_BASE64: GeneralPurpose = GeneralPurpose::new(
    &alphabet::STANDARD,
    GeneralPurposeConfig::new()
        .with_decode_padding_mode(DecodePaddingMode::Indifferent)
        .with_decode_allow_trailing_bits(true),
);

const FIELDS: &[&str] = &[
    "id",
    "hashString",
    "name",
    "peersConnected",
    "status",
    "totalSize",
    "peers",
    "rateDownload",
    "rateUpload",
    "peer-limit",
    "percentDone",
    "sizeWhenDone",
    "trackerList",
    "trackerStats",
    "isPrivate",
];

#[derive(Clone, Debug, PartialEq)]
pub struct Torrent {
    pub id: i64,
    pub hash: String,
    pub name: String,
    pub size: i64,
    pub completed_size: i64,
    pub progress: f64,
    pub private: bool,
    pub upload_speed: i64,
    pub download_speed: i64,
    pub peer_limit: Option<u32>,
    pub peers: Vec<Peer>,
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
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TorrentResponse {
    id: i64,
    hash_string: String,
    name: String,
    total_size: i64,
    size_when_done: i64,
    percent_done: f64,
    is_private: bool,
    rate_upload: i64,
    rate_download: i64,
    peers_connected: u32,
    #[serde(rename = "peer-limit")]
    peer_limit: Option<u32>,
    peers: Vec<PeerResponse>,
    tracker_list: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct PeerResponse {
    address: String,
    port: u16,
    client_name: Option<String>,
    #[serde(rename = "peer_id")]
    peer_id: Option<String>,
    #[serde(rename = "bytes_to_client")]
    bytes_to_client: Option<i64>,
    #[serde(rename = "bytes_to_peer")]
    bytes_to_peer: Option<i64>,
    rate_to_client: Option<i64>,
    rate_to_peer: Option<i64>,
    progress: f64,
    flag_str: Option<String>,
}

impl Transmission {
    pub async fn active_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.torrents(true, !self.config.ignore_private).await
    }

    pub async fn all_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.torrents(false, true).await
    }

    async fn torrents(
        &self,
        active_only: bool,
        include_private: bool,
    ) -> Result<Vec<Torrent>, Error> {
        #[derive(Deserialize)]
        struct TorrentGet {
            torrents: Vec<TorrentResponse>,
        }

        let response: TorrentGet = self
            .rpc
            .read("torrent-get", json!({"fields": FIELDS}))
            .await?;
        response
            .torrents
            .into_iter()
            .filter(|torrent| !active_only || torrent.is_active())
            .filter(|torrent| include_private || !torrent.is_private)
            .map(|torrent| torrent.into_snapshot(&self.address_policy))
            .collect()
    }
}

impl TorrentResponse {
    fn is_active(&self) -> bool {
        self.rate_download > 0 || self.rate_upload > 0 || self.peers_connected > 0
    }

    fn into_snapshot(self, policy: &AddressPolicy) -> Result<Torrent, Error> {
        let trackers = self
            .tracker_list
            .as_deref()
            .unwrap_or_default()
            .split("\n\n")
            .filter(|group| !group.trim().is_empty())
            .map(|group| {
                group
                    .lines()
                    .map(str::trim)
                    .filter(|url| !url.is_empty())
                    .map(str::to_owned)
                    .collect()
            })
            .collect();
        Ok(Torrent {
            id: self.id,
            hash: self.hash_string,
            name: self.name,
            size: self.total_size,
            completed_size: (self.size_when_done as f64 * self.percent_done) as i64,
            progress: self.percent_done,
            private: self.is_private,
            upload_speed: self.rate_upload,
            download_speed: self.rate_download,
            peer_limit: self.peer_limit,
            peers: self
                .peers
                .into_iter()
                .map(|peer| peer.into_snapshot(policy))
                .collect::<Result<_, _>>()?,
            trackers,
        })
    }
}

impl PeerResponse {
    fn into_snapshot(self, policy: &AddressPolicy) -> Result<Peer, Error> {
        let ip = self
            .address
            .parse::<IpAddr>()
            .map_err(|_| Error::InvalidPeerAddress)?;
        let (ip, port) = policy.translate_peer(ip, self.port);
        let peer_id = match self.peer_id {
            Some(id) => PEER_ID_BASE64
                .decode(id)
                .map_err(|_| Error::InvalidPeerId)?
                .into_iter()
                .map(char::from)
                .collect(),
            None => String::new(),
        };
        Ok(Peer {
            ip,
            port,
            raw_address: self.address,
            client_name: self.client_name,
            peer_id: Some(peer_id),
            download_speed: self.rate_to_client.unwrap_or(-1),
            upload_speed: self.rate_to_peer.unwrap_or(-1),
            downloaded: self.bytes_to_client.unwrap_or(-1),
            uploaded: self.bytes_to_peer.unwrap_or(-1),
            progress: self.progress,
            flags: self.flag_str,
        })
    }
}
