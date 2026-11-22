use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::Arc;

use anyhow::Result;
use ipnet::IpNet;
use pbh_core::{PcbObservation, PcbState, check_progress};
use pbh_storage::{PcbAddressKey, PcbRangeKey};
use serde_json::json;
use tokio::sync::Mutex;

use crate::config::ProgressPolicy;
use crate::downloader::{Client, Downloader, Peer, Torrent};
use crate::policy::RuleHit;
use crate::storage::Database;

const MODULE: &str = "com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker";
const CLEANUP_INTERVAL_MS: i64 = 8 * 60 * 60 * 1000;

#[derive(Default)]
pub(crate) struct PcbWave {
    // Java's per-address cache uses the downloader, torrent and address. The
    // first sampled port stays attached to the persisted address row.
    addresses: HashMap<(String, String, IpAddr), (u16, PcbState)>,
    ranges: HashMap<(String, String, String), PcbState>,
    last_cleanup: Option<i64>,
}

pub(crate) struct PcbSample<'a> {
    pub(crate) downloader: &'a Downloader,
    pub(crate) torrent: &'a Torrent,
    pub(crate) peer: &'a Peer,
    pub(crate) now: i64,
}

impl PcbWave {
    pub(crate) async fn prepare(
        &mut self,
        policy: &ProgressPolicy,
        database: Option<&Arc<Mutex<Database>>>,
        now: i64,
    ) -> Result<()> {
        let Some(database) = database else {
            return Ok(());
        };
        self.addresses.clear();
        self.ranges.clear();
        if self
            .last_cleanup
            .is_none_or(|last| now.saturating_sub(last) >= CLEANUP_INTERVAL_MS)
        {
            database
                .lock()
                .await
                .cleanup_pcb(now.saturating_sub(policy.persist_ms))
                .await?;
            self.last_cleanup = Some(now);
        }
        Ok(())
    }

    pub(crate) async fn evaluate(
        &mut self,
        policy: &ProgressPolicy,
        sample: PcbSample<'_>,
        database: Option<&Arc<Mutex<Database>>>,
    ) -> Result<Option<RuleHit>> {
        let PcbSample {
            downloader,
            torrent,
            peer,
            now,
        } = sample;
        if peer.is_handshaking() {
            return Ok(None);
        }
        let ip = peer.ip.to_canonical();
        let prefix = if ip.is_ipv4() {
            policy.ipv4_prefix
        } else {
            policy.ipv6_prefix
        };
        let range = IpNet::new(ip, prefix)?.trunc().to_string();
        let address_key = (downloader.id.clone(), torrent.id.clone(), ip);
        let range_key = (downloader.id.clone(), torrent.id.clone(), range);
        let mut address = self.addresses.get(&address_key).copied();
        let mut range = self.ranges.get(&range_key).copied();
        if let Some(database) = database
            && (address.is_none() || range.is_none())
        {
            let mut database = database.lock().await;
            if address.is_none()
                && let Some(state) = database
                    .load_pcb_address(PcbAddressKey {
                        address: ip,
                        port: peer.port,
                        torrent_id: &torrent.id,
                        downloader: &downloader.id,
                    })
                    .await?
            {
                address = Some((peer.port, state));
            }
            if range.is_none() {
                range = database
                    .load_pcb_range(PcbRangeKey {
                        range: &range_key.2,
                        torrent_id: &torrent.id,
                        downloader: &downloader.id,
                    })
                    .await?;
            }
        }
        let (port, mut address) = address.unwrap_or((peer.port, PcbState::default()));
        let mut range = range.unwrap_or_default();
        let observation = PcbObservation {
            now_ms: now,
            torrent_size: torrent.size,
            completed_size: torrent.completed_size,
            reported_uploaded: peer.uploaded,
            reported_progress: peer.progress,
            upload_speed: peer.upload_speed,
            supports_unban: !matches!(downloader.client.as_ref(), Client::Deluge(_)),
            handshaking: false,
        };
        let finding = check_progress(&policy.config, &observation, &mut range, &mut address);
        self.addresses.insert(address_key, (port, address));
        self.ranges.insert(range_key, range);
        Ok(finding.map(|finding| RuleHit {
            module: MODULE,
            action: finding.action,
            duration_ms: finding.duration_ms,
            rule: json!({"key": finding.rule, "params": []}),
            reason: json!({"key": finding.reason, "params": finding.reason_params}),
            details: finding.details,
        }))
    }

    pub(crate) async fn flush(&self, database: &Arc<Mutex<Database>>) -> Result<()> {
        if self.addresses.is_empty() && self.ranges.is_empty() {
            return Ok(());
        }
        let addresses = self
            .addresses
            .iter()
            .map(|((downloader, torrent_id, address), (port, state))| {
                (
                    PcbAddressKey {
                        downloader,
                        torrent_id,
                        address: *address,
                        port: *port,
                    },
                    *state,
                )
            })
            .collect::<Vec<_>>();
        let ranges = self
            .ranges
            .iter()
            .map(|((downloader, torrent_id, range), state)| {
                (
                    PcbRangeKey {
                        downloader,
                        torrent_id,
                        range,
                    },
                    *state,
                )
            })
            .collect::<Vec<_>>();
        database.lock().await.merge_pcb(&addresses, &ranges).await
    }
}
