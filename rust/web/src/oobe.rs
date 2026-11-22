//! First-run configuration routes modeled on PBHOOBEController.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use axum::Router;
use axum::body::Bytes;
use axum::extract::State;
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::post;
use pbh_aria2next::Aria2Next;
use pbh_biglybt::BiglyBt;
use pbh_bitcomet::BitComet;
use pbh_deluge::Deluge;
use pbh_qbittorrent::Qbittorrent;
use pbh_storage::{MySqlConfig, MySqlStore, PostgresConfig, PostgresStore, SqliteStore};
use pbh_transmission::Transmission;
use serde::Deserialize;
use serde_json::{Map, Value, json};
use serde_yaml_ng::{Mapping, Value as Yaml};
use tokio::sync::Mutex;

use crate::{WebState, config_file, messages, response};

pub struct SetupConfig {
    pub config_path: PathBuf,
}

#[derive(Clone)]
struct Setup {
    web: WebState,
    config_path: Arc<PathBuf>,
    commit: Arc<Mutex<()>>,
}

#[derive(Deserialize)]
struct InitRequest {
    token: String,
    downloader: DownloaderDraft,
    database: Option<DatabaseDraft>,
    btn: Option<BtnDraft>,
}

#[derive(Deserialize)]
struct DownloaderDraft {
    id: String,
    config: Map<String, Value>,
}

#[derive(Deserialize)]
struct DatabaseDraft {
    #[serde(rename = "type")]
    kind: String,
    host: Option<String>,
    port: Option<u16>,
    database: Option<String>,
    username: Option<String>,
    password: Option<String>,
}

#[derive(Deserialize, Default)]
struct BtnDraft {
    enabled: Option<bool>,
    submit: Option<bool>,
    app_id: Option<String>,
    app_secret: Option<String>,
}

