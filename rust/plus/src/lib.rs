//! Verify the PBH Plus license formats and expose features from validated licenses.

mod hardware;
mod payload;
pub mod remote;
mod revocation;
pub mod trial;

use std::collections::HashSet;
use std::fs::File;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;

use base64::engine::DecodePaddingMode;
use base64::engine::general_purpose::{GeneralPurpose, GeneralPurposeConfig, STANDARD};
use base64::{Engine, alphabet};
use rsa::pkcs8::DecodePublicKey;
use rsa::traits::PublicKeyParts;
use rsa::{BigUint, RsaPublicKey};
use serde::Serialize;
use sha2::{Digest, Sha256};
use thiserror::Error;

use payload::Payload;
use revocation::{CachedRevocation, cached_revocation, is_bundled_revoked};

const OFFICIAL_PUBLIC_KEY: &str = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCHxgRTk+Zx/pkN8rpK+Lbr1/f1meapIRDJIgBiSfFy4xdbmDF8wE9PJhdM+3peThz9dJQlt6dkeduIVp65rGS9oZdj7gO5YKtUCDir4NgGQGe1p2C41Xv6RiOXObLmF+ubAJILsimwtDyJT8IysEh9hgaZWnvRXT8JX9wB0Ti2rwIDAQAB";
const RSA_BLOCK_BYTES: usize = 128;
const MAX_LICENSE_BYTES: usize = 32 * 1024;
const MAX_LOCAL_WINDOW_MS: i64 = 15 * 24 * 60 * 60 * 1000 + 60_000;
const LICENSE_ENCODING: GeneralPurpose = GeneralPurpose::new(
    &alphabet::STANDARD,
    GeneralPurposeConfig::new().with_decode_padding_mode(DecodePaddingMode::Indifferent),
);

pub use hardware::hardware_uuid;

