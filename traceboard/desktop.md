# 原生桌面入口和通知区

负责人 `downloader`。Rust 实现位于 `rust/desktop/`，主应用入口由 `root` 接入，分支 `codex/rust`。Java 参考提交 `25be37332`。

## 行为依据

- `Main#initGUI` 及 `gui/PBHGuiManager` 管理桌面和控制台选择。`SwingMainWindow` 支持收起至托盘。`TrayMenu` 提供显示窗口、打开 WebUI、退出和封禁、下载器统计。`PBHGuiBridge#getWebUiUrl` 使用当前监听端口和 UTF-8 URL 编码的 token。
- Windows 通知区通过 `Shell_NotifyIconW` 注册、接收激活和退出时移除。在 `TaskbarCreated` 后重新注册。菜单和浏览器操作复用系统的 `TrackPopupMenuEx`、`ShellExecuteW`。[Windows 通知区](https://learn.microsoft.com/en-us/windows/win32/shell/taskbar) · [Windows Shell 打开操作](https://learn.microsoft.com/en-us/windows/win32/api/shellapi/nf-shellapi-shellexecutew)

## 实施

- `pbh_desktop::Tray::start(TrayLabels)` 创建专用 Win32 消息线程。复用仓库的 PeerBanHelper 图标，通过系统加载 PNG 资源。左键或键盘激活和菜单的“打开 WebUI”指向正在运行的既有服务。没有可用监听器时禁用打开操作。完全不需要 JVM、SWT 或额外系统 GUI 包。
- `WebUi::from_listener(SocketAddr, Option<&str>)` 使用实际端口。IPv4/IPv6 通配地址对应各自的回环地址，API 支持标准 URL 查询编码。应用的 `run --tray` 打开不带令牌的 URL，由用户通过 WebUI 完成认证。Java 在托盘打开链接时附带令牌。`Tray::set_status` 可更新原托盘的封禁和下载器统计。
- `Tray::exit_requested` 将退出事件交给调用方执行正常的应用关停。释放托盘或结束 macOS 应用循环会注销通知区状态。Windows 还会销毁隐藏窗口。`Tray::supported()` 在 Windows、Linux 和 macOS 返回 `true`，表示编译支持。无界面入口不启动通知区服务。
- Linux 使用 `ksni` 提供 D-Bus StatusNotifierItem 与 DBusMenu，依赖仅在 Linux 目标启用。菜单包含封禁和下载器统计、打开 WebUI 与退出，用同一份原图标发布 ARGB 图像。菜单和托盘激活都向 `xdg-open` 传递带实际监听端口的 URL，失败时回退 `gio open`。Linux 通知区剔除 URL 查询参数，WebUI 保留常规认证流程。桌面会话需要一个支持 SNI 的 watcher，首次注册失败会结束显式的 `--tray` 启动，已启动后支持 watcher 重启时重新注册。 [StatusNotifierItem 规范](https://specifications.freedesktop.org/status-notifier-item/latest-single/) · [ksni 源码](https://github.com/iovxw/ksni)
- macOS 的 `run_macos_tray` 在主线程运行 AppKit 应用与 `NSStatusItem`。后台线程执行 Rust 服务。原生菜单在打开时读取现有托盘统计和 WebUI 端口。点击图标打开菜单，WebUI 项通过 `NSWorkspace` 打开剔除查询串的 URL，退出项使用已有应用关停通道。服务返回后由主线程的 GCD 队列停止事件循环并移除状态项。 [Apple AppKit 应用事件循环](https://developer.apple.com/documentation/appkit/nsapplication) · [Apple 菜单接口](https://developer.apple.com/documentation/appkit/menus-cursors-and-the-dock)
- macOS “显示窗口”由 Java `TrayMenu` 的本地化条目 `GUI_MENU_SHOW_WINDOW` 接入。它在现有 AppKit 主线程中按需创建一个可缩放的 `NSWindow` 和 `WKWebView`，打开应用当前 WebUI 端口和普通登录页面，加载 URL 时剔除查询参数。关闭窗口保留当前 WebKit 会话，服务与菜单栏图标继续运行，重新选择时复用窗口。监听地址变化后重新打开会导航到新地址。同级 WebUI 菜单项继续用系统浏览器。嵌入式页面中用户点击要求新开窗口的 HTTP(S) 链接也交给系统浏览器。 [Apple WKWebView](https://developer.apple.com/documentation/webkit/wkwebview) · [Apple NSWindow](https://developer.apple.com/documentation/appkit/nswindow)
- Linux 可用 `cargo build --locked -p peerbanhelper --features linux-webview` 构建带嵌入式窗口的桌面二进制。该功能启用 GTK 3 和 WebKitGTK 4.1，默认 Linux 二进制继续提供 SNI 托盘和无界面服务。图形构建的 `run --tray` 在进程主线程运行 GTK/WebKit 事件循环，服务在工作线程运行。GTK 初始化成功后才出现“显示窗口”。窗口按需创建并在同一个 WebView 内加载现有 WebUI，URL 剔除查询参数，浏览器动作仍可用。关闭窗口时保留会话，Quit 在服务关停后销毁窗口。 [Debian WebKitGTK 4.1 软件包](https://packages.debian.org/source/stable/webkit2gtk) · [GTK 3 Rust 线程约定](https://gtk-rs.org/gtk3-rs/stable/latest/docs/gtk/)

## 验证

- Windows 上 `cargo +1.96.0 test -p pbh-desktop` 通过 3 项，严格 Clippy 通过。原生夹具通过当前会话的 Explorer 验证实际图标注册，模拟自身图标被移除后收到 TaskbarCreated。向夹具窗口发送命令，校验 WebUI 未就绪时拒绝打开、就绪后传递完整编码地址、退出通知和窗口销毁。打开操作使用注入的测试接收端，避免打开用户浏览器。
- Debian WSL2 上 `cargo +1.96.0 test --locked -p pbh-desktop` 通过 2 个无桌面依赖单元测试，`cargo +1.96.0 clippy --locked -p pbh-desktop --all-targets -- -D warnings` 通过。`dbus-run-session -- cargo +1.96.0 test --locked -p pbh-desktop watcher_menu_activation_and_exit_use_the_session_bus -- --ignored` 验证 D-Bus 注册、图标、启用和禁用菜单项、动态统计、watcher 重注册、托盘激活、无令牌的 URL、Exit 通知、D-Bus 注销及未提供 watcher 的启动失败。
- Debian WSL2 的实际 Linux 应用通过 `PBH_LINUX_TRAY_BINARY=<已构建的 peerbanhelper> dbus-run-session -- cargo +1.96.0 test --locked -p pbh-desktop native_service_keeps_tray_through_oobe_and_shuts_down_on_exit -- --ignored`。同一个托盘项在同一个端口完成 OOBE 并进入已认证扫描，回环 qBittorrent 返回一个种子和一个 Peer。两次打开都向隔离 PATH 中的 `xdg-open` 夹具发送不含令牌的 URL，Exit 使实际进程以 0 退出并注销 D-Bus 服务。构建使用测试缓存中解包的 Debian OpenSSL 头文件及系统已有的共享库。
- Windows 上 `python -B test/rust-parity/check_desktop_startup.py target/debug/peerbanhelper.exe webui/dist` 通过。实际 `peerbanhelper run --tray` 保持同一个通知区窗口，通过回环完成 OOBE，进入已认证扫描并持久化封禁历史。向该子进程的托盘窗口发出 Exit 后，服务以退出码 0 结束，托盘图标消失。
- Debian WSL2 上 `cargo +1.96.0 check --locked -p pbh-desktop --all-targets` 对 `aarch64-apple-darwin` 和 `x86_64-apple-darwin` 均通过，覆盖 `objc2` 类型、AppKit 导出、后台服务句柄与测试编译。两个目标的严格 Clippy 也通过。
- 加入 WKWebView 后重新在两个 Darwin 目标运行 `cargo +1.96.0 clippy --locked -p pbh-desktop --all-targets --target <darwin-target> -- -D warnings`，均通过。原生 macOS 上加载回环 HTTP、首次设置与登录、新开链接、关闭和重开窗口仍待验证。打包为 `.app` 时需验证 App Transport Security 对回环 HTTP 的处理。 [Apple ATS 文档](https://developer.apple.com/documentation/security/preventing-insecure-network-connections)
- Linux 可选嵌入式构建及严格 Clippy 通过。验证环境为 Debian Bookworm 容器、GTK 3.24.38、WebKitGTK 2.50.6、Xvfb 和 `dbus-run-session`，容器仅使用回环，WebKit 沙箱设置维持默认值。`cargo build --locked --offline -p peerbanhelper --features linux-webview` 后，用 `PBH_LINUX_TRAY_BINARY=<二进制> dbus-run-session -- xvfb-run -a cargo test --locked --offline -p pbh-desktop --features linux-webview native_service_keeps_tray_through_oobe_and_shuts_down_on_exit -- --ignored` 验证实际应用完成 OOBE、扫描一个种子与一个 Peer、打开现有浏览器动作，并且能从 DBusMenu 打开图形窗口。X11 查询确认窗口可见，内嵌页面的脚本回环请求确认 WebUI 地址没有查询串。Quit 返回退出码 0，注销托盘并销毁窗口。首轮将 GTK 置于工作线程时退出会触发 `SIGABRT`，迁至进程主线程后验证通过。无 DISPLAY 复测时保留浏览器动作和 Quit，菜单省略“显示窗口”。默认 Linux 应用依赖树未引入 WebKitGTK。

## 桌面动作对照

- `SwingMainWindow` 和 `WebUITab` 提供主窗口、隐藏到托盘与 SWT 浏览器。Java 隐藏时回收浏览器，并通过 URL 参数静默登录。macOS Rust 复用同一个 WebKit 会话并采用普通 WebUI 认证。
- 原生 `LogsTab`、`WindowMenuBar` 打开数据目录、复制 WebUI 令牌、GitHub 和关于/鸣谢，以及 `SwingGuiImpl` 的通知、对话框和 `SwingTaskbarControl` 进度仍待桌面移植。WebUI 日志和帮助可由应用现有入口使用。

## 尚待验收

- Windows 默认浏览器真实打开、托盘交互的人工观测以及安装器入口仍待验收。
- Linux 的 Wayland、GNOME/KDE 面板实际交互与默认浏览器显示仍待实机验证。macOS 的 AppKit 链接、真实菜单栏显示、菜单和默认浏览器交互仍需在原生 macOS 环境验证。
