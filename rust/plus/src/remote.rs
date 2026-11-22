//! PBH Plus license download and revocation protocol.

use std::net::IpAddr;
use std::path::Path;
use std::time::Duration;

use reqwest::{Client, Response, StatusCode, Url, redirect};
use serde::Deserialize;
use serde::de::DeserializeOwned;
use thiserror::Error;

use crate::revocation::{CachedRevocation, cache_id, save_cache};
use crate::trial::IssueError;
use crate::{LicenseService, VerifiedLicense, sha256};

const BASE: &str = "https://api.pbh-btn.com/peerbanhelper/v1/licenses/";
const MAX_LOOKUP_BYTES: usize = 2048;
const MAX_BODY_BYTES: usize = 128 * 1024;

#[derive(Clone)]
pub struct RemoteClient {
    client: Client,
    base: Url,
}

#[derive(Clone, Copy, Debug, Eq, Error, PartialEq)]
pub enum RemoteError {
    #[error("invalid PBH Plus service address")]
    InvalidEndpoint,
    #[error("PBH Plus lookup is too large")]
    InvalidLookup,
    #[error("PBH Plus service request failed")]
    Transport,
    #[error("PBH Plus service returned HTTP {0}")]
    Status(u16),
    #[error("invalid PBH Plus service response")]
    Payload,
}

/// Contains only hashes, not the license metadata or signed key.
pub struct RevocationProbe {
    id: String,
    cache_id: String,
    hashes: Vec<(&'static str, String)>,
}

pub struct RevocationUpdate {
    id: String,
    cache_id: String,
    record: CachedRevocation,
}

impl RemoteClient {
    pub fn new() -> Result<Self, RemoteError> {
        Self::with_proxy(&pbh_network::ProxyPolicy::default())
    }

    pub fn with_proxy(proxy: &pbh_network::ProxyPolicy) -> Result<Self, RemoteError> {
        Self::with_base(
            Url::parse(BASE).map_err(|_| RemoteError::InvalidEndpoint)?,
            proxy,
        )
    }

    /// Test fixture transport. This constructor accepts only loopback HTTP.
    pub fn with_loopback(base: Url) -> Result<Self, RemoteError> {
        if base.scheme() != "http"
            || !base.host_str().is_some_and(|host| {
                host.trim_matches(['[', ']'])
                    .parse::<IpAddr>()
                    .is_ok_and(|ip| ip.is_loopback())
            })
        {
            return Err(RemoteError::InvalidEndpoint);
        }
        Self::with_base(base, &pbh_network::ProxyPolicy::default())
    }

    fn with_base(base: Url, proxy: &pbh_network::ProxyPolicy) -> Result<Self, RemoteError> {
        if !base.username().is_empty()
            || base.password().is_some()
            || base.query().is_some()
            || base.fragment().is_some()
            || !base.path().ends_with('/')
        {
            return Err(RemoteError::InvalidEndpoint);
        }
        let client = proxy
            .apply(Client::builder())
            .referer(false)
            .redirect(redirect::Policy::none())
            .timeout(Duration::from_secs(15))
            .build()
            .map_err(|_| RemoteError::Transport)?;
        Ok(Self { client, base })
    }

    pub async fn download(&self, lookup: &str) -> Result<Option<String>, RemoteError> {
        let lookup = lookup.trim();
        if lookup.is_empty() || lookup.len() > MAX_LOOKUP_BYTES {
            return Err(RemoteError::InvalidLookup);
        }
        let mut url = self.endpoint("download")?;
        url.query_pairs_mut().append_pair("query", lookup);
        let response = self.get(url).await?;
        match response.status() {
            StatusCode::NO_CONTENT | StatusCode::NOT_FOUND => Ok(None),
            status if !status.is_success() => Err(RemoteError::Status(status.as_u16())),
            _ => {
                #[derive(Deserialize)]
                #[serde(rename_all = "camelCase")]
                struct Download {
                    key_text: Option<String>,
                }
                let result: Download = read_json(response).await?;
                Ok(result.key_text.filter(|key| !key.trim().is_empty()))
            }
        }
    }