#[derive(Clone, Copy, Debug, Eq, Error, PartialEq)]
pub enum Error {
    #[error("invalid embedded PBH Plus verification key")]
    InvalidOfficialKey,
    #[error("invalid PBH Plus license encoding")]
    InvalidEncoding,
    #[error("PBH Plus license cryptographic verification failed")]
    InvalidSignature,
    #[error("invalid PBH Plus license data")]
    InvalidPayload,
    #[error("unsupported PBH Plus license version")]
    UnsupportedVersion,
    #[error("PBH Plus license exceeds the local basic entitlement")]
    InvalidLocalLicense,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum LicenseStatus {
    Valid,
    NotStarted,
    Expired,
    Revoked,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlusStatus<'a> {
    pub enabled_features: Vec<&'a str>,
    pub licenses: Vec<LicenseView<'a>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LicenseView<'a> {
    pub license_id: &'a str,
    pub version: u8,
    pub status: LicenseStatus,
    data: &'a Payload,
}

pub struct VerifiedLicense {
    id: String,
    payload: Payload,
    local_trial: bool,
    bundled_revoked: bool,
    online: Option<CachedRevocation>,
}

impl VerifiedLicense {
    pub fn id(&self) -> &str {
        &self.id
    }

    pub fn status(&self, now_ms: i64) -> LicenseStatus {
        if self.bundled_revoked || self.online.is_some_and(|check| check.revoked) {
            LicenseStatus::Revoked
        } else if self.payload.common().expire_at < now_ms {
            LicenseStatus::Expired
        } else if self.payload.start_at() > now_ms {
            LicenseStatus::NotStarted
        } else {
            LicenseStatus::Valid
        }
    }
}

/// An immutable view of configured licenses. Install and removal paths can
/// rebuild the service only after the replacement configuration is saved.
pub struct LicenseService {
    official: &'static RsaPublicKey,
    local: Option<RsaPublicKey>,
    cache: PathBuf,
    licenses: Vec<VerifiedLicense>,
    rejected_count: usize,
}

impl LicenseService {
    /// A hardware UUID must come from the selected device, not from a license
    /// input. The Java file is named from the first ten SHA-256 hex characters.
    pub fn load(
        keys: &[String],
        data_dir: &Path,
        hardware_uuid: Option<&str>,
    ) -> Result<Self, Error> {
        let mut service = Self {
            official: official_key()?,
            local: hardware_uuid.and_then(|uuid| load_local_key(data_dir, uuid)),
            cache: data_dir.join("cache").join("license-revoke"),
            licenses: Vec::new(),
            rejected_count: 0,
        };
        let mut seen = HashSet::new();
        for key in keys {
            match service.verify(key) {
                Ok(verified) if seen.insert(verified.id.clone()) => service.licenses.push(verified),
                Ok(_) => {}
                Err(_) => service.rejected_count += 1,
            }
        }
        Ok(service)
    }

    pub fn rejected_count(&self) -> usize {
        self.rejected_count
    }

    pub fn verify(&self, key: &str) -> Result<VerifiedLicense, Error> {
        let key = key.trim();
        if key.is_empty() || key.len() > MAX_LICENSE_BYTES * 2 {
            return Err(Error::InvalidEncoding);
        }
        let signed = LICENSE_ENCODING
            .decode(key)
            .map_err(|_| Error::InvalidEncoding)?;
        let local = self
            .local
            .as_ref()
            .and_then(|key| recover(&signed, key).ok());
        let local_trial = local.is_some();
        let payload = match local {
            Some(json) => {
                let payload = Payload::parse(&json)?;
                validate_local(&payload)?;
                payload
            }
            None => Payload::parse(&recover(&signed, self.official)?)?,
        };
        let bundled_revoked = is_bundled_revoked(&payload);
        let online = self
            .local
            .as_ref()
            .filter(|_| !local_trial)
            .and_then(|key| cached_revocation(&self.cache, key, &payload));
        Ok(VerifiedLicense {
            id: license_id(key),
            payload,
            local_trial,
            bundled_revoked,
            online,
        })
    }

    pub fn is_feature_enabled(&self, feature: &str, now_ms: i64) -> bool {
        self.licenses.iter().any(|license| {
            license.status(now_ms) == LicenseStatus::Valid
                && license.payload.features().any(|enabled| enabled == feature)
        })
    }

    pub fn status(&self, now_ms: i64) -> PlusStatus<'_> {
        let mut seen = HashSet::new();
        let mut enabled_features = Vec::new();
        let licenses = self
            .licenses
            .iter()
            .map(|license| {
                let status = license.status(now_ms);
                if status == LicenseStatus::Valid {
                    enabled_features.extend(
                        license
                            .payload
                            .features()
                            .filter(|feature| seen.insert(*feature)),
                    );
                }
                LicenseView {
                    license_id: &license.id,
                    version: license.payload.version(),
                    status,
                    data: &license.payload,
                }
            })
            .collect();
        PlusStatus {
            enabled_features,
            licenses,
        }
    }
}

/// Configuration entries use the digest of their trimmed original license text.
pub fn license_id(entry: &str) -> String {
    sha256(entry.trim())
}

fn official_key() -> Result<&'static RsaPublicKey, Error> {
    static KEY: OnceLock<Result<RsaPublicKey, Error>> = OnceLock::new();
    KEY.get_or_init(|| {
        let encoded = STANDARD
            .decode(OFFICIAL_PUBLIC_KEY)
            .map_err(|_| Error::InvalidOfficialKey)?;
        parse_public_key(&encoded).ok_or(Error::InvalidOfficialKey)
    })
    .as_ref()
    .map_err(|error| *error)
}

fn parse_public_key(encoded: &[u8]) -> Option<RsaPublicKey> {
    let key = RsaPublicKey::from_public_key_der(encoded).ok()?;
    (key.n().bits() == RSA_BLOCK_BYTES * 8 && *key.e() == BigUint::from(65537_u32)).then_some(key)
}

