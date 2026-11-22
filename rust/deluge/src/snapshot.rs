use std::net::IpAddr;

use serde::Deserialize;

use pbh_core::AddressPolicy;

use crate::Error;

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
    pub peers: Vec<Peer>,
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
pub(crate) struct TorrentResponse {
    id: String,
    info_hash: String,
    name: String,
    size: i64,
    completed_size: i64,
    progress: f64,
    #[serde(rename = "priv")]
    private: Option<bool>,
    upload_payload_rate: i64,
    download_payload_rate: i64,
    peers: Vec<PeerResponse>,
}

#[derive(Deserialize)]
struct PeerResponse {
    ip: Option<String>,
    port: u16,
    client_name: Option<String>,
    peer_id: String,
    payload_down_speed: i64,
    payload_up_speed: i64,
    total_download: i64,
    total_upload: i64,
    progress: f64,
    flags: u32,
    source: u32,
}

impl TorrentResponse {
    pub(crate) fn into_snapshot(self, policy: &AddressPolicy) -> Result<Torrent, Error> {
        let peers = self
            .peers
            .into_iter()
            .filter_map(|peer| peer.into_snapshot(policy).transpose())
            .collect::<Result<_, _>>()?;
        Ok(Torrent {
            id: self.id,
            hash: self.info_hash,
            name: self.name,
            size: self.size,
            completed_size: self.completed_size,
            progress: self.progress / 100.0,
            private: self.private.unwrap_or(false),
            upload_speed: self.upload_payload_rate,
            download_speed: self.download_payload_rate,
            peers,
        })
    }
}

impl PeerResponse {
    fn into_snapshot(self, policy: &AddressPolicy) -> Result<Option<Peer>, Error> {
        let Some(raw_address) = self.ip.filter(|ip| !ip.trim().is_empty()) else {
            return Ok(None);
        };
        let ip = raw_address.parse().map_err(|_| Error::InvalidPeerAddress)?;
        let (ip, port) = policy.translate_peer(ip, self.port);
        Ok(Some(Peer {
            ip,
            port,
            raw_address,
            client_name: self.client_name,
            peer_id: Some(decode_peer_id(&self.peer_id)?),
            download_speed: self.payload_down_speed,
            upload_speed: self.payload_up_speed,
            downloaded: self.total_download,
            uploaded: self.total_upload,
            progress: self.progress / 100.0,
            flags: Some(peer_flags(self.flags, self.source)),
        }))
    }
}

fn decode_peer_id(hex: &str) -> Result<String, Error> {
    let mut id = String::with_capacity(8);
    for (index, pair) in hex.as_bytes().chunks_exact(2).enumerate() {
        let high = char::from(pair[0])
            .to_digit(16)
            .ok_or(Error::InvalidPeerId)?;
        let low = char::from(pair[1])
            .to_digit(16)
            .ok_or(Error::InvalidPeerId)?;
        if index < 8 {
            id.push(char::from((high * 16 + low) as u8));
        }
    }
    Ok(id)
}

fn peer_flags(flags: u32, source: u32) -> String {
    let flag = |bit: u32| flags & (1_u32 << bit) != 0_u32;
    let source = |bit: u32| source & (1_u32 << bit) != 0_u32;
    let download = match (flag(0), flag(3)) {
        (true, true) => Some("d"),
        (true, false) => Some("D"),
        (false, false) => Some("K"),
        (false, true) => None,
    };
    let upload = match (flag(2), flag(1)) {
        (true, true) => Some("u"),
        (true, false) => Some("U"),
        (false, false) => Some("?"),
        (false, true) => None,
    };
    [
        download,
        upload,
        flag(11).then_some("O"),
        flag(12).then_some("S"),
        (!flag(6)).then_some("I"),
        source(1).then_some("H"),
        source(2).then_some("X"),
        source(3).then_some("L"),
        flag(19).then_some("E"),
        flag(20).then_some("e"),
        flag(17).then_some("P"),
    ]
    .into_iter()
    .flatten()
    .collect::<Vec<_>>()
    .join(" ")
}