    pub async fn check(
        &self,
        probe: RevocationProbe,
        now_ms: i64,
    ) -> Result<RevocationUpdate, RemoteError> {
        let mut url = self.endpoint("checkRevoke")?;
        url.query_pairs_mut().extend_pairs(probe.hashes);
        let response = self.get(url).await?;
        let revoked = match response.status() {
            StatusCode::NO_CONTENT => false,
            status if !status.is_success() => return Err(RemoteError::Status(status.as_u16())),
            _ => {
                #[derive(Deserialize)]
                struct Check {
                    revoked: bool,
                }
                read_json::<Check>(response).await?.revoked
            }
        };
        Ok(RevocationUpdate {
            id: probe.id,
            cache_id: probe.cache_id,
            record: CachedRevocation {
                revoked,
                timestamp: now_ms,
            },
        })
    }

    fn endpoint(&self, name: &str) -> Result<Url, RemoteError> {
        self.base
            .join(name)
            .map_err(|_| RemoteError::InvalidEndpoint)
    }

    async fn get(&self, url: Url) -> Result<Response, RemoteError> {
        // Never retain reqwest errors that can contain the redemption query.
        self.client
            .get(url)
            .send()
            .await
            .map_err(|_| RemoteError::Transport)
    }
}

async fn read_json<T: DeserializeOwned>(mut response: Response) -> Result<T, RemoteError> {
    if response
        .content_length()
        .is_some_and(|len| len > MAX_BODY_BYTES as u64)
    {
        return Err(RemoteError::Payload);
    }
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|_| RemoteError::Transport)? {
        if bytes.len().saturating_add(chunk.len()) > MAX_BODY_BYTES {
            return Err(RemoteError::Payload);
        }
        bytes.extend_from_slice(&chunk);
    }
    serde_json::from_slice(&bytes).map_err(|_| RemoteError::Payload)
}

impl VerifiedLicense {
    pub fn revocation_probe(&self, now_ms: i64) -> Option<RevocationProbe> {
        if self.local_trial
            || self.bundled_revoked
            || self.online.is_some_and(|entry| entry.fresh(now_ms))
        {
            return None;
        }
        let hashes = self
            .payload
            .remote_fields()
            .into_iter()
            .filter_map(|(name, value)| value.map(|value| (name, sha256(value))))
            .collect();
        Some(RevocationProbe {
            id: self.id.clone(),
            cache_id: cache_id(&self.payload),
            hashes,
        })
    }

    pub fn apply_online_update(&mut self, update: &RevocationUpdate) -> bool {
        if self.local_trial || self.id != update.id {
            return false;
        }
        self.accept_online_record(update.record)
    }

    fn accept_online_record(&mut self, record: CachedRevocation) -> bool {
        if self.online.is_some_and(|prior| {
            prior.timestamp > record.timestamp
                || (prior.timestamp == record.timestamp && prior.revoked && !record.revoked)
        }) {
            return false;
        }
        self.online = Some(record);
        true
    }
}

impl LicenseService {
    pub fn revocation_probes(&self, now_ms: i64) -> Vec<RevocationProbe> {
        self.licenses
            .iter()
            .filter_map(|license| license.revocation_probe(now_ms))
            .collect()
    }

    pub fn apply_online_update(&mut self, update: &RevocationUpdate) -> bool {
        self.licenses
            .iter_mut()
            .any(|license| license.apply_online_update(update))
    }

    pub fn retain_online_state_from(&mut self, previous: &Self) {
        for license in &mut self.licenses {
            previous.restore_verified_license_state(license);
        }
    }

    pub fn restore_verified_license_state(&self, license: &mut VerifiedLicense) {
        if license.local_trial {
            return;
        }
        if let Some(record) = self
            .licenses
            .iter()
            .find(|entry| entry.id == license.id)
            .and_then(|entry| entry.online)
        {
            license.accept_online_record(record);
        }
    }
}

