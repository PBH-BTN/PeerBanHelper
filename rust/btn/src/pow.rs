use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use reqwest::header::HeaderValue;
use reqwest::{RequestBuilder, Url};
use serde::Deserialize;
use sha1::Sha1;
use sha2::{Digest, Sha224, Sha256, Sha384, Sha512, Sha512_224, Sha512_256};

use crate::{BtnClient, Error, json};

const MAX_SOLVE_TIME: Duration = Duration::from_secs(8);
const MAX_CHALLENGE_SIZE: usize = 4096;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Challenge {
    id: String,
    challenge_base64: String,
    difficulty_bits: u32,
    algorithm: String,
    expire_at: Option<u64>,
}

pub(crate) struct Proof {
    pub(crate) id: HeaderValue,
    pub(crate) solution: HeaderValue,
}

impl BtnClient {
    pub(crate) async fn with_proof(
        &self,
        request: RequestBuilder,
        require_pow: bool,
        endpoint: Option<&Url>,
        kind: &'static str,
    ) -> Result<RequestBuilder, Error> {
        self.attach_proof(request, require_pow, endpoint, kind, false)
            .await
    }

    /// Multi-interface heartbeats wait in the caller's aggregate deadline for the proof worker.
    pub(crate) async fn with_queued_proof(
        &self,
        request: RequestBuilder,
        require_pow: bool,
        endpoint: Option<&Url>,
        kind: &'static str,
    ) -> Result<RequestBuilder, Error> {
        self.attach_proof(request, require_pow, endpoint, kind, true)
            .await
    }

    async fn attach_proof(
        &self,
        request: RequestBuilder,
        require_pow: bool,
        endpoint: Option<&Url>,
        kind: &'static str,
        wait_for_worker: bool,
    ) -> Result<RequestBuilder, Error> {
        if let (true, Some(endpoint)) = (require_pow, endpoint) {
            let proof = self.proof(endpoint.clone(), kind, wait_for_worker).await?;
            return Ok(request
                .header("x-btn-powid", proof.id)
                .header("x-btn-powsolution", proof.solution));
        }
        Ok(request)
    }

    async fn proof(
        &self,
        mut endpoint: Url,
        kind: &'static str,
        wait_for_worker: bool,
    ) -> Result<Proof, Error> {
        let slots = self.pow_slots.clone();
        let permit = if wait_for_worker {
            slots
                .acquire_owned()
                .await
                .map_err(|_| Error::Proof("worker unavailable"))?
        } else {
            slots.try_acquire_owned().map_err(|_| Error::Upstream {
                status: 429,
                message: None,
            })?
        };
        endpoint.query_pairs_mut().append_pair("type", kind);
        let response = self.send(self.http.get(endpoint), "proof-of-work").await?;
        let challenge: Challenge = json(response, "proof-of-work").await?;
        tokio::task::spawn_blocking(move || {
            // Limit server-selected proof work even if the caller disconnects.
            let _permit = permit;
            challenge.solve()
        })
        .await
        .map_err(|_| Error::Proof("worker failed"))?
    }
}

impl Challenge {
    fn solve(self) -> Result<Proof, Error> {
        if self.challenge_base64.len() > MAX_CHALLENGE_SIZE * 2 {
            return Err(Error::Proof("oversized challenge"));
        }
        let seed = STANDARD
            .decode(&self.challenge_base64)
            .map_err(|_| Error::Proof("invalid challenge encoding"))?;
        if seed.is_empty() || seed.len() > MAX_CHALLENGE_SIZE {
            return Err(Error::Proof("invalid challenge size"));
        }
        let mut window = MAX_SOLVE_TIME;
        if let Some(expiry) = self.expire_at {
            let now = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default();
            let remaining = Duration::from_millis(expiry)
                .checked_sub(now)
                .ok_or(Error::Proof("challenge expired"))?;
            window = window.min(remaining);
        }
        let deadline = Instant::now() + window;
        let bits = self.difficulty_bits;
        let nonce = match self.algorithm.to_ascii_uppercase().as_str() {
            "SHA-1" | "SHA1" => solve::<Sha1>(&seed, bits, deadline),
            "SHA-224" => solve::<Sha224>(&seed, bits, deadline),
            "SHA-256" => solve::<Sha256>(&seed, bits, deadline),
            "SHA-384" => solve::<Sha384>(&seed, bits, deadline),
            "SHA-512" => solve::<Sha512>(&seed, bits, deadline),
            "SHA-512/224" => solve::<Sha512_224>(&seed, bits, deadline),
            "SHA-512/256" => solve::<Sha512_256>(&seed, bits, deadline),
            _ => Err(Error::Proof("unsupported digest")),
        }?;
        let mut id =
            HeaderValue::from_str(&self.id).map_err(|_| Error::Proof("invalid challenge ID"))?;
        let mut solution = HeaderValue::from_str(&STANDARD.encode(nonce))
            .map_err(|_| Error::Proof("invalid solution encoding"))?;
        id.set_sensitive(true);
        solution.set_sensitive(true);
        Ok(Proof { id, solution })
    }
}

fn solve<D: Digest + Clone>(seed: &[u8], bits: u32, deadline: Instant) -> Result<[u8; 8], Error> {
    if bits > (<D as Digest>::output_size() * 8) as u32 {
        return Err(Error::Proof("difficulty exceeds digest width"));
    }
    let mut initial = D::new();
    initial.update(seed);
    for nonce in 0..=u64::MAX {
        if nonce.is_multiple_of(1024) && Instant::now() >= deadline {
            break;
        }
        let nonce = nonce.to_be_bytes();
        let mut hash = initial.clone();
        hash.update(nonce);
        let digest = hash.finalize();
        if leading_zero_bits(&digest) >= bits {
            return Ok(nonce);
        }
    }
    Err(Error::Proof("challenge time limit exceeded"))
}

fn leading_zero_bits(digest: &[u8]) -> u32 {
    let mut bits = 0;
    for &byte in digest {
        bits += byte.leading_zeros();
        if byte != 0 {
            break;
        }
    }
    bits
}
