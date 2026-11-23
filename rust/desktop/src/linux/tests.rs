use std::collections::HashMap;
use std::ffi::OsString;
use std::fs;
use std::net::SocketAddr;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::sync::mpsc;
use zbus::zvariant::{OwnedObjectPath, OwnedValue, Value};
use zbus::{Connection, Proxy};

use super::*;
use crate::{Tray, TrayStatus, WebUi};

const WATCHER_NAME: &str = "org.kde.StatusNotifierWatcher";
const ITEM_INTERFACE: &str = "org.kde.StatusNotifierItem";
const MENU_INTERFACE: &str = "com.canonical.dbusmenu";
const TEST_TIMEOUT: Duration = Duration::from_secs(5);

struct Watcher(mpsc::UnboundedSender<String>);

#[zbus::interface(name = "org.kde.StatusNotifierWatcher")]
impl Watcher {
    fn register_status_notifier_item(&self, service: &str) {
        let _ = self.0.send(service.to_owned());
    }

    #[zbus(property)]
    fn is_status_notifier_host_registered(&self) -> bool {
        true
    }
}

#[test]
fn uses_the_original_icon_as_a_dbus_pixmap() {
    assert!(Tray::supported());
    let icon = decode_icon().unwrap();
    assert_eq!((icon.width, icon.height), (256, 256));
    assert_eq!(icon.data.len(), 256 * 256 * 4);
    assert!(icon.data.chunks_exact(4).any(|pixel| pixel[0] == 255));
    assert!(icon.data.chunks_exact(4).any(|pixel| pixel[0] == 0));
}

#[tokio::test]
#[ignore = "requires a dedicated dbus-run-session"]
async fn watcher_menu_activation_and_exit_use_the_session_bus() {
    let (watcher, mut registered) = watcher().await;
    let (browser, mut opened) = mpsc::unbounded_channel();
    let tray = tokio::task::spawn_blocking(move || {
        let state = Arc::new(RwLock::new(MenuState::default()));
        let (request_exit, exit) = watch::channel(false);
        let platform = PlatformTray::start_with_browser(
            TrayLabels::default(),
            state.clone(),
            request_exit,
            Box::new(move |url| {
                let _ = browser.send(url);
            }),
            #[cfg(feature = "linux-webview")]
            None,
        )
        .unwrap();
        Tray {
            state,
            exit,
            _platform: platform,
        }
    })
    .await
    .unwrap();
    let service = next(&mut registered).await;
    assert!(service.starts_with("org.kde.StatusNotifierItem-"));

    let item = Proxy::new(
        &watcher,
        service.as_str(),
        "/StatusNotifierItem",
        ITEM_INTERFACE,
    )
    .await
    .unwrap();
    assert_eq!(
        item.get_property::<String>("Title").await.unwrap(),
        "PeerBanHelper"
    );
    assert_eq!(
        item.get_property::<String>("Status").await.unwrap(),
        "Active"
    );
    let pixmaps = item.get_property::<Vec<Icon>>("IconPixmap").await.unwrap();
    assert_eq!(pixmaps[0].data.len(), 256 * 256 * 4);
    let path: OwnedObjectPath = item.get_property("Menu").await.unwrap();
    let menu = Proxy::new(&watcher, service.as_str(), path.as_str(), MENU_INTERFACE)
        .await
        .unwrap();
    let entries = menu_entries(&menu).await;
    assert!(!entries["Open WebUI"].1);
    assert!(entries["Exit"].1);
    #[cfg(feature = "linux-webview")]
    if std::env::var_os("DISPLAY").is_none() && std::env::var_os("WAYLAND_DISPLAY").is_none() {
        assert!(!entries.contains_key("Open Window"));
    }
    item.call::<_, _, ()>("Activate", &(0_i32, 0_i32))
        .await
        .unwrap();
    assert!(
        tokio::time::timeout(Duration::from_millis(100), opened.recv())
            .await
            .is_err()
    );

    tray.set_web_ui(Some(WebUi::from_listener(
        "0.0.0.0:9898".parse().unwrap(),
        Some("must-not-open"),
    )));
    tray.set_status(Some(TrayStatus {
        banned_peers: 12,
        banned_ips: 7,
        healthy_downloaders: 2,
        total_downloaders: 3,
    }));
    let entries = tokio::time::timeout(TEST_TIMEOUT, async {
        loop {
            let entries = menu_entries(&menu).await;
            if entries["Open WebUI"].1 && entries.contains_key("Downloaders: 2 / 3") {
                break entries;
            }
            tokio::time::sleep(Duration::from_millis(25)).await;
        }
    })
    .await
    .unwrap();
    assert!(!entries["Banned peers: 12 (7 IPs)"].1);
    assert!(!entries["Downloaders: 2 / 3"].1);

    click(&menu, entries["Open WebUI"].0).await;
    assert_eq!(next(&mut opened).await.as_str(), "http://127.0.0.1:9898/");
    item.call::<_, _, ()>("Activate", &(0_i32, 0_i32))
        .await
        .unwrap();
    assert_eq!(next(&mut opened).await.as_str(), "http://127.0.0.1:9898/");

    watcher.release_name(WATCHER_NAME).await.unwrap();
    let (replacement, mut registered_after_restart) = self::watcher().await;
    assert_eq!(next(&mut registered_after_restart).await, service);

    click(&menu, entries["Exit"].0).await;
    tokio::time::timeout(TEST_TIMEOUT, tray.exit_requested())
        .await
        .unwrap();
    drop(tray);
    let dbus = zbus::fdo::DBusProxy::new(&watcher).await.unwrap();
    assert!(
        !dbus
            .name_has_owner(service.as_str().try_into().unwrap())
            .await
            .unwrap()
    );
    replacement.release_name(WATCHER_NAME).await.unwrap();
    let unavailable = tokio::task::spawn_blocking(|| Tray::start(TrayLabels::default()))
        .await
        .unwrap();
    assert!(matches!(unavailable, Err(DesktopError::Native { .. })));
}

