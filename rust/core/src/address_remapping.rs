//! Address translation and ban expansion shared by the downloaders.

use std::collections::HashSet;
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};

use ipnet::{IpNet, Ipv4Net, Ipv6Net};

use crate::{AddressRule, as_network};

/// The configured `ip-remapping` and `banlist-remapping` policies.
#[derive(Clone, Debug)]
pub struct AddressPolicy {
    /// An empty list disables NAT64 translation.
    pub nat64_prefixes: Vec<Ipv6Net>,
    pub translate_teredo_peers: bool,
    pub ipv4_ban_prefix: Option<u8>,
    pub ipv6_ban_prefix: Option<u8>,
}

impl Default for AddressPolicy {
    fn default() -> Self {
        Self {
            nat64_prefixes: vec!["64:ff9b::/96".parse().expect("valid default NAT64 prefix")],
            translate_teredo_peers: false,
            ipv4_ban_prefix: None,
            ipv6_ban_prefix: Some(52),
        }
    }
}

impl AddressPolicy {
    /// Normalizes the address seen by PBH. The caller retains the raw downloader key.
    pub fn translate_peer(&self, ip: IpAddr, port: u16) -> (IpAddr, u16) {
        let ip = ip.to_canonical();
        let IpAddr::V6(v6) = ip else {
            return (ip, port);
        };
        if self.translate_teredo_peers
            && let Some((address, port)) = teredo_endpoint(v6)
        {
            return (address.into(), port);
        }
        if self
            .nat64_prefixes
            .iter()
            .any(|prefix| prefix.contains(&v6))
        {
            return (embedded_ipv4(v6).into(), port);
        }
        (ip, port)
    }

    /// Expands bans into unique hosts and networks supported by the target downloader.
    pub fn ban_entries(&self, networks: &[IpNet], supports_ranges: bool) -> Vec<IpNet> {
        let mut entries = Vec::new();
        for &network in networks {
            let network = as_network(AddressRule::Network(network)).expect("valid IP network");
            match network {
                IpNet::V4(v4) => self.push_ipv4_ban(v4, supports_ranges, &mut entries),
                IpNet::V6(v6) => self.push_ipv6_ban(v6, supports_ranges, &mut entries),
            }
        }
        let mut seen = HashSet::new();
        entries.retain(|entry| seen.insert(*entry));
        entries
    }

    /// Serializes host bans for APIs accepting a list of IP or CIDR strings.
    pub fn ban_rules(&self, addresses: &[IpAddr], supports_ranges: bool) -> Vec<String> {
        let networks: Vec<_> = addresses.iter().copied().map(IpNet::from).collect();
        self.ban_entries(&networks, supports_ranges)
            .into_iter()
            .map(|network| {
                let address = compressed_address(network.network());
                if network.prefix_len() == network.max_prefix_len() {
                    address
                } else {
                    format!("{address}/{}", network.prefix_len())
                }
            })
            .collect()
    }

    fn push_ipv4_ban(&self, v4: Ipv4Net, supports_ranges: bool, entries: &mut Vec<IpNet>) {
        push_ipv4_pair(v4, entries);
        if supports_ranges && let Some(expanded) = self.expand_ipv4(v4) {
            push_ipv4_pair(expanded, entries);
        }
    }

    fn push_ipv6_ban(&self, v6: Ipv6Net, supports_ranges: bool, entries: &mut Vec<IpNet>) {
        entries.push(v6.into());
        if self
            .nat64_prefixes
            .iter()
            .any(|prefix| prefix.contains(&v6))
        {
            let address = embedded_ipv4(v6.network());
            let length = v6.prefix_len().saturating_sub(96);
            let extracted = Ipv4Net::new(address, length)
                .expect("embedded IPv4 prefix is in range")
                .trunc();
            entries.push(extracted.into());
            if supports_ranges && let Some(expanded) = self.expand_ipv4(extracted) {
                push_ipv4_pair(expanded, entries);
            }
        } else if v6.prefix_len() >= 32
            && let Some((address, _)) = teredo_endpoint(v6.network())
        {
            entries.push(IpNet::from(IpAddr::from(address)));
        } else if supports_ranges
            && let Some(prefix) = self.ipv6_ban_prefix
            && let Ok(expanded) = Ipv6Net::new(v6.network(), prefix.min(v6.prefix_len()))
        {
            entries.push(expanded.trunc().into());
        }
    }

