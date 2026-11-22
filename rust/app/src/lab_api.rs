//! Persistent laboratory switches and their existing WebUI HTTP contract.

use std::convert::Infallible;
use std::fs;
use std::io::{ErrorKind, Write};
use std::path::{Path as FilePath, PathBuf};

use anyhow::{Context, Result, anyhow};
use axum::body::Bytes;
use axum::extract::{Path, RawQuery, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::{get, put};
use axum::{Json, Router};
use pbh_web::config_file;
use serde::Deserialize;
use serde_json::value::RawValue;
use serde_json::{Value, json};
use serde_yaml_ng::{Mapping, Value as Yaml};

use crate::ban_api::BanLanguage;

const EXPERIMENTS: [Experiment; 2] = [Experiment::SqliteVacuum, Experiment::Win32EmptyWorkingSet];
const DEFAULT_SETTING: &str = "default";

#[derive(Clone, Copy)]
pub(crate) enum Experiment {
    SqliteVacuum,
    Win32EmptyWorkingSet,
}

impl Experiment {
    fn id(self) -> &'static str {
        match self {
            Self::SqliteVacuum => "sqlite_vacuum",
            Self::Win32EmptyWorkingSet => "win32_empty_working_set",
        }
    }

    fn groups(self) -> &'static [u8] {
        match self {
            Self::SqliteVacuum => &[0, 1, 2, 3, 4],
            Self::Win32EmptyWorkingSet => &[1, 3],
        }
    }

    fn translation_keys(self) -> (&'static str, &'static str) {
        match self {
            Self::SqliteVacuum => (
                "LAB_EXPERIMENT_SQLITE_VACUUM_TITLE",
                "LAB_EXPERIMENT_SQLITE_VACUUM_DESCRIPTION",
            ),
            Self::Win32EmptyWorkingSet => (
                "LAB_EXPERIMENT_WIN32_EMPTY_WORKING_SET_TITLE",
                "LAB_EXPERIMENT_WIN32_EMPTY_WORKING_SET_DESCRIPTION",
            ),
        }
    }

    fn active(self, config: &Mapping, group: u8) -> bool {
        enabled(config)
            && match config.get(self.id()) {
                Some(Yaml::Bool(value)) => *value,
                Some(Yaml::String(value)) if value != DEFAULT_SETTING => {
                    value.eq_ignore_ascii_case("true")
                }
                None | Some(Yaml::Null | Yaml::String(_)) => self.groups().contains(&group),
                _ => false,
            }
    }

    fn dto(self, config: &Mapping, group: u8, language: BanLanguage) -> Value {
        let (title, description) = self.translation_keys();
        json!({
            "id":self.id(),
            "activated":self.active(config, group),
            "targetGroups":self.groups(),
            "title":language.render(&json!({"key":title})),
            "description":language.render(&json!({"key":description})),
        })
    }
}

#[derive(Clone)]
pub(crate) struct LabRuntime {
    path: PathBuf,
    group: u8,
    language: BanLanguage,
    demo_mode: bool,
}

impl LabRuntime {
    pub(crate) fn open(
        data_dir: &FilePath,
        installation_id: Option<&str>,
        locale: &str,
        demo_mode: bool,
    ) -> Result<Self> {
        let path = config_path(data_dir);
        initialize(&path)?;
        Ok(Self {
            path,
            group: group(installation_id),
            language: BanLanguage::from_locale(locale),
            demo_mode,
        })
    }

    fn store(&self, name: &str, value: Yaml) -> Result<()> {
        config_file::edit(&self.path, |config| -> Result<(), Infallible> {
            upgrade(config);
            config.insert(Yaml::String(name.to_owned()), value);
            fill_experiment_defaults(config);
            Ok(())
        })
        .context("unable to save laboratory configuration")
    }
}

#[cfg(test)]
pub(crate) fn experiment_active(
    data_dir: &FilePath,
    installation_id: Option<&str>,
    experiment: Experiment,
) -> Result<bool> {
    Ok(experiment.active(
        &read_config(&config_path(data_dir))?,
        group(installation_id),
    ))
}