fn load_local_key(data_dir: &Path, hardware_uuid: &str) -> Option<RsaPublicKey> {
    parse_public_key(&read_limited(
        &local_key_path(data_dir, hardware_uuid, "pub"),
        4096,
    )?)
}

fn local_key_path(data_dir: &Path, hardware_uuid: &str, extension: &str) -> PathBuf {
    let digest = sha256(hardware_uuid);
    data_dir.join(format!(
        "local_license2_keypair_{}.{extension}",
        &digest[..10]
    ))
}

fn sha256(input: &str) -> String {
    format!("{:x}", Sha256::digest(input.as_bytes()))
}

fn read_limited(path: &Path, max: u64) -> Option<Vec<u8>> {
    let mut contents = Vec::new();
    File::open(path)
        .ok()?
        .take(max + 1)
        .read_to_end(&mut contents)
        .ok()?;
    (contents.len() as u64 <= max).then_some(contents)
}

fn validate_local(payload: &Payload) -> Result<(), Error> {
    let valid_window = payload
        .common()
        .expire_at
        .checked_sub(payload.start_at())
        .is_some_and(|window| (0..=MAX_LOCAL_WINDOW_MS).contains(&window));
    if payload.version() != 2
        || payload.license_type() != Some("local")
        || !payload.features().eq(["basic"])
        || !valid_window
    {
        return Err(Error::InvalidLocalLicense);
    }
    Ok(())
}