struct Error(StatusCode, &'static str);

impl Error {
    fn bad_request(message: &'static str) -> Self {
        Self(StatusCode::BAD_REQUEST, message)
    }

    fn configuration() -> Self {
        Self(
            StatusCode::INTERNAL_SERVER_ERROR,
            "Unable to save PeerBanHelper configuration",
        )
    }
}

impl IntoResponse for Error {
    fn into_response(self) -> Response {
        response(self.0, false, Some(self.1), Value::Null)
    }
}

pub(crate) fn routes(config: SetupConfig, web: WebState) -> Router {
    let state = Setup {
        web,
        config_path: Arc::new(config.config_path),
        commit: Arc::new(Mutex::new(())),
    };
    Router::new()
        .route("/api/oobe/init", post(init))
        .route("/api/oobe/testDownloader", post(test_downloader))
        .route("/api/oobe/testDatabaseConfig", post(test_database))
        .route("/api/oobe/scanDownloader", post(scan_downloader))
        .with_state(state)
}

impl Setup {
    fn require_uninitialized(&self, headers: &HeaderMap) -> Result<(), Error> {
        if self.web.token().trim().is_empty() {
            Ok(())
        } else {
            Err(Error(
                StatusCode::FORBIDDEN,
                messages::preferred(headers).already_initialized,
            ))
        }
    }
}

async fn init(
    State(state): State<Setup>,
    headers: HeaderMap,
    body: Bytes,
) -> Result<Response, Error> {
    let guard = state.commit.clone().lock_owned().await;
    state.require_uninitialized(&headers)?;
    let request: InitRequest = parse(&body)?;
    if request.token.trim().is_empty() {
        return Err(Error::bad_request("A nonblank WebUI token is required"));
    }
    if let Some(database) = &request.database {
        validate_database(database).await?;
    }
    let settings = request.downloader.validated(&state.config_path).await?;
    let already_initialized = messages::preferred(&headers).already_initialized;
    tokio::task::spawn_blocking(move || {
        // Retain the commit guard through storage and token activation even if
        // the HTTP client disconnects before receiving the response.
        let _guard = guard;
        let token = save(&state.config_path, request, settings, already_initialized)?;
        state
            .web
            .initialized_token
            .set(token)
            .map_err(|_| Error::configuration())
    })
    .await
    .map_err(|_| Error::configuration())??;
    Ok(response(
        StatusCode::CREATED,
        true,
        Some(messages::preferred(&headers).downloader_created),
        Value::Null,
    ))
}

async fn test_downloader(
    State(state): State<Setup>,
    headers: HeaderMap,
    body: Bytes,
) -> Result<Response, Error> {
    state.require_uninitialized(&headers)?;
    let downloader: DownloaderDraft = parse(&body)?;
    downloader.validated(&state.config_path).await?;
    Ok(response(
        StatusCode::OK,
        true,
        Some(messages::preferred(&headers).downloader_valid),
        Value::Null,
    ))
}

async fn test_database(
    State(state): State<Setup>,
    headers: HeaderMap,
    body: Bytes,
) -> Result<Response, Error> {
    state.require_uninitialized(&headers)?;
    let database: DatabaseDraft = parse(&body)?;
    validate_database(&database).await?;
    Ok(response(StatusCode::OK, true, None, json!(true)))
}

async fn scan_downloader(
    State(state): State<Setup>,
    headers: HeaderMap,
) -> Result<Response, Error> {
    state.require_uninitialized(&headers)?;
    let discovered = pbh_network::discovery::discover(Vec::new()).await;
    Ok(response(StatusCode::OK, true, None, json!(discovered)))
}

fn parse<T: serde::de::DeserializeOwned>(bytes: &[u8]) -> Result<T, Error> {
    serde_json::from_slice(bytes).map_err(|_| Error::bad_request("Invalid first-run configuration"))
}

async fn validate_database(database: &DatabaseDraft) -> Result<(), Error> {
    let timeout = Duration::from_secs(5);
    let result = match database.kind.as_str() {
        "sqlite" => tokio::task::spawn_blocking(|| SqliteStore::open(":memory:").map(|_| ()))
            .await
            .map_err(|_| Error::configuration())?,
        "postgresql" => {
            let mut config = PostgresConfig::new();
            config
                .host(database.host.as_deref().unwrap_or("localhost"))
                .port(database.port.unwrap_or(5432))
                .user(database.username.as_deref().unwrap_or("peerbanhelper"))
                .password(database.password.as_deref().unwrap_or_default())
                .dbname(database.database.as_deref().unwrap_or("peerbanhelper"))
                .connect_timeout(timeout);
            tokio::time::timeout(timeout, PostgresStore::test_connection(config))
                .await
                .map_err(|_| Error::bad_request("Database connection timed out"))?
        }
        "mysql" | "mariadb" => {
            let config = MySqlConfig::default()
                .ip_or_hostname(database.host.as_deref().unwrap_or("localhost"))
                .tcp_port(database.port.unwrap_or(3306))
                .prefer_socket(false)
                .user(database.username.as_deref())
                .pass(database.password.as_deref())
                .db_name(database.database.as_deref());
            tokio::time::timeout(timeout, MySqlStore::test_connection(config))
                .await
                .map_err(|_| Error::bad_request("Database connection timed out"))?
        }
        _ => return Err(Error::bad_request("Unsupported database type")),
    };
    result.map_err(|_| Error::bad_request("Unable to connect to the database"))
}

/// Translates downloader fields submitted by the WebUI into the persisted YAML shape.
pub fn downloader_settings_from_json(
    config: &Map<String, Value>,
) -> Result<Yaml, serde_yaml_ng::Error> {
    let mut settings = Mapping::new();
    for (ui, yaml) in [
        ("type", "type"),
        ("name", "name"),
        ("endpoint", "endpoint"),
        ("username", "username"),
        ("password", "password"),
        ("token", "token"),
        ("apiKey", "api-key"),
        ("basicAuth", "basic-auth"),
        ("incrementBan", "increment-ban"),
        ("useShadowBan", "use-shadow-ban"),
        ("verifySsl", "verify-ssl"),
        ("ignorePrivate", "ignore-private"),
        ("paused", "paused"),
        ("rpcUrl", "rpc-url"),
        ("httpVersion", "http-version"),
    ] {
        if let Some(value) = config.get(ui).filter(|value| !value.is_null()) {
            settings.insert(yaml.into(), serde_yaml_ng::to_value(value)?);
        }
    }
    Ok(Yaml::Mapping(settings))
}

impl DownloaderDraft {
    async fn validated(&self, path: &Path) -> Result<Yaml, Error> {
        if self.id.trim().is_empty() {
            return Err(Error::bad_request("A downloader ID is required"));
        }
        let settings = downloader_settings_from_json(&self.config)
            .map_err(|_| Error::bad_request("Invalid downloader configuration"))?;
        match settings["type"].as_str() {
            Some(kind @ ("qbittorrent" | "qbittorrentee")) => {
                let config = decode_downloader(&settings)?;
                let client = if kind == "qbittorrentee" {
                    Qbittorrent::enhanced_read_only(config)
                } else {
                    Qbittorrent::read_only(config)
                }
                .map_err(|_| Error::bad_request("Invalid qBittorrent configuration"))?;
                client.login().await.map_err(|_| {
                    Error(StatusCode::OK, "Unable to authenticate with qBittorrent")
                })?;
            }
            Some("transmission") => {
                let config = decode_downloader(&settings)?;
                let prefix = server_prefix(path.to_owned()).await?;
                let client = Transmission::read_only(config, &prefix)
                    .map_err(|_| Error::bad_request("Invalid Transmission configuration"))?;
                client.login().await.map_err(|_| {
                    Error(StatusCode::OK, "Unable to authenticate with Transmission")
                })?;
            }
            Some("deluge" | "raccoonfink/deluge") => {
                let client = Deluge::read_only(decode_downloader(&settings)?)
                    .map_err(|_| Error::bad_request("Invalid Deluge configuration"))?;
                client
                    .login()
                    .await
                    .map_err(|_| Error(StatusCode::OK, "Unable to authenticate with Deluge"))?;
            }
            Some("aria2next") => {
                let client = Aria2Next::read_only(decode_downloader(&settings)?)
                    .map_err(|_| Error::bad_request("Invalid Aria2Next configuration"))?;
                client
                    .login()
                    .await
                    .map_err(|_| Error(StatusCode::OK, "Unable to authenticate with Aria2Next"))?;
            }
            Some("biglybt") => {
                let client = BiglyBt::read_only(decode_downloader(&settings)?)
                    .map_err(|_| Error::bad_request("Invalid BiglyBT configuration"))?;
                client
                    .login()
                    .await
                    .map_err(|_| Error(StatusCode::OK, "Unable to authenticate with BiglyBT"))?;
            }
            Some("bitcomet") => {
                let client = BitComet::read_only(decode_downloader(&settings)?)
                    .map_err(|_| Error::bad_request("Invalid BitComet configuration"))?;
                client
                    .login()
                    .await
                    .map_err(|_| Error(StatusCode::OK, "Unable to authenticate with BitComet"))?;
            }
            _ => {
                return Err(Error::bad_request(
                    "This downloader type cannot be initialized in this preview",
                ));
            }
        }
        Ok(settings)
    }
}

fn decode_downloader<T: serde::de::DeserializeOwned>(settings: &Yaml) -> Result<T, Error> {
    serde_yaml_ng::from_value(settings.clone())
        .map_err(|_| Error::bad_request("Invalid downloader configuration"))
}

async fn server_prefix(path: PathBuf) -> Result<String, Error> {
    tokio::task::spawn_blocking(move || {
        let config = load_config(&path)?;
        let server = config.get("server").and_then(Yaml::as_mapping);
        let prefix = server
            .and_then(|server| server.get("prefix"))
            .and_then(Yaml::as_str);
        let port = server
            .and_then(|server| server.get("http"))
            .and_then(Yaml::as_u64)
            .unwrap_or(9898);
        Ok(prefix.map_or_else(|| format!("http://127.0.0.1:{port}"), str::to_owned))
    })
    .await
    .map_err(|_| Error::configuration())?
}

fn save(
    path: &Path,
    request: InitRequest,
    downloader: Yaml,
    already_initialized: &'static str,
) -> Result<String, Error> {
    config_file::edit(path, |config| {
        update_config(config, request, downloader, already_initialized)
    })
    .map_err(|error| match error {
        config_file::EditError::Rejected(error) => error,
        _ => Error::configuration(),
    })
}

fn update_config(
    config: &mut Mapping,
    request: InitRequest,
    downloader: Yaml,
    already_initialized: &'static str,
) -> Result<String, Error> {
    let server = mapping(config, "server")?;
    if server
        .get("token")
        .and_then(Yaml::as_str)
        .is_some_and(|token| !token.trim().is_empty())
    {
        return Err(Error(StatusCode::FORBIDDEN, already_initialized));
    }
    server.insert("token".into(), request.token.clone().into());
    let clients = mapping(config, "client")?;
    if clients.contains_key(&request.downloader.id) {
        return Err(Error::bad_request("A downloader already uses this ID"));
    }
    clients.insert(request.downloader.id.into(), downloader);
    if let Some(database) = request.database {
        let section = mapping(config, "database")?;
        for (key, value) in [
            ("type", Some(Yaml::from(database.kind))),
            ("host", database.host.map(Yaml::from)),
            ("port", database.port.map(Yaml::from)),
            ("database", database.database.map(Yaml::from)),
            ("username", database.username.map(Yaml::from)),
            ("password", database.password.map(Yaml::from)),
        ] {
            if let Some(value) = value {
                section.insert(key.into(), value);
            }
        }
    }
    if let Some(btn) = request.btn {
        let section = mapping(config, "btn")?;
        for (key, value) in [
            ("enabled", btn.enabled.map(Yaml::from)),
            ("submit", btn.submit.map(Yaml::from)),
            ("app-id", btn.app_id.map(Yaml::from)),
            ("app-secret", btn.app_secret.map(Yaml::from)),
        ] {
            if let Some(value) = value {
                section.insert(key.into(), value);
            }
        }
    }
    Ok(request.token)
}

fn mapping<'a>(parent: &'a mut Mapping, name: &str) -> Result<&'a mut Mapping, Error> {
    let value = parent
        .entry(name.into())
        .or_insert_with(|| Yaml::Mapping(Mapping::new()));
    value.as_mapping_mut().ok_or_else(Error::configuration)
}

fn load_config(path: &Path) -> Result<Mapping, Error> {
    let contents = fs::read_to_string(path).map_err(|_| Error::configuration())?;
    serde_yaml_ng::from_str(&contents).map_err(|_| Error::configuration())
}