#[cfg(feature = "linux-webview")]
async fn x11_window_info(title: &str) -> std::process::Output {
    Command::new("xwininfo")
        .args(["-name", title])
        .env("LC_ALL", "C")
        .output()
        .await
        .unwrap()
}

#[cfg(feature = "linux-webview")]
async fn visible_x11_window(title: &str) -> bool {
    String::from_utf8_lossy(&x11_window_info(title).await.stdout).contains("Map State: IsViewable")
}

#[tokio::test]
#[ignore = "requires dbus-run-session and PBH_LINUX_TRAY_BINARY"]
async fn native_service_keeps_tray_through_oobe_and_shuts_down_on_exit() {
    const OPEN_WEB_UI: &str = "WebUI";
    const OPEN_DATA_DIRECTORY: &str = "Open data file storage location...";
    #[cfg(feature = "linux-webview")]
    const COPY_WEB_UI_TOKEN: &str = "Copy WebUI Token...";
    const QUIT: &str = "Quit";
    let binary = std::env::var_os("PBH_LINUX_TRAY_BINARY").expect("set PBH_LINUX_TRAY_BINARY");
    let workspace = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let data = tempfile::tempdir().unwrap();
    let webui_dir = workspace.join("webui/dist");
    #[cfg(feature = "linux-webview")]
    let (webui_dir, render_probe) = if std::env::var_os("DISPLAY").is_some() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let endpoint = listener.local_addr().unwrap();
        let webui_fixture = data.path().join("webui");
        fs::create_dir(&webui_fixture).unwrap();
        fs::write(
            webui_fixture.join("index.html"),
            format!(
                r#"<!doctype html><body><script>
                const probe = document.createElement('img');
                probe.src = `http://{endpoint}/${{location.search ? 'unexpected-query' : 'rendered'}}`;
                document.body.append(probe);
                </script>"#
            ),
        )
        .unwrap();
        (webui_fixture, Some(listener))
    } else {
        (webui_dir, None)
    };
    #[cfg(feature = "linux-webview")]
    let test_clipboard =
        render_probe.is_some() && std::env::var_os("PBH_LINUX_TRAY_TEST_XCLIP").is_some();
    let config = data.path().join("config");
    fs::create_dir(&config).unwrap();
    let probe = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    let address = probe.local_addr().unwrap();
    drop(probe);
    fs::write(config.join("config.yml"), format!(
        "language: en_us\nserver:\n  address: 127.0.0.1\n  http: {}\n  token: ''\nprivacy:\n  analytics: false\n", address.port()
    )).unwrap();
    fs::write(
        config.join("profile.yml"),
        "check-interval: 100\nmodule: {}\n",
    )
    .unwrap();
    fs::write(
        data.path().join("flags.properties"),
        "pbh.forceDisableIPDB=true\n",
    )
    .unwrap();

    let opener = data.path().join("xdg-open");
    let opened = data.path().join("opened.txt");
    fs::write(
        &opener,
        "#!/bin/sh\nprintf '%s\\n' \"$1\" > \"$PBH_TRAY_OPENED\"\n",
    )
    .unwrap();
    fs::set_permissions(opener, fs::Permissions::from_mode(0o700)).unwrap();
    let mut path = OsString::from(data.path());
    path.push(":");
    path.push(std::env::var_os("PATH").unwrap());

    let mut downloader = Command::new("python3")
        .args([
            "-B",
            "-c",
            r#"from capture_qbittorrent import DownloaderState
from http.server import ThreadingHTTPServer
server = ThreadingHTTPServer(('127.0.0.1', 0), DownloaderState().handler())
print(server.server_port, flush=True)
server.serve_forever()
"#,
        ])
        .current_dir(workspace.join("test/rust-parity"))
        .stdout(Stdio::piped())
        .kill_on_drop(true)
        .spawn()
        .unwrap();
    let mut port = String::new();
    let downloader_stdout = downloader.stdout.take().unwrap();
    tokio::time::timeout(
        TEST_TIMEOUT,
        BufReader::new(downloader_stdout).read_line(&mut port),
    )
    .await
    .unwrap()
    .unwrap();
    let fixture_port: u16 = port.trim().parse().unwrap();

    let (watcher, mut registered) = watcher().await;
    let mut service = Command::new(binary)
        .args(["run", "--tray", "--data-dir"])
        .arg(data.path())
        .arg("--webui-dir")
        .arg(webui_dir)
        .env("PATH", path)
        .env("PBH_TRAY_OPENED", &opened)
        .kill_on_drop(true)
        .spawn()
        .unwrap();
    let name = next(&mut registered).await;
    assert!(name.starts_with(&format!(
        "org.kde.StatusNotifierItem-{}-",
        service.id().unwrap()
    )));
    let menu = Proxy::new(&watcher, name.as_str(), "/MenuBar", MENU_INTERFACE)
        .await
        .unwrap();
    let entries = tokio::time::timeout(TEST_TIMEOUT, async {
        loop {
            let entries = menu_entries(&menu).await;
            if entries[OPEN_WEB_UI].1 {
                break entries;
            }
            tokio::time::sleep(Duration::from_millis(25)).await;
        }
    })
    .await
    .unwrap();
    assert!(entries[OPEN_DATA_DIRECTORY].1);
    #[cfg(feature = "linux-webview")]
    if render_probe.is_some() {
        assert!(!entries[COPY_WEB_UI_TOKEN].1);
    }
    #[cfg(feature = "linux-webview")]
    if test_clipboard {
        assert!(!read_x11_clipboard().await.status.success());
    }
    let first_manifest = request(address, "GET", "/api/metadata/manifest", None, "")
        .await
        .unwrap();
    assert!(first_manifest.starts_with("HTTP/1.1 200"));
    click(&menu, entries[OPEN_WEB_UI].0).await;
    assert_opened_target(&opened, &format!("http://{address}/\n")).await;
    fs::remove_file(&opened).unwrap();
    click(&menu, entries[OPEN_DATA_DIRECTORY].0).await;
    assert_opened_target(&opened, &format!("{}\n", data.path().display())).await;

    let token = "fixture-linux-tray-token";
    let setup = format!(
        r#"{{"token":"{token}","database":{{"type":"sqlite"}},"downloader":{{"id":"loopback","config":{{"type":"qbittorrent","name":"Tray Fixture","endpoint":"http://127.0.0.1:{fixture_port}","incrementBan":true}}}}}}"#
    );
    let initialized = request(address, "POST", "/api/oobe/init", None, &setup)
        .await
        .unwrap();
    assert!(initialized.starts_with("HTTP/1.1 201"), "{initialized}");
    tokio::time::timeout(TEST_TIMEOUT, async {
        loop {
            if request(address, "GET", "/api/downloaders", Some(token), "")
                .await
                .is_ok_and(|reply| {
                    reply.starts_with("HTTP/1.1 200") && reply.contains("Tray Fixture")
                })
            {
                break;
            }
            tokio::time::sleep(Duration::from_millis(25)).await;
        }
    })
    .await
    .unwrap();
    let updated = menu_entries(&menu).await;
    fs::remove_file(&opened).unwrap();
    click(&menu, updated[OPEN_WEB_UI].0).await;
    assert_opened_target(&opened, &format!("http://{address}/\n")).await;

    #[cfg(feature = "linux-webview")]
    if let Some(probe) = render_probe {
        click(&menu, menu_entries(&menu).await["Open Window"].0).await;
        tokio::time::timeout(TEST_TIMEOUT, async {
            while !visible_x11_window("PeerBanHelper").await {
                tokio::time::sleep(Duration::from_millis(50)).await;
            }
            let (stream, _) = probe.accept().await.unwrap();
            let mut stream = BufReader::new(stream);
            let mut request = String::new();
            stream.read_line(&mut request).await.unwrap();
            assert!(request.starts_with("GET /rendered "), "{request}");
            stream
                .write_all(b"HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
                .await
                .unwrap();
        })
        .await
        .unwrap();
        let entries = menu_entries(&menu).await;
        assert!(entries[COPY_WEB_UI_TOKEN].1);
        if test_clipboard {
            assert!(!read_x11_clipboard().await.status.success());
            click(&menu, entries[COPY_WEB_UI_TOKEN].0).await;
            tokio::time::timeout(TEST_TIMEOUT, async {
                loop {
                    let contents = read_x11_clipboard().await;
                    if contents.status.success() {
                        assert_eq!(contents.stdout, token.as_bytes());
                        break;
                    }
                    tokio::time::sleep(Duration::from_millis(25)).await;
                }
            })
            .await
            .unwrap();
        }
    } else {
        assert!(!updated.contains_key("Open Window"));
        assert!(!updated.contains_key(COPY_WEB_UI_TOKEN));
    }

    let updated = menu_entries(&menu).await;
    click(&menu, updated[QUIT].0).await;
    let status = tokio::time::timeout(TEST_TIMEOUT, service.wait())
        .await
        .unwrap()
        .unwrap();
    assert!(status.success(), "PeerBanHelper exited with {status}");
    #[cfg(feature = "linux-webview")]
    if std::env::var_os("DISPLAY").is_some() {
        assert!(!x11_window_info("PeerBanHelper").await.status.success());
    }
    let dbus = zbus::fdo::DBusProxy::new(&watcher).await.unwrap();
    assert!(
        !dbus
            .name_has_owner(name.as_str().try_into().unwrap())
            .await
            .unwrap()
    );
    downloader.kill().await.unwrap();
}

