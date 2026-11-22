//! PBH Plus license configuration and protected HTTP entry points.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, RwLock};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use anyhow::{Context, Result, anyhow};
use axum::extract::rejection::JsonRejection;
use axum::extract::{DefaultBodyLimit, Request, State};
use axum::http::{HeaderMap, StatusCode};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post, put};
use axum::{Json, Router};
use pbh_plus::remote::{RemoteClient, RevocationUpdate};
use pbh_plus::trial::{ChallengeError, Challenges, IssueError, TrialIdentity, TrialPermit};
use pbh_plus::{LicenseService, LicenseStatus, VerifiedLicense, hardware_uuid, license_id};
use pbh_web::config_file::{self, EditError};
use serde::Deserialize;
use serde_json::{Value, json};
use serde_yaml_ng::{Mapping, Value as Yaml};

use crate::ban_api::BanLanguage;

const LICENSES_KEY: &str = "pbh-plus-key";

#[derive(Clone)]
pub(crate) struct PlusRuntime(Arc<Runtime>);

struct Runtime {
    data_dir: PathBuf,
    config_path: PathBuf,
    hardware_id: Option<String>,
    demo_mode: bool,
    remote: Option<RemoteClient>,
    updates: Mutex<()>,
    challenges: Mutex<Challenges>,
    licenses: RwLock<LicenseService>,
}

impl PlusRuntime {
    pub(crate) fn open(
        data_dir: &Path,
        config_path: &Path,
        demo_mode: bool,
        proxy: &pbh_network::ProxyPolicy,
    ) -> Result<Self> {
        let config = read_config(config_path)?;
        let hardware_id = hardware_uuid().or_else(|| {
            config
                .get("installation-id")?
                .as_str()
                .filter(|id| !id.is_empty())
                .map(|id| format!("IID-{id}"))
        });
        let remote = match RemoteClient::with_proxy(proxy) {
            Ok(remote) => Some(remote),
            Err(error) => {
                eprintln!("PBH Plus online service: {error}");
                None
            }
        };
        let runtime = Self::with_hardware(
            data_dir,
            config_path,
            demo_mode,
            hardware_id,
            &config,
            remote,
        )?;
        if runtime.0.remote.is_some() {
            runtime.start_online_checks();
        }
        Ok(runtime)
    }

    fn with_hardware(
        data_dir: &Path,
        config_path: &Path,
        demo_mode: bool,
        hardware_id: Option<String>,
        config: &Mapping,
        remote: Option<RemoteClient>,
    ) -> Result<Self> {
        let licenses =
            LicenseService::load(&license_inputs(config), data_dir, hardware_id.as_deref())?;
        Ok(Self(Arc::new(Runtime {
            data_dir: data_dir.to_owned(),
            config_path: config_path.to_owned(),
            hardware_id,
            demo_mode,
            remote,
            updates: Mutex::new(()),
            challenges: Mutex::new(Challenges::default()),
            licenses: RwLock::new(licenses),
        })))
    }

    pub(crate) fn basic_enabled(&self) -> bool {
        let Some(now) = time_ms() else { return false };
        self.0
            .licenses
            .read()
            .is_ok_and(|licenses| licenses.is_feature_enabled("basic", now))
    }

    fn status(&self) -> Result<Value> {
        let now = time_ms().context("system time is unavailable")?;
        let licenses = self
            .0
            .licenses
            .read()
            .map_err(|_| anyhow!("license snapshot is unavailable"))?;
        Ok(serde_json::to_value(licenses.status(now))?)
    }

