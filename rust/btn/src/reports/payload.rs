use std::net::IpAddr;

use serde::ser::{Error as _, SerializeStruct};
use serde::{Serialize, Serializer};
use serde_json::Value;
use sha2::{Digest, Sha256};

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
#[serde(transparent)]
pub struct TorrentIdentifier(String);

impl TorrentIdentifier {
    /// Replicate Guava's CRC32 hash string and salted SHA-256 used by all BTN reports.
    pub fn from_info_hash(info_hash: &str) -> Self {
        let lowercase = info_hash.to_lowercase();
        // Guava's HashCode.fromInt() represents the CRC as four little-endian bytes.
        let crc32 = crc32fast::hash(lowercase.as_bytes());
        let salt = format!("{:08x}", crc32.swap_bytes());
        let mut hash = Sha256::new();
        hash.update(lowercase.as_bytes());
        hash.update(salt.as_bytes());
        Self(format!("{:x}", hash.finalize()))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[derive(Clone, Serialize)]
pub struct BanHistory {
    pub ban_at: i64,
    #[serde(serialize_with = "java_address")]
    pub peer_ip: IpAddr,
    pub peer_port: i32,
    pub peer_id: Option<String>,
    pub peer_client_name: Option<String>,
    #[serde(serialize_with = "finite_float")]
    pub peer_progress: f64,
    pub peer_flag: Option<String>,
    pub torrent_identifier: TorrentIdentifier,
    pub torrent_is_private: bool,
    pub torrent_size: i64,
    pub from_peer_traffic: i64,
    pub to_peer_traffic: i64,
    #[serde(serialize_with = "finite_float")]
    pub downloader_progress: f64,
    pub module: Option<String>,
    pub rule: String,
    pub description: String,
    #[serde(serialize_with = "encoded_json")]
    pub structured_data: Value,
}

#[derive(Clone, Serialize)]
pub struct Swarm {
    pub torrent_identifier: TorrentIdentifier,
    pub torrent_is_private: Option<bool>,
    pub torrent_size: i64,
    pub downloader: Option<String>,
    #[serde(serialize_with = "finite_float")]
    pub downloader_progress: f64,
    #[serde(serialize_with = "java_address")]
    pub peer_ip: IpAddr,
    pub peer_port: i32,
    pub peer_id: Option<String>,
    pub peer_client_name: Option<String>,
    #[serde(serialize_with = "finite_float")]
    pub peer_progress: f64,
    pub to_peer_traffic: i64,
    pub to_peer_traffic_offset: i64,
    pub from_peer_traffic: i64,
    pub from_peer_traffic_offset: i64,
    pub first_time_seen: i64,
    pub last_time_seen: i64,
    pub peer_last_flags: Option<String>,
    pub upload_speed: i64,
    pub download_speed: i64,
    pub download_speed_max: i64,
    pub upload_speed_max: i64,
}

#[derive(Clone)]
pub struct PeerHistory {
    pub ip_address: IpAddr,
    pub peer_id: Option<String>,
    pub client_name: Option<String>,
    pub torrent_identifier: TorrentIdentifier,
    pub torrent_size: i64,
    pub downloaded: i64,
    pub downloaded_offset: i64,
    pub uploaded: i64,
    pub uploaded_offset: i64,
    pub first_time_seen: i64,
    pub last_time_seen: i64,
    pub peer_flag: Option<String>,
}

impl Serialize for PeerHistory {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        let mut row = serializer.serialize_struct("PeerHistory", 14)?;
        row.serialize_field("ip_address", &java_address_string(self.ip_address))?;
        // The Java legacy DTO leaves these fields at their primitive defaults.
        row.serialize_field("port", &0)?;
        row.serialize_field("peer_id", &self.peer_id)?;
        row.serialize_field("client_name", &self.client_name)?;
        row.serialize_field("torrent_identifier", &self.torrent_identifier)?;
        row.serialize_field("torrent_is_private", &false)?;
        row.serialize_field("torrent_size", &self.torrent_size)?;
        row.serialize_field("downloaded", &self.downloaded)?;
        row.serialize_field("downloaded_offset", &self.downloaded_offset)?;
        row.serialize_field("uploaded", &self.uploaded)?;
        row.serialize_field("uploaded_offset", &self.uploaded_offset)?;
        row.serialize_field("first_time_seen", &self.first_time_seen)?;
        row.serialize_field("last_time_seen", &self.last_time_seen)?;
        row.serialize_field("peer_flag", &self.peer_flag)?;
        row.end()
    }
}

#[derive(Clone, Serialize)]
pub struct LegacyPeer {
    pub ip_address: String,
    pub peer_port: i32,
    pub peer_id: Option<String>,
    pub client_name: Option<String>,
    pub torrent_identifier: TorrentIdentifier,
    pub torrent_is_private: bool,
    pub torrent_size: i64,
    pub downloaded: i64,
    pub rt_download_speed: i64,
    pub uploaded: i64,
    pub rt_upload_speed: i64,
    #[serde(serialize_with = "finite_float")]
    pub peer_progress: f64,
    #[serde(serialize_with = "finite_float")]
    pub downloader_progress: f64,
    pub peer_flag: Option<String>,
}

#[derive(Clone, Serialize)]
pub struct LegacyBan {
    pub btn_ban: bool,
    pub ban_unique_id: String,
    pub module: String,
    pub rule: String,
    pub peer: LegacyPeer,
    pub structured_data: Value,
}

impl LegacyBan {
    /// Java used its string representation of the ban metadata as the identifier seed.
    pub fn id_from_seed(seed: &str) -> String {
        format!("{:x}", Sha256::digest(seed.as_bytes()))
    }
}

#[derive(Clone)]
pub struct LegacyBanEvent {
    pub ban_at: i64,
    pub disconnect: bool,
    pub exclude_from_report: bool,
    pub ban: LegacyBan,
}

fn java_address<S: Serializer>(address: &IpAddr, serializer: S) -> Result<S::Ok, S::Error> {
    serializer.serialize_str(&java_address_string(*address))
}

fn java_address_string(address: IpAddr) -> String {
    match address {
        IpAddr::V4(address) => address.to_string(),
        IpAddr::V6(address) => address
            .segments()
            .map(|segment| format!("{segment:x}"))
            .join(":"),
    }
}

fn finite_float<S: Serializer>(value: &f64, serializer: S) -> Result<S::Ok, S::Error> {
    if !value.is_finite() {
        return Err(S::Error::custom("invalid floating-point value"));
    }
    serializer.serialize_f64(*value)
}

fn encoded_json<S: Serializer>(value: &Value, serializer: S) -> Result<S::Ok, S::Error> {
    let string = serde_json::to_string(value).map_err(S::Error::custom)?;
    serializer.serialize_str(&string)
}