fn group(installation_id: Option<&str>) -> u8 {
    let hash = installation_id
        .unwrap_or("???")
        .encode_utf16()
        .fold(0_i32, |hash, code| {
            hash.wrapping_mul(31).wrapping_add(i32::from(code))
        });
    (hash % 5).unsigned_abs() as u8
}

fn config_path(data_dir: &FilePath) -> PathBuf {
    crate::config::config_dir(data_dir).join("laboratory.yml")
}

fn enabled(config: &Mapping) -> bool {
    config
        .get("enabled")
        .and_then(Yaml::as_bool)
        .unwrap_or_default()
}

fn fill_defaults(config: &mut Mapping) {
    upgrade(config);
    fill_experiment_defaults(config);
}

fn upgrade(config: &mut Mapping) {
    if config
        .get("config-version")
        .and_then(Yaml::as_i64)
        .unwrap_or(0)
        == 0
    {
        config.insert(Yaml::String("enabled".into()), Yaml::Bool(true));
        config.insert(
            Yaml::String("config-version".into()),
            Yaml::Number(1.into()),
        );
    }
}

fn fill_experiment_defaults(config: &mut Mapping) {
    if enabled(config) {
        for experiment in EXPERIMENTS {
            if config.get(experiment.id()).is_none_or(Yaml::is_null) {
                config.insert(
                    Yaml::String(experiment.id().into()),
                    Yaml::String(DEFAULT_SETTING.into()),
                );
            }
        }
    }
}

fn read_raw(path: &FilePath) -> Result<Option<Mapping>> {
    let contents = match fs::read_to_string(path) {
        Ok(contents) => contents,
        Err(error) if error.kind() == ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error).context("unable to read laboratory configuration"),
    };
    serde_yaml_ng::from_str(&contents)
        .map(Some)
        .context("invalid laboratory configuration")
}

fn read_config(path: &FilePath) -> Result<Mapping> {
    let mut config = read_raw(path)?.unwrap_or_default();
    fill_defaults(&mut config);
    Ok(config)
}

fn initialize(path: &FilePath) -> Result<()> {
    let directory = path
        .parent()
        .context("laboratory configuration has no parent directory")?;
    fs::create_dir_all(directory)?;
    if !path.exists() {
        let mut defaults = Mapping::new();
        fill_defaults(&mut defaults);
        let mut draft = tempfile::NamedTempFile::new_in(directory)?;
        draft.write_all(serde_yaml_ng::to_string(&defaults)?.as_bytes())?;
        draft.as_file().sync_all()?;
        match draft.into_temp_path().persist_noclobber(path) {
            Ok(()) => return Ok(()),
            Err(error) if error.error.kind() == ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error.error.into()),
        }
    }
    let Some(original) = read_raw(path)? else {
        return Err(anyhow!(
            "laboratory configuration disappeared while opening"
        ));
    };
    let mut upgraded = original.clone();
    fill_defaults(&mut upgraded);
    if upgraded != original {
        config_file::edit(path, |config| -> Result<(), Infallible> {
            fill_defaults(config);
            Ok(())
        })?;
    }
    Ok(())
}

pub(crate) fn router(runtime: LabRuntime) -> Router {
    Router::new()
        .route("/api/laboratory/config", get(get_config).post(set_config))
        .route("/api/laboratory/experiments", get(experiments))
        .route("/api/laboratory/isExperimentActivated", get(activated))
        .route("/api/laboratory/experiment/{id}", put(set_experiment))
        .with_state(runtime)
}

async fn get_config(State(runtime): State<LabRuntime>, headers: HeaderMap) -> Response {
    if let Some(response) = demo_response(&runtime, &headers) {
        return response;
    }
    run(&runtime, &headers, |runtime| {
        let config = read_config(&runtime.path)?;
        Ok(success(json!({"enabled":enabled(&config)})))
    })
    .await
}

#[derive(Deserialize)]
#[serde(untagged)]
enum Toggle {
    Boolean(bool),
    Text(String),
}

#[derive(Deserialize)]
struct ConfigChange {
    enabled: Option<Toggle>,
}

