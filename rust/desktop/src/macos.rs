//! The AppKit status item and its application loop stay on the process main thread.

use std::cell::RefCell;
use std::io;
use std::sync::{Arc, RwLock};
use std::thread;

use dispatch2::DispatchQueue;
use objc2::rc::{Retained, autoreleasepool};
use objc2::runtime::{ProtocolObject, Sel};
use objc2::{
    AnyThread, DefinedClass, MainThreadMarker, MainThreadOnly, define_class, msg_send, sel,
};
use objc2_app_kit::{
    NSApplication, NSApplicationActivationPolicy, NSEvent, NSEventModifierFlags, NSEventType,
    NSImage, NSMenu, NSMenuDelegate, NSMenuItem, NSStatusBar, NSStatusItem,
    NSVariableStatusItemLength, NSWorkspace,
};
use objc2_foundation::{NSData, NSObject, NSObjectProtocol, NSPoint, NSSize, NSString, NSURL};
use tokio::sync::watch;
use url::Url;

use crate::{DesktopError, MenuState, Tray, TrayLabels, WebUi};

mod window;
use window::EmbeddedWindow;

const ICON: &[u8] = include_bytes!("../../../src/main/resources/assets/icon.png");
const BAN_STATISTIC: isize = 0;
const DOWNLOADER_STATISTIC: isize = 1;
const STATISTIC_SEPARATOR: isize = 2;
const OPEN_WINDOW: isize = 3;
const OPEN_WEB_UI: isize = 4;

/// Runs an accessory AppKit application on the main thread and the PBH service on a worker.
/// The service owns the portable tray handle and handles Exit through its normal shutdown signal.
pub fn run_macos_tray<R: Send + 'static>(
    labels: TrayLabels,
    run_service: impl FnOnce(Tray) -> R + Send + 'static,
) -> Result<R, DesktopError> {
    let marker =
        MainThreadMarker::new().ok_or_else(|| desktop_error("start AppKit on the main thread"))?;
    autoreleasepool(|_| {
        let app = NSApplication::sharedApplication(marker);
        if !app.setActivationPolicy(NSApplicationActivationPolicy::Accessory) {
            return Err(desktop_error("start the macOS menu bar application"));
        }

        let state = Arc::new(RwLock::new(MenuState::default()));
        let (request_exit, exit) = watch::channel(false);
        let _native = NativeTray::new(marker, labels, state.clone(), request_exit.clone())?;
        let tray = Tray { state, exit };
        let worker = thread::Builder::new()
            .name("PeerBanHelper service".into())
            .spawn(move || {
                let _stop = StopAppOnDrop;
                run_service(tray)
            })
            .map_err(DesktopError::Worker)?;

        app.run();
        request_exit.send_replace(true);
        worker
            .join()
            .map_err(|_| DesktopError::ServiceWorkerPanicked)
    })
}

struct StopAppOnDrop;

impl Drop for StopAppOnDrop {
    fn drop(&mut self) {
        DispatchQueue::main().exec_async(|| {
            let Some(marker) = MainThreadMarker::new() else { return };
            let app = NSApplication::sharedApplication(marker);
            app.stop(None);
            // AppKit checks the stop flag when it next retrieves an event.
            if let Some(wake) = NSEvent::otherEventWithType_location_modifierFlags_timestamp_windowNumber_context_subtype_data1_data2(
                NSEventType::ApplicationDefined,
                NSPoint::new(0.0, 0.0),
                NSEventModifierFlags::empty(),
                0.0,
                0,
                None,
                0,
                0,
                0,
            ) {
                app.postEvent_atStart(&wake, true);
            }
        });
    }
}

struct NativeTray {
    bar: Retained<NSStatusBar>,
    item: Retained<NSStatusItem>,
    _target: Retained<MenuTarget>,
}

impl NativeTray {
    fn new(
        marker: MainThreadMarker,
        labels: TrayLabels,
        state: Arc<RwLock<MenuState>>,
        exit: watch::Sender<bool>,
    ) -> Result<Self, DesktopError> {
        let bar = NSStatusBar::systemStatusBar();
        let item = bar.statusItemWithLength(NSVariableStatusItemLength);
        let target = MenuTarget::new(
            marker,
            MenuActions {
                labels,
                state,
                exit,
                window: RefCell::new(None),
            },
        );
        let native = Self {
            bar,
            item,
            _target: target.clone(),
        };
        let button = native
            .item
            .button(marker)
            .ok_or_else(|| desktop_error("create the PeerBanHelper menu bar button"))?;
        let title = NSString::from_str(&target.ivars().labels.title);
        button.setToolTip(Some(&title));
        let icon_bytes = NSData::with_bytes(ICON);
        if let Some(icon) = NSImage::initWithData(NSImage::alloc(), &icon_bytes) {
            icon.setSize(NSSize::new(18.0, 18.0));
            button.setImage(Some(&icon));
        } else {
            button.setTitle(&title);
        }

        let menu = NSMenu::initWithTitle(NSMenu::alloc(marker), &title);
        menu.setAutoenablesItems(false);
        menu.setDelegate(Some(ProtocolObject::from_ref(&*target)));
        menu.addItem(&NSMenuItem::new(marker));
        menu.addItem(&NSMenuItem::new(marker));
        menu.addItem(&NSMenuItem::separatorItem(marker));
        add_action(
            &menu,
            &target,
            &target.ivars().labels.open_window,
            sel!(openWindow:),
            marker,
        );
        add_action(
            &menu,
            &target,
            &target.ivars().labels.open_web_ui,
            sel!(openWebUi:),
            marker,
        );
        menu.addItem(&NSMenuItem::separatorItem(marker));
        add_action(
            &menu,
            &target,
            &target.ivars().labels.exit,
            sel!(exitService:),
            marker,
        );
        target.refresh(&menu);
        native.item.setMenu(Some(&menu));
        Ok(native)
    }
}