#[cfg(feature = "linux-webview")]
async fn read_x11_clipboard() -> std::process::Output {
    Command::new("xclip")
        .args(["-selection", "clipboard", "-out"])
        .output()
        .await
        .unwrap()
}

async fn assert_opened_target(opened: &Path, expected: &str) {
    let destination = tokio::time::timeout(TEST_TIMEOUT, async {
        loop {
            if let Ok(url) = fs::read_to_string(opened)
                && url.ends_with('\n')
            {
                break url;
            }
            tokio::time::sleep(Duration::from_millis(25)).await;
        }
    })
    .await
    .unwrap();
    assert_eq!(destination, expected);
}

async fn request(
    address: SocketAddr,
    method: &str,
    path: &str,
    token: Option<&str>,
    body: &str,
) -> io::Result<String> {
    tokio::time::timeout(TEST_TIMEOUT, async {
        let mut socket = TcpStream::connect(address).await?;
        let auth = token
            .map(|token| format!("Authorization: Bearer {token}\r\n"))
            .unwrap_or_default();
        let request = format!(
            "{method} {path} HTTP/1.1\r\nHost: {address}\r\n{auth}Connection: close\r\nContent-Type: application/json\r\nContent-Length: {}\r\n\r\n{body}",
            body.len()
        );
        socket.write_all(request.as_bytes()).await?;
        let mut response = String::new();
        socket.read_to_string(&mut response).await?;
        Ok(response)
    }).await.map_err(|error| io::Error::new(io::ErrorKind::TimedOut, error))?
}

