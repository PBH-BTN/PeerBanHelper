//! StatusNotifierItem and DBusMenu support for a Linux desktop session.

use std::ffi::OsString;
use std::io::{self, Cursor};
use std::process::Stdio;
use std::sync::{Arc, RwLock, mpsc};
use std::thread::{self, JoinHandle};
use std::time::Duration;

use ksni::menu::StandardItem;
use ksni::{Icon, MenuItem, TrayMethods as _};
use tokio::process::Command;
use tokio::sync::watch;
use url::Url;

use crate::{DesktopError, MenuState, TrayLabels};

#[cfg(feature = "linux-webview")]
mod window;
#[cfg(feature = "linux-webview")]
pub use window::run_linux_tray;

const ICON: &[u8] = include_bytes!("../../../src/main/resources/assets/icon.png");
const STARTUP_TIMEOUT: Duration = Duration::from_secs(5);
const STOP_TIMEOUT: Duration = Duration::from_secs(2);
type Browser = Box<dyn Fn(Url) + Send>;

pub(super) struct PlatformTray {
    changes: Option<watch::Sender<()>>,
    worker: Option<JoinHandle<()>>,
}

impl PlatformTray {
    pub(super) fn start(
        labels: TrayLabels,
        menu: Arc<RwLock<MenuState>>,
        exit: watch::Sender<bool>,
    ) -> Result<Self, DesktopError> {
        Self::start_with_browser(
            labels,
            menu,
            exit,
            Box::new(open_default_browser),
            #[cfg(feature = "linux-webview")]
            None,
        )
    }

    fn start_with_browser(
        labels: TrayLabels,
        menu: Arc<RwLock<MenuState>>,
        exit: watch::Sender<bool>,
        open: Browser,
        #[cfg(feature = "linux-webview")] window: Option<window::WindowAction>,
    ) -> Result<Self, DesktopError> {
        let (ready, startup) = mpsc::sync_channel(1);
        let (changes, updates) = watch::channel(());
        let worker = thread::Builder::new()
            .name("PeerBanHelper tray".into())
            .spawn(move || {
                let runtime = match tokio::runtime::Builder::new_current_thread()
                    .enable_all()
                    .build()
                {
                    Ok(runtime) => runtime,
                    Err(error) => {
                        let _ = ready.send(Err(DesktopError::Worker(error)));
                        return;
                    }
                };
                let item = Item {
                    #[cfg(feature = "linux-webview")]
                    window,
                    labels,
                    menu,
                    exit,
                    open,
                    icon: decode_icon(),
                };
                runtime.block_on(run(item, updates, ready));
            })
            .map_err(DesktopError::Worker)?;
        match startup.recv() {
            Ok(Ok(())) => Ok(Self {
                changes: Some(changes),
                worker: Some(worker),
            }),
            result => {
                let _ = worker.join();
                Err(result
                    .ok()
                    .and_then(Result::err)
                    .unwrap_or(DesktopError::WorkerStopped))
            }
        }
    }

    pub(super) fn refresh(&self) {
        if let Some(changes) = &self.changes {
            changes.send_replace(());
        }
    }
}

impl Drop for PlatformTray {
    fn drop(&mut self) {
        self.changes.take();
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}

async fn run(
    item: Item,
    mut updates: watch::Receiver<()>,
    ready: mpsc::SyncSender<Result<(), DesktopError>>,
) {
    let started = tokio::time::timeout(STARTUP_TIMEOUT, item.spawn()).await;
    let handle = match started {
        Ok(Ok(handle)) => handle,
        Ok(Err(error)) => {
            let _ = ready.send(Err(registration_error(io::Error::other(error))));
            return;
        }
        Err(error) => {
            let _ = ready.send(Err(registration_error(io::Error::new(
                io::ErrorKind::TimedOut,
                error,
            ))));
            return;
        }
    };
    if ready.send(Ok(())).is_ok() {
        while updates.changed().await.is_ok() {
            // The menu state is shared. Updating the service rebuilds the D-Bus menu and emits changes.
            if matches!(
                tokio::time::timeout(STOP_TIMEOUT, handle.update(|_| ())).await,
                Ok(None)
            ) {
                break;
            }
        }
    }
    let _ = tokio::time::timeout(STOP_TIMEOUT, handle.shutdown()).await;
}

fn registration_error(source: io::Error) -> DesktopError {
    DesktopError::Native {
        operation: "register the PeerBanHelper tray with the Linux notification area",
        source,
    }
}

struct Item {
    labels: TrayLabels,
    menu: Arc<RwLock<MenuState>>,
    exit: watch::Sender<bool>,
    open: Browser,
    icon: Option<Icon>,
    #[cfg(feature = "linux-webview")]
    window: Option<window::WindowAction>,
}

impl Item {
    fn snapshot(&self) -> MenuState {
        self.menu
            .read()
            .unwrap_or_else(|error| error.into_inner())
            .clone()
    }

    fn web_ui_destination(&self) -> Option<Url> {
        self.snapshot().web_ui.map(|web_ui| web_ui.0)
    }

