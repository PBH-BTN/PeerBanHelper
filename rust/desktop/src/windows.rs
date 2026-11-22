use std::io;
use std::mem::size_of;
use std::path::PathBuf;
use std::ptr::{null, null_mut};
use std::sync::{Arc, RwLock, mpsc};
use std::thread::{self, JoinHandle};

use tokio::sync::watch;
use windows_sys::Win32::Foundation::{HINSTANCE, HWND, LPARAM, LRESULT, POINT, WPARAM};
use windows_sys::Win32::System::Com::{
    COINIT_APARTMENTTHREADED, COINIT_DISABLE_OLE1DDE, CoInitializeEx, CoUninitialize,
};
use windows_sys::Win32::System::LibraryLoader::GetModuleHandleW;
use windows_sys::Win32::System::Threading::GetCurrentThreadId;
use windows_sys::Win32::UI::Shell::{
    NIF_ICON, NIF_MESSAGE, NIF_SHOWTIP, NIF_TIP, NIM_ADD, NIM_DELETE, NIM_SETFOCUS, NIM_SETVERSION,
    NIN_SELECT, NINF_KEY, NOTIFYICON_VERSION_4, NOTIFYICONDATAW, Shell_NotifyIconW, ShellExecuteW,
};
use windows_sys::Win32::UI::WindowsAndMessaging::*;

use crate::{DesktopError, MenuState, TrayLabels, WebUi};

const NOTIFICATION: u32 = WM_APP + 1;
const KEY_SELECT: u32 = NIN_SELECT | NINF_KEY;
const ICON_ID: u32 = 1;
const OPEN: u16 = 1;
const EXIT: u16 = 2;
const OPEN_WINDOW: u16 = 3;
const ICON_LEN: usize = include_bytes!("../../../src/main/resources/assets/icon.png").len();
#[repr(align(4))]
struct IconResource([u8; ICON_LEN]);
static ICON: IconResource = IconResource(*include_bytes!(
    "../../../src/main/resources/assets/icon.png"
));

type Browser = Box<dyn Fn(&WebUi) + Send>;

pub(super) struct PlatformTray {
    window: usize,
    thread: Option<JoinHandle<()>>,
}

impl PlatformTray {
    pub(super) fn start(
        labels: TrayLabels,
        menu: Arc<RwLock<MenuState>>,
        exit: watch::Sender<bool>,
        profile: PathBuf,
    ) -> Result<Self, DesktopError> {
        Self::start_with_browser(labels, menu, exit, profile, Box::new(open_default_browser))
    }

