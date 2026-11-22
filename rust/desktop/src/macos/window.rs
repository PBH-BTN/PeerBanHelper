//! A reusable native WebUI window owned by the AppKit tray on the main thread.

use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2::{MainThreadMarker, MainThreadOnly, define_class, msg_send, sel};
use objc2_app_kit::{NSApplication, NSBackingStoreType, NSWindow, NSWindowStyleMask, NSWorkspace};
use objc2_foundation::{
    NSObject, NSObjectProtocol, NSPoint, NSRect, NSSize, NSString, NSURL, NSURLRequest,
};
use objc2_web_kit::{
    WKNavigationAction, WKNavigationType, WKUIDelegate, WKWebView, WKWebViewConfiguration,
    WKWindowFeatures,
};
use url::Url;

pub(super) struct EmbeddedWindow {
    window: Retained<NSWindow>,
    web_view: Retained<WKWebView>,
    destination: Option<Url>,
    _links: Retained<ExternalLinks>,
}

impl EmbeddedWindow {
    pub(super) fn new(marker: MainThreadMarker, title: &str) -> Self {
        let frame = NSRect::new(NSPoint::new(0.0, 0.0), NSSize::new(1100.0, 720.0));
        let style = NSWindowStyleMask::Titled
            | NSWindowStyleMask::Closable
            | NSWindowStyleMask::Miniaturizable
            | NSWindowStyleMask::Resizable;
        // SAFETY: construction and subsequent use are on the process main thread.
        let window = unsafe {
            NSWindow::initWithContentRect_styleMask_backing_defer(
                NSWindow::alloc(marker),
                frame,
                style,
                NSBackingStoreType::Buffered,
                false,
            )
        };
        // SAFETY: the Rust handle owns this window, including after the user closes it.
        unsafe { window.setReleasedWhenClosed(false) };
        window.setTitle(&NSString::from_str(title));
        window.center();

        // SAFETY: the frame and configuration are created here on the main thread.
        let web_view = unsafe {
            let configuration = WKWebViewConfiguration::new(marker);
            WKWebView::initWithFrame_configuration(WKWebView::alloc(marker), frame, &configuration)
        };
        let links = ExternalLinks::new(marker);
        // SAFETY: this window holds the link delegate for the lifetime of the web view.
        unsafe { web_view.setUIDelegate(Some(ProtocolObject::from_ref(&*links))) };
        window.setContentView(Some(&web_view));
        Self {
            window,
            web_view,
            destination: None,
            _links: links,
        }
    }

    pub(super) fn show(&mut self, marker: MainThreadMarker, destination: Url) {
        if self.destination.as_ref() != Some(&destination) {
            let Some(url) = NSURL::URLWithString(&NSString::from_str(destination.as_str())) else {
                return;
            };
            let request = NSURLRequest::requestWithURL(&url);
            // SAFETY: the request has a valid HTTP URL and the view is used on the main thread.
            if unsafe { self.web_view.loadRequest(&request) }.is_some() {
                self.destination = Some(destination);
            }
        }
        if self.window.isMiniaturized() {
            self.window.deminiaturize(None);
        }
        self.window.makeKeyAndOrderFront(None);
        let app = NSApplication::sharedApplication(marker);
        if app.respondsToSelector(sel!(activate)) {
            app.activate();
        } else {
            // macOS before 14 provides the older activation method.
            #[allow(deprecated)]
            app.activateIgnoringOtherApps(true);
        }
    }
}

impl Drop for EmbeddedWindow {
    fn drop(&mut self) {
        // SAFETY: the window and its weak delegate are still live on the main thread.
        unsafe { self.web_view.stopLoading() };
        self.window.close();
    }
}

define_class!(
    #[unsafe(super(NSObject))]
    #[thread_kind = MainThreadOnly]
    #[name = "PeerBanHelperExternalWebUiLinks"]
    struct ExternalLinks;

    unsafe impl NSObjectProtocol for ExternalLinks {}

    unsafe impl WKUIDelegate for ExternalLinks {
        #[unsafe(method_id(webView:createWebViewWithConfiguration:forNavigationAction:windowFeatures:))]
        unsafe fn open_tab(
            &self,
            _web_view: &WKWebView,
            _configuration: &WKWebViewConfiguration,
            action: &WKNavigationAction,
            _features: &WKWindowFeatures,
        ) -> Option<Retained<WKWebView>> {
            // SAFETY: WebKit supplies a valid navigation action for this main-thread callback.
            if unsafe { action.navigationType() } == WKNavigationType::LinkActivated {
                let request = unsafe { action.request() };
                if let Some(url) = request.URL()
                    && url.scheme().is_some_and(|scheme| {
                        matches!(scheme.to_string().as_str(), "http" | "https")
                    })
                {
                    NSWorkspace::sharedWorkspace().openURL(&url);
                }
            }
            None
        }
    }
);

impl ExternalLinks {
    fn new(marker: MainThreadMarker) -> Retained<Self> {
        let this = Self::alloc(marker).set_ivars(());
        // SAFETY: NSObject initializes the newly allocated delegate on the main thread.
        unsafe { msg_send![super(this), init] }
    }
}
