use std::net::IpAddr;

use indexmap::IndexMap;
use serde::Deserialize;

use crate::Error;

#[derive(Clone, Debug, PartialEq)]
pub struct Torrent {
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
    /// Downloader's original peer key, used by the incremental ban API.
    pub raw_address: String,
    pub client_name: Option<String>,
    pub peer_id: Option<String>,
    pub download_speed: i64,
    pub upload_speed: i64,
    pub downloaded: i64,
    pub uploaded: i64,
    pub progress: f64,
    pub flags: Option<String>,
    pub connection: Option<String>,
}

impl Peer {
    pub fn is_handshaking(&self) -> bool {
        self.upload_speed <= 0 && self.download_speed <= 0
    }
}

#[derive(Deserialize)]
pub(crate) struct TorrentResponse {
    pub hash: String,
    pub name: String,
    #[serde(default)]
    total_size: i64,
    #[serde(default)]
    pub completed: i64,
    #[serde(default)]
    progress: f64,
    #[serde(default)]
    upspeed: i64,
    #[serde(default)]
    dlspeed: i64,
    #[serde(flatten)]
    properties: TorrentProperties,
}

impl TorrentResponse {
    pub fn into_snapshot(
        mut self,
        extra: Option<TorrentProperties>,
        fill_private: bool,
    ) -> Torrent {
        if let Some(extra) = extra {
            if fill_private && self.properties.is_private.is_none() {
                self.properties.is_private = extra.is_private;
            }
            if self.properties.piece_size <= 0 || self.properties.pieces_have <= 0 {
                self.properties.piece_size = extra.piece_size;
                self.properties.pieces_have = extra.pieces_have;
            }
        }

        let completed_size = if self.properties.piece_size > 0 && self.properties.pieces_have > 0 {
            self.properties
                .piece_size
                .wrapping_mul(self.properties.pieces_have)
        } else {
            -1
        };
        Torrent {
            hash: self.hash,
            name: self.name,
            size: self.total_size,
            completed_size,
            progress: self.progress,
            private: self.properties.is_private.unwrap_or(false),
            upload_speed: self.upspeed,
            download_speed: self.dlspeed,
        }
    }
}

#[derive(Clone, Copy, Default, Deserialize)]
#[serde(default)]
pub(crate) struct TorrentProperties {
    is_private: Option<bool>,
    piece_size: i64,
    pieces_have: i64,
}

#[derive(Deserialize)]
pub(crate) struct PeersResponse {
    pub peers: IndexMap<String, PeerResponse>,
}

#[derive(Default, Deserialize)]
#[serde(default)]
pub(crate) struct PeerResponse {
    ip: Option<String>,
    port: u16,
    client: Option<String>,
    peer_id_client: Option<String>,
    dl_speed: i64,
    up_speed: i64,
    downloaded: i64,
    uploaded: i64,
    progress: f64,
    flags: Option<String>,
    connection: Option<String>,
    shadowbanned: Option<bool>,
}

impl PeerResponse {
    pub fn into_snapshot(self, raw_address: String, enhanced: bool) -> Result<Option<Peer>, Error> {
        let Some(ip) = self.ip.as_deref().filter(|ip| !ip.trim().is_empty()) else {
            return Ok(None);
        };
        let connection = self.connection.as_deref().unwrap_or_default();
        if ["http", "https", "web"]
            .iter()
            .any(|kind| connection.eq_ignore_ascii_case(kind))
            || raw_address.contains(".onion")
            || raw_address.contains(".i2p")
            || (enhanced && self.shadowbanned.unwrap_or(false))
        {
            return Ok(None);
        }

        let ip = ip
            .parse::<IpAddr>()
            .map_err(|_| Error::InvalidPeerAddress)?
            .to_canonical();
        Ok(Some(Peer {
            ip,
            port: self.port,
            raw_address,
            client_name: self.client,
            peer_id: self.peer_id_client,
            download_speed: self.dl_speed,
            upload_speed: self.up_speed,
            downloaded: self.downloaded,
            uploaded: self.uploaded,
            progress: self.progress,
            flags: self.flags,
            connection: self.connection,
        }))
    }
}
