use std::net::IpAddr;

use reqwest::{Method, StatusCode};
use serde::Deserialize;

use pbh_core::AddressPolicy;

use crate::{BiglyBt, Error, check_status, decode};

const ACTIVE_FILTERS: [u8; 3] = [4, 5, 8];

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
    /// Present for v2 batch snapshots; individual queries supply peers when absent.
    pub peers: Option<Vec<Peer>>,
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
    pub protocol: Option<String>,
}

#[derive(Deserialize)]
struct SnapshotEntry {
    download: DownloadResponse,
    peers: Vec<Option<PeerResponse>>,
}

#[derive(Deserialize)]
struct DownloadResponse {
    name: String,
    torrent: TorrentMeta,
    stats: DownloadStats,
    trackers: Option<Vec<Vec<String>>>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TorrentMeta {
    info_hash: String,
    size: i64,
    #[serde(default)]
    private_torrent: bool,
}

#[derive(Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct DownloadStats {
    remaining_bytes: i64,
    completed_in_thousand_notation: i32,
    rt_download_speed: i64,
    rt_upload_speed: i64,
}

#[derive(Deserialize)]
struct PeerManagerResponse {
    peers: Option<Vec<Option<PeerResponse>>>,
}

#[derive(Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct PeerResponse {
    ip: Option<String>,
    port: u16,
    client: Option<String>,
    peer_id: Option<String>,
    stats: Option<PeerStats>,
    state: u8,
    percent_done_in_thousand_notation: i32,
    peer_source: Option<String>,
    protocol: Option<String>,
    interesting: bool,
    interested: bool,
    choked: bool,
    choking: bool,
    incoming: bool,
    use_crypto: bool,
    snubbed: bool,
    optimistic_unchoke: bool,
}

#[derive(Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct PeerStats {
    rt_download_speed: i64,
    rt_upload_speed: i64,
    total_received: i64,
    total_sent: i64,
}

impl BiglyBt {
    pub async fn active_torrents(&self) -> Result<Vec<Torrent>, Error> {
        let operation = "batch peer snapshot";
        let request = self
            .request(Method::GET, "v2/snapshot")
            .query(&[("includePrivate", !self.config.ignore_private)]);
        let response = self.send(request, operation).await?;
        if response.status() == StatusCode::INSUFFICIENT_STORAGE {
            return self
                .downloads(&ACTIVE_FILTERS, !self.config.ignore_private)
                .await;
        }
        let entries: Vec<SnapshotEntry> =
            decode(check_status(response, operation)?, operation).await?;
        Ok(entries
            .into_iter()
            .map(|entry| {
                let mut torrent = Torrent::from(entry.download);
                torrent.peers = Some(read_peers(entry.peers, &self.address_policy));
                torrent
            })
            .collect())
    }

    pub async fn all_torrents(&self) -> Result<Vec<Torrent>, Error> {
        self.downloads(&[], true).await
    }

    async fn downloads(
        &self,
        filters: &[u8],
        include_private: bool,
    ) -> Result<Vec<Torrent>, Error> {
        let filters: Vec<_> = filters.iter().map(|filter| ("filter", filter)).collect();
        let request = self.request(Method::GET, "downloads").query(&filters);
        let entries: Vec<DownloadResponse> = self.get_json(request, "downloads").await?;
        Ok(entries
            .into_iter()
            .filter(|entry| include_private || !entry.torrent.private_torrent)
            .map(Torrent::from)
            .collect())
    }

    pub async fn peers(&self, hash: &str) -> Result<Vec<Peer>, Error> {
        let operation = "torrent peers";
        let response = self
            .send(self.torrent_request(Method::GET, hash, "peers"), operation)
            .await?;
        if response.status() == StatusCode::NOT_FOUND {
            return Ok(Vec::new());
        }
        let peers: Option<PeerManagerResponse> =
            decode(check_status(response, operation)?, operation).await?;
        Ok(read_peers(
            peers
                .and_then(|response| response.peers)
                .unwrap_or_default(),
            &self.address_policy,
        ))
    }
}

impl From<DownloadResponse> for Torrent {
    fn from(download: DownloadResponse) -> Self {
        Self {
            id: download.torrent.info_hash.clone(),
            hash: download.torrent.info_hash,
            name: download.name,
            size: download.torrent.size,
            completed_size: download
                .torrent
                .size
                .wrapping_sub(download.stats.remaining_bytes),
            progress: f64::from(download.stats.completed_in_thousand_notation) / 1000.0,
            private: download.torrent.private_torrent,
            upload_speed: download.stats.rt_upload_speed,
            download_speed: download.stats.rt_download_speed,
            trackers: download.trackers.unwrap_or_default(),
            peers: None,
        }
    }
}

fn read_peers(peers: Vec<Option<PeerResponse>>, policy: &AddressPolicy) -> Vec<Peer> {
    peers
        .into_iter()
        .flatten()
        .filter_map(|peer| peer.into_peer(policy))
        .collect()
}

impl PeerResponse {
    fn into_peer(self, policy: &AddressPolicy) -> Option<Peer> {
        let stats = self.stats.as_ref()?;
        let address = self.ip.as_deref()?;
        let address = address.strip_prefix('/').unwrap_or(address);
        let (ip, port) = policy.translate_peer(address.parse().ok()?, self.port);
        let id = decode_hex_id(self.peer_id.as_deref().unwrap_or_default())?;
        let handshaking = id.is_empty() || !matches!(self.state, 30 | 40);
        let flags = self.flags();
        Some(Peer {
            ip,
            port,
            raw_address: address.to_owned(),
            client_name: self.client,
            peer_id: Some(id),
            download_speed: stats.rt_download_speed,
            upload_speed: stats.rt_upload_speed,
            downloaded: stats.total_received,
            uploaded: stats.total_sent,
            progress: f64::from(self.percent_done_in_thousand_notation) / 1000.0,
            flags: Some(flags),
            handshaking,
            protocol: self.protocol,
        })
    }

    fn flags(&self) -> String {
        let download = match (self.interesting, self.choked) {
            (true, true) => Some("d"),
            (true, false) => Some("D"),
            (false, false) => Some("K"),
            (false, true) => None,
        };
        let upload = match (self.interested, self.choking) {
            (true, true) => Some("u"),
            (true, false) => Some("U"),
            (false, false) => Some("?"),
            (false, true) => None,
        };
        let source = self.peer_source.as_deref();
        [
            download,
            upload,
            self.optimistic_unchoke.then_some("O"),
            self.snubbed.then_some("S"),
            self.incoming.then_some("I"),
            (source == Some("DHT")).then_some("H"),
            (source == Some("PeerExchange")).then_some("X"),
            self.use_crypto.then_some("E"),
            (self.protocol.as_deref() == Some("uTP")).then_some("P"),
        ]
        .into_iter()
        .flatten()
        .collect::<Vec<_>>()
        .join(" ")
    }
}

fn decode_hex_id(value: &str) -> Option<String> {
    let pairs = value.as_bytes().chunks_exact(2);
    if !pairs.remainder().is_empty() {
        return None;
    }
    pairs
        .map(|pair| {
            let upper = char::from(pair[0]).to_digit(16)?;
            let lower = char::from(pair[1]).to_digit(16)?;
            Some(char::from((upper * 16 + lower) as u8))
        })
        .collect()
}
