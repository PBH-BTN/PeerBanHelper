use std::collections::BTreeMap;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime};

use axum::Router;
use axum::extract::{Request, State};
use axum::http::{StatusCode, header};
use axum::response::{IntoResponse, Response};
use lzma_rust2::{XzOptions, XzWriter};
use pbh_geoip::update::{
    Credentials, DatabaseUpdater, UpdateError, UpdateOptions, UpdateResult, UpdateStatus,
};
use pbh_geoip::{Database, GeoIp};
use reqwest::Url;
use sha2::{Digest, Sha256};
use tempfile::TempDir;
use tokio::net::TcpListener;
use tokio::task::JoinHandle;

const CITY: &[u8] = include_bytes!("fixtures/GeoIP2-City-Test.mmdb");
const ASN: &[u8] = include_bytes!("fixtures/GeoLite2-ASN-Test.mmdb");

#[derive(Clone)]
enum Reply {
    Body(Vec<u8>),
    Authenticate(Vec<u8>),
    Redirect(String),
    AuthenticateThenRedirect(String),
}

type Requests = Arc<Mutex<Vec<(String, Option<String>)>>>;

struct Mirror {
    url: String,
    requests: Requests,
    task: JoinHandle<()>,
}

impl Mirror {
    async fn open(replies: BTreeMap<&'static str, Reply>) -> Self {
        let requests = Requests::default();
        let app = Router::new()
            .fallback(serve)
            .with_state((Arc::new(replies), requests.clone()));
        let listener = TcpListener::bind(("127.0.0.1", 0)).await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let task = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
        Self {
            url,
            requests,
            task,
        }
    }

    fn base(&self, suffix: &str) -> Url {
        Url::parse(&format!("{}{suffix}", self.url)).unwrap()
    }
}

impl Drop for Mirror {
    fn drop(&mut self) {
        self.task.abort();
    }
}

