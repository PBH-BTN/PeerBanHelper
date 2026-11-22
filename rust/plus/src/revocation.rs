use std::collections::HashSet;
use std::fs;
use std::io::{self, Write};
use std::path::Path;
use std::sync::OnceLock;

use rsa::RsaPublicKey;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tempfile::NamedTempFile;

use crate::payload::Payload;
use crate::trial::{IssueError, local_signer, sign_local_payload};
use crate::{read_limited, recover, sha256};

const BUNDLED_CSV: &str = include_str!("../../../src/main/resources/revoked-licenses.csv");
const HEADERS: [&str; 5] = [
    "license_to_hash",
    "description_hash",
    "order_id_hash",
    "payment_order_id_hash",
    "email_hash",
];
type Hashes = [HashSet<String>; HEADERS.len()];

pub(crate) fn is_bundled_revoked(payload: &Payload) -> bool {
    static BUNDLED: OnceLock<Hashes> = OnceLock::new();
    is_revoked(payload, BUNDLED.get_or_init(|| parse(BUNDLED_CSV)))
}

fn is_revoked(payload: &Payload, hashes: &Hashes) -> bool {
    payload
        .revoke_fields()
        .into_iter()
        .zip(hashes)
        .any(|(field, list)| field.is_some_and(|value| list.contains(&sha256(value))))
}

fn parse(data: &str) -> Hashes {
    let mut hashes = Hashes::default();
    let mut csv = csv::ReaderBuilder::new()
        .flexible(true)
        .trim(csv::Trim::All)
        .from_reader(data.as_bytes());
    let Ok(header) = csv.headers() else {
        return hashes;
    };
    let indexes = HEADERS.map(|name| header.iter().position(|item| item == name));
    for row in csv.records().filter_map(Result::ok) {
        for (index, hashes) in indexes.iter().zip(&mut hashes) {
            if let Some(value) = index.and_then(|index| row.get(index))
                && value.len() == 64
                && value.bytes().all(|byte| byte.is_ascii_hexdigit())
            {
                hashes.insert(value.to_ascii_lowercase());
            }
        }
    }
    hashes
}

#[derive(Clone, Copy, Deserialize, Serialize)]
pub(crate) struct CachedRevocation {
    pub(crate) revoked: bool,
    pub(crate) timestamp: i64,
}

impl CachedRevocation {
    pub(crate) fn fresh(&self, now_ms: i64) -> bool {
        const FRESH_MS: i64 = 24 * 60 * 60 * 1000;
        now_ms
            .checked_sub(self.timestamp)
            .is_some_and(|age| (0..=FRESH_MS).contains(&age))
    }
}

pub(crate) fn cache_id(payload: &Payload) -> String {
    let mut digest = Sha256::new();
    for value in payload.revoke_fields().into_iter().flatten() {
        digest.update(value.as_bytes());
    }
    format!("{:x}", digest.finalize())
}

pub(crate) fn cached_revocation(
    cache: &Path,
    local_key: &RsaPublicKey,
    payload: &Payload,
) -> Option<CachedRevocation> {
    read_cache(cache, local_key, &cache_id(payload))
}

fn read_cache(cache: &Path, local_key: &RsaPublicKey, id: &str) -> Option<CachedRevocation> {
    let path = cache.join(format!("{id}.json"));
    let signed = read_limited(&path, 32 * 1024)?;
    let contents = recover(&signed, local_key).ok()?;
    serde_json::from_slice(&contents).ok()
}

pub(crate) fn save_cache(
    data_dir: &Path,
    hardware_id: &str,
    id: &str,
    record: CachedRevocation,
) -> Result<(), IssueError> {
    let directory = data_dir.join("cache").join("license-revoke");
    fs::create_dir_all(&directory).map_err(IssueError::KeyIo)?;
    let key = local_signer(data_dir, hardware_id)?;
    if read_cache(&directory, &key.to_public_key(), id).is_some_and(|old| {
        old.timestamp > record.timestamp
            || (old.timestamp == record.timestamp && old.revoked && !record.revoked)
    }) {
        return Ok(());
    }
    let json = serde_json::to_vec(&record).map_err(|_| IssueError::InvalidTrial)?;
    let signed = sign_local_payload(&key, &json)?;
    store_signed(&directory, id, &signed).map_err(IssueError::KeyIo)
}

fn store_signed(directory: &Path, id: &str, signed: &[u8]) -> io::Result<()> {
    let mut draft = NamedTempFile::new_in(directory)?;
    draft.write_all(signed)?;
    draft.as_file().sync_all()?;
    draft
        .into_temp_path()
        .persist(directory.join(format!("{id}.json")))
        .map_err(|error| error.error)
}

#[cfg(test)]
mod tests {
    use serde_json::json;

    use super::*;

    #[test]
    fn revocation_matches_the_correct_field_and_ignores_blank_columns() {
        let data = format!(
            "email_hash,payment_order_id_hash,license_to_hash,description_hash,order_id_hash\n,,{},,\n,,NA,,\n",
            sha256("Fixture")
        );
        let payload = json!({
            "verifyMagic":"PeerBanHelper","licenseVersion":2,"licenseTo":"Fixture","description":"Other",
        });
        let checked = Payload::parse(&serde_json::to_vec(&payload).unwrap()).unwrap();
        let hashes = parse(&data);
        assert!(is_revoked(&checked, &hashes));
        let payload = json!({
            "verifyMagic":"PeerBanHelper","licenseVersion":2,"licenseTo":"Other","description":"Fixture",
        });
        let checked = Payload::parse(&serde_json::to_vec(&payload).unwrap()).unwrap();
        assert!(!is_revoked(&checked, &hashes));
    }
}
