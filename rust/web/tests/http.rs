use std::net::SocketAddr;
use std::path::PathBuf;

use axum::Router;
use axum::body::{Body, to_bytes};
use axum::extract::connect_info::MockConnectInfo;
use axum::http::{Method, Request, StatusCode, header};
use axum::response::Response;
use axum::routing::get;
use pbh_web::{
    BuildVersion, Manifest, Module, SetupConfig, WebConfig, router, router_with_protected_routes,
    router_with_routes, router_with_setup,
};
use serde_json::{Value, json};
use tower::ServiceExt;

const TOKEN: &str = "fixture-token";

fn config(token: &str, static_dir: Option<PathBuf>) -> WebConfig {
    WebConfig {
        token: token.into(),
        static_dir,
        allow_cors: false,
        manifest: Manifest {
            version: BuildVersion {
                version: "9.6.0".into(),
                os: "Windows".into(),
                branch: "codex/rust".into(),
                commit: "25be37332d769a1d09d5f62a5376fb3ea1ad339e".into(),
                abbrev: "25be37332".into(),
            },
            modules: vec![Module {
                class_name: "com.ghostchu.peerbanhelper.module.impl.rule.IPBlackList".into(),
                config_name: "ip-address-blocker".into(),
            }],
            installation_id: "fixture-id".into(),
            analytics: false,
        },
    }
}

async fn app_request(app: &Router, method: Method, uri: &str, body: &str) -> Response {
    app.clone()
        .oneshot(
            Request::builder()
                .method(method)
                .uri(uri)
                .header(header::CONTENT_TYPE, "application/json")
                .body(Body::from(body.to_owned()))
                .unwrap(),
        )
        .await
        .unwrap()
}

async fn read_json(response: Response) -> Value {
    serde_json::from_slice(&to_bytes(response.into_body(), 4096).await.unwrap()).unwrap()
}

fn with_client(app: Router) -> Router {
    app.layer(MockConnectInfo(SocketAddr::from(([127, 0, 0, 1], 9999))))
}

#[tokio::test]
async fn manifest_remains_public_and_unknown_api_uses_the_envelope() {
    let app = router(config(TOKEN, None));
    let response = app_request(&app, Method::GET, "/api/metadata/manifest", "").await;
    assert_eq!(response.status(), StatusCode::OK);
    let body = read_json(response).await;
    assert_eq!(body["success"], true);
    assert_eq!(body["message"], Value::Null);
    assert_eq!(body["data"]["version"]["version"], "9.6.0");
    assert_eq!(body["data"]["installationId"], "fixture-id");
    assert_eq!(
        body["data"]["modules"][0]["configName"],
        "ip-address-blocker"
    );

    for (method, path) in [
        (Method::GET, "/api/missing"),
        (Method::PUT, "/api/auth/login"),
    ] {
        let response = app_request(&app, method, path, "").await;
        assert_eq!(response.status(), StatusCode::METHOD_NOT_ALLOWED);
        let body = read_json(response).await;
        assert_eq!(body["success"], false);
        assert_eq!(body["data"], Value::Null);
        assert!(body["message"].as_str().unwrap().ends_with(path));
    }
}

#[tokio::test]
async fn uninitialized_and_invalid_login_are_distinct() {
    let app = with_client(router(config("", None)));
    let response = app_request(&app, Method::POST, "/api/auth/login", "{}").await;
    assert_eq!(response.status(), StatusCode::SEE_OTHER);
    assert_eq!(response.headers()[header::LOCATION], "/init");
    assert_eq!(
        read_json(response).await["data"],
        json!({"location": "/init"})
    );

    let app = with_client(router(config(TOKEN, None)));
    let response = app_request(&app, Method::POST, "/api/auth/login", "null").await;
    assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    let response = app_request(
        &app,
        Method::POST,
        "/api/auth/login",
        r#"{"token":"wrong"}"#,
    )
    .await;
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    assert!(response.headers().get(header::SET_COOKIE).is_none());
}

