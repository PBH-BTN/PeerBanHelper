//! GTK and WebKit stay on the process main thread until the service finishes.

use std::sync::{Arc, RwLock};
use std::thread;

use gtk::glib::{MainContext, MainLoop, Propagation};
use gtk::prelude::*;
use tokio::sync::watch;
use url::Url;
use webkit2gtk::{URIRequestExt, WebViewExt};

use super::{DesktopError, MenuState, PlatformTray, TrayLabels, open_default_browser};
use crate::Tray;

/// Runs the GTK event loop on the Linux process main thread and the service on a worker.
/// A session without a graphical display retains the browser-only StatusNotifierItem.
pub fn run_linux_tray<R: Send + 'static>(
    labels: TrayLabels,
    run_service: impl FnOnce(Tray) -> R + Send + 'static,
) -> Result<R, DesktopError> {
    if gtk::init().is_err() {
        return Tray::start(labels).map(run_service);
    }

    let (window, mut requests) = watch::channel(WindowCommand::Idle);
    let state = Arc::new(RwLock::new(MenuState::default()));
    let (request_exit, exit) = watch::channel(false);
    let platform = PlatformTray::start_with_browser(
        labels.clone(),
        state.clone(),
        request_exit,
        Box::new(open_default_browser),
        Some(WindowAction(window.clone())),
    )?;
    let tray = Tray {
        state,
        exit,
        _platform: platform,
    };
    let worker = thread::Builder::new()
        .name("PeerBanHelper service".into())
        .spawn(move || {
            let _stop = StopOnDrop(window);
            run_service(tray)
        })
        .map_err(DesktopError::Worker)?;

    let context = MainContext::default();
    let event_loop = MainLoop::new(Some(&context), false);
    let stop = event_loop.clone();
    context.spawn_local(async move {
        let mut window = None;
        while requests.changed().await.is_ok() {
            let command = requests.borrow_and_update().clone();
            match command {
                WindowCommand::Idle => {}
                WindowCommand::Show(url) => window
                    .get_or_insert_with(|| EmbeddedWindow::new(&labels.title))
                    .show(url),
                WindowCommand::Stop => break,
            }
        }
        drop(window);
        stop.quit();
    });
    event_loop.run();
    worker
        .join()
        .map_err(|_| DesktopError::ServiceWorkerPanicked)
}

#[derive(Clone)]
enum WindowCommand {
    Idle,
    Show(Url),
    Stop,
}

pub(super) struct WindowAction(watch::Sender<WindowCommand>);

impl WindowAction {
    pub(super) fn show(&self, url: Url) {
        self.0.send_if_modified(|command| {
            if matches!(command, WindowCommand::Stop) {
                return false;
            }
            *command = WindowCommand::Show(url);
            true
        });
    }

    pub(super) fn copy_to_clipboard(&self, token: Arc<str>) {
        // Attach the operation to the GTK loop. The caller handles D-Bus menu events on a worker.
        MainContext::default().spawn(async move {
            let clipboard = gtk::Clipboard::get(&gtk::gdk::SELECTION_CLIPBOARD);
            clipboard.set_text(&token);
            clipboard.store();
        });
    }
}

struct StopOnDrop(watch::Sender<WindowCommand>);

impl Drop for StopOnDrop {
    fn drop(&mut self) {
        self.0.send_replace(WindowCommand::Stop);
    }
}

impl Drop for EmbeddedWindow {
    fn drop(&mut self) {
        self.web_view.stop_loading();
        // SAFETY: every reference is confined to this GTK thread and no widget methods are called afterward.
        unsafe { self.window.destroy() };
    }
}

struct EmbeddedWindow {
    window: gtk::Window,
    web_view: webkit2gtk::WebView,
    destination: Option<Url>,
}

impl EmbeddedWindow {
    fn new(title: &str) -> Self {
        let window = gtk::Window::new(gtk::WindowType::Toplevel);
        window.set_title(title);
        window.set_default_size(1100, 720);
        let icon = gtk::gdk_pixbuf::PixbufLoader::new();
        if icon.write(super::ICON).is_ok() && icon.close().is_ok() {
            window.set_icon(icon.pixbuf().as_ref());
        }
        window.connect_delete_event(|window, _| {
            window.hide();
            Propagation::Stop
        });
        let web_view = webkit2gtk::WebView::new();
        web_view.connect_create(|_, action| {
            if action.is_user_gesture()
                && let Some(uri) = action.request().and_then(|request| request.uri())
                && let Ok(url) = Url::parse(&uri)
                && matches!(url.scheme(), "http" | "https")
            {
                let _ = gtk::show_uri_on_window(
                    None::<&gtk::Window>,
                    url.as_str(),
                    gtk::current_event_time(),
                );
            }
            None
        });
        window.add(&web_view);
        Self {
            window,
            web_view,
            destination: None,
        }
    }

    fn show(&mut self, destination: Url) {
        if self.destination.as_ref() != Some(&destination) {
            self.web_view.load_uri(destination.as_str());
            self.destination = Some(destination);
        }
        self.window.show_all();
        self.window.present();
    }
}