    fn edit(&self, requested: Edit) -> Result<Outcome> {
        let _updates = self
            .0
            .updates
            .lock()
            .map_err(|_| anyhow!("license edit is unavailable"))?;
        let result = config_file::edit(&self.0.config_path, |config| {
            let now = time_ms()
                .context("system time is unavailable")
                .map_err(EditAbort::Failed)?;
            self.plan_edit(config, requested, now)
        });
        let (outcome, next) = match result {
            Ok(changed) => changed,
            Err(EditError::Rejected(EditAbort::Unchanged(outcome))) => return Ok(outcome),
            Err(EditError::Rejected(EditAbort::Failed(error))) => return Err(error),
            Err(error) => return Err(anyhow!("unable to save license configuration: {error}")),
        };
        *self
            .0
            .licenses
            .write()
            .map_err(|_| anyhow!("license snapshot is unavailable"))? = next;
        Ok(outcome)
    }

    fn plan_edit(
        &self,
        config: &mut Mapping,
        requested: Edit,
        now: i64,
    ) -> Result<(Outcome, LicenseService), EditAbort> {
        let mut keys = license_inputs(config);
        let mut current = self.load(&keys).map_err(EditAbort::Failed)?;
        {
            let previous = self
                .0
                .licenses
                .read()
                .map_err(|_| EditAbort::Failed(anyhow!("license snapshot is unavailable")))?;
            current.retain_online_state_from(&previous);
        }
        let mut observed = None;
        let outcome = match requested {
            Edit::Import { input, online } => {
                let input = input.trim();
                let mut verified = match current.verify(input) {
                    Ok(license) => license,
                    Err(error) => return Err(EditAbort::Unchanged(Outcome::Invalid(error))),
                };
                current.restore_verified_license_state(&mut verified);
                if let Some(update) = online
                    && verified.apply_online_update(&update)
                {
                    current.apply_online_update(&update);
                    self.apply_snapshot_update(&update)
                        .map_err(EditAbort::Failed)?;
                    self.save_online_update(&update);
                    observed = Some(update);
                }
                match verified.status(now) {
                    LicenseStatus::Expired => return Err(EditAbort::Unchanged(Outcome::Expired)),
                    LicenseStatus::Revoked => return Err(EditAbort::Unchanged(Outcome::Revoked)),
                    LicenseStatus::Valid | LicenseStatus::NotStarted => {}
                }
                if keys.iter().any(|key| license_id(key) == verified.id()) {
                    return Err(EditAbort::Unchanged(Outcome::Imported));
                }
                keys.push(input.to_owned());
                Outcome::Imported
            }
            Edit::Delete(id) => {
                if !current
                    .status(now)
                    .licenses
                    .iter()
                    .any(|license| license.license_id == id)
                {
                    return Err(EditAbort::Unchanged(Outcome::Missing));
                }
                keys.retain(|key| license_id(key) != id);
                Outcome::Deleted
            }
            Edit::Trial { permit, language } => {
                self.add_trial(&current, &mut keys, permit, language, now)?;
                Outcome::Imported
            }
        };
        let mut next = self.load(&keys).map_err(EditAbort::Failed)?;
        next.retain_online_state_from(&current);
        if let Some(update) = observed {
            next.apply_online_update(&update);
        }
        config.insert(
            Yaml::String(LICENSES_KEY.into()),
            Yaml::Sequence(keys.into_iter().map(Yaml::String).collect()),
        );
        Ok((outcome, next))
    }

    fn verify_snapshot(&self, input: &str) -> Result<VerifiedLicense, ImportFailure> {
        let current = self
            .0
            .licenses
            .read()
            .map_err(|_| ImportFailure::Internal(anyhow!("license snapshot is unavailable")))?;
        let mut verified = current.verify(input).map_err(ImportFailure::Invalid)?;
        current.restore_verified_license_state(&mut verified);
        Ok(verified)
    }

