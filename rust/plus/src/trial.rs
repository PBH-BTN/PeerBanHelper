//! The authenticated local trial protocol. A valid single-use proof is required
//! before a fixed, basic-only trial can be signed by the device's own key.

use std::collections::HashMap;
use std::fs::File;
use std::io::{self, Read, Write};
use std::path::Path;
use std::time::{Duration, Instant};

use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use rand::{RngCore, rngs::OsRng};
use rsa::pkcs8::{DecodePrivateKey, EncodePrivateKey, EncodePublicKey};
use rsa::traits::PublicKeyParts;
use rsa::{BigUint, Pkcs1v15Sign, RsaPrivateKey, RsaPublicKey};
use serde::Serialize;
use sha1::{Digest, Sha1};
use tempfile::NamedTempFile;
use thiserror::Error;
use uuid::Uuid;
use zeroize::Zeroizing;

use crate::payload::Payload;
use crate::{
    LICENSE_ENCODING, LicenseService, MAX_LICENSE_BYTES, RSA_BLOCK_BYTES, local_key_path,
    parse_public_key,
};

const CHALLENGE_TTL: Duration = Duration::from_secs(60 * 60);
const MAX_CHALLENGES: usize = 4096;
const MAX_NONCE_BYTES: usize = 128;
const MAX_KEY_BYTES: u64 = 4096;
const DIFFICULTY: u8 = 5;
pub const TRIAL_DURATION_MS: i64 = 15 * 24 * 60 * 60 * 1000;

#[derive(Debug, Error, Eq, PartialEq)]
pub enum ChallengeError {
    #[error("trial challenge is missing or expired")]
    Missing,
    #[error("trial challenge proof is invalid")]
    InvalidProof,
    #[error("unable to generate a random trial challenge")]
    Random,
}

