use std::collections::BTreeSet;
use std::net::{IpAddr, SocketAddr};
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result, bail};
use pbh_aria2next::Aria2Next;
use pbh_biglybt::BiglyBt;
use pbh_bitcomet::BitComet;
use pbh_core::AddressPolicy;
use pbh_deluge::Deluge;
use pbh_network::ProxyPolicy;
use pbh_qbittorrent::Qbittorrent;
use pbh_transmission::Transmission;

use crate::config::DownloaderConfig;

pub(crate) struct Downloader {
    pub(crate) id: String,
    pub(crate) name: String,
    pub(crate) kind: String,
    pub(crate) client: Arc<Client>,
    tunnel_host: Option<String>,
    pub(crate) needs_full_list: bool,
    pub(crate) pending_removals: BTreeSet<IpAddr>,
}

pub(crate) enum Client {
    Qbittorrent(Box<Qbittorrent>),
    Transmission(Box<Transmission>),
    Deluge(Box<Deluge>),
    Aria2Next(Box<Aria2Next>),
    BiglyBt(Box<BiglyBt>),
    BitComet(Box<BitComet>),
}

pub(crate) struct AddedBan<'a> {
    pub(crate) address: IpAddr,
    pub(crate) raw_address: &'a str,
}

pub(crate) struct Snapshot {
    pub(crate) torrents: Vec<(Torrent, Vec<Peer>)>,
    pub(crate) failures: Vec<String>,
}

#[derive(Clone)]
pub(crate) struct Torrent {
    pub(crate) id: String,
    pub(crate) hash: String,
    pub(crate) name: String,
    pub(crate) size: i64,
    pub(crate) completed_size: i64,
    pub(crate) progress: f64,
    pub(crate) private: bool,
    pub(crate) upload_speed: i64,
    pub(crate) download_speed: i64,
}

pub(crate) struct Peer {
    pub(crate) ip: IpAddr,
    pub(crate) port: u16,
    pub(crate) raw_address: String,
    pub(crate) forwarded_from: Option<SocketAddr>,
    pub(crate) client_name: Option<String>,
    pub(crate) peer_id: Option<String>,
    pub(crate) download_speed: i64,
    pub(crate) upload_speed: i64,
    pub(crate) downloaded: i64,
    pub(crate) uploaded: i64,
    pub(crate) progress: f64,
    pub(crate) flags: Option<String>,
    pub(crate) handshaking: bool,
}

impl Peer {
    pub(crate) fn is_handshaking(&self) -> bool {
        self.handshaking
    }
}

impl Downloader {
    pub(crate) fn configure(
        config: &DownloaderConfig,
        id: &str,
        interval: Duration,
        prefix: &str,
        apply: bool,
        address_policy: &AddressPolicy,
    ) -> Result<Option<Self>> {
        Self::configure_with_proxy(
            config,
            id,
            interval,
            prefix,
            apply,
            address_policy,
            &ProxyPolicy::default(),
        )
    }

