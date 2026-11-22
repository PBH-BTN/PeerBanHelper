use std::fmt::Write;
use std::net::IpAddr;

use ipnet::IpNet;
use pbh_core::{AddressPolicy, compressed_address};
use uuid::Uuid;

pub const BLOCKLIST_PATH: &str = "/blocklist/p2p-plain-format";
const TRANSMISSION_EMPTY_LIST: &str = "TransmissionWorkaround:127.127.127.127-127.127.127.127";

/// Renders host bans and their default PBH address mappings for Transmission.
pub fn render_p2p_blocklist(addresses: &[IpAddr], user_agent: Option<&str>) -> String {
    let hosts: Vec<IpNet> = addresses.iter().copied().map(IpNet::from).collect();
    render_p2p_blocklist_networks(&hosts, user_agent)
}

/// Renders hosts and CIDRs as P2P Plain address ranges using PBH defaults.
pub fn render_p2p_blocklist_networks(networks: &[IpNet], user_agent: Option<&str>) -> String {
    render_p2p_blocklist_networks_with_policy(networks, user_agent, &AddressPolicy::default())
}

/// Renders hosts and CIDRs using the configured PBH remapping policy.
pub fn render_p2p_blocklist_networks_with_policy(
    networks: &[IpNet],
    user_agent: Option<&str>,
    policy: &AddressPolicy,
) -> String {
    let mut result = String::new();
    for network in policy.ban_entries(networks, true) {
        let start = compressed_address(network.network());
        let end = compressed_address(network.broadcast());
        let id = Uuid::new_v4().simple();
        writeln!(result, "{id}:{start}-{end}").expect("writes to String cannot fail");
    }
    if result.is_empty() && user_agent.is_some_and(|agent| agent.starts_with("Transmission")) {
        result.push_str(TRANSMISSION_EMPTY_LIST);
    }
    result
}