    fn expand_ipv4(&self, v4: Ipv4Net) -> Option<Ipv4Net> {
        let prefix = self.ipv4_ban_prefix?;
        Ipv4Net::new(v4.network(), prefix.min(v4.prefix_len()))
            .ok()
            .map(|net| net.trunc())
    }
}

fn push_ipv4_pair(v4: Ipv4Net, entries: &mut Vec<IpNet>) {
    let mapped = Ipv6Net::new(v4.network().to_ipv6_mapped(), 96 + v4.prefix_len())
        .expect("mapped IPv4 prefix is in range");
    entries.extend([IpNet::V4(v4), IpNet::V6(mapped)]);
}

fn embedded_ipv4(address: Ipv6Addr) -> Ipv4Addr {
    let segments = address.segments();
    Ipv4Addr::from((u32::from(segments[6]) << 16) | u32::from(segments[7]))
}

fn teredo_endpoint(address: Ipv6Addr) -> Option<(Ipv4Addr, u16)> {
    let segments = address.segments();
    if segments[..2] == [0x2001, 0] {
        Some((
            Ipv4Addr::from(!u32::from(embedded_ipv4(address))),
            !segments[5],
        ))
    } else {
        None
    }
}

/// Uses hexadecimal segments for mapped IPv4, as required by the ban-list formats.
pub fn compressed_address(address: IpAddr) -> String {
    if let IpAddr::V6(v6) = address
        && v6.to_ipv4_mapped().is_some()
    {
        let segments = v6.segments();
        return format!("::ffff:{:x}:{:x}", segments[6], segments[7]);
    }
    address.to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn host(value: &str) -> IpAddr {
        value.parse().unwrap()
    }

    fn network(value: &str) -> IpNet {
        value.parse().unwrap()
    }

    #[test]
    fn peer_translation_uses_custom_nat64_and_optional_teredo_port() {
        let mut policy = AddressPolicy {
            nat64_prefixes: vec!["2001:db8:64::/96".parse().unwrap()],
            ..AddressPolicy::default()
        };
        let default_nat64 = host("64:ff9b::c000:237");
        let teredo = host("2001:0:4136:e378:8000:63bf:3fff:fdd2");
        assert_eq!(policy.translate_peer(default_nat64, 7), (default_nat64, 7));
        assert_eq!(
            policy.translate_peer(host("2001:db8:64::c000:237"), 1234),
            (host("192.0.2.55"), 1234)
        );
        assert_eq!(
            policy.translate_peer(host("::ffff:192.0.2.55"), 1234),
            (host("192.0.2.55"), 1234)
        );
        assert_eq!(policy.translate_peer(teredo, 1234), (teredo, 1234));
        assert_eq!(
            policy.ban_rules(&[teredo], true),
            [teredo.to_string(), "192.0.2.45".into()]
        );

        policy.translate_teredo_peers = true;
        assert_eq!(
            policy.translate_peer(teredo, 1234),
            (host("192.0.2.45"), 40000)
        );
        policy.nat64_prefixes.clear();
        let custom_nat64 = host("2001:db8:64::c000:237");
        assert_eq!(
            policy.translate_peer(custom_nat64, 1234),
            (custom_nat64, 1234)
        );
    }

    #[test]
    fn expands_bans_by_downloader_capability_and_preserves_wider_networks() {
        let policy = AddressPolicy {
            nat64_prefixes: vec!["2001:db8:64::/96".parse().unwrap()],
            translate_teredo_peers: false,
            ipv4_ban_prefix: Some(30),
            ipv6_ban_prefix: None,
        };
        let addresses = [
            host("2001:db8:64::c000:237"),
            host("2001:db8:42::1"),
            host("2001:db8:64::c000:237"),
        ];
        assert_eq!(
            policy.ban_rules(&addresses, true),
            [
                "2001:db8:64::c000:237",
                "192.0.2.55",
                "192.0.2.52/30",
                "::ffff:c000:234/126",
                "2001:db8:42::1"
            ]
        );
        assert_eq!(
            policy.ban_rules(&addresses, false),
            ["2001:db8:64::c000:237", "192.0.2.55", "2001:db8:42::1"]
        );
        assert_eq!(
            policy.ban_entries(
                &[network("::ffff:c000:200/120"), network("192.0.2.0/24")],
                true
            ),
            [network("192.0.2.0/24"), network("::ffff:c000:200/120")]
        );
        assert_eq!(
            policy.ban_entries(&[network("2001:db8:64::c000:200/120")], false),
            [
                network("2001:db8:64::c000:200/120"),
                network("192.0.2.0/24")
            ]
        );
    }
}