    async fn prepare_import(&self, input: String) -> Result<Edit, ImportFailure> {
        let (input, verified) = match self.verify_snapshot(&input) {
            Ok(license) => (input, license),
            Err(
                error @ ImportFailure::Invalid(
                    pbh_plus::Error::InvalidEncoding | pbh_plus::Error::InvalidSignature,
                ),
            ) => {
                let Some(remote) = &self.0.remote else {
                    return Err(error);
                };
                let downloaded = match remote.download(&input).await {
                    Ok(Some(downloaded)) => downloaded,
                    Ok(None) => return Err(error),
                    Err(failure) => {
                        eprintln!("PBH Plus license lookup: {failure}");
                        return Err(error);
                    }
                };
                let verified = self.verify_snapshot(&downloaded)?;
                (downloaded, verified)
            }
            Err(error) => return Err(error),
        };
        let mut online = None;
        if let (Some(remote), Some(now)) = (&self.0.remote, time_ms())
            && let Some(probe) = verified.revocation_probe(now)
        {
            match remote.check(probe, now).await {
                Ok(update) => online = Some(update),
                Err(failure) => eprintln!("PBH Plus revocation check: {failure}"),
            }
        }
        Ok(Edit::Import { input, online })
    }

    fn start_online_checks(&self) {
        let Ok(handle) = tokio::runtime::Handle::try_current() else {
            return;
        };
        let runtime = Arc::downgrade(&self.0);
        handle.spawn(async move {
            loop {
                match runtime.upgrade() {
                    Some(runtime) => Self(runtime).refresh_online().await,
                    None => break,
                }
                // The legacy cache still covers the exact 24-hour boundary.
                tokio::time::sleep(Duration::from_secs(24 * 60 * 60) + Duration::from_millis(1))
                    .await;
            }
        });
    }

    async fn refresh_online(&self) {
        let (Some(remote), Some(now)) = (&self.0.remote, time_ms()) else {
            return;
        };
        let Ok(probes) = self
            .0
            .licenses
            .read()
            .map(|current| current.revocation_probes(now))
        else {
            return;
        };
        for probe in probes {
            let Some(now) = time_ms() else { break };
            let update = match remote.check(probe, now).await {
                Ok(update) => update,
                Err(failure) => {
                    eprintln!("PBH Plus revocation check: {failure}");
                    continue;
                }
            };
            let runtime = self.clone();
            let result = tokio::task::spawn_blocking(move || {
                let _updates = runtime
                    .0
                    .updates
                    .lock()
                    .map_err(|_| anyhow!("license edit is unavailable"))?;
                if runtime.apply_snapshot_update(&update)? {
                    runtime.save_online_update(&update);
                }
                Ok::<_, anyhow::Error>(())
            })
            .await;
            match result {
                Ok(Err(error)) => eprintln!("PBH Plus revocation update: {error}"),
                Err(error) => eprintln!("PBH Plus revocation worker: {error}"),
                Ok(Ok(())) => {}
            }
        }
    }

    /// Called while holding the update lock. The verified in-memory result
    /// remains usable even when the optional Java-compatible cache cannot be saved.
    fn apply_snapshot_update(&self, update: &RevocationUpdate) -> Result<bool> {
        Ok(self
            .0
            .licenses
            .write()
            .map_err(|_| anyhow!("license snapshot is unavailable"))?
            .apply_online_update(update))
    }

    fn save_online_update(&self, update: &RevocationUpdate) {
        if let Err(error) = update.persist_cache(&self.0.data_dir, self.0.hardware_id.as_deref()) {
            eprintln!("PBH Plus revocation cache: {error}");
        }
    }

