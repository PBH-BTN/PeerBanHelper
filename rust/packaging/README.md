# PeerBanHelper Rust preview

Open a terminal in this directory. On Windows run `./peerbanhelper.exe run`. On Linux or macOS run `./peerbanhelper run`. The service hosts the packaged WebUI at `http://127.0.0.1:9898` by default. The first run creates configuration files under `data/config` and opens the setup wizard on that address.

To use a different directory, pass `--data-dir PATH`. Use a copy of the Java installation's data for preview testing. Stop the Java service before letting this binary apply changes to the same downloaders or write to the same database. The Rust process locks its data directory against other Rust writers.

Pass `--tray` on Windows, Linux and macOS to show the notification-area controls. The embedded window uses WebView2 on Windows and WKWebView on macOS. Linux uses StatusNotifierItem with the session bus and offers an embedded window in builds with the `linux-webview` feature.

Build the native container from the repository root with `docker build -t peerbanhelper-rust .`. To package existing artifacts with `Dockerfile-Release`, first put a Linux executable compatible with Debian Bookworm in `target/release/peerbanhelper` and build `webui/dist`. The container uses the existing `/app/data` persistent mount and port 9898. On Linux, use host networking if your endpoint URLs refer to downloaders on the host. The entry point runs the same `peerbanhelper run` lifecycle as the portable package.

This package is for migration testing. H2, PF4J and Aviator are retired from the Rust implementation. See the migration traceboard in the source repository for compatibility and verification status.