async fn watcher() -> (Connection, mpsc::UnboundedReceiver<String>) {
    let (register, registered) = mpsc::unbounded_channel();
    let connection = zbus::connection::Builder::session()
        .unwrap()
        .name(WATCHER_NAME)
        .unwrap()
        .serve_at("/StatusNotifierWatcher", Watcher(register))
        .unwrap()
        .build()
        .await
        .unwrap();
    (connection, registered)
}

async fn menu_entries(menu: &Proxy<'_>) -> HashMap<String, (i32, bool)> {
    type Layout = (i32, HashMap<String, OwnedValue>, Vec<OwnedValue>);
    let (_, (_, _, entries)): (u32, Layout) = menu
        .call("GetLayout", &(0_i32, 1_i32, vec!["label", "enabled"]))
        .await
        .unwrap();
    entries
        .into_iter()
        .filter_map(|entry| {
            let entry: zbus::zvariant::Structure<'_> = Value::from(entry).try_into().unwrap();
            let mut fields = entry.into_fields().into_iter();
            let id: i32 = fields.next().unwrap().downcast().unwrap();
            let properties: HashMap<String, OwnedValue> =
                fields.next().unwrap().downcast().unwrap();
            let label = <&str>::try_from(properties.get("label")?).ok()?;
            let enabled = properties
                .get("enabled")
                .and_then(|value| bool::try_from(value).ok())
                .unwrap_or(true);
            Some((label.to_owned(), (id, enabled)))
        })
        .collect()
}

async fn click(menu: &Proxy<'_>, id: i32) {
    menu.call::<_, _, ()>("Event", &(id, "clicked", Value::from(0_i32), 0_u32))
        .await
        .unwrap();
}

async fn next<T>(receiver: &mut mpsc::UnboundedReceiver<T>) -> T {
    tokio::time::timeout(TEST_TIMEOUT, receiver.recv())
        .await
        .unwrap()
        .unwrap()
}