    fn start_with_browser(
        labels: TrayLabels,
        menu: Arc<RwLock<MenuState>>,
        exit: watch::Sender<bool>,
        profile: PathBuf,
        open: Browser,
    ) -> Result<Self, DesktopError> {
        let (ready, startup) = mpsc::sync_channel(1);
        let worker = thread::Builder::new()
            .name("PeerBanHelper tray".into())
            .spawn(move || {
                let _com = match Apartment::initialize() {
                    Ok(com) => com,
                    Err(error) => {
                        let _ = ready.send(Err(error));
                        return;
                    }
                };
                let window = match NativeWindow::create(labels, menu, exit, profile, open) {
                    Ok(window) => window,
                    Err(error) => {
                        let _ = ready.send(Err(error));
                        return;
                    }
                };
                if ready.send(Ok(window.handle as usize)).is_ok() {
                    window.dispatch();
                }
            })
            .map_err(DesktopError::Worker)?;
        match startup.recv() {
            Ok(Ok(window)) => Ok(Self {
                window,
                thread: Some(worker),
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
}

impl Drop for PlatformTray {
    fn drop(&mut self) {
        if let Some(thread) = self.thread.take() {
            // SAFETY: the message is posted to the worker's window. A closed window ignores it.
            if !thread.is_finished() {
                unsafe { PostMessageW(self.window as HWND, WM_CLOSE, 0, 0) };
            }
            let _ = thread.join();
        }
    }
}

struct Apartment;

impl Apartment {
    fn initialize() -> Result<Self, DesktopError> {
        // SAFETY: this is a new thread and supplies no reserved COM argument.
        let result = unsafe {
            CoInitializeEx(
                null(),
                (COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE) as u32,
            )
        };
        if result < 0 {
            Err(DesktopError::Native {
                operation: "initialize the Windows desktop worker",
                source: io::Error::from_raw_os_error(result),
            })
        } else {
            Ok(Self)
        }
    }
}

impl Drop for Apartment {
    fn drop(&mut self) {
        // SAFETY: each successful initialization is balanced on its owning thread.
        unsafe { CoUninitialize() };
    }
}

struct WindowClass {
    instance: HINSTANCE,
    name: Vec<u16>,
}

impl WindowClass {
    fn register() -> Result<Self, DesktopError> {
        // SAFETY: null requests the module handle of the current executable.
        let instance = unsafe { GetModuleHandleW(null()) };
        if instance.is_null() {
            return Err(native_error("identify the PeerBanHelper executable"));
        }
        let thread = unsafe { GetCurrentThreadId() };
        let name = wide(&format!(
            "PeerBanHelper.Tray.{}.{thread}",
            std::process::id()
        ));
        let class = WNDCLASSW {
            hInstance: instance,
            lpfnWndProc: Some(window_proc),
            lpszClassName: name.as_ptr(),
            ..WNDCLASSW::default()
        };
        // SAFETY: `class` and its name remain valid for the registration call.
        if unsafe { RegisterClassW(&class) } == 0 {
            return Err(native_error("register the PeerBanHelper tray window"));
        }
        Ok(Self { instance, name })
    }
}

impl Drop for WindowClass {
    fn drop(&mut self) {
        // SAFETY: the owning window is already destroyed and the registered name is retained.
        unsafe { UnregisterClassW(self.name.as_ptr(), self.instance) };
    }
}

struct Icon(HICON);

impl Icon {
    fn load() -> Result<Self, DesktopError> {
        // SAFETY: this immutable resource is the existing PeerBanHelper icon. No shared flag is used.
        let handle = unsafe {
            CreateIconFromResourceEx(
                ICON.0.as_ptr(),
                ICON_LEN as u32,
                1,
                0x0003_0000,
                GetSystemMetrics(SM_CXSMICON),
                GetSystemMetrics(SM_CYSMICON),
                0,
            )
        };
        if handle.is_null() {
            Err(native_error("decode the PeerBanHelper icon"))
        } else {
            Ok(Self(handle))
        }
    }
}

impl Drop for Icon {
    fn drop(&mut self) {
        // SAFETY: CreateIconFromResourceEx returned this non-shared handle.
        unsafe { DestroyIcon(self.0) };
    }
}

struct WindowState {
    labels: TrayLabels,
    menu: Arc<RwLock<MenuState>>,
    exit: watch::Sender<bool>,
    open: Browser,
    icon: Icon,
    taskbar_created: u32,
    profile: PathBuf,
    web_window: RefCell<Option<web_window::Window>>,
    close_pending: Cell<bool>,
}

impl WindowState {
    fn snapshot(&self) -> MenuState {
        self.menu
            .read()
            .unwrap_or_else(|error| error.into_inner())
            .clone()
    }

    fn action(&self, owner: HWND, command: u16) {
        match command {
            OPEN => {
                if let Some(web_ui) = self.snapshot().web_ui {
                    (self.open)(&web_ui);
                }
            }
            EXIT => {
                self.exit.send_replace(true);
            }
            OPEN_WINDOW => {
                if let Some(web_ui) = self.snapshot().web_ui {
                    self.show_window(owner, web_ui);
                }
            }
            _ => {}
        }
    }

    fn show_window(&self, owner: HWND, web_ui: WebUi) {
        // WebView2 pumps messages during construction. A repeated command can
        // wait for that first request to finish without taking a second borrow.
        let Ok(mut window) = self.web_window.try_borrow_mut() else {
            return;
        };
        let mut address = web_ui.0.clone();
        address.set_query(None);
        let result = match window.as_mut() {
            Some(window) => window.show(&address),
            None => web_window::Window::new(&self.labels.title, &address, self.profile.clone())
                .and_then(|mut view| {
                    view.show(&address)?;
                    *window = Some(view);
                    Ok(())
                }),
        };
        drop(window);
        if self.close_pending.replace(false) {
            // SAFETY: this is the tray window that owns the callback state.
            unsafe {
                PostMessageW(owner, WM_CLOSE, 0, 0);
            }
        } else if let Err(error) = result {
            eprintln!("Unable to open the PeerBanHelper window: {error}");
            (self.open)(&WebUi(address));
        }
    }

    fn add_icon(&self, window: HWND) -> Result<(), DesktopError> {
        let mut icon = notify(window);
        icon.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP | NIF_SHOWTIP;
        icon.uCallbackMessage = NOTIFICATION;
        icon.hIcon = self.icon.0;
        let mut tooltip = [0; 128];
        for (destination, unit) in tooltip.iter_mut().take(127).zip(
            self.labels
                .title
                .split('\0')
                .next()
                .unwrap_or_default()
                .encode_utf16(),
        ) {
            *destination = unit;
        }
        icon.szTip = tooltip;
        // SAFETY: `icon` and its resource belong to this live window.
        if unsafe { Shell_NotifyIconW(NIM_ADD, &icon) } == 0 {
            return Err(DesktopError::TrayUnavailable);
        }
        icon.Anonymous.uVersion = NOTIFYICON_VERSION_4;
        if unsafe { Shell_NotifyIconW(NIM_SETVERSION, &icon) } == 0 {
            unsafe { Shell_NotifyIconW(NIM_DELETE, &icon) };
            return Err(DesktopError::TrayUnavailable);
        }
        Ok(())
    }

    fn popup(&self, owner: HWND, parameters: WPARAM) {
        let Some(menu) = Popup::new() else {
            return;
        };
        let snapshot = self.snapshot();
        if let Some(stats) = snapshot.status {
            let ban = label_counts(
                &self.labels.ban_statistics,
                stats.banned_peers,
                stats.banned_ips,
            );
            let downloaders = label_counts(
                &self.labels.downloader_statistics,
                stats.healthy_downloaders,
                stats.total_downloaders,
            );
            menu.append(0, &ban, MF_GRAYED);
            menu.append(0, &downloaders, MF_GRAYED);
            menu.separator();
        }
        let options = if snapshot.web_ui.is_some() {
            MF_STRING
        } else {
            MF_GRAYED
        };
        menu.append(OPEN_WINDOW, &self.labels.open_window, options);
        menu.append(OPEN, &self.labels.open_web_ui, options);
        menu.separator();
        menu.append(EXIT, &self.labels.exit, MF_STRING);
        let (x, y) = menu_position(parameters);
        // SAFETY: all handles belong to this notification and the popup stays alive until completion.
        let command = unsafe {
            SetForegroundWindow(owner);
            let command = TrackPopupMenuEx(
                menu.0,
                TPM_NONOTIFY | TPM_RETURNCMD | TPM_RIGHTBUTTON,
                x,
                y,
                owner,
                null(),
            );
            PostMessageW(owner, WM_NULL, 0, 0);
            Shell_NotifyIconW(NIM_SETFOCUS, &notify(owner));
            command
        };
        self.action(owner, command as u16);
    }
}

struct NativeWindow {
    handle: HWND,
    _state: Box<WindowState>,
    _class: WindowClass,
}

impl NativeWindow {
    fn create(
        labels: TrayLabels,
        menu: Arc<RwLock<MenuState>>,
        exit: watch::Sender<bool>,
        profile: PathBuf,
        open: Browser,
    ) -> Result<Self, DesktopError> {
        let class = WindowClass::register()?;
        let state = Box::new(WindowState {
            labels,
            menu,
            exit,
            open,
            profile,
            icon: Icon::load()?,
            // SAFETY: the name is a valid null-terminated wide string.
            taskbar_created: unsafe { RegisterWindowMessageW(windows_sys::w!("TaskbarCreated")) },
            web_window: RefCell::default(),
            close_pending: Cell::new(false),
        });
        let title = wide(&state.labels.title);
        // SAFETY: the state's allocation remains at a stable address until the window is destroyed.
        let handle = unsafe {
            CreateWindowExW(
                WS_EX_TOOLWINDOW,
                class.name.as_ptr(),
                title.as_ptr(),
                WS_POPUP,
                0,
                0,
                0,
                0,
                null_mut(),
                null_mut(),
                class.instance,
                (&*state as *const WindowState).cast(),
            )
        };
        if handle.is_null() {
            return Err(native_error("create the PeerBanHelper tray window"));
        }
        let window = Self {
            handle,
            _state: state,
            _class: class,
        };
        window._state.add_icon(handle)?;
        Ok(window)
    }

    fn dispatch(&self) {
        let mut message = MSG::default();
        // SAFETY: the message loop only dispatches messages retrieved for its own thread.
        unsafe {
            while GetMessageW(&mut message, null_mut(), 0, 0) > 0 {
                TranslateMessage(&message);
                DispatchMessageW(&message);
            }
        }
    }
}

impl Drop for NativeWindow {
    fn drop(&mut self) {
        // SAFETY: the UI thread owns this window and its callback state is still alive.
        unsafe {
            if IsWindow(self.handle) != 0 {
                DestroyWindow(self.handle);
            }
        }
    }
}

unsafe extern "system" fn window_proc(
    window: HWND,
    message: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        // SAFETY: Win32 supplies the create structure for WM_NCCREATE and the stored pointer
        // belongs to the stable NativeWindow allocation until WM_NCDESTROY clears it.
        unsafe {
            if message == WM_NCCREATE {
                let create = &*(lparam as *const CREATESTRUCTW);
                SetWindowLongPtrW(window, GWLP_USERDATA, create.lpCreateParams as isize);
            } else if message == WM_NCDESTROY {
                SetWindowLongPtrW(window, GWLP_USERDATA, 0);
            }
            let state = (GetWindowLongPtrW(window, GWLP_USERDATA) as *const WindowState).as_ref();
            if let Some(state) = state {
                match message {
                    NOTIFICATION if (lparam as u32 >> 16) == ICON_ID => {
                        match lparam as u16 as u32 {
                            NIN_SELECT | KEY_SELECT => state.action(window, OPEN),
                            WM_CONTEXTMENU => state.popup(window, wparam),
                            _ => {}
                        }
                        return 0;
                    }
                    WM_COMMAND => {
                        state.action(window, wparam as u16);
                        return 0;
                    }
                    WM_CLOSE => {
                        if state.web_window.try_borrow().is_err() {
                            state.close_pending.set(true);
                            return 0;
                        }
                        EndMenu();
                        DestroyWindow(window);
                        return 0;
                    }
                    WM_DESTROY => {
                        Shell_NotifyIconW(NIM_DELETE, &notify(window));
                        PostQuitMessage(0);
                        return 0;
                    }
                    event if event != 0 && event == state.taskbar_created => {
                        let _ = state.add_icon(window);
                        return 0;
                    }
                    _ => {}
                }
            }
            DefWindowProcW(window, message, wparam, lparam)
        }
    }))
    .unwrap_or(0)
}

struct Popup(HMENU);

impl Popup {
    fn new() -> Option<Self> {
        // SAFETY: CreatePopupMenu returns a new menu with no external inputs.
        let menu = unsafe { CreatePopupMenu() };
        (!menu.is_null()).then(|| Self(menu))
    }