async fn serve(
    State((replies, requests)): State<(Arc<BTreeMap<&'static str, Reply>>, Requests)>,
    request: Request,
) -> Response {
    let path = request.uri().path();
    let auth = request
        .headers()
        .get(header::AUTHORIZATION)
        .and_then(|value| value.to_str().ok());
    requests
        .lock()
        .unwrap()
        .push((path.to_owned(), auth.map(str::to_owned)));
    match replies.get(path) {
        Some(Reply::Body(data)) => data.clone().into_response(),
        Some(Reply::Authenticate(data)) if auth.is_some() => data.clone().into_response(),
        Some(Reply::Redirect(location)) => {
            (StatusCode::FOUND, [(header::LOCATION, location)]).into_response()
        }
        Some(Reply::AuthenticateThenRedirect(location)) if auth.is_some() => {
            (StatusCode::FOUND, [(header::LOCATION, location)]).into_response()
        }
        Some(Reply::Authenticate(_) | Reply::AuthenticateThenRedirect(_)) => {
            StatusCode::UNAUTHORIZED.into_response()
        }
        None => StatusCode::NOT_FOUND.into_response(),
    }
}

fn xz(raw: &[u8]) -> Vec<u8> {
    let mut writer = XzWriter::new(Vec::new(), XzOptions::with_preset(1)).unwrap();
    writer.write_all(raw).unwrap();
    writer.finish().unwrap()
}

fn install(data_dir: &Path, database: Database, raw: &[u8], aged: bool) -> PathBuf {
    let directory = data_dir.join("ipdb").join("geoip");
    fs::create_dir_all(&directory).unwrap();
    let destination = directory.join(database.filename());
    let mut file = fs::File::create(&destination).unwrap();
    file.write_all(raw).unwrap();
    if aged {
        file.set_modified(SystemTime::now() - Duration::from_secs(46 * 24 * 60 * 60))
            .unwrap();
    }
    destination
}

async fn refresh(path: &Path, options: UpdateOptions, mirrors: Vec<Url>) -> Vec<UpdateResult> {
    let path = path.to_owned();
    tokio::task::spawn_blocking(move || {
        DatabaseUpdater::with_mirrors(mirrors)
            .unwrap()
            .refresh(&path, &options)
    })
    .await
    .unwrap()
}

fn credentials() -> Option<Credentials> {
    Some(Credentials {
        account_id: "account".into(),
        license_key: "secret".into(),
    })
}

#[tokio::test]
async fn fills_a_missing_file_even_when_periodic_updates_are_disabled() {
    let directory = TempDir::new().unwrap();
    install(directory.path(), Database::City, CITY, true);
    install(directory.path(), Database::GeoCn, CITY, false);
    let compressed = xz(ASN);
    let checksum = Sha256::digest(&compressed).into();
    let mirror = Mirror::open(BTreeMap::from([
        (
            "/first/GeoLite2-ASN.mmdb.xz",
            Reply::Body(xz(b"wrong file")),
        ),
        (
            "/second/GeoLite2-ASN.mmdb.xz",
            Reply::Authenticate(compressed.clone()),
        ),
    ]))
    .await;
    let mut options = UpdateOptions {
        auto_update: false,
        credentials: credentials(),
        ..UpdateOptions::default()
    };
    options.asn.sha256 = Some(checksum);
    let result = refresh(
        directory.path(),
        options,
        vec![mirror.base("/first"), mirror.base("/second/")],
    )
    .await;
    let [city, asn, geocn] = &result[..] else {
        panic!("expected all three database outcomes")
    };
    assert!(matches!(city.status, UpdateStatus::Current));
    assert!(matches!(geocn.status, UpdateStatus::Current));
    let UpdateStatus::Installed(metadata) = &asn.status else {
        panic!("{:?}", asn.status)
    };
    assert_eq!(metadata.mirror_index, 1);
    assert_eq!(metadata.sha256, checksum);
    assert_eq!(metadata.compressed_bytes, compressed.len() as u64);
    assert_eq!(metadata.database_bytes, ASN.len() as u64);
    let requests = mirror.requests.lock().unwrap();
    assert_eq!(
        &requests[..],
        &[
            ("/first/GeoLite2-ASN.mmdb.xz".to_owned(), None),
            ("/second/GeoLite2-ASN.mmdb.xz".to_owned(), None),
            (
                "/second/GeoLite2-ASN.mmdb.xz".to_owned(),
                Some("Basic YWNjb3VudDpzZWNyZXQ=".into())
            ),
        ]
    );
    let loaded = GeoIp::open(directory.path(), "en");
    assert_eq!(
        loaded
            .lookup("1.0.0.1".parse().unwrap())
            .asn
            .unwrap()
            .number,
        Some(15169)
    );
    let mut files = fs::read_dir(directory.path().join("ipdb/geoip"))
        .unwrap()
        .map(|entry| entry.unwrap().file_name().into_string().unwrap())
        .collect::<Vec<_>>();
    files.sort();
    assert_eq!(files, ["GeoCN.mmdb", "GeoIP-ASN.mmdb", "GeoIP-City.mmdb"]);
}

#[tokio::test]
async fn validates_old_database_replacement_and_preserves_usable_files_on_failure() {
    let directory = TempDir::new().unwrap();
    let target = install(directory.path(), Database::City, CITY, true);
    install(directory.path(), Database::Asn, ASN, false);
    install(directory.path(), Database::GeoCn, CITY, false);
    let mirror = Mirror::open(BTreeMap::from([
        ("/wrong-type/GeoLite2-City.mmdb.xz", Reply::Body(xz(ASN))),
        (
            "/invalid/GeoLite2-City.mmdb.xz",
            Reply::Body(xz(b"invalid mmdb")),
        ),
        ("/valid/GeoLite2-City.mmdb.xz", Reply::Body(xz(CITY))),
    ]))
    .await;
    let options = UpdateOptions::default();
    let result = refresh(
        directory.path(),
        options.clone(),
        vec![mirror.base("/wrong-type"), mirror.base("/invalid")],
    )
    .await;
    assert!(matches!(
        result[0].status,
        UpdateStatus::Unavailable {
            retained_existing: true,
            error: UpdateError::InvalidDatabase(_)
        }
    ));
    assert_eq!(fs::read(&target).unwrap(), CITY);
    let still_active = GeoIp::open(directory.path(), "en");
    assert_eq!(
        still_active
            .lookup("89.160.20.128".parse().unwrap())
            .city
            .unwrap()
            .name
            .as_deref(),
        Some("Linköping")
    );
    let mut mismatched = options.clone();
    mismatched.city.sha256 = Some([0; 32]);
    let result = refresh(directory.path(), mismatched, vec![mirror.base("/valid")]).await;
    assert!(matches!(
        result[0].status,
        UpdateStatus::Unavailable {
            retained_existing: true,
            error: UpdateError::Checksum
        }
    ));
    let result = refresh(
        directory.path(),
        options.clone(),
        vec![mirror.base("/valid")],
    )
    .await;
    assert!(matches!(result[0].status, UpdateStatus::Installed(_)));
    assert!(
        target
            .metadata()
            .unwrap()
            .modified()
            .unwrap()
            .elapsed()
            .unwrap()
            < Duration::from_secs(5)
    );
    let result = refresh(directory.path(), options, vec![mirror.base("/valid")]).await;
    assert!(
        result
            .iter()
            .all(|item| matches!(item.status, UpdateStatus::Current))
    );
    assert_eq!(
        fs::read_dir(directory.path().join("ipdb/geoip"))
            .unwrap()
            .count(),
        3
    );
}

#[tokio::test]
async fn follows_download_redirects_without_forwarding_credentials() {
    let directory = TempDir::new().unwrap();
    install(directory.path(), Database::Asn, ASN, false);
    install(directory.path(), Database::GeoCn, CITY, false);
    let target = Mirror::open(BTreeMap::from([("/object", Reply::Body(xz(CITY)))])).await;
    let auth_required = Mirror::open(BTreeMap::from([
        (
            "/public/GeoLite2-City.mmdb.xz",
            Reply::Redirect(format!("{}/object", target.url)),
        ),
        (
            "/private/GeoLite2-City.mmdb.xz",
            Reply::AuthenticateThenRedirect(format!("{}/object", target.url)),
        ),
    ]))
    .await;
    let mut options = UpdateOptions {
        credentials: credentials(),
        ..UpdateOptions::default()
    };
    let result = refresh(
        directory.path(),
        options.clone(),
        vec![auth_required.base("/public")],
    )
    .await;
    assert!(matches!(result[0].status, UpdateStatus::Installed(_)));
    install(directory.path(), Database::City, CITY, true);
    let result = refresh(
        directory.path(),
        options.clone(),
        vec![auth_required.base("/private")],
    )
    .await;
    assert!(matches!(result[0].status, UpdateStatus::Installed(_)));
    let requests = target.requests.lock().unwrap().clone();
    assert_eq!(
        requests,
        [("/object".into(), None), ("/object".into(), None)]
    );
    assert!(
        auth_required
            .requests
            .lock()
            .unwrap()
            .last()
            .unwrap()
            .1
            .is_some()
    );

    install(directory.path(), Database::City, CITY, true);
    options.city.name = "../escape".into();
    let before = auth_required.requests.lock().unwrap().len();
    let result = refresh(
        directory.path(),
        options,
        vec![auth_required.base("/private")],
    )
    .await;
    assert!(matches!(
        result[0].status,
        UpdateStatus::Unavailable {
            retained_existing: true,
            error: UpdateError::InvalidAssetName
        }
    ));
    assert_eq!(auth_required.requests.lock().unwrap().len(), before);
}
