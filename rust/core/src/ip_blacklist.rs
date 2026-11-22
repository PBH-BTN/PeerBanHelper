use std::collections::BTreeSet;
use std::fmt;
use std::net::{IpAddr, Ipv4Addr};
use std::str::FromStr;

use ipnet::{IpNet, Ipv4Net};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AddressRule {
    Host(IpAddr),
    Network(IpNet),
}

/// An address or prefix outside the forms supported by the Rust parser.
#[derive(Debug, thiserror::Error)]
#[error("invalid address or network: {0}")]
pub struct AddressRuleError(String);

impl FromStr for AddressRule {
    type Err = AddressRuleError;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        let invalid = || AddressRuleError(value.to_owned());
        if let Some((host, mask)) = value.split_once('/') {
            let ip = parse_host(host).map_err(|_| invalid())?;
            let network = match (ip, mask.parse::<Ipv4Addr>()) {
                (IpAddr::V4(address), Ok(netmask)) => {
                    IpNet::V4(Ipv4Net::with_netmask(address, netmask).map_err(|_| invalid())?)
                }
                _ => IpNet::new(ip, mask.parse::<u8>().map_err(|_| invalid())?)
                    .map_err(|_| invalid())?,
            };
            return Ok(Self::Network(network));
        }

        parse_host(value)
            .map(normalize_ip)
            .map(Self::Host)
            .map_err(|_| invalid())
    }
}

fn parse_host(value: &str) -> Result<IpAddr, std::net::AddrParseError> {
    value
        .strip_prefix('[')
        .and_then(|value| value.strip_suffix(']'))
        .unwrap_or(value)
        .parse()
}

pub fn normalize_ip(ip: IpAddr) -> IpAddr {
    ip.to_canonical()
}

impl AddressRule {
    pub fn contains(self, address: IpAddr) -> bool {
        match self {
            Self::Host(host) => host == normalize_ip(address),
            Self::Network(network) => {
                network.contains(&address) || network.contains(&normalize_ip(address))
            }
        }
    }
}

impl fmt::Display for AddressRule {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Host(ip) => ip.fmt(formatter),
            Self::Network(network) => network.fmt(formatter),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BlacklistMatch {
    Port(u16),
    Address(AddressRule),
}

#[derive(Debug, Default)]
pub struct IpBlacklist {
    pub ports: BTreeSet<u16>,
    pub addresses: Vec<AddressRule>,
}

impl IpBlacklist {
    /// Non-GeoIP matching from Java `IPBlackList.shouldBanPeer`.
    pub fn check(&self, address: IpAddr, port: u16, handshaking: bool) -> Option<BlacklistMatch> {
        if handshaking {
            return None;
        }
        if self.ports.contains(&port) {
            return Some(BlacklistMatch::Port(port));
        }
        self.addresses
            .iter()
            .copied()
            .find(|rule| rule.contains(address))
            .map(BlacklistMatch::Address)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checks_handshake_and_port_before_address() {
        let blacklist = IpBlacklist {
            ports: BTreeSet::from([6881]),
            addresses: vec!["198.51.100.7/24".parse().unwrap()],
        };
        let address = "198.51.100.42".parse().unwrap();
        assert_eq!(blacklist.check(address, 6881, true), None);
        assert_eq!(
            blacklist.check(address, 6881, false),
            Some(BlacklistMatch::Port(6881))
        );
        assert_eq!(
            blacklist.check(address, 6882, false),
            Some(BlacklistMatch::Address(blacklist.addresses[0]))
        );
    }

    #[test]
    fn supports_documented_dotted_mask_and_ipv6_prefix() {
        let v4: AddressRule = "198.51.100.7/255.255.255.0".parse().unwrap();
        let v6: AddressRule = "2001:db8::/32".parse().unwrap();
        assert!(v4.contains("::ffff:198.51.100.255".parse().unwrap()));
        assert!(!v4.contains("198.51.101.0".parse().unwrap()));
        assert!(v6.contains("2001:db8:1234::1".parse().unwrap()));
        assert!(!v6.contains("2001:db9::1".parse().unwrap()));
        assert!("198.51.100.7/255.0.255.0".parse::<AddressRule>().is_err());
    }
}