    fn add_trial(
        &self,
        current: &LicenseService,
        keys: &mut Vec<String>,
        permit: TrialPermit,
        language: BanLanguage,
        now: i64,
    ) -> Result<(), EditAbort> {
        let owner = std::env::var(if cfg!(windows) { "USERNAME" } else { "USER" })
            .unwrap_or_else(|_| "Local User".into());
        let source = language.render(&json!({"key":"FREE_LICENSE_SOURCE"}));
        let description = language.render(&json!({"key":"FREE_LICENSE_DESCRIPTION"}));
        let issued = current.issue_local_trial(
            permit,
            &self.0.data_dir,
            self.0.hardware_id.as_deref(),
            TrialIdentity {
                owner: &owner,
                source: &source,
                description: &description,
            },
            now,
        );
        let issued = match issued {
            Ok(issued) => issued,
            Err(IssueError::AlreadyActive) => {
                return Err(EditAbort::Unchanged(Outcome::AlreadyActive));
            }
            Err(error) => return Err(EditAbort::Failed(error.into())),
        };
        // The first trial may have created the public key. Load it and verify
        // the generated license through the same path used after restart.
        let service = self.load(&[]).map_err(EditAbort::Failed)?;
        let verified = service
            .verify(&issued)
            .map_err(|error| EditAbort::Failed(error.into()))?;
        if verified.status(now) != LicenseStatus::Valid {
            return Err(EditAbort::Unchanged(Outcome::Revoked));
        }
        keys.push(issued);
        Ok(())
    }

    fn load(&self, keys: &[String]) -> Result<LicenseService> {
        Ok(LicenseService::load(
            keys,
            &self.0.data_dir,
            self.0.hardware_id.as_deref(),
        )?)
    }
}

fn read_config(path: &Path) -> Result<Mapping> {
    let bytes = fs::read(path).context("unable to read main configuration")?;
    // Do not propagate parser diagnostics that can include license text.
    serde_yaml_ng::from_slice(&bytes).map_err(|_| anyhow!("invalid main configuration"))
}

fn license_inputs(config: &Mapping) -> Vec<String> {
    match config.get(LICENSES_KEY) {
        Some(Yaml::String(key)) => vec![key.to_owned()],
        Some(Yaml::Sequence(keys)) => keys
            .iter()
            .filter_map(|key| key.as_str().map(str::to_owned))
            .collect(),
        _ => Vec::new(),
    }
}

pub(crate) fn router(runtime: PlusRuntime) -> Router {
    Router::new()
        .route("/api/pbhplus/status", get(status))
        .route("/api/pbhplus/key", put(import_key).delete(delete_key))
        .route(
            "/api/pbhplus/claimRenewFreeLicenseCaptcha",
            get(claim_trial),
        )
        .route("/api/pbhplus/renewFreeLicense", post(renew_trial))
        .layer(DefaultBodyLimit::max(128 * 1024))
        .with_state(runtime)
}

pub(crate) async fn require_basic(
    State(runtime): State<PlusRuntime>,
    request: Request,
    next: Next,
) -> Response {
    if !runtime.basic_enabled() {
        return response(
            StatusCode::PAYMENT_REQUIRED,
            false,
            json!("PBH Plus License not activated"),
            None,
        );
    }
    next.run(request).await
}

async fn status(State(runtime): State<PlusRuntime>, headers: HeaderMap) -> Response {
    match runtime.status() {
        Ok(status) => response(StatusCode::OK, true, Value::Null, Some(status)),
        Err(error) => internal_error(&headers, error),
    }
}

#[derive(Deserialize)]
struct ImportBody {
    key: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct DeleteBody {
    license_id: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TrialBody {
    captcha_id: String,
    captcha_nonce: String,
}

enum Edit {
    Import {
        input: String,
        online: Option<RevocationUpdate>,
    },
    Delete(String),
    Trial {
        permit: TrialPermit,
        language: BanLanguage,
    },
}

enum Outcome {
    Imported,
    Deleted,
    Expired,
    Revoked,
    Missing,
    AlreadyActive,
    Invalid(pbh_plus::Error),
}

enum EditAbort {
    Unchanged(Outcome),
    Failed(anyhow::Error),
}

#[derive(Debug)]
enum ImportFailure {
    Invalid(pbh_plus::Error),
    Internal(anyhow::Error),
}

impl std::fmt::Display for EditAbort {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Unchanged(_) => formatter.write_str("license configuration was not changed"),
            Self::Failed(error) => error.fmt(formatter),
        }
    }
}