impl RevocationUpdate {
    pub fn persist_cache(
        &self,
        data_dir: &Path,
        hardware_id: Option<&str>,
    ) -> Result<(), IssueError> {
        if let Some(hardware_id) = hardware_id {
            save_cache(data_dir, hardware_id, &self.cache_id, self.record)?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use std::collections::VecDeque;
    use std::fs;
    use std::sync::{Arc, Mutex};

    use axum::Router;
    use axum::extract::{Request, State};
    use axum::http::header;
    use axum::response::{IntoResponse, Response as AxumResponse};
    use serde_json::json;
    use tokio::net::TcpListener;
    use tokio::task::JoinHandle;

    use crate::tests::{fixture_license, fixture_public_key, issue_v2};
    use crate::{LicenseStatus, load_local_key};

    use super::*;

    const NOW: i64 = 1_700_000_000_000;
    const HARDWARE: &str = "remote-cache-fixture";
    const ONE_DAY_MS: i64 = 24 * 60 * 60 * 1000;

    #[derive(Default)]
    struct FixtureState {
        checks: VecDeque<(StatusCode, &'static str)>,
        observed: Vec<Url>,
    }

    struct Fixture {
        client: RemoteClient,
        state: Arc<Mutex<FixtureState>>,
        server: JoinHandle<()>,
    }

    impl Fixture {
        async fn open(checks: VecDeque<(StatusCode, &'static str)>) -> Self {
            let state = Arc::new(Mutex::new(FixtureState {
                checks,
                observed: Vec::new(),
            }));
            let app = Router::new().fallback(respond).with_state(state.clone());
            let listener = TcpListener::bind(("127.0.0.1", 0)).await.unwrap();
            let base = format!(
                "http://{}/peerbanhelper/v1/licenses/",
                listener.local_addr().unwrap()
            );
            let client = RemoteClient::with_loopback(Url::parse(&base).unwrap()).unwrap();
            let server = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
            Self {
                client,
                state,
                server,
            }
        }
    }

    impl Drop for Fixture {
        fn drop(&mut self) {
            self.server.abort();
        }
    }

    async fn respond(
        State(state): State<Arc<Mutex<FixtureState>>>,
        request: Request,
    ) -> AxumResponse {
        let url = Url::parse(&format!("http://127.0.0.1{}", request.uri())).unwrap();
        let mut state = state.lock().unwrap();
        state.observed.push(url.clone());
        match url.path() {
            "/peerbanhelper/v1/licenses/checkRevoke" => {
                let (status, body) = state.checks.pop_front().unwrap();
                (status, body).into_response()
            }
            "/peerbanhelper/v1/licenses/download" => {
                match url.query_pairs().next().unwrap().1.as_ref() {
                    "purchase + code&1" => {
                        (StatusCode::OK, r#"{"keyText":"fixture-issued-key"}"#).into_response()
                    }
                    "missing" => StatusCode::NOT_FOUND.into_response(),
                    "none" => StatusCode::NO_CONTENT.into_response(),
                    "redirect secret" => (
                        StatusCode::TEMPORARY_REDIRECT,
                        [(header::LOCATION, "/redirected")],
                    )
                        .into_response(),
                    _ => (
                        StatusCode::INTERNAL_SERVER_ERROR,
                        "private response details",
                    )
                        .into_response(),
                }
            }
            _ => panic!("unexpected request path {}", url.path()),
        }
    }

    fn test_service(data_dir: &Path, key: &str) -> LicenseService {
        let mut service = LicenseService {
            official: fixture_public_key(),
            local: load_local_key(data_dir, HARDWARE),
            cache: data_dir.join("cache").join("license-revoke"),
            licenses: Vec::new(),
            rejected_count: 0,
        };
        service.licenses.push(service.verify(key).unwrap());
        service
    }

    #[tokio::test]
    async fn exact_hashes_cache_signature_and_failure_retention() {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path();
        let mut data = issue_v2("official", json!(["basic"]), NOW, NOW + ONE_DAY_MS * 10);
        data["paymentGateway"] = json!("gateway");
        data["sku"] = json!("product");
        let key = fixture_license(data.clone());
        let mut service = test_service(path, &key);
        let fixture = Fixture::open(VecDeque::from([
            (StatusCode::OK, r#"{"revoked":true}"#),
            (StatusCode::SERVICE_UNAVAILABLE, "private error body"),
            (StatusCode::OK, r#"{"unexpected":"private body"}"#),
            (StatusCode::NO_CONTENT, ""),
        ]))
        .await;

        let probe = service.revocation_probes(NOW).pop().unwrap();
        let update = fixture.client.check(probe, NOW).await.unwrap();
        assert!(service.apply_online_update(&update));
        assert!(!service.is_feature_enabled("basic", NOW));
        assert!(service.revocation_probes(NOW + ONE_DAY_MS).is_empty());
        let mut without_cache = test_service(path, &key);
        assert!(without_cache.is_feature_enabled("basic", NOW));
        without_cache.retain_online_state_from(&service);
        assert!(!without_cache.is_feature_enabled("basic", NOW));
        update.persist_cache(path, Some(HARDWARE)).unwrap();
        let expected_cache = path
            .join("cache/license-revoke")
            .join(format!("{}.json", update.cache_id));
        let cached = fs::read(&expected_cache).unwrap();
        let mut restarted = test_service(path, &key);
        assert_eq!(
            restarted.status(NOW).licenses[0].status,
            LicenseStatus::Revoked
        );
        fs::write(
            &expected_cache,
            br#"{"revoked":true,"timestamp":1700000000000}"#,
        )
        .unwrap();
        assert!(test_service(path, &key).is_feature_enabled("basic", NOW));
        fs::write(&expected_cache, cached).unwrap();

        let later = NOW + ONE_DAY_MS + 1;
        let probe = restarted.revocation_probes(later).pop().unwrap();
        assert!(matches!(
            fixture.client.check(probe, later).await,
            Err(RemoteError::Status(503))
        ));
        let probe = restarted.revocation_probes(later).pop().unwrap();
        assert!(matches!(
            fixture.client.check(probe, later).await,
            Err(RemoteError::Payload)
        ));
        assert_eq!(
            restarted.status(later).licenses[0].status,
            LicenseStatus::Revoked
        );
        assert!(!restarted.is_feature_enabled("basic", later));
        let probe = restarted.revocation_probes(later).pop().unwrap();
        let fresh = fixture.client.check(probe, later).await.unwrap();
        assert!(restarted.apply_online_update(&fresh));
        assert!(!restarted.apply_online_update(&update));
        assert!(restarted.is_feature_enabled("basic", later));
        fresh.persist_cache(path, Some(HARDWARE)).unwrap();
        assert!(test_service(path, &key).is_feature_enabled("basic", later));
        let state = fixture.state.lock().unwrap();
        let expected = [
            "licenseTo",
            "description",
            "orderId",
            "paymentOrderId",
            "paymentGateway",
            "sku",
            "source",
            "type",
        ]
        .map(|field| (field.to_owned(), sha256(data[field].as_str().unwrap())));
        assert_eq!(state.observed.len(), 4);
        for request in &state.observed {
            assert_eq!(
                request.query_pairs().into_owned().collect::<Vec<_>>(),
                expected
            );
            assert!(!request.as_str().contains("fixture@example.test"));
        }
    }

    #[tokio::test]
    async fn download_encoding_no_redirects_and_no_credential_in_errors() {
        let fixture = Fixture::open(VecDeque::new()).await;
        assert_eq!(
            fixture
                .client
                .download(" purchase + code&1 ")
                .await
                .unwrap()
                .as_deref(),
            Some("fixture-issued-key")
        );
        assert_eq!(fixture.client.download("missing").await.unwrap(), None);
        assert_eq!(fixture.client.download("none").await.unwrap(), None);
        let error = fixture
            .client
            .download("redirect secret")
            .await
            .unwrap_err();
        assert_eq!(error, RemoteError::Status(307));
        assert!(!format!("{error:?} {error}").contains("secret"));
        let error = fixture
            .client
            .download("private redemption")
            .await
            .unwrap_err();
        assert_eq!(error, RemoteError::Status(500));
        assert!(!format!("{error:?} {error}").contains("private"));
        let state = fixture.state.lock().unwrap();
        assert_eq!(state.observed.len(), 5);
        assert_eq!(
            state.observed[0].query_pairs().next().unwrap().1,
            "purchase + code&1"
        );
    }
}
