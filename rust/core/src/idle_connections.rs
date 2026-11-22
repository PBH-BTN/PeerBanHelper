//! Timed idle-peer checks from the Java IdleConnectionDosProtection module.

use std::collections::{HashMap, HashSet};
use std::net::SocketAddr;

const ACCESS_EXPIRY_MS: i64 = 300_000;
const MISSED_BATCH_LIMIT: u8 = 5;

#[derive(Clone, Copy, Debug, Default)]
pub enum ProtectionMode {
    #[default]
    ByPeerFlags,
    SeedingOnly,
    Always,
}

impl From<i64> for ProtectionMode {
    fn from(code: i64) -> Self {
        match code {
            1 => Self::SeedingOnly,
            2 => Self::Always,
            _ => Self::ByPeerFlags,
        }
    }
}

#[derive(Default)]
pub struct IdleConnections {
    entries: HashMap<SocketAddr, IdleState>,
}

pub struct IdleConfig {
    pub mode: ProtectionMode,
    pub max_duration_ms: i64,
    pub speed_threshold: i64,
    pub reset_on_status_change: bool,
    pub min_status_change_percentage: f64,
    pub ignore_interested: bool,
}

pub struct IdleObservation<'a> {
    pub address: SocketAddr,
    pub flags: Option<&'a str>,
    pub seeding: bool,
    pub progress: f64,
    pub upload_speed: i64,
    pub download_speed: i64,
    pub uploaded: i64,
    pub downloaded: i64,
}

#[derive(Debug, Clone, Copy)]
pub struct IdleTimeout {
    pub start_ms: i64,
    pub progress: f64,
    pub uploaded: i64,
    pub downloaded: i64,
}

struct IdleState {
    start: IdleTimeout,
    accessed_at_ms: i64,
    missed_batches: u8,
}

impl IdleConnections {
    pub fn observe_batch(&mut self, now_ms: i64, peers: impl IntoIterator<Item = SocketAddr>) {
        if self.entries.is_empty() {
            return;
        }
        let seen: HashSet<_> = peers.into_iter().collect();
        self.entries.retain(|address, state| {
            if !seen.contains(address) {
                state.missed_batches = state.missed_batches.saturating_add(1);
            }
            state.missed_batches <= MISSED_BATCH_LIMIT
                && now_ms.saturating_sub(state.accessed_at_ms) < ACCESS_EXPIRY_MS
        });
    }

    pub fn check(
        &mut self,
        config: &IdleConfig,
        now_ms: i64,
        peer: &IdleObservation<'_>,
    ) -> Option<IdleTimeout> {
        if !peer.seeding {
            match config.mode {
                ProtectionMode::SeedingOnly => return None,
                ProtectionMode::ByPeerFlags => {
                    let flags = peer.flags?;
                    if config.ignore_interested && flags.contains(['d', 'D', 'u', 'U']) {
                        return None;
                    }
                }
                ProtectionMode::Always => {}
            }
        }
        if peer.upload_speed > config.speed_threshold
            || peer.download_speed > config.speed_threshold
        {
            self.entries.remove(&peer.address);
            return None;
        }

        let state = self
            .entries
            .entry(peer.address)
            .or_insert_with(|| IdleState {
                start: IdleTimeout {
                    start_ms: now_ms,
                    progress: peer.progress,
                    uploaded: peer.uploaded,
                    downloaded: peer.downloaded,
                },
                accessed_at_ms: now_ms,
                missed_batches: 0,
            });
        state.accessed_at_ms = now_ms;
        let orig = state.start;
        let elapsed = now_ms.saturating_sub(orig.start_ms);
        // Preserve the source calculation's milliseconds and its progress scaling.
        let divisor = elapsed.saturating_add(1).max(1);
        let upload_rate = peer.uploaded.saturating_sub(orig.uploaded) / divisor;
        let download_rate = peer.downloaded.saturating_sub(orig.downloaded) / divisor;
        let change = (peer.progress * 100.0 - orig.progress).abs();
        if upload_rate > config.speed_threshold
            || download_rate > config.speed_threshold
            || (config.reset_on_status_change && change >= config.min_status_change_percentage)
        {
            self.entries.remove(&peer.address);
            return None;
        }
        if elapsed > config.max_duration_ms {
            self.entries.remove(&peer.address);
            return Some(orig);
        }
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn timeout_is_strict_and_speed_resets_the_timer() {
        let mut tracker = IdleConnections::default();
        let config = IdleConfig {
            mode: ProtectionMode::Always,
            max_duration_ms: 1000,
            speed_threshold: 64,
            reset_on_status_change: false,
            min_status_change_percentage: 0.001,
            ignore_interested: true,
        };
        let mut peer = IdleObservation {
            address: "203.0.113.10:6881".parse().unwrap(),
            flags: None,
            seeding: false,
            progress: 0.0,
            upload_speed: 0,
            download_speed: 0,
            uploaded: 0,
            downloaded: 0,
        };
        assert!(tracker.check(&config, 100, &peer).is_none());
        assert!(tracker.check(&config, 1100, &peer).is_none());
        peer.upload_speed = 65;
        assert!(tracker.check(&config, 1101, &peer).is_none());
        peer.upload_speed = 0;
        assert!(tracker.check(&config, 1200, &peer).is_none());
        assert_eq!(tracker.check(&config, 2201, &peer).unwrap().start_ms, 1200);
    }
}
