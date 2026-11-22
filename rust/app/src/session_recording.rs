//! Gather session samples by local calendar day for the existing metrics store.

use std::collections::BTreeMap;
use std::net::IpAddr;

use anyhow::Result;
use pbh_storage::{PeerRecordTorrent, PeerSessionObservation};
use serde::Deserialize;
use time::OffsetDateTime;
use tokio::sync::Mutex;

use crate::dashboard::start_of_day;
use crate::downloader::{Downloader, Peer, Torrent};
use crate::storage::Database;

const MAX_PENDING: usize = 1_000;

#[derive(Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct SessionPolicy {
    data_flush_interval: i64,
    cleanup_interval: i64,
    data_retention_time: i64,
}

impl Default for SessionPolicy {
    fn default() -> Self {
        Self {
            data_flush_interval: 3_600_000,
            cleanup_interval: 3_600_000,
            data_retention_time: 15_552_000_000,
        }
    }
}

type Key = (i64, String, String, IpAddr, u16);

#[derive(Default)]
pub(crate) struct SessionRecorder {
    samples: BTreeMap<Key, Sample>,
    last_aggregate: Option<i64>,
    last_cleanup: Option<i64>,
}

struct Sample {
    torrent_name: String,
    torrent_size: i64,
    torrent_private: bool,
    peer_id: Option<String>,
    client_name: Option<String>,
    last_flags: Option<String>,
}

impl SessionRecorder {
    pub(crate) async fn observe(
        &mut self,
        downloader: &Downloader,
        torrent: &Torrent,
        peer: &Peer,
        day_start: i64,
        database: &Mutex<Database>,
    ) -> Result<()> {
        if peer.is_handshaking() {
            return Ok(());
        }
        self.samples.insert(
            (
                day_start,
                downloader.id.clone(),
                torrent.hash.clone(),
                peer.ip.to_canonical(),
                peer.port,
            ),
            Sample {
                torrent_name: torrent.name.clone(),
                torrent_size: torrent.size,
                torrent_private: torrent.private,
                peer_id: peer.peer_id.clone(),
                client_name: peer.client_name.clone(),
                last_flags: peer.flags.clone(),
            },
        );
        if self.samples.len() >= MAX_PENDING {
            self.flush(&mut *database.lock().await).await?;
        }
        Ok(())
    }

    pub(crate) async fn maintain(
        &mut self,
        policy: &SessionPolicy,
        database: &Mutex<Database>,
        now: i64,
        force: bool,
    ) -> Result<()> {
        if force
            || self
                .last_aggregate
                .is_none_or(|last| now.saturating_sub(last) >= policy.data_flush_interval.max(1))
        {
            let mut database = database.lock().await;
            self.flush(&mut database).await?;
            let today = OffsetDateTime::from_unix_timestamp_nanos(i128::from(now) * 1_000_000)?;
            database
                .aggregate_peer_sessions(start_of_day(today))
                .await?;
            self.last_aggregate = Some(now);
        }
        if self
            .last_cleanup
            .is_none_or(|last| now.saturating_sub(last) >= policy.cleanup_interval.max(1))
        {
            database
                .lock()
                .await
                .cleanup_connection_metrics(now.saturating_sub(policy.data_retention_time))
                .await?;
            self.last_cleanup = Some(now);
        }
        Ok(())
    }

    async fn flush(&mut self, database: &mut Database) -> Result<()> {
        if self.samples.is_empty() {
            return Ok(());
        }
        let samples = self
            .samples
            .iter()
            .map(
                |((day, downloader, hash, address, port), sample)| PeerSessionObservation {
                    timeframe_at_ms: *day,
                    downloader,
                    torrent: PeerRecordTorrent {
                        info_hash: hash,
                        name: &sample.torrent_name,
                        size: sample.torrent_size,
                        private: Some(sample.torrent_private),
                    },
                    address: *address,
                    port: *port,
                    peer_id: sample.peer_id.as_deref(),
                    client_name: sample.client_name.as_deref(),
                    last_flags: sample.last_flags.as_deref(),
                },
            )
            .collect::<Vec<_>>();
        database.upsert_peer_sessions(&samples).await?;
        self.samples.clear();
        Ok(())
    }
}
