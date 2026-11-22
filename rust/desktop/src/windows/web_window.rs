//! A WebView2 window owned by the tray thread and its COM apartment.

use std::num::NonZeroIsize;
use std::path::PathBuf;
use std::ptr::{null, null_mut};

use url::Url;
use windows_sys::Win32::Foundation::{HINSTANCE, HWND, LPARAM, LRESULT, WPARAM};
use windows_sys::Win32::System::LibraryLoader::GetModuleHandleW;
use windows_sys::Win32::System::Threading::GetCurrentThreadId;
use windows_sys::Win32::UI::WindowsAndMessaging::*;
use wry::raw_window_handle::{
    HandleError, HasWindowHandle, RawWindowHandle, Win32WindowHandle, WindowHandle,
};
use wry::{WebContext, WebView, WebViewBuilder};

use super::{native_error, wide};
use crate::DesktopError;

#[derive(Debug, thiserror::Error)]
pub(super) enum Error {
    #[error(transparent)]
    Native(#[from] DesktopError),
    #[error("WebView2 could not show the local WebUI")]
    WebView(#[source] wry::Error),
}

pub(super) struct Window {
    // Close the controller before releasing its Win32 parent.
    view: WebView,
    host: Host,
    address: Url,
}

impl Window {
    pub(super) fn new(title: &str, address: &Url, profile: PathBuf) -> Result<Self, Error> {
        let host = Host::new(title)?;
        let mut context = WebContext::new(Some(profile));
        let view = WebViewBuilder::new_with_web_context(&mut context)
            .with_url(address.as_str())
            .build(&host)
            .map_err(Error::WebView)?;
        Ok(Self {
            view,
            host,
            address: address.clone(),
        })
    }

    pub(super) fn show(&mut self, address: &Url) -> Result<(), Error> {
        if address != &self.address {
            self.view
                .load_url(address.as_str())
                .map_err(Error::WebView)?;
            self.address.clone_from(address);
        }
        // SAFETY: this handle is live and belongs to the current tray thread.
        unsafe {
            ShowWindow(self.host.window, SW_RESTORE);
            SetForegroundWindow(self.host.window);
        }
        Ok(())
    }
}

struct Host {
    window: HWND,
    instance: HINSTANCE,
    class: Vec<u16>,
}

impl Host {
    fn new(title: &str) -> Result<Self, DesktopError> {
        // SAFETY: these calls identify the current Windows executable and thread.
        let (instance, thread) = unsafe { (GetModuleHandleW(null()), GetCurrentThreadId()) };
        if instance.is_null() {
            return Err(native_error("identify the PeerBanHelper executable"));
        }
        let class = wide(&format!(
            "PeerBanHelper.WebUI.{}.{thread}",
            std::process::id()
        ));
        let definition = WNDCLASSW {
            hInstance: instance,
            lpfnWndProc: Some(window_proc),
            lpszClassName: class.as_ptr(),
            ..WNDCLASSW::default()
        };
        // SAFETY: Win32 reads the descriptor and copies its null-terminated name.
        if unsafe { RegisterClassW(&definition) } == 0 {
            return Err(native_error("register the PeerBanHelper WebUI window"));
        }
        let mut host = Self {
            window: null_mut(),
            instance,
            class,
        };
        let title = wide(title);
        // SAFETY: the window class was registered by this thread; title and class remain valid.
        host.window = unsafe {
            CreateWindowExW(
                0,
                host.class.as_ptr(),
                title.as_ptr(),
                WS_OVERLAPPEDWINDOW | WS_CLIPCHILDREN,
                CW_USEDEFAULT,
                CW_USEDEFAULT,
                1120,
                780,
                null_mut(),
                null_mut(),
                instance,
                null(),
            )
        };
        if host.window.is_null() {
            return Err(native_error("create the PeerBanHelper WebUI window"));
        }
        Ok(host)
    }
}

impl HasWindowHandle for Host {
    fn window_handle(&self) -> Result<WindowHandle<'_>, HandleError> {
        let handle = NonZeroIsize::new(self.window as isize).ok_or(HandleError::Unavailable)?;
        // SAFETY: Host owns the Win32 window for the entire borrow.
        Ok(unsafe {
            WindowHandle::borrow_raw(RawWindowHandle::Win32(Win32WindowHandle::new(handle)))
        })
    }
}

impl Drop for Host {
    fn drop(&mut self) {
        // SAFETY: the window and its class are constructed and destroyed on the same thread.
        unsafe {
            if !self.window.is_null() {
                DestroyWindow(self.window);
            }
            UnregisterClassW(self.class.as_ptr(), self.instance);
        }
    }
}

unsafe extern "system" fn window_proc(
    window: HWND,
    message: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    // SAFETY: Windows invokes this callback with a live window on the owning UI thread.
    unsafe {
        if message == WM_CLOSE {
            ShowWindow(window, SW_HIDE);
            return 0;
        }
        DefWindowProcW(window, message, wparam, lparam)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{self, BufRead, BufReader, Write};
    use std::net::TcpListener;
    use std::sync::mpsc;
    use std::time::{Duration, Instant};

    #[test]
    #[ignore = "requires the Windows WebView2 runtime"]
    fn native_window_fetches_the_loopback_webui_and_releases_the_host() {
        let _com = super::super::Apartment::initialize().unwrap();
        let server = TcpListener::bind("127.0.0.1:0").unwrap();
        server.set_nonblocking(true).unwrap();
        let address =
            Url::parse(&format!("http://{}/fixture", server.local_addr().unwrap())).unwrap();
        let (requested, request) = mpsc::channel();
        let deadline = Instant::now() + Duration::from_secs(15);
        let fixture = std::thread::spawn(move || {
            while Instant::now() < deadline {
                let (mut stream, _) = match server.accept() {
                    Ok(connection) => connection,
                    Err(error) if error.kind() == io::ErrorKind::WouldBlock => {
                        std::thread::sleep(Duration::from_millis(10));
                        continue;
                    }
                    Err(error) => panic!("fixture accept: {error}"),
                };
                stream
                    .set_read_timeout(Some(Duration::from_millis(500)))
                    .unwrap();
                let mut reader = BufReader::new(&stream);
                let mut line = String::new();
                // Chromium may start a speculative connection without sending a request.
                if !matches!(reader.read_line(&mut line), Ok(length) if length > 0) {
                    continue;
                }
                let mut header = String::new();
                while matches!(reader.read_line(&mut header), Ok(length) if length > 2) {
                    header.clear();
                }
                stream
                    .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 9\r\nConnection: close\r\n\r\nWebUI ok!")
                    .unwrap();
                requested.send(line).unwrap();
                return;
            }
        });

        let temporary = tempfile::tempdir().unwrap();
        let profile = temporary.path().join("profile");
        let window =
            Window::new("PeerBanHelper WebView2 fixture", &address, profile.clone()).unwrap();
        let handle = window.host.window;
        let response = loop {
            if let Ok(line) = request.try_recv() {
                break line;
            }
            assert!(
                Instant::now() < deadline,
                "WebView2 did not request the fixture page"
            );
            let mut message = MSG::default();
            // SAFETY: the test owns the hidden HWND and pumps its apartment messages.
            unsafe {
                while PeekMessageW(&mut message, null_mut(), 0, 0, PM_REMOVE) != 0 {
                    TranslateMessage(&message);
                    DispatchMessageW(&message);
                }
            }
            std::thread::sleep(Duration::from_millis(10));
        };
        fixture.join().unwrap();
        assert!(profile.is_dir());
        assert!(response.starts_with("GET /fixture HTTP/"), "{response:?}");
        drop(window);
        // SAFETY: the handle is used only for a Win32 liveness query.
        assert_eq!(unsafe { IsWindow(handle) }, 0);
    }
}
