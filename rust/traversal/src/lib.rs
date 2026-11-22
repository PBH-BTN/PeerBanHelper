//! Network traversal protocols and live connection address translation.

pub mod forwarder;
mod mappings;
mod stun;
pub mod tunnel;
mod udp;
pub mod upnp;

pub use mappings::{ConnectionMapping, ConnectionMappings, MappingInUse};
pub use stun::{BindingRequest, BindingResponse, ChangeRequest, PacketError, StunVersion};
pub use udp::{Error, NatResult, NatType, RetryPolicy, UdpStunClient};

fn epoch_millis() -> u64 {
    let millis = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis();
    u64::try_from(millis).unwrap_or(u64::MAX)
}