#[derive(Debug, Error)]
pub enum IssueError {
    #[error("a valid basic license already exists")]
    AlreadyActive,
    #[error("the device has no hardware or installation identifier")]
    MissingHardware,
    #[error("the existing local key pair is invalid or incomplete")]
    InvalidKey,
    #[error("unable to read or write the local trial keys")]
    KeyIo(#[source] io::Error),
    #[error("unable to generate or use the local signing key")]
    Signing,
    #[error("unable to encode a local trial")]
    InvalidTrial,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Challenge {
    pub challenge_id: String,
    pub challenge_base64: String,
    pub difficulty_bits: u8,
    pub algorithm: &'static str,
}

#[derive(Default)]
pub struct Challenges {
    pending: HashMap<Uuid, Entry>,
}

struct Entry {
    seed: [u8; 32],
    issued: Instant,
}

/// Constructed only by a successfully verified challenge and consumed when signing.
pub struct TrialPermit(());

impl Challenges {
    pub fn create(&mut self, now: Instant) -> Result<Challenge, ChallengeError> {
        self.pending.retain(|_, entry| entry.live(now));
        if self.pending.len() >= MAX_CHALLENGES
            && let Some(oldest) = self
                .pending
                .iter()
                .min_by_key(|(_, entry)| entry.issued)
                .map(|(id, _)| *id)
        {
            self.pending.remove(&oldest);
        }
        let mut seed = [0; 32];
        OsRng
            .try_fill_bytes(&mut seed)
            .map_err(|_| ChallengeError::Random)?;
        let id = loop {
            let mut random = [0; 16];
            OsRng
                .try_fill_bytes(&mut random)
                .map_err(|_| ChallengeError::Random)?;
            let id = uuid::Builder::from_random_bytes(random).into_uuid();
            if let std::collections::hash_map::Entry::Vacant(slot) = self.pending.entry(id) {
                slot.insert(Entry { seed, issued: now });
                break id;
            }
        };
        Ok(Challenge {
            challenge_id: id.to_string(),
            challenge_base64: STANDARD.encode(seed),
            difficulty_bits: DIFFICULTY,
            algorithm: "SHA-1",
        })
    }

    pub fn verify(
        &mut self,
        id: &str,
        nonce: &str,
        now: Instant,
    ) -> Result<TrialPermit, ChallengeError> {
        let id = Uuid::parse_str(id).map_err(|_| ChallengeError::Missing)?;
        let Some(entry) = self.pending.get(&id) else {
            return Err(ChallengeError::Missing);
        };
        if !entry.live(now) {
            self.pending.remove(&id);
            return Err(ChallengeError::Missing);
        }
        if nonce.len() > MAX_NONCE_BYTES.div_ceil(3) * 4 {
            return Err(ChallengeError::InvalidProof);
        }
        let nonce = LICENSE_ENCODING
            .decode(nonce)
            .map_err(|_| ChallengeError::InvalidProof)?;
        if nonce.len() > MAX_NONCE_BYTES || !valid_proof(&entry.seed, &nonce) {
            return Err(ChallengeError::InvalidProof);
        }
        self.pending.remove(&id);
        Ok(TrialPermit(()))
    }
}

impl Entry {
    fn live(&self, now: Instant) -> bool {
        now.saturating_duration_since(self.issued) < CHALLENGE_TTL
    }
}

fn valid_proof(seed: &[u8], nonce: &[u8]) -> bool {
    let mut sha = Sha1::new();
    sha.update(seed);
    sha.update(nonce);
    sha.finalize()[0] >> (8 - DIFFICULTY) == 0
}

pub struct TrialIdentity<'a> {
    pub owner: &'a str,
    pub source: &'a str,
    pub description: &'a str,
}

impl LicenseService {
    /// The caller must hold the configuration update lock, so eligibility and
    /// persistence see the same license set even when requests overlap.
    pub fn issue_local_trial(
        &self,
        _permit: TrialPermit,
        data_dir: &Path,
        hardware_id: Option<&str>,
        identity: TrialIdentity<'_>,
        now_ms: i64,
    ) -> Result<String, IssueError> {
        if self.is_feature_enabled("basic", now_ms) {
            return Err(IssueError::AlreadyActive);
        }
        let hardware_id = hardware_id
            .filter(|id| !id.is_empty())
            .ok_or(IssueError::MissingHardware)?;
        let expire_at = now_ms
            .checked_add(TRIAL_DURATION_MS)
            .ok_or(IssueError::InvalidTrial)?;
        let payload = Payload::local_trial(
            identity.owner,
            identity.source,
            identity.description,
            now_ms,
            expire_at,
        );
        let encoded = serde_json::to_vec(&payload).map_err(|_| IssueError::InvalidTrial)?;
        if encoded.len() > MAX_LICENSE_BYTES / RSA_BLOCK_BYTES * (RSA_BLOCK_BYTES - 11) {
            return Err(IssueError::InvalidTrial);
        }
        let key = local_signer(data_dir, hardware_id)?;
        Ok(STANDARD.encode(sign_local_payload(&key, &encoded)?))
    }
}

pub(crate) fn sign_local_payload(
    key: &RsaPrivateKey,
    encoded: &[u8],
) -> Result<Vec<u8>, IssueError> {
    let mut signed = Vec::new();
    for chunk in encoded.chunks(RSA_BLOCK_BYTES - 11) {
        // Match Java Type-1 blocks with blinded RSA. Callers supply only an
        // internally constructed trial or verified revocation result.
        let block = key
            .sign_with_rng(&mut OsRng, Pkcs1v15Sign::new_unprefixed(), chunk)
            .map_err(|_| IssueError::Signing)?;
        signed.extend(block);
    }
    Ok(signed)
}

pub(crate) fn local_signer(
    data_dir: &Path,
    hardware_id: &str,
) -> Result<RsaPrivateKey, IssueError> {
    let private_path = local_key_path(data_dir, hardware_id, "key");
    let public_path = local_key_path(data_dir, hardware_id, "pub");
    let public = read_existing(&public_path)?;
    let private = match read_existing(&private_path)? {
        Some(encoded) => decode_private(&encoded)?,
        None if public.is_some() => return Err(IssueError::InvalidKey),
        None => {
            let key = RsaPrivateKey::new(&mut OsRng, RSA_BLOCK_BYTES * 8)
                .map_err(|_| IssueError::Signing)?;
            let encoded = key.to_pkcs8_der().map_err(|_| IssueError::Signing)?;
            if install_new(&private_path, encoded.as_bytes())? {
                key
            } else {
                let existing = read_existing(&private_path)?.ok_or(IssueError::InvalidKey)?;
                decode_private(&existing)?
            }
        }
    };
    let derived = private.to_public_key();
    match public {
        Some(public) => verify_public(&public, &derived)?,
        None => {
            let encoded = derived
                .to_public_key_der()
                .map_err(|_| IssueError::Signing)?;
            if !install_new(&public_path, encoded.as_bytes())? {
                let existing = read_existing(&public_path)?.ok_or(IssueError::InvalidKey)?;
                verify_public(&existing, &derived)?;
            }
        }
    }
    Ok(private)
}

fn decode_private(encoded: &[u8]) -> Result<RsaPrivateKey, IssueError> {
    let private = RsaPrivateKey::from_pkcs8_der(encoded).map_err(|_| IssueError::InvalidKey)?;
    if private.n().bits() != RSA_BLOCK_BYTES * 8 || *private.e() != BigUint::from(65537_u32) {
        return Err(IssueError::InvalidKey);
    }
    private.validate().map_err(|_| IssueError::InvalidKey)?;
    Ok(private)
}

fn verify_public(encoded: &[u8], derived: &RsaPublicKey) -> Result<(), IssueError> {
    let key = parse_public_key(encoded).ok_or(IssueError::InvalidKey)?;
    if &key != derived {
        return Err(IssueError::InvalidKey);
    }
    Ok(())
}

fn read_existing(path: &Path) -> Result<Option<Zeroizing<Vec<u8>>>, IssueError> {
    let file = match File::open(path) {
        Ok(file) => file,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(IssueError::KeyIo(error)),
    };
    let mut data = Zeroizing::new(Vec::new());
    file.take(MAX_KEY_BYTES + 1)
        .read_to_end(&mut data)
        .map_err(IssueError::KeyIo)?;
    if data.len() as u64 > MAX_KEY_BYTES {
        return Err(IssueError::InvalidKey);
    }
    Ok(Some(data))
}

fn install_new(path: &Path, encoded: &[u8]) -> Result<bool, IssueError> {
    let directory = path.parent().ok_or(IssueError::InvalidKey)?;
    let mut staging = NamedTempFile::new_in(directory).map_err(IssueError::KeyIo)?;
    staging.write_all(encoded).map_err(IssueError::KeyIo)?;
    staging.as_file().sync_all().map_err(IssueError::KeyIo)?;
    match staging.into_temp_path().persist_noclobber(path) {
        Ok(()) => Ok(true),
        Err(error) if error.error.kind() == io::ErrorKind::AlreadyExists => Ok(false),
        Err(error) => Err(IssueError::KeyIo(error.error)),
    }
}

#[cfg(test)]
mod tests {
    use std::fs;