async fn claim_trial(State(runtime): State<PlusRuntime>, headers: HeaderMap) -> Response {
    if runtime.0.demo_mode {
        return demo_mode(&headers);
    }
    let result = runtime
        .0
        .challenges
        .lock()
        .map_err(|_| anyhow!("local trial challenges are unavailable"))
        .and_then(|mut challenges| challenges.create(Instant::now()).map_err(Into::into))
        .and_then(|challenge| Ok(serde_json::to_value(challenge)?));
    match result {
        Ok(data) => response(StatusCode::OK, true, Value::Null, Some(data)),
        Err(error) => internal_error(&headers, error),
    }
}

async fn renew_trial(
    State(runtime): State<PlusRuntime>,
    headers: HeaderMap,
    body: Result<Json<TrialBody>, JsonRejection>,
) -> Response {
    if runtime.0.demo_mode {
        return demo_mode(&headers);
    }
    let Ok(Json(body)) = body else {
        return bad_request();
    };
    let verified = match runtime.0.challenges.lock() {
        Ok(mut challenges) => {
            challenges.verify(&body.captcha_id, &body.captcha_nonce, Instant::now())
        }
        Err(_) => {
            return internal_error(&headers, anyhow!("local trial challenges are unavailable"));
        }
    };
    let permit = match verified {
        Ok(permit) => permit,
        Err(ChallengeError::Missing) => {
            return message(
                &headers,
                StatusCode::OK,
                false,
                "POW_CAPTCHA_SERVER_NOT_FOUND",
                &[],
            );
        }
        Err(ChallengeError::InvalidProof) => {
            return message(
                &headers,
                StatusCode::OK,
                false,
                "POW_CAPTCHA_SERVER_VERIFY_FAILED",
                &[],
            );
        }
        Err(error) => return internal_error(&headers, error.into()),
    };
    let language = BanLanguage::from_headers(&headers);
    edit(runtime, headers, Ok(Edit::Trial { permit, language })).await
}

async fn import_key(
    State(runtime): State<PlusRuntime>,
    headers: HeaderMap,
    body: Result<Json<ImportBody>, JsonRejection>,
) -> Response {
    if runtime.0.demo_mode {
        return demo_mode(&headers);
    }
    let Ok(Json(body)) = body else {
        return bad_request();
    };
    match runtime.prepare_import(body.key).await {
        Ok(command) => edit(runtime, headers, Ok(command)).await,
        Err(ImportFailure::Invalid(error)) => message(
            &headers,
            StatusCode::OK,
            false,
            "PBH_LICENSE_PARSE_FAILED",
            &[error.to_string()],
        ),
        Err(ImportFailure::Internal(error)) => internal_error(&headers, error),
    }
}

async fn delete_key(
    State(runtime): State<PlusRuntime>,
    headers: HeaderMap,
    body: Result<Json<DeleteBody>, JsonRejection>,
) -> Response {
    edit(
        runtime,
        headers,
        body.map(|Json(body)| Edit::Delete(body.license_id)),
    )
    .await
}

async fn edit(
    runtime: PlusRuntime,
    headers: HeaderMap,
    command: Result<Edit, JsonRejection>,
) -> Response {
    if runtime.0.demo_mode {
        return demo_mode(&headers);
    }
    let Ok(command) = command else {
        return bad_request();
    };
    let outcome = tokio::task::spawn_blocking(move || runtime.edit(command)).await;
    let (key, success) = match outcome {
        Ok(Ok(Outcome::Imported)) => ("PBH_PLUS_LICENSE_UPDATED", true),
        Ok(Ok(Outcome::Deleted)) => ("PBH_LICENSE_KEY_DELETED", true),
        Ok(Ok(Outcome::Expired)) => ("PBH_LICENSE_KEY_EXPIRED", false),
        Ok(Ok(Outcome::Revoked)) => ("PBH_LICENSE_KEY_REVOKED", false),
        Ok(Ok(Outcome::AlreadyActive)) => ("PBH_PLUS_FREE_LICENSE_DENIED_EXISTS", false),
        Ok(Ok(Outcome::Missing)) => {
            return message(
                &headers,
                StatusCode::INTERNAL_SERVER_ERROR,
                false,
                "WEBAPI_INTERNAL_ERROR",
                &[],
            );
        }
        Ok(Ok(Outcome::Invalid(error))) => {
            return message(
                &headers,
                StatusCode::OK,
                false,
                "PBH_LICENSE_PARSE_FAILED",
                &[error.to_string()],
            );
        }
        Ok(Err(error)) => return internal_error(&headers, error),
        Err(error) => return internal_error(&headers, error.into()),
    };
    message(&headers, StatusCode::OK, success, key, &[])
}

