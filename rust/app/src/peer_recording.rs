//! Capture the same peers and persisted keys as Java PeerRecordingServiceModule.

use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::Arc;

use anyhow::Result;
use pbh_geoip::GeoData;
use pbh_storage::{PeerRecordObservation, PeerRecordTorrent, TrackedSwarmObservation};
use serde::Deserialize;
use tokio::sync::Mutex;

use crate::downloader::{Downloader, Peer, Torrent};
use crate::storage::Database;

const MAX_PENDING: usize = 50_000;

#[derive(Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct RecordingPolicy {
    data_flush_interval: i64,
    data_retention_time: i64,
    data_cleanup_interval: i64,
}

impl Default for RecordingPolicy {
    fn default() -> Self {
        Self {
            data_flush_interval: 20_000,
            data_retention_time: -1,
            data_cleanup_interval: -1,
        }
    }
}

type RecordKey = (String, String, IpAddr, u16);

#[derive(Default)]
pub(crate) struct Recorder {
    pending: HashMap<RecordKey, OwnedObservation>,
    last_flush: Option<i64>,
    last_cleanup: Option<i64>,
    sequence: u64,
}

pub(crate) type SwarmBatch = Vec<(RecordKey, OwnedObservation)>;

#[derive(Default)]
pub(crate) struct SwarmRecorder {
    pending: SwarmBatch,
    last_flush: Option<i64>,
}

pub(crate) struct OwnedObservation {
    torrent: Torrent,
    peer_id: Option<String>,
    client_name: Option<String>,
    uploaded: i64,
    downloaded: i64,
    progress: f64,
    upload_speed: i64,
    download_speed: i64,
    last_flags: Option<String>,
    observed_at_ms: i64,
    sequence: u64,
    geoip: Option<serde_json::Value>,
}

impl Recorder {
    pub(crate) fn observe(
        &mut self,
        downloader: &Downloader,
        torrent: &Torrent,
        peer: &Peer,
        now: i64,
        geoip: Option<&GeoData>,
    ) {
        let identifying = [peer.client_name.as_deref(), peer.peer_id.as_deref()]
            .into_iter()
            .flatten()
            .any(|value| !value.trim().is_empty());
        if !identifying && peer.is_handshaking() {
            return;
        }
        let key = (
            downloader.id.clone(),
            torrent.hash.clone(),
            peer.ip.to_canonical(),
            peer.port,
        );
        self.sequence = self.sequence.wrapping_add(1);
        self.pending.insert(
            key,
            OwnedObservation::new(torrent, peer, now, self.sequence, geoip),
        );
    }

    pub(crate) async fn maintain(
        &mut self,
        policy: &RecordingPolicy,
        database: &Arc<Mutex<Database>>,
        now: i64,
        flush_now: bool,
    ) -> Result<()> {
        if policy.data_retention_time > 0
            && self
                .last_cleanup
                .is_none_or(|last| now.saturating_sub(last) >= policy.data_cleanup_interval.max(1))
        {
            database
                .lock()
                .await
                .cleanup_peer_records(now.saturating_sub(policy.data_retention_time))
                .await?;
            self.last_cleanup = Some(now);
        }
        if flush_now
            || self.pending.len() >= MAX_PENDING
            || self
                .last_flush
                .is_none_or(|last| now.saturating_sub(last) >= policy.data_flush_interval.max(1))
        {
            self.flush(database, now).await?;
        }
        Ok(())
    }

    pub(crate) async fn flush(&mut self, database: &Arc<Mutex<Database>>, now: i64) -> Result<()> {
        if !self.pending.is_empty() {
            let mut ordered = self.pending.iter().collect::<Vec<_>>();
            ordered.sort_by_key(|(_, record)| (record.observed_at_ms, record.sequence));
            let rows = ordered
                .into_iter()
                .map(
                    |((downloader_id, _, address, port), record)| PeerRecordObservation {
                        address: *address,
                        port: *port,
                        downloader_id,
                        torrent: PeerRecordTorrent {
                            info_hash: &record.torrent.hash,
                            name: &record.torrent.name,
                            size: record.torrent.size,
                            private: Some(record.torrent.private),
                        },
                        peer_id: record.peer_id.as_deref(),
                        client_name: record.client_name.as_deref(),
                        uploaded: record.uploaded,
                        downloaded: record.downloaded,
                        upload_speed: record.upload_speed,
                        download_speed: record.download_speed,
                        last_flags: record.last_flags.as_deref(),
                        observed_at_ms: record.observed_at_ms,
                        peer_geoip: record.geoip.as_ref(),
                    },
                )
                .collect::<Vec<_>>();
            database.lock().await.upsert_peer_records(&rows).await?;
            self.pending.clear();
        }
        self.last_flush = Some(now);
        Ok(())
    }
}

impl OwnedObservation {
    fn new(
        torrent: &Torrent,
        peer: &Peer,
        now: i64,
        sequence: u64,
        geoip: Option<&GeoData>,
    ) -> Self {
        Self {
            torrent: torrent.clone(),
            peer_id: peer.peer_id.clone(),
            client_name: peer.client_name.clone(),
            uploaded: peer.uploaded,
            downloaded: peer.downloaded,
            progress: peer.progress,
            upload_speed: peer.upload_speed,
            download_speed: peer.download_speed,
            last_flags: peer.flags.clone(),
            observed_at_ms: now,
            sequence,
            geoip: geoip.map(|data| serde_json::json!(data)),
        }
    }
}

impl SwarmRecorder {
    pub(crate) fn capture(
        batch: &mut SwarmBatch,
        downloader: &Downloader,
        torrent: &Torrent,
        peer: &Peer,
        now: i64,
    ) {
        if !peer.is_handshaking() {
            let key = (
                downloader.id.clone(),
                torrent.hash.clone(),
                peer.ip.to_canonical(),
                peer.port,
            );
            batch.push((key, OwnedObservation::new(torrent, peer, now, 0, None)));
        }
    }

    pub(crate) fn append(&mut self, batch: SwarmBatch) {
        self.pending.extend(batch);
    }

    pub(crate) async fn maintain(
        &mut self,
        interval_ms: i64,
        database: &Arc<Mutex<Database>>,
        now: i64,
        flush_now: bool,
    ) -> Result<()> {
        if flush_now
            || self.pending.len() >= MAX_PENDING
            || self
                .last_flush
                .is_none_or(|last| now.saturating_sub(last) >= interval_ms.max(1))
        {
            self.flush(database, now).await?;
        }
        Ok(())
    }

    pub(crate) async fn flush(&mut self, database: &Arc<Mutex<Database>>, now: i64) -> Result<()> {
        if !self.pending.is_empty() {
            let rows = self
                .pending
                .iter()
                .map(
                    |((downloader, info_hash, ip, port), record)| TrackedSwarmObservation {
                        ip: *ip,
                        port: i32::from(*port),
                        info_hash,
                        torrent_is_private: record.torrent.private,
                        torrent_size: record.torrent.size,
                        downloader,
                        downloader_progress: record.torrent.progress,
                        peer_id: record.peer_id.as_deref(),
                        client_name: record.client_name.as_deref(),
                        peer_progress: record.progress,
                        uploaded: record.uploaded,
                        upload_speed: record.upload_speed,
                        downloaded: record.downloaded,
                        download_speed: record.download_speed,
                        last_flags: record.last_flags.as_deref(),
                        seen_at_ms: record.observed_at_ms,
                    },
                )
                .collect::<Vec<_>>();
            database.lock().await.upsert_tracked_swarm(&rows).await?;
            self.pending.clear();
        }
        self.last_flush = Some(now);
        Ok(())
    }
}
