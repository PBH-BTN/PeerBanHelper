//! Runtime information, global pause, and settings required by the existing WebUI.

use std::net::SocketAddr;
use std::path::{Path as FilePath, PathBuf};
use std::sync::{Arc, OnceLock};
use std::time::Instant;

use anyhow::{Context, Result, bail};
use axum::extract::{ConnectInfo, DefaultBodyLimit, Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::{
    Json, Router,
    routing::{get, post},
};
use serde::Deserialize;
use serde_json::{Map, Value, json};
use serde_yaml_ng::{Mapping, Value as Yaml};
use sysinfo::System;

use crate::ban_api::BanLanguage;
use crate::config::{DatabaseConfig, MainConfig, Profile, read_yaml};
use crate::web::ScanState;

#[derive(Clone)]
struct General {
    scan: ScanState,
    data_dir: Arc<PathBuf>,
    analytics: bool,
    database: DatabaseConfig,
    listener: (String, u16),
    use_proxy: bool,
    started: Instant,
}

pub(crate) fn router(scan: ScanState, data_dir: &FilePath, config: &MainConfig) -> Router {
    Router::new()
        .route("/api/general/global", get(global).patch(update_global))
        .route("/api/general/status", get(status))
        .route("/api/general/stacktrace", get(stacktrace))
        .route("/api/general/checkModuleAvailable", get(module_available))
        .route("/api/general/reload", post(reload))
        .route(
            "/api/general/{config}",
            get(configuration).put(save_configuration),
        )
        .layer(DefaultBodyLimit::max(1024 * 1024))
        .with_state(General {
            scan,
            data_dir: Arc::new(data_dir.to_owned()),
            analytics: config.privacy.analytics,
            database: config.database.clone(),
            listener: (config.server.address.clone(), config.server.http),
            use_proxy: config.proxy_policy().is_ok_and(|proxy| proxy.enabled()),
            started: *STARTED.get_or_init(Instant::now),
        })
}

static STARTED: OnceLock<Instant> = OnceLock::new();

async fn global(State(state): State<General>) -> Json<Value> {
    success(json!({"globalPaused": *state.scan.paused.borrow(), "analytics": state.analytics}))
}

async fn stacktrace(headers: HeaderMap) -> Response {
    let trace = std::backtrace::Backtrace::force_capture().to_string();
    if headers
        .get(axum::http::header::CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        == Some("application/json")
    {
        success(json!(trace)).into_response()
    } else {
        trace.into_response()
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct GlobalPatch {
    global_paused: Option<bool>,
}

async fn update_global(
    State(state): State<General>,
    headers: HeaderMap,
    Json(patch): Json<GlobalPatch>,
) -> Response {
    if state.scan.demo_mode {
        return error(
            StatusCode::BAD_REQUEST,
            &headers,
            "DEMO_MODE_OPERATION_NOT_PERMITTED",
        );
    }
    if let Some(paused) = patch.global_paused {
        state.scan.paused.send_replace(paused);
    }
    Json(json!({"success": true, "message": "OK!", "data": null})).into_response()
}

#[derive(Deserialize)]
struct ModuleQuery {
    module: String,
}

async fn module_available(
    State(state): State<General>,
    Query(query): Query<ModuleQuery>,
) -> Json<Value> {
    success(json!(state.scan.modules.contains(&query.module)))
}

async fn status(
    State(state): State<General>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    headers: HeaderMap,
) -> Response {
    let memory = tokio::task::spawn_blocking(|| {
        let mut system = System::new().ok()?;
        system.refresh_memory();
        Some((
            system.total_memory(),
            system.available_memory(),
            System::load_average()
                .ok()
                .map(|load| load.one)
                .unwrap_or(-1.0),
            System::os_version().unwrap_or_default(),
        ))
    })
    .await;
    let Ok(Some((total, free, load, version))) = memory else {
        return error(
            StatusCode::INTERNAL_SERVER_ERROR,
            &headers,
            "WEBAPI_INTERNAL_ERROR",
        );
    };
    let os = match std::env::consts::OS {
        "linux" => "Linux",
        "windows" => "Windows",
        "macos" => "Mac OS X",
        "freebsd" => "FreeBSD",
        "solaris" => "Solaris",
        _ => "Other",
    };
    let toolchain = include_str!("../../../rust-toolchain.toml")
        .lines()
        .find_map(|line| line.strip_prefix("channel = "))
        .map(|version| version.trim_matches('"'))
        .unwrap_or("");
    let proxy = [
        "x-real-ip",
        "x-forwarded-for",
        "proxy-client-ip",
        "wl-proxy-client-ip",
    ]
    .iter()
    .any(|header| headers.contains_key(*header));
    success(json!({
        "native": {"runtime": "Rust", "version": toolchain, "bitness": usize::BITS},
        "system": {
            "os": os, "version": version, "architecture": std::env::consts::ARCH,
            "cores": std::thread::available_parallelism().map_or(1, usize::from),
            "memory": {"total": total, "free": free},
            "load": load,
            "network": {
                "internet_access": *state.scan.reachability.borrow(),
                "nat_type": state.scan.auto_stun.nat_type(),
                "use_proxy": state.use_proxy,
                "reverse_proxy": proxy,
                "client_ip": peer.ip(),
            },
        },
        "peerbanhelper": {
            "version": env!("CARGO_PKG_VERSION"),
            "commit_id": option_env!("PBH_BUILD_GIT_COMMIT").unwrap_or_default(),
            "compile_time": option_env!("PBH_BUILD_UNIX_TIME").and_then(|value| value.parse::<i64>().ok()),
            "release": std::env::var("PBH_RELEASE").unwrap_or_else(|_| "native".to_owned()),
            "uptime": state.started.elapsed().as_secs(),
            "data_dir": if state.scan.demo_mode { None } else { Some(state.data_dir.as_path()) },
            "database_type": state.database.kind,
        },
    })).into_response()
}

async fn configuration(
    State(state): State<General>,
    headers: HeaderMap,
    Path(config): Path<String>,
) -> Response {
    if !matches!(config.as_str(), "config" | "profile") {
        return StatusCode::NOT_FOUND.into_response();
    }
    let path = state.data_dir.join("config").join(format!("{config}.yml"));
    let Ok(Ok(mut document)) =
        tokio::task::spawn_blocking(move || crate::config::read_yaml::<Value>(&path)).await
    else {
        return error(
            StatusCode::INTERNAL_SERVER_ERROR,
            &headers,
            "WEBAPI_INTERNAL_ERROR",
        );
    };
    if config == "config" {
        if state.scan.demo_mode {
            for field in [
                "client",
                "proxy",
                "database",
                "push-notification",
                "ip-database",
                "pbh-plus-key",
                "installation-id",
            ] {
                document[field] = Value::Null;
            }
            document["server"]["token"] = json!("REDACTED_IN_DEMO_MODE");
            document["btn"]["app-id"] = json!("REDACTED_IN_DEMO_MODE");
            document["btn"]["app-secret"] = json!("REDACTED_IN_DEMO_MODE");
        }
        let pushes = document["push-notification"]
            .as_object()
            .map(|providers| {
                providers
                    .iter()
                    .filter_map(|(name, configuration)| {
                        let mut item = configuration.as_object()?.clone();
                        item.insert("push-notification-name".into(), json!(name));
                        Some(Value::Object(item))
                    })
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        document["push-notification"] = json!(pushes);
        if let Some(btn) = document["btn"].as_object_mut() {
            btn.remove("allow-script-execute");
        }
    } else {
        if let Some(module) = document["module"].as_object_mut() {
            module.remove("expression-engine");
        }
        for (module, rule) in [
            ("peer-id-blacklist", "banned-peer-id"),
            ("client-name-blacklist", "banned-client-name"),
            ("ptr-blacklist", "ptr-rules"),
        ] {
            if let Some(rules) = document["module"][module][rule].as_array_mut() {
                for rule in rules {
                    if let Some(encoded) = rule.as_str()
                        && let Ok(Value::Object(parsed)) = serde_json::from_str(encoded)
                    {
                        *rule = Value::Object(parsed);
                    }
                }
            }
        }
    }
    success(underscores(document)).into_response()
}

async fn save_configuration(
    State(state): State<General>,
    headers: HeaderMap,
    Path(config): Path<String>,
    Json(patch): Json<Map<String, Value>>,
) -> Response {
    if state.scan.demo_mode {
        return error(
            StatusCode::BAD_REQUEST,
            &headers,
            "DEMO_MODE_OPERATION_NOT_PERMITTED",
        );
    }
    if !matches!(config.as_str(), "config" | "profile") {
        return StatusCode::NOT_FOUND.into_response();
    }
    let path = state.data_dir.join("config").join(format!("{config}.yml"));
    let edited = tokio::task::spawn_blocking(move || {
        pbh_web::config_file::edit(&path, |document| -> Result<()> {
            merge_yaml(document, patch, &mut Vec::new())?;
            let merged = Yaml::Mapping(document.clone());
            if config == "config" {
                let main: MainConfig = serde_yaml_ng::from_value(merged)?;
                validate_main(&main)?;
            } else {
                let profile: Profile = serde_yaml_ng::from_value(merged)?;
                profile.policies(true)?;
            }
            Ok(())
        })
    })
    .await;
    match edited {
        Ok(Ok(())) => reload_response(&state, &headers, StatusCode::CREATED).await,
        Ok(Err(error)) => {
            crate::journal::log_warning!("Settings edit rejected: {error}");
            self::error(StatusCode::BAD_REQUEST, &headers, "RELOAD_RESULT_FAILED")
        }
        Err(error) => {
            crate::journal::log_warning!("Settings edit task: {error}");
            self::error(
                StatusCode::INTERNAL_SERVER_ERROR,
                &headers,
                "WEBAPI_INTERNAL_ERROR",
            )
        }
    }
}

async fn reload(State(state): State<General>, headers: HeaderMap) -> Response {
    if state.scan.demo_mode {
        return error(
            StatusCode::BAD_REQUEST,
            &headers,
            "DEMO_MODE_OPERATION_NOT_PERMITTED",
        );
    }
    reload_response(&state, &headers, StatusCode::OK).await
}

async fn reload_response(state: &General, headers: &HeaderMap, status: StatusCode) -> Response {
    let data_dir = state.data_dir.clone();
    let configuration = tokio::task::spawn_blocking(move || -> Result<MainConfig> {
        let main = read_yaml(&data_dir.join("config/config.yml"))?;
        let profile: Profile = read_yaml(&data_dir.join("config/profile.yml"))?;
        validate_main(&main)?;
        profile.policies(true)?;
        Ok(main)
    })
    .await;
    let config = match configuration {
        Ok(Ok(config)) => config,
        Ok(Err(error)) => {
            crate::journal::log_warning!("Settings reload rejected: {error}");
            return self::error(StatusCode::BAD_REQUEST, headers, "RELOAD_RESULT_FAILED");
        }
        Err(error) => {
            crate::journal::log_warning!("Settings validation task: {error}");
            return self::error(
                StatusCode::INTERNAL_SERVER_ERROR,
                headers,
                "WEBAPI_INTERNAL_ERROR",
            );
        }
    };
    let mut entries = vec![json!({"reloadable": "PeerBanHelper", "reloadResult": "SCHEDULED"})];
    if config.database != state.database {
        entries.push(json!({"reloadable": "Database", "reloadResult": "REQUIRE_RESTART"}));
    }
    if config.server.address != state.listener.0 || config.server.http != state.listener.1 {
        entries.push(json!({"reloadable": "HTTP listener", "reloadResult": "REQUIRE_RESTART"}));
    }
    let key = if entries.len() == 1 {
        "RELOAD_RESULT_SCHEDULED"
    } else {
        "RELOAD_RESULT_REQUIRE_RESTART"
    };
    let message = BanLanguage::from_headers(headers).render(&json!({"key": key}));
    state.scan.reload.send_replace(true);
    (
        status,
        Json(json!({"success": true, "message": message, "data": entries})),
    )
        .into_response()
}

fn validate_main(config: &MainConfig) -> Result<()> {
    if config.server.token.trim().is_empty() {
        bail!("server token cannot be empty");
    }
    if !matches!(
        config.database.kind.as_str(),
        "sqlite" | "postgresql" | "mysql" | "mariadb"
    ) {
        bail!("unsupported database type");
    }
    config.address_policy()?;
    let proxy = config.proxy_policy()?;
    if config.btn.enabled {
        pbh_btn::BtnClient::with_proxy(
            config.btn.clone(),
            config
                .installation_id
                .as_deref()
                .unwrap_or("not-initialized"),
            &proxy,
        )?;
    }
    Ok(())
}

fn merge_yaml(
    document: &mut Mapping,
    patch: Map<String, Value>,
    path: &mut Vec<String>,
) -> Result<()> {
    for (input, value) in patch {
        let raw = Yaml::String(input.clone());
        let name = if document.contains_key(&raw)
            || matches!(path.as_slice(), [parent] if parent == "client")
        {
            input
        } else {
            input.replace('_', "-")
        };
        let key = Yaml::String(name.clone());
        if path.is_empty() && name == "push-notification" {
            document.insert(key, yaml_pushes(value)?);
        } else if matches!(path.as_slice(), [module, feature] if module == "module" && matches!(
            (feature.as_str(), name.as_str()),
            ("peer-id-blacklist", "banned-peer-id") | ("client-name-blacklist", "banned-client-name") | ("ptr-blacklist", "ptr-rules")
        )) {
            let rules = value
                .as_array()
                .context("expected string rule list")?
                .iter()
                .map(|rule| match rule {
                    Value::Object(_) => Ok(Yaml::String(serde_json::to_string(rule)?)),
                    Value::String(rule) => Ok(Yaml::String(rule.clone())),
                    _ => bail!("expected a string rule"),
                })
                .collect::<Result<_>>()?;
            document.insert(key, Yaml::Sequence(rules));
        } else if let Value::Object(fields) = value {
            let entry = document
                .entry(key)
                .or_insert_with(|| Yaml::Mapping(Mapping::new()));
            if !entry.is_mapping() {
                *entry = Yaml::Mapping(Mapping::new());
            }
            path.push(name);
            merge_yaml(entry.as_mapping_mut().unwrap(), fields, path)?;
            path.pop();
        } else if value.is_null() {
            document.remove(key);
        } else {
            document.insert(key, yaml_value(value)?);
        }
    }
    Ok(())
}

fn yaml_pushes(value: Value) -> Result<Yaml> {
    let Value::Array(items) = value else {
        bail!("expected push provider list");
    };
    let mut result = Mapping::new();
    for item in items {
        let Value::Object(mut fields) = item else {
            bail!("expected push provider object");
        };
        let name = fields
            .remove("push_notification_name")
            .or_else(|| fields.remove("push-notification-name"));
        let Some(Value::String(name)) = name else {
            bail!("push provider requires a name");
        };
        if result
            .insert(Yaml::String(name), yaml_value(Value::Object(fields))?)
            .is_some()
        {
            bail!("duplicate push provider name");
        }
    }
    Ok(Yaml::Mapping(result))
}

fn yaml_value(value: Value) -> Result<Yaml> {
    match value {
        Value::Object(fields) => fields
            .into_iter()
            .map(|(key, value)| Ok((Yaml::String(key.replace('_', "-")), yaml_value(value)?)))
            .collect::<Result<Mapping>>()
            .map(Yaml::Mapping),
        Value::Array(values) => values
            .into_iter()
            .map(yaml_value)
            .collect::<Result<_>>()
            .map(Yaml::Sequence),
        value => Ok(serde_yaml_ng::to_value(value)?),
    }
}

fn underscores(value: Value) -> Value {
    match value {
        Value::Object(fields) => Value::Object(
            fields
                .into_iter()
                .map(|(key, value)| (key.replace('-', "_"), underscores(value)))
                .collect::<Map<_, _>>(),
        ),
        Value::Array(values) => Value::Array(values.into_iter().map(underscores).collect()),
        value => value,
    }
}

fn success(data: Value) -> Json<Value> {
    Json(json!({"success": true, "message": null, "data": data}))
}

fn error(status: StatusCode, headers: &HeaderMap, key: &str) -> Response {
    let message = BanLanguage::from_headers(headers).render(&json!({"key": key}));
    (
        status,
        Json(json!({"success": false, "message": message, "data": null})),
    )
        .into_response()
}