    pub(crate) fn configure_with_proxy(
        config: &DownloaderConfig,
        id: &str,
        interval: Duration,
        prefix: &str,
        apply: bool,
        address_policy: &AddressPolicy,
        proxy_policy: &ProxyPolicy,
    ) -> Result<Option<Self>> {
        let (name, client) = match config.kind.as_str() {
            "qbittorrent" | "qbittorrentee" => {
                let settings: pbh_qbittorrent::Config = config.decode(id)?;
                if settings.paused {
                    return Ok(None);
                }
                let name = settings.name.clone();
                let client = Qbittorrent::connect_with_proxy(
                    settings,
                    config.kind == "qbittorrentee",
                    !apply,
                    proxy_policy,
                )
                .with_context(|| format!("configure downloader {id}"))?
                .with_scan_interval(interval)
                .with_address_policy(address_policy.clone());
                (name, Client::Qbittorrent(Box::new(client)))
            }
            "transmission" => {
                let settings: pbh_transmission::Config = config.decode(id)?;
                if settings.paused {
                    return Ok(None);
                }
                let name = settings.name.clone();
                let client =
                    Transmission::connect_with_proxy(settings, prefix, !apply, proxy_policy)
                        .with_context(|| format!("configure downloader {id}"))?;
                (
                    name,
                    Client::Transmission(Box::new(
                        client.with_address_policy(address_policy.clone()),
                    )),
                )
            }
            "deluge" | "raccoonfink/deluge" => {
                let settings: pbh_deluge::Config = config.decode(id)?;
                if settings.paused {
                    return Ok(None);
                }
                let name = settings.name.clone();
                let client = Deluge::connect_with_proxy(settings, !apply, proxy_policy)
                    .with_context(|| format!("configure downloader {id}"))?;
                (
                    name,
                    Client::Deluge(Box::new(client.with_address_policy(address_policy.clone()))),
                )
            }
            "aria2next" => {
                let settings: pbh_aria2next::Config = config.decode(id)?;
                if settings.paused {
                    return Ok(None);
                }
                let name = settings.name.clone();
                let client = Aria2Next::connect_with_proxy(settings, !apply, proxy_policy)
                    .with_context(|| format!("configure downloader {id}"))?;
                (
                    name,
                    Client::Aria2Next(Box::new(client.with_address_policy(address_policy.clone()))),
                )
            }
            "biglybt" => {
                let settings: pbh_biglybt::Config = config.decode(id)?;
                if settings.paused {
                    return Ok(None);
                }
                let name = settings.name.clone();
                let client = BiglyBt::connect_with_proxy(settings, !apply, proxy_policy)
                    .with_context(|| format!("configure downloader {id}"))?;
                (
                    name,
                    Client::BiglyBt(Box::new(client.with_address_policy(address_policy.clone()))),
                )
            }
            "bitcomet" => {
                let settings: pbh_bitcomet::Config = config.decode(id)?;
                if settings.paused {
                    return Ok(None);
                }
                let name = settings.name.clone();
                let client = BitComet::connect_with_proxy(settings, !apply, proxy_policy)
                    .with_context(|| format!("configure downloader {id}"))?;
                (
                    name,
                    Client::BitComet(Box::new(client.with_address_policy(address_policy.clone()))),
                )
            }
            _ => bail!("downloader {id} uses unsupported type {}", config.kind),
        };
        Ok(Some(Self {
            id: id.into(),
            name: name.unwrap_or_else(|| id.into()),
            kind: match config.kind.as_str() {
                "deluge" => "raccoonfink/deluge".into(),
                _ => config.kind.clone(),
            },
            client: Arc::new(client),
            tunnel_host: (apply
                && !matches!(config.kind.as_str(), "deluge" | "raccoonfink/deluge"))
            .then(|| {
                config
                    .settings
                    .get("endpoint")
                    .and_then(serde_yaml_ng::Value::as_str)
            })
            .flatten()
            .and_then(|value| reqwest::Url::parse(value.trim()).ok())
            .and_then(|url| {
                url.host_str()
                    .map(|host| host.trim_matches(['[', ']']).to_owned())
            }),
            needs_full_list: true,
            pending_removals: BTreeSet::new(),
        }))
    }

    pub(crate) fn auto_tunnel_host(&self) -> Option<&str> {
        self.tunnel_host.as_deref()
    }
}

impl Client {
    pub(crate) async fn login_for_auto_tunnel(&self) -> Result<()> {
        if matches!(self, Self::Deluge(_)) {
            bail!("Deluge does not support live BitTorrent port updates");
        }
        self.check_credentials().await
    }

    pub(crate) async fn check_credentials(&self) -> Result<()> {
        match self {
            Self::Qbittorrent(client) => client.login().await?,
            Self::Transmission(client) => client.login().await?,
            Self::Aria2Next(client) => client.login().await?,
            Self::BiglyBt(client) => client.login().await?,
            Self::BitComet(client) => client.login().await?,
            Self::Deluge(client) => client.login().await?,
        }
        Ok(())
    }

