//! PeerBanHelper's downloader-independent policy and scan state.

mod address_remapping;
mod decision;
mod idle_connections;
mod ip_blacklist;
mod ip_prefixes;
mod multi_dialing;
mod progress_cheat;
mod range_ban;
mod string_rules;
mod xunlei;

pub use address_remapping::{AddressPolicy, compressed_address};
pub use decision::{Action, Decision, select_decision};
pub use idle_connections::{
    IdleConfig, IdleConnections, IdleObservation, IdleTimeout, ProtectionMode,
};
pub use ip_blacklist::{AddressRule, AddressRuleError, BlacklistMatch, IpBlacklist, normalize_ip};
pub use ip_prefixes::{PrefixMap, as_network, covering_network, parse_address};
pub use multi_dialing::{MultiDialConfig, MultiDialMatch, MultiDialing};
pub use progress_cheat::{PcbConfig, PcbFinding, PcbObservation, PcbState, check_progress};
pub use range_ban::{RangeBan, RelatedBan};
pub use string_rules::{StringRuleMatch, StringRules, StringRulesError};
pub use xunlei::{XunleiBan, xunlei_ban};