#[tokio::test]
async fn cookie_bearer_and_query_tokens_apply_only_to_protected_routes() {
    let private = Router::new().route("/api/private", get(|| async { "protected" }));
    let app = with_client(router_with_protected_routes(config(TOKEN, None), private));
    let request = |uri: &str, auth: Option<(&str, &str)>| {
        let mut request = Request::builder().uri(uri);
        if let Some((name, value)) = auth {
            request = request.header(name, value);
        }
        app.clone().oneshot(request.body(Body::empty()).unwrap())
    };
    let denied = request("/api/private", None).await.unwrap();
    assert_eq!(denied.status(), StatusCode::FORBIDDEN);
    let bearer = request(
        "/api/private",
        Some(("Authorization", "Bearer fixture-token")),
    )
    .await
    .unwrap();
    assert_eq!(bearer.status(), StatusCode::OK);
    let denied = request(
        "/api/private?token=fixture-token",
        Some(("Authorization", "Basic unused")),
    )
    .await
    .unwrap();
    assert_eq!(denied.status(), StatusCode::FORBIDDEN);
    let query = request("/api/private?token=fixture-token", None)
        .await
        .unwrap();
    assert_eq!(query.status(), StatusCode::OK);

    let response = app_request(
        &app,
        Method::POST,
        "/api/auth/login",
        r#"{"token":"fixture-token"}"#,
    )
    .await;
    assert_eq!(response.status(), StatusCode::OK);
    let cookie = response.headers()[header::SET_COOKIE].to_str().unwrap();
    assert!(cookie.starts_with("JSESSIONID="));
    assert!(cookie.contains("HttpOnly"));
    assert!(!cookie.contains(TOKEN));
    let cookie = cookie.split(';').next().unwrap();
    assert_eq!(
        request("/api/private", Some(("Cookie", cookie)))
            .await
            .unwrap()
            .status(),
        StatusCode::OK
    );

    let logout = app
        .clone()
        .oneshot(
            Request::builder()
                .method(Method::POST)
                .uri("/api/auth/logout")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(
        read_json(logout).await,
        json!({"success":true,"message":"success","data":null})
    );
    assert_eq!(
        request("/api/private", Some(("Cookie", cookie)))
            .await
            .unwrap()
            .status(),
        StatusCode::FORBIDDEN
    );
}

#[tokio::test]
async fn fails_closed_after_the_same_login_failure_threshold() {
    let app = with_client(router(config(TOKEN, None)));
    for _ in 0..11 {
        let response = app_request(
            &app,
            Method::POST,
            "/api/auth/login",
            r#"{"token":"wrong"}"#,
        )
        .await;
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }
    let response = app_request(
        &app,
        Method::POST,
        "/api/auth/login",
        r#"{"token":"fixture-token"}"#,
    )
    .await;
    assert_eq!(response.status(), StatusCode::TOO_MANY_REQUESTS);
    assert!(response.headers().get(header::SET_COOKIE).is_none());
}

#[tokio::test]
async fn static_assets_and_spa_deep_links_use_the_configured_root() {
    let directory = tempfile::tempdir().unwrap();
    std::fs::write(
        directory.path().join("index.html"),
        "<html>application</html>",
    )
    .unwrap();
    std::fs::create_dir(directory.path().join("assets")).unwrap();
    std::fs::write(directory.path().join("assets/app.js"), "const app = 1;").unwrap();
    let app = router(config(TOKEN, Some(directory.path().into())));
    for path in ["/", "/settings/history"] {
        let response = app_request(&app, Method::GET, path, "").await;
        assert_eq!(response.status(), StatusCode::OK);
        assert_eq!(
            to_bytes(response.into_body(), 4096).await.unwrap(),
            "<html>application</html>"
        );
    }
    let asset = app_request(&app, Method::GET, "/assets/app.js", "").await;
    assert_eq!(asset.status(), StatusCode::OK);
    assert_eq!(
        to_bytes(asset.into_body(), 4096).await.unwrap(),
        "const app = 1;"
    );
    let api = app_request(&app, Method::GET, "/api/settings/history", "").await;
    assert_eq!(api.status(), StatusCode::METHOD_NOT_ALLOWED);
    assert_eq!(api.headers()[header::CONTENT_TYPE], "application/json");
}

#[tokio::test]
async fn allows_public_integration_routes_without_web_auth() {
    let public = Router::new().route("/blocklist/p2p-plain-format", get(|| async { "fixture" }));
    let app = router_with_routes(config("", None), public, Router::new());
    let response = app_request(&app, Method::GET, "/blocklist/p2p-plain-format", "").await;
    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(
        to_bytes(response.into_body(), 4096).await.unwrap(),
        "fixture"
    );
}

#[tokio::test]
async fn first_run_validates_and_publishes_configuration_once() {
    let directory = tempfile::tempdir().unwrap();
    let config_path = directory.path().join("config.yml");
    let original = "server:\n  token: ''\n  address: 127.0.0.1\nextra:\n  keep: true\ndatabase:\n  type: sqlite\n  port: 3306\n";
    std::fs::write(&config_path, original).unwrap();
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let endpoint = listener.local_addr().unwrap();
    let mock_qbittorrent = Router::new()
        .route(
            "/api/v2/app/buildInfo",
            get(|| async { axum::Json(json!({"libtorrent": "2.0.11"})) }),
        )
        .route("/api/v2/app/version", get(|| async { "v5.1.1" }))
        .route(
            "/fixture/rpc",
            get(|| async {
                (
                    StatusCode::CONFLICT,
                    [("x-transmission-session-id", "fixture")],
                )
            })
            .post(
                |headers: axum::http::HeaderMap, axum::Json(body): axum::Json<Value>| async move {
                    assert_eq!(headers["x-transmission-session-id"], "fixture");
                    assert_eq!(body["method"], "session-get");
                    axum::Json(json!({"result":"success","arguments":{
                        "version":"4.1.0","blocklist-enabled":false,"blocklist-url":"",
                    }}))
                },
            ),
        );
    let downloader =
        tokio::spawn(async move { axum::serve(listener, mock_qbittorrent).await.unwrap() });

    let app = with_client(router_with_setup(
        config("", None),
        Router::new(),
        Router::new(),
        SetupConfig {
            config_path: config_path.clone(),
        },
    ));
    let draft = json!({"id":"fixture-downloader","config":{
        "type":"qbittorrent","name":"fixture","endpoint":format!("http://{endpoint}"),
        "basicAuth":{"user":"","pass":""},"verifySsl":true,"incrementBan":true,
    }});
    let probe = app_request(
        &app,
        Method::POST,
        "/api/oobe/testDownloader",
        &draft.to_string(),
    )
    .await;
    assert_eq!(read_json(probe).await["success"], true);
    assert_eq!(std::fs::read_to_string(&config_path).unwrap(), original);

    let transmission = json!({"id":"transmission","config":{
        "type":"transmission","endpoint":format!("http://{endpoint}"),"rpcUrl":"/fixture/rpc",
    }});
    let probe = app_request(
        &app,
        Method::POST,
        "/api/oobe/testDownloader",
        &transmission.to_string(),
    )
    .await;
    assert_eq!(read_json(probe).await["success"], true);
    assert_eq!(std::fs::read_to_string(&config_path).unwrap(), original);

    let database_probe = app_request(
        &app,
        Method::POST,
        "/api/oobe/testDatabaseConfig",
        r#"{"type":"sqlite"}"#,
    )
    .await;
    assert_eq!(read_json(database_probe).await["data"], true);
    let remote =
        json!({"token":TOKEN,"downloader":draft,"database":{"type":"postgresql"}}).to_string();
    let response = app_request(&app, Method::POST, "/api/oobe/init", &remote).await;
    assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    assert_eq!(std::fs::read_to_string(&config_path).unwrap(), original);

    let setup = json!({"token":TOKEN,"downloader":draft,"database":{"type":"sqlite"}}).to_string();
    let (first, second) = tokio::join!(
        app_request(&app, Method::POST, "/api/oobe/init", &setup),
        app_request(&app, Method::POST, "/api/oobe/init", &setup),
    );
    let mut statuses = [first.status(), second.status()];
    statuses.sort();
    assert_eq!(statuses, [StatusCode::CREATED, StatusCode::FORBIDDEN]);
    let discovery = app_request(&app, Method::POST, "/api/oobe/scanDownloader", "").await;
    assert_eq!(discovery.status(), StatusCode::FORBIDDEN);
    let saved: serde_yaml_ng::Value =
        serde_yaml_ng::from_str(&std::fs::read_to_string(&config_path).unwrap()).unwrap();
    assert_eq!(saved["server"]["token"], TOKEN);
    assert_eq!(saved["server"]["address"], "127.0.0.1");
    assert_eq!(saved["client"]["fixture-downloader"]["increment-ban"], true);
    assert_eq!(
        saved["client"]["fixture-downloader"]["basic-auth"]["user"],
        ""
    );
    assert_eq!(saved["database"]["port"], 3306);
    assert_eq!(saved["extra"]["keep"], true);

    let logged_in = app_request(
        &app,
        Method::POST,
        "/api/auth/login",
        r#"{"token":"fixture-token"}"#,
    )
    .await;
    assert_eq!(logged_in.status(), StatusCode::OK);
    downloader.abort();
}
