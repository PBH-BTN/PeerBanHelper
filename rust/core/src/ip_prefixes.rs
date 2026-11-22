//! Prefix matching and address literals shared by PBH rule feeds.

use std::collections::{BTreeMap, HashMap};
use std::net::{IpAddr, Ipv4Addr};

use ipnet::IpNet;

use crate::{AddressRule, normalize_ip};

pub fn as_network(rule: AddressRule) -> Option<IpNet> {
    let network = match rule {
        AddressRule::Host(address) => {
            return IpNet::new(address, if address.is_ipv4() { 32 } else { 128 }).ok();
        }
        AddressRule::Network(network) => network.trunc(),
    };
    if let IpNet::V6(network) = network
        && network.prefix_len() >= 96
        && let Some(address) = network.network().to_ipv4_mapped()
    {
        return IpNet::new(address.into(), network.prefix_len() - 96).ok();
    }
    Some(network)
}

pub fn parse_address(raw: &str) -> Option<AddressRule> {
    if let Ok(rule) = raw.parse() {
        return Some(rule);
    }
    if let Some((host, prefix)) = raw.split_once('/') {
        let host = ipv4_literal(host)?;
        let prefix = if prefix.contains('.') {
            ipv4_literal(prefix)?.to_string()
        } else {
            prefix.to_owned()
        };
        return format!("{host}/{prefix}").parse().ok();
    }
    ipv4_literal(raw).map(|ip| AddressRule::Host(ip.into()))
}

// IPAddressString's IPv4 defaults also accept inet_aton literals, including
// octal dotted components and a wider final component in shortened addresses.
fn ipv4_literal(raw: &str) -> Option<Ipv4Addr> {
    let fields: Vec<_> = raw.split('.').collect();
    if fields.len() > 4 {
        return None;
    }
    let (last, leading) = fields.split_last()?;
    let mut bits = 0u32;
    for field in leading {
        bits = (bits << 8) | u32::from(u8::try_from(number(field)?).ok()?);
    }
    let tail_bits = (4 - leading.len()) * 8;
    let last = number(last)?;
    if last > u32::MAX >> (32 - tail_bits) {
        return None;
    }
    let leading = bits.checked_shl(tail_bits as u32).unwrap_or_default();
    Some(Ipv4Addr::from_bits(leading | last))
}

fn number(field: &str) -> Option<u32> {
    let (digits, radix) = if let Some(digits) = field
        .strip_prefix("0x")
        .or_else(|| field.strip_prefix("0X"))
    {
        (digits, 16)
    } else if let Some(digits) = field
        .strip_prefix("0b")
        .or_else(|| field.strip_prefix("0B"))
    {
        (digits, 2)
    } else if field.starts_with('0')
        && field.len() > 1
        && field.bytes().all(|digit| matches!(digit, b'0'..=b'7'))
    {
        (field, 8)
    } else {
        (field, 10)
    };
    if digits.starts_with(['+', '-']) {
        return None;
    }
    u32::from_str_radix(digits, radix).ok()
}

pub fn covering_network(start: IpAddr, end: IpAddr) -> Option<IpNet> {
    let prefix = match (start, end) {
        (IpAddr::V4(start), IpAddr::V4(end)) => (start.to_bits() ^ end.to_bits()).leading_zeros(),
        (IpAddr::V6(start), IpAddr::V6(end)) => (start.to_bits() ^ end.to_bits()).leading_zeros(),
        _ => return None,
    };
    IpNet::new(start, prefix as u8)
        .ok()
        .map(|network| network.trunc())
}

/// Index only occupied prefix lengths, avoiding a scan across every rule per Peer.
#[derive(Default)]
pub struct PrefixMap<T> {
    v4: BTreeMap<u8, HashMap<IpNet, T>>,
    v6: BTreeMap<u8, HashMap<IpNet, T>>,
}

impl<T> PrefixMap<T> {
    pub fn insert(&mut self, network: IpNet, value: T) {
        let family = if network.addr().is_ipv4() {
            &mut self.v4
        } else {
            &mut self.v6
        };
        family
            .entry(network.prefix_len())
            .or_default()
            .insert(network, value);
    }

    pub fn find(&self, address: IpAddr) -> Option<(IpNet, &T)> {
        let address = normalize_ip(address);
        let family = if address.is_ipv4() {
            &self.v4
        } else {
            &self.v6
        };
        family.iter().find_map(|(length, values)| {
            let network = IpNet::new(address, *length).ok()?.trunc();
            values.get(&network).map(|value| (network, value))
        })
    }

    pub fn len(&self) -> usize {
        self.v4
            .values()
            .chain(self.v6.values())
            .map(HashMap::len)
            .sum()
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }
}

#[cfg(test)]
mod tests {
    use super::{as_network, parse_address};

    #[test]
    fn legacy_literals_and_ipv4_mapped_subnets_use_the_same_prefix_index() {
        for (literal, expected) in [
            ("010.010.010.010", "8.8.8.8/32"),
            ("0xc0000209", "192.0.2.9/32"),
            ("192.0.521", "192.0.2.9/32"),
            ("::ffff:192.0.2.0/120", "192.0.2.0/24"),
        ] {
            let parsed = parse_address(literal).and_then(as_network).unwrap();
            assert_eq!(parsed.to_string(), expected);
        }
        assert!(parse_address("192.0.65536").is_none());
        assert!(parse_address("192.0.2.256").is_none());
    }
}