    pub(crate) async fn bt_listen_port(&self) -> Result<u16> {
        Ok(match self {
            Self::Qbittorrent(client) => client.listen_port().await?,
            Self::Transmission(client) => client.listen_port().await?,
            Self::Aria2Next(client) => client.listen_port().await?,
            Self::BiglyBt(client) => client.listen_port().await?,
            Self::BitComet(client) => client.listen_port().await?,
            Self::Deluge(_) => bail!("Deluge does not support live BitTorrent port updates"),
        })
    }

    pub(crate) async fn set_bt_listen_port(&self, port: u16) -> Result<()> {
        match self {
            Self::Qbittorrent(client) => client.set_listen_port(port).await?,
            Self::Transmission(client) => client.set_listen_port(port).await?,
            Self::Aria2Next(client) => client.set_listen_port(port).await?,
            Self::BiglyBt(client) => client.set_listen_port(port).await?,
            Self::BitComet(client) => client.set_listen_port(port).await?,
            Self::Deluge(_) => bail!("Deluge does not support live BitTorrent port updates"),
        }
        Ok(())
    }

    pub(crate) async fn snapshot(&self) -> Result<Snapshot> {
        let mut snapshot = Snapshot {
            torrents: Vec::new(),
            failures: Vec::new(),
        };
        match self {
            Self::Qbittorrent(client) => {
                client.login().await?;
                for torrent in client.active_torrents().await? {
                    let peers = match client.peers(&torrent.hash).await {
                        Ok(peers) => peers.into_iter().map(Peer::from).collect(),
                        Err(error) => {
                            snapshot.failures.push(error.to_string());
                            Vec::new()
                        }
                    };
                    snapshot.torrents.push((torrent.into(), peers));
                }
            }
            Self::Transmission(client) => {
                client.login().await?;
                snapshot.torrents = client
                    .active_torrents()
                    .await?
                    .into_iter()
                    .map(|mut torrent| {
                        let peers = std::mem::take(&mut torrent.peers)
                            .into_iter()
                            .map(Peer::from)
                            .collect();
                        (torrent.into(), peers)
                    })
                    .collect();
            }
            Self::Deluge(client) => {
                client.login().await?;
                snapshot.torrents = client
                    .active_torrents()
                    .await?
                    .into_iter()
                    .map(|mut torrent| {
                        let peers = std::mem::take(&mut torrent.peers)
                            .into_iter()
                            .map(Peer::from)
                            .collect();
                        (torrent.into(), peers)
                    })
                    .collect();
            }
            Self::Aria2Next(client) => {
                client.login().await?;
                for torrent in client.active_torrents().await? {
                    let peers = match client.peers(&torrent.id).await {
                        Ok(peers) => peers.into_iter().map(Peer::from).collect(),
                        Err(error) => {
                            snapshot.failures.push(error.to_string());
                            Vec::new()
                        }
                    };
                    snapshot.torrents.push((torrent.into(), peers));
                }
            }
            Self::BiglyBt(client) => {
                client.login().await?;
                for mut torrent in client.active_torrents().await? {
                    let peers = if let Some(peers) = torrent.peers.take() {
                        peers
                    } else {
                        match client.peers(&torrent.id).await {
                            Ok(peers) => peers,
                            Err(error) => {
                                snapshot.failures.push(error.to_string());
                                Vec::new()
                            }
                        }
                    };
                    snapshot
                        .torrents
                        .push((torrent.into(), peers.into_iter().map(Peer::from).collect()));
                }
            }
            Self::BitComet(client) => {
                client.login().await?;
                for torrent in client.active_torrents().await? {
                    let peers = match client.peers(&torrent.id).await {
                        Ok(peers) => peers.into_iter().map(Peer::from).collect(),
                        Err(error) => {
                            snapshot.failures.push(error.to_string());
                            Vec::new()
                        }
                    };
                    snapshot.torrents.push((torrent.into(), peers));
                }
            }
        }
        Ok(snapshot)
    }