fn demo_mode(headers: &HeaderMap) -> Response {
    message(
        headers,
        StatusCode::BAD_REQUEST,
        false,
        "DEMO_MODE_OPERATION_NOT_PERMITTED",
        &[],
    )
}

fn bad_request() -> Response {
    response(
        StatusCode::BAD_REQUEST,
        false,
        json!("Invalid license request"),
        None,
    )
}

fn internal_error(headers: &HeaderMap, error: anyhow::Error) -> Response {
    eprintln!("Unable to handle PBH Plus license configuration: {error}");
    message(
        headers,
        StatusCode::INTERNAL_SERVER_ERROR,
        false,
        "WEBAPI_INTERNAL_ERROR",
        &[],
    )
}

fn message(
    headers: &HeaderMap,
    status: StatusCode,
    success: bool,
    key: &str,
    args: &[String],
) -> Response {
    let text = BanLanguage::from_headers(headers).render(&json!({"key":key,"params":args}));
    response(status, success, json!(text), None)
}

fn response(status: StatusCode, success: bool, message: Value, data: Option<Value>) -> Response {
    (
        status,
        Json(json!({"success":success,"message":message,"data":data})),
    )
        .into_response()
}

fn time_ms() -> Option<i64> {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .ok()?
        .as_millis()
        .try_into()
        .ok()
}

#[cfg(test)]
mod tests {
    use axum::middleware;

    use super::*;

    #[tokio::test]
    async fn invalid_license_cannot_mutate_config_or_open_basic_route() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("config.yml");
        let initial = "server:\n  token: test-fixture\npbh-plus-key: []\n";
        fs::write(&path, initial).unwrap();
        let runtime = PlusRuntime::with_hardware(
            temp.path(),
            &path,
            false,
            None,
            &read_config(&path).unwrap(),
            None,
        )
        .unwrap();
        assert!(!runtime.basic_enabled());
        let gated = Router::new()
            .route("/basic", get(|| async { StatusCode::OK }))
            .layer(middleware::from_fn_with_state(
                runtime.clone(),
                require_basic,
            ));
        let app = router(runtime).merge(gated);
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
        let client = reqwest::Client::builder().no_proxy().build().unwrap();
        let protected = client.get(format!("{address}/basic")).send().await.unwrap();
        assert_eq!(protected.status(), StatusCode::PAYMENT_REQUIRED);
        let result = client
            .put(format!("{address}/api/pbhplus/key"))
            .header("content-type", "application/json")
            .body(r#"{"key":"invalid-test-license"}"#)
            .send()
            .await
            .unwrap();
        assert_eq!(result.status(), StatusCode::OK);
        let body = result.text().await.unwrap();
        assert_eq!(
            serde_json::from_str::<Value>(&body).unwrap()["success"],
            false
        );
        assert!(!body.contains("invalid-test-license"));
        assert_eq!(fs::read_to_string(&path).unwrap(), initial);
        let status = client
            .get(format!("{address}/api/pbhplus/status"))
            .send()
            .await
            .unwrap();
        let status: Value = serde_json::from_slice(&status.bytes().await.unwrap()).unwrap();
        assert_eq!(
            status,
            json!({"success":true,"message":null,"data":{"enabledFeatures":[],"licenses":[]}})
        );
        server.abort();
    }

