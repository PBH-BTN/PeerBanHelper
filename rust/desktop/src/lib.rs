//! Native desktop controls for an existing PeerBanHelper WebUI listener.

use std::future::pending;
use std::io;
use std::net::{Ipv4Addr, Ipv6Addr, SocketAddr};
use std::path::{Path, PathBuf};
use std::sync::{Arc, RwLock};

use thiserror::Error;
use tokio::sync::watch;
use url::Url;

#[cfg(target_os = "linux")]
mod linux;
#[cfg(target_os = "macos")]
mod macos;
#[cfg(windows)]
pub mod process;
#[cfg(windows)]
mod windows;

#[cfg(target_os = "linux")]
use linux as platform;
#[cfg(all(target_os = "linux", feature = "linux-webview"))]
pub use linux::run_linux_tray;
#[cfg(target_os = "macos")]
pub use macos::run_macos_tray;
#[cfg(windows)]
use windows as platform;

#[derive(Clone)]
pub struct WebUi(Url);

impl WebUi {
    /// Creates a browser destination from the listener that is already serving this process.
    pub fn from_listener(address: SocketAddr, token: Option<&str>) -> Self {
        let local = match address {
            SocketAddr::V4(addr) if addr.ip().is_unspecified() => {
                SocketAddr::new(Ipv4Addr::LOCALHOST.into(), addr.port())
            }
            SocketAddr::V6(addr) if addr.ip().is_unspecified() => {
                SocketAddr::new(Ipv6Addr::LOCALHOST.into(), addr.port())
            }
            address => SocketAddr::new(address.ip(), address.port()),
        };
        let mut url = Url::parse(&format!("http://{local}/")).expect("valid local listener URL");
        if let Some(token) = token.filter(|token| !token.is_empty()) {
            url.query_pairs_mut().append_pair("token", token);
        }
        Self(url)
    }

    /// Browser destination, which may include the local WebUI access token.
    pub fn as_url(&self) -> &str {
        self.0.as_str()
    }

    fn without_query(mut self) -> Self {
        self.0.set_query(None);
        self
    }
}

#[derive(Clone, Debug)]
pub struct TrayLabels {
    pub title: String,
    pub open_window: String,
    pub open_web_ui: String,
    pub open_data_directory: String,
    pub copy_web_ui_token: String,
    pub exit: String,
    pub ban_statistics: String,
    pub downloader_statistics: String,
}

impl Default for TrayLabels {
    fn default() -> Self {
        Self {
            title: "PeerBanHelper".into(),
            open_window: "Open Window".into(),
            open_web_ui: "Open WebUI".into(),
            open_data_directory: "Open data file storage location...".into(),
            copy_web_ui_token: "Copy WebUI Token...".into(),
            exit: "Exit".into(),
            ban_statistics: "Banned peers: {} ({} IPs)".into(),
            downloader_statistics: "Downloaders: {} / {}".into(),
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct TrayStatus {
    pub banned_peers: u64,
    pub banned_ips: u64,
    pub healthy_downloaders: u64,
    pub total_downloaders: u64,
}

#[derive(Debug, Error)]
pub enum DesktopError {
    #[error("the native PeerBanHelper tray is unavailable on this platform")]
    Unsupported,
    #[error("unable to start the PeerBanHelper desktop worker")]
    Worker(#[source] io::Error),
    #[error("the PeerBanHelper desktop worker stopped during startup")]
    WorkerStopped,
    #[cfg(any(
        target_os = "macos",
        all(target_os = "linux", feature = "linux-webview")
    ))]
    #[error("the PeerBanHelper service worker panicked")]
    ServiceWorkerPanicked,
    #[error("unable to {operation}")]
    Native {
        operation: &'static str,
        #[source]
        source: io::Error,
    },
    #[error("the Windows taskbar notification area rejected the PeerBanHelper icon")]
    TrayUnavailable,
}

#[derive(Clone, Default)]
struct MenuState {
    web_ui: Option<WebUi>,
    data_directory: Option<PathBuf>,
    web_ui_token: Option<Arc<str>>,
    status: Option<TrayStatus>,
}

pub struct Tray {
    state: Arc<RwLock<MenuState>>,
    exit: watch::Receiver<bool>,
    #[cfg(any(windows, target_os = "linux"))]
    _platform: platform::PlatformTray,
}

impl Tray {
    /// Compile-time availability. The desktop session must provide a notification area.
    pub const fn supported() -> bool {
        cfg!(any(windows, target_os = "linux", target_os = "macos"))
    }