    pub(crate) async fn sync(
        &self,
        full: &[IpAddr],
        added: &[AddedBan<'_>],
        removed: &[IpAddr],
        force_full: bool,
    ) -> Result<()> {
        let has_removals = !removed.is_empty();
        match self {
            Self::Qbittorrent(client) => {
                client.login().await?;
                let added = added.iter().map(|ban| ban.raw_address).collect::<Vec<_>>();
                client
                    .sync_bans(full, &added, has_removals, force_full)
                    .await?;
            }
            Self::Transmission(client) => {
                client.login().await?;
                client.update_blocklist().await?;
            }
            Self::Deluge(client) => {
                client.login().await?;
                let added = added.iter().map(|ban| ban.address).collect::<Vec<_>>();
                client
                    .sync_bans(full, &added, has_removals, force_full)
                    .await?;
            }
            Self::Aria2Next(client) => {
                client.login().await?;
                client.replace_bans(full).await?;
            }
            Self::BiglyBt(client) => {
                client.login().await?;
                client.sync_bans(full, force_full).await?;
            }
            Self::BitComet(client) => {
                client.login().await?;
                client.sync_bans(full, removed).await?;
            }
        }
        Ok(())
    }
}

impl From<pbh_qbittorrent::Torrent> for Torrent {
    fn from(torrent: pbh_qbittorrent::Torrent) -> Self {
        Self {
            id: torrent.hash.clone(),
            hash: torrent.hash,
            name: torrent.name,
            size: torrent.size,
            completed_size: torrent.completed_size,
            progress: torrent.progress,
            private: torrent.private,
            upload_speed: torrent.upload_speed,
            download_speed: torrent.download_speed,
        }
    }
}

impl From<pbh_transmission::Torrent> for Torrent {
    fn from(torrent: pbh_transmission::Torrent) -> Self {
        Self {
            id: torrent.id.to_string(),
            hash: torrent.hash,
            name: torrent.name,
            size: torrent.size,
            completed_size: torrent.completed_size,
            progress: torrent.progress,
            private: torrent.private,
            upload_speed: torrent.upload_speed,
            download_speed: torrent.download_speed,
        }
    }
}

impl From<pbh_deluge::Torrent> for Torrent {
    fn from(torrent: pbh_deluge::Torrent) -> Self {
        Self {
            id: torrent.id,
            hash: torrent.hash,
            name: torrent.name,
            size: torrent.size,
            completed_size: torrent.completed_size,
            progress: torrent.progress,
            private: torrent.private,
            upload_speed: torrent.upload_speed,
            download_speed: torrent.download_speed,
        }
    }
}

impl From<pbh_aria2next::Torrent> for Torrent {
    fn from(torrent: pbh_aria2next::Torrent) -> Self {
        Self {
            id: torrent.id,
            hash: torrent.hash,
            name: torrent.name,
            size: torrent.size,
            completed_size: torrent.completed_size,
            progress: torrent.progress,
            private: torrent.private,
            upload_speed: torrent.upload_speed,
            download_speed: torrent.download_speed,
        }
    }
}

impl From<pbh_biglybt::Torrent> for Torrent {
    fn from(torrent: pbh_biglybt::Torrent) -> Self {
        Self {
            id: torrent.id,
            hash: torrent.hash,
            name: torrent.name,
            size: torrent.size,
            completed_size: torrent.completed_size,
            progress: torrent.progress,
            private: torrent.private,
            upload_speed: torrent.upload_speed,
            download_speed: torrent.download_speed,
        }
    }
}

impl From<pbh_bitcomet::Torrent> for Torrent {
    fn from(torrent: pbh_bitcomet::Torrent) -> Self {
        Self {
            id: torrent.id,
            hash: torrent.hash,
            name: torrent.name,
            size: torrent.size,
            completed_size: torrent.completed_size,
            progress: torrent.progress,
            private: torrent.private,
            upload_speed: torrent.upload_speed,
            download_speed: torrent.download_speed,
        }
    }
}

impl From<pbh_qbittorrent::Peer> for Peer {
    fn from(peer: pbh_qbittorrent::Peer) -> Self {
        Self {
            ip: peer.ip,
            port: peer.port,
            raw_address: peer.raw_address,
            forwarded_from: None,
            client_name: peer.client_name,
            peer_id: peer.peer_id,
            upload_speed: peer.upload_speed,
            download_speed: peer.download_speed,
            uploaded: peer.uploaded,
            downloaded: peer.downloaded,
            progress: peer.progress,
            flags: peer.flags,
            handshaking: peer.upload_speed <= 0 && peer.download_speed <= 0,
        }
    }
}

impl From<pbh_transmission::Peer> for Peer {
    fn from(peer: pbh_transmission::Peer) -> Self {
        Self {
            ip: peer.ip,
            port: peer.port,
            raw_address: peer.raw_address,
            forwarded_from: None,
            client_name: peer.client_name,
            peer_id: peer.peer_id,
            upload_speed: peer.upload_speed,
            download_speed: peer.download_speed,
            uploaded: peer.uploaded,
            downloaded: peer.downloaded,
            progress: peer.progress,
            flags: peer.flags,
            handshaking: peer.upload_speed <= 0 && peer.download_speed <= 0,
        }
    }
}

impl From<pbh_deluge::Peer> for Peer {
    fn from(peer: pbh_deluge::Peer) -> Self {
        Self {
            ip: peer.ip,
            port: peer.port,
            raw_address: peer.raw_address,
            forwarded_from: None,
            client_name: peer.client_name,
            peer_id: peer.peer_id,
            upload_speed: peer.upload_speed,
            download_speed: peer.download_speed,
            uploaded: peer.uploaded,
            downloaded: peer.downloaded,
            progress: peer.progress,
            flags: peer.flags,
            handshaking: peer.upload_speed <= 0 && peer.download_speed <= 0,
        }
    }
}

impl From<pbh_aria2next::Peer> for Peer {
    fn from(peer: pbh_aria2next::Peer) -> Self {
        Self {
            ip: peer.ip,
            port: peer.port,
            raw_address: peer.raw_address,
            forwarded_from: None,
            client_name: peer.client_name,
            peer_id: peer.peer_id,
            upload_speed: peer.upload_speed,
            download_speed: peer.download_speed,
            uploaded: peer.uploaded,
            downloaded: peer.downloaded,
            progress: peer.progress,
            flags: peer.flags,
            handshaking: peer.handshaking,
        }
    }
}

impl From<pbh_biglybt::Peer> for Peer {
    fn from(peer: pbh_biglybt::Peer) -> Self {
        Self {
            ip: peer.ip,
            port: peer.port,
            raw_address: peer.raw_address,
            forwarded_from: None,
            client_name: peer.client_name,
            peer_id: peer.peer_id,
            upload_speed: peer.upload_speed,
            download_speed: peer.download_speed,
            uploaded: peer.uploaded,
            downloaded: peer.downloaded,
            progress: peer.progress,
            flags: peer.flags,
            handshaking: peer.handshaking,
        }
    }
}

impl From<pbh_bitcomet::Peer> for Peer {
    fn from(peer: pbh_bitcomet::Peer) -> Self {
        Self {
            ip: peer.ip,
            port: peer.port,
            raw_address: peer.raw_address,
            forwarded_from: None,
            client_name: peer.client_name,
            peer_id: peer.peer_id,
            upload_speed: peer.upload_speed,
            download_speed: peer.download_speed,
            uploaded: peer.uploaded,
            downloaded: peer.downloaded,
            progress: peer.progress,
            flags: peer.flags,
            handshaking: peer.handshaking,
        }
    }
}