/// The legacy Java format recovers each Type-1 PKCS#1 block using the
/// verification key. Perform only public-key operations and require every
/// byte of padding and the payload delimiter to match the format.
fn recover(signed: &[u8], key: &RsaPublicKey) -> Result<Vec<u8>, Error> {
    if signed.is_empty()
        || signed.len() > MAX_LICENSE_BYTES
        || !signed.len().is_multiple_of(RSA_BLOCK_BYTES)
    {
        return Err(Error::InvalidEncoding);
    }
    let mut result = Vec::new();
    for chunk in signed.chunks_exact(RSA_BLOCK_BYTES) {
        let signature = BigUint::from_bytes_be(chunk);
        if &signature >= key.n() {
            return Err(Error::InvalidSignature);
        }
        let decoded = signature.modpow(key.e(), key.n()).to_bytes_be();
        // BigUint omits the leading zero of 00 01 FF...FF 00 <payload>.
        if decoded.len() != RSA_BLOCK_BYTES - 1 || decoded[0] != 1 {
            return Err(Error::InvalidSignature);
        }
        let padding = &decoded[1..];
        let separator = padding
            .iter()
            .position(|byte| *byte != 0xff)
            .filter(|index| *index >= 8 && padding[*index] == 0)
            .ok_or(Error::InvalidSignature)?;
        let payload = &padding[separator + 1..];
        if payload.is_empty() {
            return Err(Error::InvalidSignature);
        }
        result.extend(payload);
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use std::fs;

    use rand::{SeedableRng, rngs::StdRng};
    use rsa::RsaPrivateKey;
    use rsa::pkcs8::EncodePublicKey;
    use rsa::traits::PrivateKeyParts;
    use serde_json::{Value, json};

    use super::*;

    const HARDWARE: &str = "test-only-hardware";
    const NOW: i64 = 1_700_000_000_000;

    fn fixture_key() -> &'static RsaPrivateKey {
        static KEY: OnceLock<RsaPrivateKey> = OnceLock::new();
        KEY.get_or_init(|| {
            RsaPrivateKey::new(&mut StdRng::seed_from_u64(1), RSA_BLOCK_BYTES * 8).unwrap()
        })
    }

    pub(crate) fn fixture_public_key() -> &'static RsaPublicKey {
        static KEY: OnceLock<RsaPublicKey> = OnceLock::new();
        KEY.get_or_init(|| fixture_key().to_public_key())
    }

    fn fixture_signature(bytes: &[u8]) -> Vec<u8> {
        bytes
            .chunks(RSA_BLOCK_BYTES - 11)
            .flat_map(|payload| {
                let mut block = vec![0xff; RSA_BLOCK_BYTES];
                block[0] = 0;
                block[1] = 1;
                let split = block.len() - payload.len();
                block[split - 1] = 0;
                block[split..].copy_from_slice(payload);
                fixture_private_transform(block)
            })
            .collect()
    }

    fn fixture_private_transform(block: Vec<u8>) -> Vec<u8> {
        let key = fixture_key();
        let bytes = BigUint::from_bytes_be(&block)
            .modpow(key.d(), key.n())
            .to_bytes_be();
        let mut padded = vec![0; RSA_BLOCK_BYTES - bytes.len()];
        padded.extend(bytes);
        padded
    }

    pub(crate) fn fixture_license(payload: Value) -> String {
        STANDARD.encode(fixture_signature(&serde_json::to_vec(&payload).unwrap()))
    }

    fn install_local_key(path: &Path) {
        let id = sha256(HARDWARE);
        let public = fixture_public_key().to_public_key_der().unwrap();
        fs::write(
            path.join(format!("local_license2_keypair_{}.pub", &id[..10])),
            public.as_ref(),
        )
        .unwrap();
    }

    pub(crate) fn issue_v2(kind: &str, features: Value, starts: i64, expires: i64) -> Value {
        json!({
            "verifyMagic":"PeerBanHelper", "licenseVersion":2, "type":kind,
            "licenseTo":"测试 Fixture", "source":"unit test", "orderId":"order",
            "description":"multi-block fixture", "paymentOrderId":"payment",
            "email":"fixture@example.test", "createAt":NOW, "startAt":starts, "expireAt":expires,
            "features":features,
        })
    }

    #[test]
    fn inactive_without_cryptographic_license_and_pinned_official_key_rejects_untrusted_fixture() {
        let dir = tempfile::tempdir().unwrap();
        let synthetic = fixture_license(issue_v2("local", json!(["basic"]), NOW, NOW + 1));
        let clear_json = STANDARD
            .encode(br#"{"verifyMagic":"PeerBanHelper","licenseVersion":2,"features":["basic"]}"#);
        let service = LicenseService::load(
            &[synthetic, clear_json, "arbitrary unlock".into()],
            dir.path(),
            None,
        )
        .unwrap();
        assert_eq!(service.rejected_count(), 3);
        assert!(!service.is_feature_enabled("basic", NOW));
        assert_eq!(
            serde_json::to_value(service.status(NOW)).unwrap(),
            json!({"enabledFeatures":[],"licenses":[]})
        );
    }

    #[test]
    fn verified_legacy_and_v2_formats_keep_status_and_entitlement_separate() {
        let v1 = fixture_license(json!({
            "verifyMagic":"PeerBanHelper", "source":"synthetic issuer", "licenseTo":"Legacy Fixture",
            "createAt":NOW-1000, "expireAt":NOW+50,
        }));
        let future = fixture_license(issue_v2(
            "official",
            json!(["basic", "charts", "basic"]),
            NOW + 51,
            NOW + 100,
        ));
        let mut service = LicenseService {
            official: fixture_public_key(),
            local: None,
            cache: PathBuf::new(),
            licenses: Vec::new(),
            rejected_count: 0,
        };
        service.licenses = [v1, future]
            .iter()
            .map(|value| service.verify(value).unwrap())
            .collect();
        let status = serde_json::to_value(service.status(NOW)).unwrap();
        assert_eq!(status["enabledFeatures"], json!(["basic", "paid"]));
        assert_eq!(status["licenses"][0]["version"], 1);
        assert_eq!(status["licenses"][0]["data"]["type"], "afdian");
        assert!(status["licenses"][0]["data"].get("keyText").is_none());
        assert_eq!(status["licenses"][1]["status"], "NOT_STARTED");
        assert_eq!(status["licenses"][1]["data"]["paymentOrderId"], "payment");
        assert!(service.is_feature_enabled("paid", NOW + 50));
        assert!(service.is_feature_enabled("charts", NOW + 51));
        assert!(!service.is_feature_enabled("paid", NOW + 51));
        assert_eq!(
            service.status(NOW + 51).enabled_features,
            ["basic", "charts"]
        );
        assert!(!service.is_feature_enabled("basic", NOW + 101));
    }

    #[test]
    fn existing_hardware_key_only_activates_valid_bounded_basic_licenses() {
        let dir = tempfile::tempdir().unwrap();
        install_local_key(dir.path());
        let key = fixture_license(issue_v2("local", json!(["basic"]), NOW, NOW + 100));
        let paid = fixture_license(issue_v2("local", json!(["basic", "paid"]), NOW, NOW + 100));
        let prolonged = fixture_license(issue_v2(
            "local",
            json!(["basic"]),
            NOW,
            NOW + MAX_LOCAL_WINDOW_MS + 1,
        ));
        let bad_type = fixture_license(issue_v2("official", json!(["basic"]), NOW, NOW + 100));
        let service = LicenseService::load(
            &[format!(" {key} "), key.clone(), paid, prolonged, bad_type],
            dir.path(),
            Some(HARDWARE),
        )
        .unwrap();
        assert_eq!(service.rejected_count(), 3);
        assert_eq!(service.status(NOW).licenses.len(), 1);
        assert_eq!(service.status(NOW).licenses[0].license_id, license_id(&key));
        assert!(!service.is_feature_enabled("basic", NOW - 1));
        assert!(service.is_feature_enabled("basic", NOW + 100));
        assert!(!service.is_feature_enabled("basic", NOW + 101));
        assert!(
            !LicenseService::load(&[key], dir.path(), Some("a different machine"))
                .unwrap()
                .is_feature_enabled("basic", NOW)
        );
    }

    #[test]
    fn local_trial_is_excluded_from_online_revocations_and_cache() {
        let dir = tempfile::tempdir().unwrap();
        install_local_key(dir.path());
        let license = issue_v2("local", json!(["basic"]), NOW, NOW + 100);
        let key = fixture_license(license.clone());
        let cache_material = format!(
            "{}{}{}{}{}",
            license["licenseTo"].as_str().unwrap(),
            license["description"].as_str().unwrap(),
            license["orderId"].as_str().unwrap(),
            license["paymentOrderId"].as_str().unwrap(),
            license["email"].as_str().unwrap(),
        );
        let cache = dir.path().join("cache").join("license-revoke");
        fs::create_dir_all(&cache).unwrap();
        let cache = cache.join(format!("{}.json", sha256(&cache_material)));
        let check = json!({"revoked":true,"timestamp":NOW-9_000_000_000_i64});
        let clear = serde_json::to_vec(&check).unwrap();
        fs::write(&cache, &clear).unwrap();
        let service =
            LicenseService::load(std::slice::from_ref(&key), dir.path(), Some(HARDWARE)).unwrap();
        assert!(service.is_feature_enabled("basic", NOW));
        fs::write(&cache, fixture_signature(&clear)).unwrap();
        let service = LicenseService::load(&[key], dir.path(), Some(HARDWARE)).unwrap();
        assert!(service.is_feature_enabled("basic", NOW));
        assert!(service.revocation_probes(NOW).is_empty());
    }

    #[test]
    fn malformed_ciphertext_and_invalid_padding_fail_closed() {
        let key = fixture_public_key();
        assert_eq!(recover(&[1; 128], key), Err(Error::InvalidSignature));
        assert_eq!(recover(&[1; 127], key), Err(Error::InvalidEncoding));
        let mut block = vec![1; RSA_BLOCK_BYTES];
        block[0] = 0;
        block[2..9].fill(0xff);
        block[9] = 0;
        assert_eq!(
            recover(&fixture_private_transform(block), key),
            Err(Error::InvalidSignature)
        );
    }
}