    #[test]
    fn shared_configuration_editor_preserves_unrelated_values() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("config.yml");
        fs::write(
            &path,
            "# test fixture\nserver:\n  token: test-token\npbh-plus-key: []\n",
        )
        .unwrap();
        let config = read_config(&path).unwrap();
        config_file::edit(&path, |config| -> Result<()> {
            config.insert(
                Yaml::String(LICENSES_KEY.into()),
                Yaml::Sequence(Vec::new()),
            );
            Ok(())
        })
        .unwrap();
        assert_eq!(read_config(&path).unwrap(), config);
        assert_eq!(fs::read_dir(temp.path()).unwrap().count(), 2);
    }

    #[tokio::test]
    async fn trial_routes_require_login_and_two_valid_proofs_commit_only_one_license() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("config.yml");
        fs::write(&path, "server:\n  token: trial-fixture\npbh-plus-key: []\n").unwrap();
        let runtime = PlusRuntime::with_hardware(
            temp.path(),
            &path,
            false,
            Some("trial-fixture-device".into()),
            &read_config(&path).unwrap(),
            None,
        )
        .unwrap();
        let config = pbh_web::WebConfig {
            token: "trial-fixture".into(),
            manifest: pbh_web::Manifest {
                version: pbh_web::BuildVersion {
                    version: "fixture".into(),
                    os: String::new(),
                    branch: String::new(),
                    commit: String::new(),
                    abbrev: String::new(),
                },
                modules: Vec::new(),
                installation_id: "fixture".into(),
                analytics: false,
            },
            static_dir: None,
            allow_cors: false,
        };
        let app = pbh_web::router_with_protected_routes(config, router(runtime.clone()));
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = format!("http://{}/api/pbhplus", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            pbh_web::serve(listener, app, std::future::pending())
                .await
                .unwrap()
        });
        let client = reqwest::Client::builder().no_proxy().build().unwrap();
        let claim = format!("{address}/claimRenewFreeLicenseCaptcha");
        let renew = format!("{address}/renewFreeLicense");
        assert_eq!(
            client.get(&claim).send().await.unwrap().status(),
            StatusCode::FORBIDDEN
        );
        let challenge = client
            .get(claim)
            .bearer_auth("trial-fixture")
            .send()
            .await
            .unwrap();
        let challenge: Value = serde_json::from_slice(&challenge.bytes().await.unwrap()).unwrap();
        assert_eq!(challenge["data"]["algorithm"], "SHA-1");
        assert_eq!(challenge["data"]["difficultyBits"], 5);
        let claim_id = challenge["data"]["challengeId"].as_str().unwrap();
        let invalid_proof =
            json!({"captchaId":claim_id,"captchaNonce":"malformed!nonce"}).to_string();
        let unauthorized = client
            .post(&renew)
            .header("content-type", "application/json")
            .body(invalid_proof.clone())
            .send()
            .await
            .unwrap();
        assert_eq!(unauthorized.status(), StatusCode::FORBIDDEN);
        let invalid = client
            .post(renew)
            .bearer_auth("trial-fixture")
            .header("content-type", "application/json")
            .body(invalid_proof)
            .send()
            .await
            .unwrap();
        let invalid: Value = serde_json::from_slice(&invalid.bytes().await.unwrap()).unwrap();
        assert_eq!(
            invalid["message"],
            "Captcha verification failed, please try again"
        );
        assert!(!runtime.basic_enabled());

        let left = proof_for_edit(&runtime);
        let right = proof_for_edit(&runtime);
        let first = runtime.clone();
        let second = runtime.clone();
        let (a, b) = tokio::join!(
            tokio::task::spawn_blocking(move || first.edit(left).unwrap()),
            tokio::task::spawn_blocking(move || second.edit(right).unwrap()),
        );
        assert!(matches!(
            (a.unwrap(), b.unwrap()),
            (Outcome::Imported, Outcome::AlreadyActive)
                | (Outcome::AlreadyActive, Outcome::Imported)
        ));
        assert_eq!(license_inputs(&read_config(&path).unwrap()).len(), 1);
        assert_eq!(
            runtime.status().unwrap()["enabledFeatures"],
            json!(["basic"])
        );
        assert_eq!(
            runtime.status().unwrap()["licenses"][0]["data"]["type"],
            "local"
        );
        server.abort();
    }

    fn proof_for_edit(runtime: &PlusRuntime) -> Edit {
        let mut challenges = runtime.0.challenges.lock().unwrap();
        let now = Instant::now();
        let challenge = challenges.create(now).unwrap();
        let permit = (0..4096)
            .find_map(|candidate| {
                challenges
                    .verify(&challenge.challenge_id, &format!("{candidate:05X}A=="), now)
                    .ok()
            })
            .expect("the five-bit challenge has a valid candidate");
        Edit::Trial {
            permit,
            language: BanLanguage::En,
        }
    }

    #[tokio::test]
    async fn downloaded_keys_still_require_local_verification_and_trial_is_not_checked_online() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("config.yml");
        fs::write(
            &path,
            "server:\n  token: online-fixture\npbh-plus-key: []\n",
        )
        .unwrap();
        let hardware = Some("download-fixture-device".to_owned());
        let local = PlusRuntime::with_hardware(
            temp.path(),
            &path,
            false,
            hardware.clone(),
            &read_config(&path).unwrap(),
            None,
        )
        .unwrap();
        let edit = proof_for_edit(&local);
        assert!(matches!(local.edit(edit).unwrap(), Outcome::Imported));
        let key = license_inputs(&read_config(&path).unwrap()).pop().unwrap();
        assert!(matches!(
            local.edit(Edit::Delete(license_id(&key))).unwrap(),
            Outcome::Deleted
        ));

        let observed = Arc::new(Mutex::new(Vec::new()));
        let requests = observed.clone();
        let fixture = Router::new().fallback(move |uri: axum::http::Uri| {
            let requests = requests.clone();
            let key = key.clone();
            async move {
                requests.lock().unwrap().push(uri.path().to_owned());
                let query = uri.query().unwrap_or_default();
                if query == "query=valid-fixture" {
                    Json(json!({"keyText":key}))
                } else {
                    Json(json!({"keyText":"invalid-signed-response"}))
                }
            }
        });
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let base = format!("http://{}/licenses/", listener.local_addr().unwrap());
        let server = tokio::spawn(async move { axum::serve(listener, fixture).await.unwrap() });
        let runtime = PlusRuntime::with_hardware(
            temp.path(),
            &path,
            false,
            hardware,
            &read_config(&path).unwrap(),
            Some(RemoteClient::with_loopback(reqwest::Url::parse(&base).unwrap()).unwrap()),
        )
        .unwrap();
        let before = fs::read(&path).unwrap();
        assert!(matches!(
            runtime.prepare_import("invalid-fixture".into()).await,
            Err(ImportFailure::Invalid(
                pbh_plus::Error::InvalidEncoding | pbh_plus::Error::InvalidSignature,
            ))
        ));
        assert_eq!(fs::read(&path).unwrap(), before);
        let planned = runtime
            .prepare_import("valid-fixture".into())
            .await
            .unwrap();
        assert!(matches!(runtime.edit(planned).unwrap(), Outcome::Imported));
        assert!(runtime.basic_enabled());
        runtime.refresh_online().await;
        assert_eq!(
            &*observed.lock().unwrap(),
            &["/licenses/download", "/licenses/download"]
        );
        assert_eq!(license_inputs(&read_config(&path).unwrap()).len(), 1);
        server.abort();
    }
}