    /// Creates a tray on Windows or Linux. The macOS application uses its main event loop.
    pub fn start(labels: TrayLabels) -> Result<Self, DesktopError> {
        #[cfg(windows)]
        {
            Self::start_windows(labels, std::path::Path::new("data"))
        }
        #[cfg(target_os = "linux")]
        {
            let state = Arc::new(RwLock::new(MenuState::default()));
            let (request_exit, exit) = watch::channel(false);
            let platform = platform::PlatformTray::start(labels, state.clone(), request_exit)?;
            Ok(Self {
                state,
                exit,
                _platform: platform,
            })
        }
        #[cfg(not(any(windows, target_os = "linux")))]
        {
            let _ = labels;
            Err(DesktopError::Unsupported)
        }
    }

    /// Starts the tray with the WebView2 profile in this installation's data directory.
    #[cfg(windows)]
    pub fn start_windows(
        labels: TrayLabels,
        data_dir: &std::path::Path,
    ) -> Result<Self, DesktopError> {
        let profile = std::path::absolute(data_dir)
            .map_err(|source| DesktopError::Native {
                operation: "resolve the PeerBanHelper data directory",
                source,
            })?
            .join("webview2");
        let state = Arc::new(RwLock::new(MenuState::default()));
        let (request_exit, exit) = watch::channel(false);
        let platform = platform::PlatformTray::start(labels, state.clone(), request_exit, profile)?;
        Ok(Self {
            state,
            exit,
            _platform: platform,
        })
    }

    /// `None` clears all listener actions while the listener is unavailable.
    pub fn set_web_ui(&self, web_ui: Option<WebUi>) {
        {
            let mut state = self
                .state
                .write()
                .unwrap_or_else(|error| error.into_inner());
            state.web_ui = web_ui.map(WebUi::without_query);
            if state.web_ui.is_none() {
                state.data_directory = None;
                state.web_ui_token = None;
            }
        }
        #[cfg(target_os = "linux")]
        self._platform.refresh();
    }

    /// Updates the native actions when the WebUI listener and configuration become active.
    pub fn set_web_ui_context(&self, web_ui: WebUi, data_directory: &Path, token: &str) {
        {
            let mut state = self
                .state
                .write()
                .unwrap_or_else(|error| error.into_inner());
            state.web_ui = Some(web_ui.without_query());
            state.data_directory = Some(data_directory.to_owned());
            state.web_ui_token =
                (!token.trim().is_empty() && !token.contains('\0')).then(|| Arc::from(token));
        }
        #[cfg(target_os = "linux")]
        self._platform.refresh();
    }

    pub fn set_status(&self, status: Option<TrayStatus>) {
        self.state
            .write()
            .unwrap_or_else(|error| error.into_inner())
            .status = status;
        #[cfg(target_os = "linux")]
        self._platform.refresh();
    }

    /// Resolves once the user chooses Exit. The application performs its normal shutdown.
    pub async fn exit_requested(&self) {
        let mut exit = self.exit.clone();
        if exit.wait_for(|requested| *requested).await.is_err() {
            // Losing the notification area must not stop the service.
            pending().await
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn browser_destination_maps_wildcard_listeners_and_encodes_access_tokens() {
        let target = WebUi::from_listener("0.0.0.0:9898".parse().unwrap(), Some("a& b=中文"));
        assert_eq!(
            target.as_url(),
            "http://127.0.0.1:9898/?token=a%26+b%3D%E4%B8%AD%E6%96%87"
        );
        let target = WebUi::from_listener("[::]:9898".parse().unwrap(), Some("fixture"));
        assert_eq!(target.without_query().as_url(), "http://[::1]:9898/");
    }

    #[cfg(not(any(windows, target_os = "linux", target_os = "macos")))]
    #[test]
    fn headless_build_has_no_platform_tray_requirement() {
        assert!(!Tray::supported());
        assert!(matches!(
            Tray::start(TrayLabels::default()),
            Err(DesktopError::Unsupported)
        ));
    }
}