async fn set_config(
    State(runtime): State<LabRuntime>,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    if let Some(response) = demo_response(&runtime, &headers) {
        return response;
    }
    let Ok(body) = serde_json::from_slice::<ConfigChange>(&body) else {
        return internal_error(&runtime, &headers);
    };
    let enabled = match body.enabled {
        Some(Toggle::Boolean(enabled)) => enabled,
        Some(Toggle::Text(value)) => value.eq_ignore_ascii_case("true"),
        None => false,
    };
    run(&runtime, &headers, move |runtime| {
        runtime.store("enabled", Yaml::Bool(enabled))?;
        Ok(success(json!(enabled)))
    })
    .await
}

async fn experiments(State(runtime): State<LabRuntime>, headers: HeaderMap) -> Response {
    let language = BanLanguage::from_headers_or(&headers, runtime.language);
    run(&runtime, &headers, move |runtime| {
        let config = read_config(&runtime.path)?;
        let experiments = EXPERIMENTS.iter().map(|entry| entry.dto(&config, runtime.group, language)).collect::<Vec<_>>();
        Ok(success(json!({"experiments":experiments,"mygroup":runtime.group,"labEnabled":enabled(&config)})))
    }).await
}

async fn activated(
    State(runtime): State<LabRuntime>,
    headers: HeaderMap,
    RawQuery(query): RawQuery,
) -> Response {
    let id = form_urlencoded::parse(query.as_deref().unwrap_or_default().as_bytes())
        .find(|(key, _)| key == "id")
        .map(|(_, value)| value.into_owned());
    let experiment = id.as_ref().and_then(|id| {
        EXPERIMENTS
            .into_iter()
            .find(|experiment| experiment.id().eq_ignore_ascii_case(id))
    });
    let Some(experiment) = experiment else {
        return payload(
            StatusCode::OK,
            false,
            json!("Experiment not found"),
            Value::Null,
        );
    };
    run(&runtime, &headers, move |runtime| {
        let config = read_config(&runtime.path)?;
        Ok(success(json!(experiment.active(&config, runtime.group))))
    })
    .await
}

#[derive(Deserialize)]
struct ExperimentChange<'a> {
    #[serde(borrow)]
    status: Option<&'a RawValue>,
}

fn experiment_status(body: &[u8]) -> Result<Option<String>> {
    let Some(status) = serde_json::from_slice::<ExperimentChange<'_>>(body)?.status else {
        return Ok(None);
    };
    let value = status.get();
    match value.as_bytes().first() {
        Some(b'"') => Ok(Some(serde_json::from_str(value)?)),
        // The Java DTO receives the original numeric or boolean JSON token as a string.
        Some(b'-' | b'0'..=b'9' | b't' | b'f') => Ok(Some(value.to_owned())),
        _ => Err(anyhow!("invalid laboratory experiment status type")),
    }
}

async fn set_experiment(
    State(runtime): State<LabRuntime>,
    headers: HeaderMap,
    Path(id): Path<String>,
    body: Bytes,
) -> Response {
    if let Some(response) = demo_response(&runtime, &headers) {
        return response;
    }
    let Ok(status) = experiment_status(&body) else {
        return internal_error(&runtime, &headers);
    };
    let Some(experiment) = EXPERIMENTS.into_iter().find(|entry| entry.id() == id) else {
        return invalid(&format!(
            "Invalid experiment id: {id}, it's not exists in Experiments registry"
        ));
    };
    let setting = match status.as_deref() {
        Some(value) if value.eq_ignore_ascii_case("true") => Yaml::Bool(true),
        Some(value) if value.eq_ignore_ascii_case("false") => Yaml::Bool(false),
        _ => Yaml::String(DEFAULT_SETTING.into()),
    };
    run(&runtime, &headers, move |runtime| {
        runtime.store(experiment.id(), setting)?;
        Ok(success(json!(status)))
    })
    .await
}

async fn run(
    runtime: &LabRuntime,
    headers: &HeaderMap,
    operation: impl FnOnce(LabRuntime) -> Result<Response> + Send + 'static,
) -> Response {
    let owned = runtime.clone();
    let error = match tokio::task::spawn_blocking(move || operation(owned)).await {
        Ok(Ok(response)) => return response,
        Ok(Err(error)) => error,
        Err(error) => error.into(),
    };
    crate::journal::log_warning!("Laboratory: {error}");
    internal_error(runtime, headers)
}

