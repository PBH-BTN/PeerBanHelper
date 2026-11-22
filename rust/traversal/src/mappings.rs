use std::collections::{HashMap, hash_map::Entry};
use std::net::SocketAddr;
use std::sync::{Arc, Mutex};

use thiserror::Error;

/// Maps a forwarder's local upstream socket to the real downstream peer.
#[derive(Clone, Default)]
pub struct ConnectionMappings {
    entries: Arc<Mutex<HashMap<SocketAddr, SocketAddr>>>,
}

#[derive(Debug, Error, Eq, PartialEq)]
#[error("the upstream local socket already has an active peer mapping")]
pub struct MappingInUse;

/// Owns a connection's registration. Dropping it removes the translation.
pub struct ConnectionMapping {
    mappings: ConnectionMappings,
    upstream: SocketAddr,
}

impl ConnectionMappings {
    pub fn register(
        &self,
        upstream: SocketAddr,
        downstream: SocketAddr,
    ) -> Result<ConnectionMapping, MappingInUse> {
        let upstream = canonical(upstream);
        let mut entries = self
            .entries
            .lock()
            .expect("connection mappings lock poisoned");
        match entries.entry(upstream) {
            Entry::Vacant(slot) => slot.insert(canonical(downstream)),
            Entry::Occupied(_) => return Err(MappingInUse),
        };
        Ok(ConnectionMapping {
            mappings: self.clone(),
            upstream,
        })
    }

    pub fn translate(&self, upstream: SocketAddr) -> Option<SocketAddr> {
        self.entries
            .lock()
            .expect("connection mappings lock poisoned")
            .get(&canonical(upstream))
            .copied()
    }
}

impl Drop for ConnectionMapping {
    fn drop(&mut self) {
        self.mappings
            .entries
            .lock()
            .expect("connection mappings lock poisoned")
            .remove(&self.upstream);
    }
}

pub(crate) fn canonical(address: SocketAddr) -> SocketAddr {
    match address {
        SocketAddr::V6(ipv6) if let Some(ipv4) = ipv6.ip().to_ipv4_mapped() => {
            SocketAddr::new(ipv4.into(), ipv6.port())
        }
        address => address,
    }
}
