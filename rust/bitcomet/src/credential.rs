use aes::Aes256;
use base64::{Engine, engine::general_purpose::STANDARD};
use cbc::cipher::{BlockEncryptMut, KeyIvInit, block_padding::Pkcs7};
use hmac::{Hmac, Mac};
use pbkdf2::pbkdf2_hmac_array;
use serde::Serialize;
use sha1::Sha1;
use sha2::Sha256;

use crate::Error;

// UUID.nameUUIDFromBytes("PeerBanHelper") used by the Java adapter. Keeping the
// same identity lets BitComet recognize devices registered before migration.
pub(crate) const CLIENT_ID: &str = "78ec56a2-8d0c-31ca-a843-f49fc1f20153";

#[derive(Serialize)]
struct Credentials<'a> {
    username: &'a str,
    password: &'a str,
}

pub(crate) fn encode(username: &str, password: &str) -> Result<String, Error> {
    let plaintext =
        serde_json::to_vec(&Credentials { username, password }).map_err(|_| Error::Credential)?;
    let mut material = [0_u8; 32];
    getrandom::fill(&mut material).map_err(|_| Error::Credential)?;
    Ok(encrypt(&plaintext, &material))
}

// RNCryptor v3 password mode. One independently generated salt for each key,
// followed by a fresh IV, the ciphertext and an authentication tag.
fn encrypt(plaintext: &[u8], material: &[u8; 32]) -> String {
    let (encryption_salt, tail) = material.split_at(8);
    let (hmac_salt, iv) = tail.split_at(8);
    let encryption_key =
        pbkdf2_hmac_array::<Sha1, 32>(CLIENT_ID.as_bytes(), encryption_salt, 10_000);
    let mac_key = pbkdf2_hmac_array::<Sha1, 32>(CLIENT_ID.as_bytes(), hmac_salt, 10_000);
    let ciphertext = cbc::Encryptor::<Aes256>::new_from_slices(&encryption_key, iv)
        .expect("RNCryptor uses a 32-byte AES key and a 16-byte IV")
        .encrypt_padded_vec_mut::<Pkcs7>(plaintext);
    let mut envelope = Vec::with_capacity(2 + material.len() + ciphertext.len() + 32);
    envelope.extend_from_slice(&[3, 1]);
    envelope.extend_from_slice(material);
    envelope.extend_from_slice(&ciphertext);
    let mut mac = Hmac::<Sha256>::new_from_slice(&mac_key).expect("HMAC accepts the derived key");
    mac.update(&envelope);
    envelope.extend_from_slice(&mac.finalize().into_bytes());
    STANDARD.encode(envelope)
}

#[cfg(test)]
mod tests {
    use super::encrypt;

    #[test]
    fn matches_independently_calculated_rncryptor_envelope() {
        let material = std::array::from_fn(|index| index as u8);
        let plaintext = r#"{"username":"tester","password":"密码"}"#;
        assert_eq!(
            encrypt(plaintext.as_bytes(), &material),
            "AwEAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eH6uyrQPahhQmXzXIWGTfke91ypMgCa8E+Ps9CBFXDl4963X6fy6R4/yibHeS6PEk1m5fOevOAe//5KtzsLdMzX1P7TIWqZOuWB5Rvr7rjRA6",
        );
    }
}
