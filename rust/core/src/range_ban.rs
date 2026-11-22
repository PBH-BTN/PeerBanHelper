//! Related-address lookup from the Java AutoRangeBan rule.

use std::net::IpAddr;

use ipnet::{IpNet, PrefixLenError};

use crate::normalize_ip;

#[derive(Clone, Copy, Debug)]
pub struct RangeBan {
    ipv4_prefix: u8,
    ipv6_prefix: u8,
}

#[derive(Debug, PartialEq, Eq)]
pub struct RelatedBan {
    pub address: IpAddr,
    pub network: IpNet,
}

impl RangeBan {
    pub fn new(ipv4_prefix: u8, ipv6_prefix: u8) -> Result<Self, PrefixLenError> {
        IpNet::new(IpAddr::V4(std::net::Ipv4Addr::UNSPECIFIED), ipv4_prefix)?;
        IpNet::new(IpAddr::V6(std::net::Ipv6Addr::UNSPECIFIED), ipv6_prefix)?;
        Ok(Self {
            ipv4_prefix,
            ipv6_prefix,
        })
    }

    pub fn related_ban(
        self,
        peer: IpAddr,
        handshaking: bool,
        banned: &[(IpAddr, bool)],
    ) -> Option<RelatedBan> {
        let peer = normalize_ip(peer);
        if handshaking
            || banned
                .iter()
                .any(|(address, _)| normalize_ip(*address) == peer)
        {
            return None;
        }
        let prefix = if peer.is_ipv4() {
            self.ipv4_prefix
        } else {
            self.ipv6_prefix
        };
        banned.iter().find_map(|(address, disconnect_only)| {
            let address = normalize_ip(*address);
            if *disconnect_only || peer.is_ipv4() != address.is_ipv4() {
                return None;
            }
            let network = IpNet::new(address, prefix).ok()?.trunc();
            network
                .contains(&peer)
                .then_some(RelatedBan { address, network })
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_current_full_bans_trigger_nearby_addresses() {
        let rule = RangeBan::new(24, 64).unwrap();
        let existing = "203.0.113.1".parse().unwrap();
        let other = "::ffff:203.0.113.2".parse().unwrap();
        assert_eq!(rule.related_ban(other, false, &[(existing, true)]), None);
        assert_eq!(
            rule.related_ban(existing, false, &[(existing, false)]),
            None
        );
        assert_eq!(rule.related_ban(other, true, &[(existing, false)]), None);
        let matched = rule
            .related_ban(other, false, &[(existing, false)])
            .unwrap();
        assert_eq!(matched.network.to_string(), "203.0.113.0/24");
    }
}
