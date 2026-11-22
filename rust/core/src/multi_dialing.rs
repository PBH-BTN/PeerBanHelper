//! Subnet peer counting and retry windows from Java MultiDialingBlocker.

use std::collections::{HashMap, VecDeque};
use std::net::IpAddr;

use ipnet::IpNet;

use crate::normalize_ip;

const MAX_SUBNETS: usize = 1024;
const MAX_PEERS_PER_SUBNET: usize = 16;

pub struct MultiDialConfig {
    pub ipv4_prefix: u8,
    pub ipv6_prefix: u8,
    pub ipv4_limit: i64,
    pub ipv6_limit: i64,
    pub lifespan_ms: i64,
    pub hunting_ms: Option<i64>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct MultiDialMatch {
    pub subnet: IpNet,
    pub peer_count: usize,
    pub hunting: bool,
}

#[derive(Default)]
pub struct MultiDialing {
    subnets: HashMap<(String, IpNet), PeerGroup>,
    hunting: HashMap<(String, IpNet), i64>,
}

#[derive(Default)]
struct PeerGroup {
    peers: VecDeque<(IpAddr, i64)>,
    last_access_ms: i64,
}

impl MultiDialing {
    pub fn check(
        &mut self,
        config: &MultiDialConfig,
        now: i64,
        torrent: &str,
        peer: IpAddr,
        handshaking: bool,
    ) -> Option<MultiDialMatch> {
        if handshaking {
            return None;
        }
        let address = normalize_ip(peer);
        let (prefix, limit) = if address.is_ipv4() {
            (config.ipv4_prefix, config.ipv4_limit)
        } else {
            (config.ipv6_prefix, config.ipv6_limit)
        };
        let subnet = IpNet::new(address, prefix).ok()?.trunc();
        let key = (torrent.to_owned(), subnet);
        // Drop the group on access if its outer window expired. Peer entries have
        // independent lifetimes and only distinct addresses contribute to the threshold.
        if self
            .subnets
            .get(&key)
            .is_some_and(|group| now.saturating_sub(group.last_access_ms) >= config.lifespan_ms)
        {
            self.subnets.remove(&key);
        }
        if !self.subnets.contains_key(&key)
            && self.subnets.len() >= MAX_SUBNETS
            && let Some(oldest) = self
                .subnets
                .iter()
                .min_by_key(|(_, group)| group.last_access_ms)
                .map(|(key, _)| key.clone())
        {
            self.subnets.remove(&oldest);
        }
        let group = self.subnets.entry(key.clone()).or_default();
        group.last_access_ms = now;
        group.peers.retain(|(known, last)| {
            *known != address && now.saturating_sub(*last) < config.lifespan_ms
        });
        if group.peers.len() >= MAX_PEERS_PER_SUBNET {
            group.peers.pop_front();
        }
        group.peers.push_back((address, now));
        let peer_count = group.peers.len();

        let over_limit = peer_count as i64 > limit;
        let mut hunting = false;
        if let Some(window) = config.hunting_ms {
            hunting = !over_limit
                && self
                    .hunting
                    .get(&key)
                    .is_some_and(|seen| now.saturating_sub(*seen) < window);
            if over_limit || hunting {
                if !self.hunting.contains_key(&key)
                    && self.hunting.len() >= MAX_SUBNETS
                    && let Some(oldest) = self
                        .hunting
                        .iter()
                        .min_by_key(|(_, seen)| **seen)
                        .map(|(key, _)| key.clone())
                {
                    self.hunting.remove(&oldest);
                }
                self.hunting.insert(key, now);
            }
        }
        (over_limit || hunting).then_some(MultiDialMatch {
            subnet,
            peer_count,
            hunting,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn counts_distinct_hosts_per_torrent_and_refreshes_hunting_window() {
        let mut state = MultiDialing::default();
        let config = MultiDialConfig {
            ipv4_prefix: 24,
            ipv6_prefix: 56,
            ipv4_limit: 1,
            ipv6_limit: 5,
            lifespan_ms: 100,
            hunting_ms: Some(1000),
        };
        let a = "203.0.113.5".parse().unwrap();
        let b = "203.0.113.6".parse().unwrap();
        assert!(state.check(&config, 100, "torrent", a, false).is_none());
        assert!(state.check(&config, 101, "torrent", a, false).is_none());
        assert!(state.check(&config, 102, "torrent", b, true).is_none());
        assert!(state.check(&config, 103, "other", b, false).is_none());
        let hit = state.check(&config, 104, "torrent", b, false).unwrap();
        assert_eq!(hit.peer_count, 2);
        assert!(!hit.hunting);
        assert!(
            state
                .check(&config, 500, "torrent", a, false)
                .unwrap()
                .hunting
        );
        assert!(state.check(&config, 1500, "torrent", a, false).is_none());
    }
}