    use crate::LicenseStatus;

    use super::*;

    const HARDWARE: &str = "test-only-trial-device";
    const NOW_MS: i64 = 1_700_000_000_000;
    const IDENTITY: TrialIdentity<'_> = TrialIdentity {
        owner: "fixture user",
        source: "fixture source",
        description: "fixture description",
    };

    fn nonce(challenge: &Challenge) -> String {
        let seed = STANDARD.decode(&challenge.challenge_base64).unwrap();
        (0_u32..)
            .map(u32::to_be_bytes)
            .find(|nonce| valid_proof(&seed, nonce))
            .map(|nonce| STANDARD.encode(nonce))
            .unwrap()
    }

    fn permit() -> TrialPermit {
        let now = Instant::now();
        let mut challenges = Challenges::default();
        let challenge = challenges.create(now).unwrap();
        challenges
            .verify(&challenge.challenge_id, &nonce(&challenge), now)
            .unwrap()
    }

    #[test]
    fn challenges_expire_once_and_incorrect_proofs_can_be_retried() {
        let now = Instant::now();
        let mut challenges = Challenges::default();
        let challenge = challenges.create(now).unwrap();
        let proof = nonce(&challenge);
        assert_eq!(challenge.algorithm, "SHA-1");
        assert_eq!(challenge.difficulty_bits, 5);
        assert_eq!(
            STANDARD.decode(&challenge.challenge_base64).unwrap().len(),
            32
        );
        assert_eq!(
            Uuid::parse_str(&challenge.challenge_id)
                .unwrap()
                .get_version_num(),
            4
        );
        assert!(matches!(
            challenges.verify(&challenge.challenge_id, "invalid-nonce", now),
            Err(ChallengeError::InvalidProof)
        ));
        assert!(
            challenges
                .verify(
                    &challenge.challenge_id,
                    &proof,
                    now + CHALLENGE_TTL - Duration::from_millis(1)
                )
                .is_ok()
        );
        assert!(matches!(
            challenges.verify(&challenge.challenge_id, &proof, now),
            Err(ChallengeError::Missing)
        ));
        let expired = challenges.create(now).unwrap();
        assert!(matches!(
            challenges.verify(&expired.challenge_id, &nonce(&expired), now + CHALLENGE_TTL),
            Err(ChallengeError::Missing)
        ));
    }