fn internal_error(runtime: &LabRuntime, headers: &HeaderMap) -> Response {
    let message = BanLanguage::from_headers_or(headers, runtime.language)
        .render(&json!({"key":"WEBAPI_INTERNAL_ERROR"}));
    payload(
        StatusCode::INTERNAL_SERVER_ERROR,
        false,
        json!(message),
        Value::Null,
    )
}

fn demo_response(runtime: &LabRuntime, headers: &HeaderMap) -> Option<Response> {
    runtime.demo_mode.then(|| {
        let text = BanLanguage::from_headers_or(headers, runtime.language)
            .render(&json!({"key":"DEMO_MODE_OPERATION_NOT_PERMITTED"}));
        payload(StatusCode::BAD_REQUEST, false, json!(text), Value::Null)
    })
}

fn success(data: Value) -> Response {
    payload(StatusCode::OK, true, Value::Null, data)
}

fn invalid(message: &str) -> Response {
    payload(
        StatusCode::BAD_REQUEST,
        false,
        json!(message),
        json!(message),
    )
}

fn payload(status: StatusCode, success: bool, message: Value, data: Value) -> Response {
    (
        status,
        Json(json!({"success":success,"message":message,"data":data})),
    )
        .into_response()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn java_installation_group_uses_signed_utf16_hash() {
        assert_eq!(group(None), 4);
        assert_eq!(group(Some("negatives")), 2);
        assert_eq!(group(Some("fixture-😀-设备")), 4);
    }

    #[test]
    fn experiment_status_preserves_java_string_coercion() {
        assert_eq!(
            experiment_status(br#"{"status":1.10e3}"#)
                .unwrap()
                .as_deref(),
            Some("1.10e3")
        );
        assert_eq!(experiment_status(br#"{"status":null}"#).unwrap(), None);
        assert!(experiment_status(br#"{"status":[]}"#).is_err());
    }

    #[test]
    fn existing_configuration_is_upgraded_without_losing_values() {
        let data = tempfile::tempdir().unwrap();
        let path = config_path(data.path());
        assert!(experiment_active(data.path(), None, Experiment::SqliteVacuum).unwrap());
        assert!(!path.exists());
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, "").unwrap();
        initialize(&path).unwrap();
        assert_eq!(
            read_raw(&path).unwrap().unwrap()["config-version"].as_i64(),
            Some(1)
        );

        fs::write(
            &path,
            "enabled: false\nsqlite_vacuum: false\nother: [1, 2]\n",
        )
        .unwrap();
        let runtime = LabRuntime::open(data.path(), Some("g"), "en-US", false).unwrap();
        let config = read_raw(&path).unwrap().unwrap();
        assert!(enabled(&config));
        assert_eq!(config["sqlite_vacuum"], Yaml::Bool(false));
        assert_eq!(config["other"].as_sequence().unwrap().len(), 2);
        assert!(Experiment::Win32EmptyWorkingSet.active(&config, runtime.group));
        runtime.store("enabled", Yaml::Bool(false)).unwrap();
        let reopened = LabRuntime::open(data.path(), Some("g"), "en-US", false).unwrap();
        assert!(
            !experiment_active(data.path(), Some("g"), Experiment::Win32EmptyWorkingSet).unwrap()
        );
        let config = read_raw(&reopened.path).unwrap().unwrap();
        assert!(!enabled(&config));
        assert_eq!(config["sqlite_vacuum"], Yaml::Bool(false));
        assert_eq!(config["other"].as_sequence().unwrap().len(), 2);
    }

    #[tokio::test]
    async fn laboratory_routes_authenticate_translate_and_persist_on_loopback() {
        let data = tempfile::tempdir().unwrap();
        let runtime = LabRuntime::open(data.path(), Some("g"), "zh-TW", false).unwrap();
        let config = pbh_web::WebConfig {
            token: "laboratory-fixture".into(),
            manifest: pbh_web::Manifest {
                version: pbh_web::BuildVersion {
                    version: String::new(),
                    os: String::new(),
                    branch: String::new(),
                    commit: String::new(),
                    abbrev: String::new(),
                },
                modules: Vec::new(),
                installation_id: "g".into(),
                analytics: false,
            },
            static_dir: None,
            allow_cors: false,
        };
        let app = pbh_web::router_with_protected_routes(config, router(runtime.clone()));
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}/api/laboratory", listener.local_addr().unwrap());
        let server = tokio::spawn(pbh_web::serve(listener, app, std::future::pending()));
        let client = reqwest::Client::builder().no_proxy().build().unwrap();
        assert_eq!(
            client
                .get(format!("{url}/experiments"))
                .send()
                .await
                .unwrap()
                .status(),
            StatusCode::FORBIDDEN
        );
        let request = |method: reqwest::Method, path: &str| {
            client
                .request(method, format!("{url}/{path}"))
                .bearer_auth("laboratory-fixture")
        };

        let response = request(reqwest::Method::GET, "config")
            .send()
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK);
        let body: Value = response.json().await.unwrap();
        assert_eq!(
            body,
            json!({"success":true,"message":null,"data":{"enabled":true}})
        );
        let list: Value = request(reqwest::Method::GET, "experiments")
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(list["data"]["mygroup"], 3);
        assert_eq!(
            list["data"]["experiments"][1]["targetGroups"],
            json!([1, 3])
        );
        assert_eq!(
            list["data"]["experiments"][1]["title"],
            "Win32 平台：清空工作集"
        );
        let english: Value = request(reqwest::Method::GET, "experiments")
            .header("accept-language", "en-US")
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(
            english["data"]["experiments"][1]["title"],
            "Win32 Platform: Empty Working Set"
        );

        let experiment = "experiment/win32_empty_working_set";
        let toggle: Value = request(reqwest::Method::PUT, experiment)
            .json(&json!({"status":false}))
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(
            toggle,
            json!({"success":true,"message":null,"data":"false"})
        );
        assert_eq!(
            read_raw(&runtime.path).unwrap().unwrap()["win32_empty_working_set"],
            Yaml::Bool(false)
        );
        let lookup = "isExperimentActivated?id=WIN32_EMPTY_WORKING_SET";
        let active: Value = request(reqwest::Method::GET, lookup)
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(active["data"], false);

        let reset: Value = request(reqwest::Method::PUT, experiment)
            .json(&json!({"status":"other"}))
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(reset["data"], "other");
        assert!(
            experiment_active(data.path(), Some("g"), Experiment::Win32EmptyWorkingSet).unwrap()
        );
        let config: Value = request(reqwest::Method::POST, "config")
            .body(r#"{"enabled":false}"#)
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(config["data"], false);
        assert!(
            !experiment_active(data.path(), Some("g"), Experiment::Win32EmptyWorkingSet).unwrap()
        );

        let invalid = request(reqwest::Method::POST, "config")
            .header("accept-language", "en-US")
            .body(r#"{"enabled":123}"#)
            .send()
            .await
            .unwrap();
        assert_eq!(invalid.status(), StatusCode::INTERNAL_SERVER_ERROR);
        assert_eq!(
            invalid.json::<Value>().await.unwrap()["message"],
            "An internal server error occurred while processing the WebAPI request, please check the console logs"
        );

        let unknown: Value = request(reqwest::Method::GET, "isExperimentActivated?id=unknown")
            .send()
            .await
            .unwrap()
            .error_for_status()
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(
            unknown,
            json!({"success":false,"message":"Experiment not found","data":null})
        );
        let unknown = request(reqwest::Method::PUT, "experiment/unknown")
            .json(&json!({"status":true}))
            .send()
            .await
            .unwrap();
        assert_eq!(unknown.status(), StatusCode::BAD_REQUEST);
        let unknown: Value = unknown.json().await.unwrap();
        assert_eq!(
            unknown["message"],
            "Invalid experiment id: unknown, it's not exists in Experiments registry"
        );
        assert_eq!(unknown["message"], unknown["data"]);
        server.abort();
    }
}