impl Drop for NativeTray {
    fn drop(&mut self) {
        self.item.setMenu(None);
        self.bar.removeStatusItem(&self.item);
    }
}

fn add_action(
    menu: &NSMenu,
    target: &MenuTarget,
    label: &str,
    selector: Sel,
    marker: MainThreadMarker,
) {
    let item = NSMenuItem::new(marker);
    item.setTitle(&NSString::from_str(label));
    item.setEnabled(true);
    // SAFETY: the native tray retains the target. Both selectors use the menu-item action signature.
    unsafe {
        item.setTarget(Some(target));
        item.setAction(Some(selector));
    }
    menu.addItem(&item);
}

struct MenuActions {
    labels: TrayLabels,
    state: Arc<RwLock<MenuState>>,
    exit: watch::Sender<bool>,
    window: RefCell<Option<EmbeddedWindow>>,
}

define_class!(
    #[unsafe(super(NSObject))]
    #[thread_kind = MainThreadOnly]
    #[name = "PeerBanHelperMenuTarget"]
    #[ivars = MenuActions]
    struct MenuTarget;

    unsafe impl NSObjectProtocol for MenuTarget {}

    unsafe impl NSMenuDelegate for MenuTarget {
        #[unsafe(method(menuNeedsUpdate:))]
        fn menu_needs_update(&self, menu: &NSMenu) {
            self.refresh(menu);
        }
    }

    impl MenuTarget {
        #[unsafe(method(openWindow:))]
        fn open_window(&self, _sender: &NSMenuItem) {
            let Some(destination) = self.snapshot().web_ui.map(web_ui_destination) else { return };
            let actions = self.ivars();
            let marker = self.mtm();
            actions.window.borrow_mut()
                .get_or_insert_with(|| EmbeddedWindow::new(marker, &actions.labels.title))
                .show(marker, destination);
        }

        #[unsafe(method(openWebUi:))]
        fn open_web_ui(&self, _sender: &NSMenuItem) {
            if let Some(url) = self.snapshot().web_ui.map(web_ui_destination)
                && let Some(url) = NSURL::URLWithString(&NSString::from_str(url.as_str()))
            {
                NSWorkspace::sharedWorkspace().openURL(&url);
            }
        }

        #[unsafe(method(exitService:))]
        fn exit_service(&self, _sender: &NSMenuItem) {
            self.ivars().exit.send_replace(true);
        }
    }
);

impl MenuTarget {
    fn new(marker: MainThreadMarker, ivars: MenuActions) -> Retained<Self> {
        let this = Self::alloc(marker).set_ivars(ivars);
        // SAFETY: NSObject's initializer receives this class's new object with initialized ivars.
        unsafe { msg_send![super(this), init] }
    }

    fn snapshot(&self) -> MenuState {
        self.ivars()
            .state
            .read()
            .unwrap_or_else(|error| error.into_inner())
            .clone()
    }

    fn refresh(&self, menu: &NSMenu) {
        let snapshot = self.snapshot();
        for index in [BAN_STATISTIC, DOWNLOADER_STATISTIC, STATISTIC_SEPARATOR] {
            if let Some(item) = menu.itemAtIndex(index) {
                item.setHidden(snapshot.status.is_none());
                item.setEnabled(false);
            }
        }
        if let Some(status) = snapshot.status {
            let labels = &self.ivars().labels;
            let render = |template: &str, first: u64, second: u64| {
                NSString::from_str(&template.replacen("{}", &first.to_string(), 1).replacen(
                    "{}",
                    &second.to_string(),
                    1,
                ))
            };
            if let Some(item) = menu.itemAtIndex(BAN_STATISTIC) {
                item.setTitle(&render(
                    &labels.ban_statistics,
                    status.banned_peers,
                    status.banned_ips,
                ));
            }
            if let Some(item) = menu.itemAtIndex(DOWNLOADER_STATISTIC) {
                item.setTitle(&render(
                    &labels.downloader_statistics,
                    status.healthy_downloaders,
                    status.total_downloaders,
                ));
            }
        }
        for index in [OPEN_WINDOW, OPEN_WEB_UI] {
            if let Some(item) = menu.itemAtIndex(index) {
                item.setEnabled(snapshot.web_ui.is_some());
            }
        }
    }
}

fn web_ui_destination(web_ui: WebUi) -> Url {
    let mut url = web_ui.0;
    url.set_query(None);
    url
}

fn desktop_error(operation: &'static str) -> DesktopError {
    DesktopError::Native {
        operation,
        source: io::Error::other("AppKit notification area is unavailable"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn macos_tray_requires_the_application_main_thread() {
        assert!(Tray::supported());
        assert!(matches!(
            Tray::start(TrayLabels::default()),
            Err(DesktopError::Unsupported)
        ));
        let off_main_thread = thread::spawn(|| run_macos_tray(TrayLabels::default(), |_| ()))
            .join()
            .unwrap();
        assert!(matches!(off_main_thread, Err(DesktopError::Native { .. })));
    }

    #[test]
    fn opening_web_ui_omits_the_access_token() {
        let web_ui = WebUi::from_listener("[::]:9898".parse().unwrap(), Some("secret fixture"));
        assert_eq!(web_ui_destination(web_ui).as_str(), "http://[::1]:9898/");
    }
}