    #[test]
    fn signs_only_basic_for_15_days_and_reuses_java_key_files_after_expiration() {
        let temporary = tempfile::tempdir().unwrap();
        let directory = temporary.path();
        let current = LicenseService::load(&[], directory, Some(HARDWARE)).unwrap();
        assert!(matches!(
            current.issue_local_trial(permit(), directory, None, IDENTITY, NOW_MS),
            Err(IssueError::MissingHardware)
        ));
        let issued = current
            .issue_local_trial(permit(), directory, Some(HARDWARE), IDENTITY, NOW_MS)
            .unwrap();
        let inputs = [issued.clone()];
        let active = LicenseService::load(&inputs, directory, Some(HARDWARE)).unwrap();
        let expiry = NOW_MS + TRIAL_DURATION_MS;
        assert_eq!(
            active.verify(&issued).unwrap().status(NOW_MS - 1),
            LicenseStatus::NotStarted
        );
        assert_eq!(active.status(NOW_MS).enabled_features, ["basic"]);
        assert!(active.is_feature_enabled("basic", expiry));
        assert!(!active.is_feature_enabled("paid", expiry));
        assert!(!active.is_feature_enabled("basic", expiry + 1));
        assert!(matches!(
            active.issue_local_trial(permit(), directory, Some(HARDWARE), IDENTITY, expiry),
            Err(IssueError::AlreadyActive)
        ));
        let private_path = local_key_path(directory, HARDWARE, "key");
        let public_path = local_key_path(directory, HARDWARE, "pub");
        let private = fs::read(&private_path).unwrap();
        let public = fs::read(&public_path).unwrap();
        let renewed = active
            .issue_local_trial(permit(), directory, Some(HARDWARE), IDENTITY, expiry + 1)
            .unwrap();
        assert_ne!(renewed, issued);
        assert_eq!(fs::read(&private_path).unwrap(), private);
        assert_eq!(fs::read(&public_path).unwrap(), public);
        assert_eq!(
            active.verify(&renewed).unwrap().status(expiry + 1),
            LicenseStatus::Valid
        );
        assert!(
            LicenseService::load(&[renewed], directory, Some("wrong device"))
                .unwrap()
                .status(expiry + 1)
                .licenses
                .is_empty()
        );

        fs::remove_file(&public_path).unwrap();
        let _ = active
            .issue_local_trial(permit(), directory, Some(HARDWARE), IDENTITY, expiry + 1)
            .unwrap();
        assert_eq!(fs::read(&public_path).unwrap(), public);
        fs::remove_file(&private_path).unwrap();
        assert!(matches!(
            active.issue_local_trial(permit(), directory, Some(HARDWARE), IDENTITY, expiry + 1),
            Err(IssueError::InvalidKey)
        ));
        assert_eq!(fs::read(&public_path).unwrap(), public);
        assert!(!private_path.exists());
    }
}
