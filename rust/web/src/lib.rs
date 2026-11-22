//! Native HTTP routes for the existing PeerBanHelper WebUI.

mod auth;
pub mod config_file;
mod messages;
mod oobe;

use std::future::Future;
use std::io;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::{Arc, OnceLock};

use axum::extract::{DefaultBodyLimit, Request, State};
use axum::http::{Method, StatusCode, Uri};
use axum::response::{IntoResponse, Json, Response};
use axum::routing::{get, post};
use axum::{Router, middleware};
use serde::Serialize;
use serde_json::{Value, json};
use tokio::net::TcpListener;
use tower::ServiceExt;
use tower_http::cors::CorsLayer;
use tower_http::services::{ServeDir, ServeFile};
use tower_sessions::{Expiry, MemoryStore, SessionManagerLayer, cookie::SameSite};

pub use oobe::{SetupConfig, downloader_settings_from_json};

pub struct WebConfig {
    pub token: String,
    pub manifest: Manifest,
    pub static_dir: Option<PathBuf>,
    pub allow_cors: bool,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Manifest {
    pub version: BuildVersion,
    pub modules: Vec<Module>,
    pub installation_id: String,
    pub analytics: bool,
}

#[derive(Clone, Debug, Serialize)]
pub struct BuildVersion {
    pub version: String,
    pub os: String,
    pub branch: String,
    pub commit: String,
    pub abbrev: String,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Module {
    pub class_name: String,
    pub config_name: String,
}

#[derive(Clone)]
struct WebState {
    config: Arc<WebConfig>,
    authentication: auth::Authentication,
    initialized_token: Arc<OnceLock<String>>,
}

impl WebState {
    fn token(&self) -> &str {
        self.initialized_token.get().unwrap_or(&self.config.token)
    }
}

pub fn router(config: WebConfig) -> Router {
    router_with_routes(config, Router::new(), Router::new())
}

/// The caller supplies only implemented, token-protected routes.
pub fn router_with_protected_routes(config: WebConfig, protected_routes: Router) -> Router {
    router_with_routes(config, Router::new(), protected_routes)
}

/// Public routes are appropriate for tokens and integration endpoints that authorize separately.
pub fn router_with_routes(config: WebConfig, public: Router, protected: Router) -> Router {
    router_with_sessions(config, public, protected, None, WebSessions::default())
}

pub fn router_with_setup(
    config: WebConfig,
    public: Router,
    protected: Router,
    setup: SetupConfig,
) -> Router {
    router_with_sessions(
        config,
        public,
        protected,
        Some(setup),
        WebSessions::default(),
    )
}

/// Authentication sessions and rate limits shared by successive HTTP routers.
#[derive(Clone, Default)]
pub struct WebSessions {
    memory: MemoryStore,
    authentication: auth::Authentication,
}

pub fn router_with_sessions(
    config: WebConfig,
    public: Router,
    protected: Router,
    setup: Option<SetupConfig>,
    sessions: WebSessions,
) -> Router {
    let state = WebState {
        authentication: sessions.authentication,
        config: Arc::new(config),
        initialized_token: Arc::new(OnceLock::new()),
    };
    let session = SessionManagerLayer::new(sessions.memory)
        .with_name("JSESSIONID")
        .with_same_site(SameSite::Lax)
        .with_secure(false)
        .with_expiry(Expiry::OnSessionEnd);

    let mut api = Router::new()
        .route("/api/metadata/manifest", get(manifest))
        .route("/api/auth/login", post(auth::login))
        .route("/api/auth/logout", post(auth::logout))
        .method_not_allowed_fallback(api_unavailable)
        .with_state(state.clone());
    api = api.merge(public);
    if protected.has_routes() {
        api = api.merge(protected.route_layer(middleware::from_fn_with_state(
            state.clone(),
            auth::authorize,
        )));
    }
    if let Some(setup) = setup.filter(|_| state.token().trim().is_empty()) {
        api = api.merge(oobe::routes(setup, state.clone()));
    }
    let api = api
        .fallback_service(StaticFiles::new(state.config.static_dir.clone()).router())
        .layer(session)
        .layer(DefaultBodyLimit::max(16 * 1024));
    if state.config.allow_cors {
        api.layer(CorsLayer::permissive())
    } else {
        api
    }
}

/// Run on an already bound listener, retaining the socket peer for authentication limits.
pub async fn serve(
    listener: TcpListener,
    app: Router,
    shutdown: impl Future<Output = ()> + Send + 'static,
) -> io::Result<()> {
    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<SocketAddr>(),
    )
    .with_graceful_shutdown(shutdown)
    .await
}

async fn manifest(State(state): State<WebState>) -> Response {
    response(StatusCode::OK, true, None, json!(state.config.manifest))
}

async fn api_unavailable(method: Method, uri: Uri, headers: axum::http::HeaderMap) -> Response {
    if method == Method::OPTIONS {
        return StatusCode::OK.into_response();
    }
    unavailable(&method, &uri, &headers)
}

fn unavailable(method: &Method, uri: &Uri, headers: &axum::http::HeaderMap) -> Response {
    let message = messages::preferred(headers)
        .route_unavailable
        .replacen("{}", method.as_str(), 1)
        .replacen("{}", uri.path(), 1);
    response(
        StatusCode::METHOD_NOT_ALLOWED,
        false,
        Some(&message),
        Value::Null,
    )
}

fn response(status: StatusCode, success: bool, message: Option<&str>, data: Value) -> Response {
    (
        status,
        Json(json!({"success": success, "message": message, "data": data})),
    )
        .into_response()
}

#[derive(Clone)]
struct StaticFiles {
    files: Option<ServeDir<ServeFile>>,
}

impl StaticFiles {
    fn new(directory: Option<PathBuf>) -> Self {
        let files = directory.map(|directory| {
            let index = ServeFile::new(directory.join("index.html"));
            ServeDir::new(directory).fallback(index)
        });
        Self { files }
    }

    fn router(self) -> Router {
        Router::new().fallback(Self::serve).with_state(self)
    }

    async fn serve(State(files): State<Self>, request: Request) -> Response {
        if request.method() == Method::OPTIONS {
            return StatusCode::OK.into_response();
        }
        if request.uri().path().starts_with("/api") {
            return unavailable(request.method(), request.uri(), request.headers());
        }
        match files.files {
            Some(files) => files.oneshot(request).await.into_response(),
            None => StatusCode::NOT_FOUND.into_response(),
        }
    }
}
