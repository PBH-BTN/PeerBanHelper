use serde::{Deserialize, Serialize};

/// Priority follows `PeerAction` and `DigestionSession` in the Java reference.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Deserialize, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Action {
    NoAction,
    BanForDisconnect,
    Ban,
    Skip,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
pub struct Decision {
    pub action: Action,
    /// The rule's proposed duration, in milliseconds, before global fallback.
    pub duration: i64,
    pub reason: String,
}

/// Combine determinations for the same downloader, torrent, and peer.
/// When action and duration are identical, the last determination wins.
pub fn select_decision(candidates: impl IntoIterator<Item = Decision>) -> Option<Decision> {
    candidates
        .into_iter()
        .max_by_key(|decision| (decision.action, decision.duration))
}