    fn append(&self, id: u16, label: &str, flags: MENU_ITEM_FLAGS) {
        let label = wide(label);
        // SAFETY: `label` is UTF-16 with a terminator and is copied by the menu API.
        unsafe { AppendMenuW(self.0, flags, usize::from(id), label.as_ptr()) };
    }

    fn separator(&self) {
        // SAFETY: separator items require no label or command identifier.
        unsafe { AppendMenuW(self.0, MF_SEPARATOR, 0, null()) };
    }
}

impl Drop for Popup {
    fn drop(&mut self) {
        // SAFETY: this is the owning handle for the popup.
        unsafe { DestroyMenu(self.0) };
    }
}

fn menu_position(parameters: WPARAM) -> (i32, i32) {
    let x = i32::from(parameters as u16 as i16);
    let y = i32::from((parameters >> 16) as u16 as i16);
    if (x, y) != (-1, -1) {
        return (x, y);
    }
    let mut point = POINT::default();
    // SAFETY: `point` is writable for the duration of GetCursorPos.
    unsafe { GetCursorPos(&mut point) };
    (point.x, point.y)
}

fn notify(window: HWND) -> NOTIFYICONDATAW {
    NOTIFYICONDATAW {
        cbSize: size_of::<NOTIFYICONDATAW>() as u32,
        hWnd: window,
        uID: ICON_ID,
        ..Default::default()
    }
}

fn wide(value: &str) -> Vec<u16> {
    value
        .split('\0')
        .next()
        .unwrap_or_default()
        .encode_utf16()
        .chain([0])
        .collect()
}

fn label_counts(template: &str, first: u64, second: u64) -> String {
    let mut label = template.replacen("{}", &first.to_string(), 1);
    label = label.replacen("{}", &second.to_string(), 1);
    label
}

fn open_default_browser(web_ui: &WebUi) {
    let url = wide(web_ui.as_url());
    // SAFETY: the shell receives a validated HTTP URL and no command-line parameters.
    let result = unsafe {
        ShellExecuteW(
            null_mut(),
            windows_sys::w!("open"),
            url.as_ptr(),
            null(),
            null(),
            SW_SHOWNORMAL,
        )
    } as isize;
    if result <= 32 {
        eprintln!("Unable to open the PeerBanHelper WebUI (Windows shell code {result}).");
    }
}

fn native_error(operation: &'static str) -> DesktopError {
    DesktopError::Native {
        operation,
        source: io::Error::last_os_error(),
    }
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use windows_sys::Win32::Foundation::RECT;
    use windows_sys::Win32::UI::Shell::{NOTIFYICONIDENTIFIER, Shell_NotifyIconGetRect};

    use super::*;
    use crate::Tray;

    #[tokio::test]
    async fn native_tray_registers_accepts_commands_and_closes() {
        // The shell is absent on the Windows service runner but present on an interactive desktop.
        if unsafe { GetShellWindow() }.is_null() {
            return;
        }
        let (request_exit, exit) = watch::channel(false);
        let (opened, open) = mpsc::channel();
        let menu = Arc::new(RwLock::new(MenuState::default()));
        let labels = TrayLabels {
            title: "PeerBanHelper tray test".into(),
            ..TrayLabels::default()
        };
        let temporary = tempfile::tempdir().unwrap();
        let platform = PlatformTray::start_with_browser(
            labels,
            menu.clone(),
            request_exit,
            temporary.path().join("profile"),
            Box::new(move |web_ui| {
                let _ = opened.send(web_ui.as_url().to_owned());
            }),
        )
        .expect("register the fixture tray icon with Explorer");
        let window = platform.window as HWND;
        let tray = Tray {
            state: menu,
            exit,
            _platform: platform,
        };
        let identifier = NOTIFYICONIDENTIFIER {
            cbSize: size_of::<NOTIFYICONIDENTIFIER>() as u32,
            hWnd: window,
            uID: ICON_ID,
            ..Default::default()
        };
        let mut rect = RECT::default();
        // SAFETY: lookup is limited to the fixture's registered notification icon.
        let known = unsafe { Shell_NotifyIconGetRect(&identifier, &mut rect) };
        assert!(
            known >= 0,
            "Explorer could not locate the notification icon: {known:#x}"
        );

        // Simulate a taskbar refresh using only this fixture's icon and own window.
        let recreated = unsafe { RegisterWindowMessageW(windows_sys::w!("TaskbarCreated")) };
        assert_ne!(recreated, 0);
        assert_ne!(unsafe { Shell_NotifyIconW(NIM_DELETE, &notify(window)) }, 0);
        assert_ne!(unsafe { PostMessageW(window, recreated, 0, 0) }, 0);
        tokio::time::timeout(Duration::from_secs(2), async {
            while unsafe { Shell_NotifyIconGetRect(&identifier, &mut rect) } < 0 {
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        })
        .await
        .unwrap();

        let select = ((ICON_ID << 16) | KEY_SELECT) as LPARAM;
        // SAFETY: both messages target the fixture's own tray window.
        assert_ne!(unsafe { PostMessageW(window, NOTIFICATION, 0, select) }, 0);
        assert!(matches!(
            open.recv_timeout(Duration::from_millis(100)),
            Err(mpsc::RecvTimeoutError::Timeout)
        ));
        tray.set_web_ui(Some(WebUi::from_listener(
            "127.0.0.1:9898".parse().unwrap(),
            Some("a+b"),
        )));
        assert_ne!(unsafe { PostMessageW(window, NOTIFICATION, 0, select) }, 0);
        assert_eq!(
            open.recv_timeout(Duration::from_secs(2)).unwrap(),
            "http://127.0.0.1:9898/?token=a%2Bb"
        );
        assert_ne!(
            unsafe { PostMessageW(window, WM_COMMAND, usize::from(EXIT), 0) },
            0
        );
        tokio::time::timeout(Duration::from_secs(2), tray.exit_requested())
            .await
            .unwrap();

        drop(tray);
        assert_eq!(unsafe { IsWindow(window) }, 0);
    }

    #[test]
    fn legacy_statistics_are_available_as_localized_menu_labels() {
        assert_eq!(label_counts("下载器：{} / {}", 2, 3), "下载器：2 / 3");
    }
}
mod web_window;

use std::cell::{Cell, RefCell};