    fn open_web_ui(&self) {
        if let Some(url) = self.web_ui_destination() {
            (self.open)(url);
        }
    }

    #[cfg(feature = "linux-webview")]
    fn open_window(&self) {
        if let (Some(window), Some(url)) = (&self.window, self.web_ui_destination()) {
            window.show(url);
        }
    }

    fn open_data_directory(&self) {
        if let Some(directory) = self.snapshot().data_directory {
            open_desktop_target(directory.into_os_string());
        }
    }

    #[cfg(feature = "linux-webview")]
    fn copy_web_ui_token(&self) {
        if let (Some(window), Some(token)) = (&self.window, self.snapshot().web_ui_token) {
            window.copy_to_clipboard(token);
        }
    }
}

impl ksni::Tray for Item {
    fn id(&self) -> String {
        "PeerBanHelper".into()
    }

    fn title(&self) -> String {
        self.labels.title.clone()
    }

    fn icon_name(&self) -> String {
        // Fall back to an installed icon theme if the embedded icon cannot be loaded.
        if self.icon.is_some() {
            String::new()
        } else {
            "web-browser".into()
        }
    }

    fn icon_pixmap(&self) -> Vec<Icon> {
        self.icon.iter().cloned().collect()
    }

    fn tool_tip(&self) -> ksni::ToolTip {
        ksni::ToolTip {
            title: self.title(),
            ..Default::default()
        }
    }

    fn activate(&mut self, _x: i32, _y: i32) {
        self.open_web_ui();
    }

    fn menu(&self) -> Vec<MenuItem<Self>> {
        let state = self.snapshot();
        let mut items = Vec::new();
        if let Some(status) = state.status {
            items.push(statistic(
                &self.labels.ban_statistics,
                status.banned_peers,
                status.banned_ips,
            ));
            items.push(statistic(
                &self.labels.downloader_statistics,
                status.healthy_downloaders,
                status.total_downloaders,
            ));
            items.push(MenuItem::Separator);
        }
        #[cfg(feature = "linux-webview")]
        if self.window.is_some() {
            items.push(
                StandardItem {
                    label: self.labels.open_window.clone(),
                    enabled: state.web_ui.is_some(),
                    activate: Box::new(|tray: &mut Self| tray.open_window()),
                    ..Default::default()
                }
                .into(),
            );
        }
        items.extend([
            StandardItem {
                label: self.labels.open_web_ui.clone(),
                enabled: state.web_ui.is_some(),
                activate: Box::new(|tray: &mut Self| tray.open_web_ui()),
                ..Default::default()
            }
            .into(),
            StandardItem {
                label: self.labels.open_data_directory.clone(),
                enabled: state.data_directory.is_some(),
                activate: Box::new(|tray: &mut Self| tray.open_data_directory()),
                ..Default::default()
            }
            .into(),
        ]);
        #[cfg(feature = "linux-webview")]
        if self.window.is_some() {
            items.push(
                StandardItem {
                    label: self.labels.copy_web_ui_token.clone(),
                    enabled: state.web_ui_token.is_some(),
                    activate: Box::new(|tray: &mut Self| tray.copy_web_ui_token()),
                    ..Default::default()
                }
                .into(),
            );
        }
        items.extend([
            MenuItem::Separator,
            StandardItem {
                label: self.labels.exit.clone(),
                activate: Box::new(|tray: &mut Self| {
                    tray.exit.send_replace(true);
                }),
                ..Default::default()
            }
            .into(),
        ]);
        items
    }
}

fn statistic(label: &str, first: u64, second: u64) -> MenuItem<Item> {
    StandardItem {
        label: label
            .replacen("{}", &first.to_string(), 1)
            .replacen("{}", &second.to_string(), 1),
        enabled: false,
        ..Default::default()
    }
    .into()
}

fn decode_icon() -> Option<Icon> {
    let mut decoder = png::Decoder::new(Cursor::new(ICON)).read_info().ok()?;
    let mut data = vec![0; decoder.output_buffer_size()?];
    let frame = decoder.next_frame(&mut data).ok()?;
    if frame.color_type != png::ColorType::Rgba || frame.bit_depth != png::BitDepth::Eight {
        return None;
    }
    data.truncate(frame.buffer_size());
    for pixel in data.chunks_exact_mut(4) {
        pixel.rotate_right(1);
    }
    Some(Icon {
        width: i32::try_from(frame.width).ok()?,
        height: i32::try_from(frame.height).ok()?,
        data,
    })
}

fn open_default_browser(url: Url) {
    open_desktop_target(url.as_str().into());
}

fn open_desktop_target(target: OsString) {
    tokio::spawn(async move {
        let launch = Command::new("xdg-open")
            .arg(&target)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .await;
        if !launch.is_ok_and(|status| status.success()) {
            let _ = Command::new("gio")
                .arg("open")
                .arg(target)
                .stdin(Stdio::null())
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .status()
                .await;
        }
    });
}

#[cfg(test)]
mod tests;
