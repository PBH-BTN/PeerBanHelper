# 原生启动和便携预览

- `peerbanhelper run` 使用本机配置树。在未初始化时开放既有 Vue OOBE，收到原子保存的有效配置后，先完成已经开始的 HTTP 响应，再启动同进程扫描服务。已有 token 的安装直接使用扫描入口。该路径没有 Java 运行时、JAR 或 Gradle 依赖。
- `rust/packaging/package.ps1` 将原生二进制、构建好的 `webui/dist`、许可和启动说明写到新 staging 路径。CI 复用仓库的 WebUI 构建，计划在 Windows、Linux、macOS 构建原生发布模式并上传 tar.gz 预览包。GitHub 尚无本分支流水线结果。
- Windows 使用本机 cargo 构建的调试二进制及生产 WebUI 产物完成 staging。运行 `test/rust-parity/check_native_startup.py`，回环浏览器入口取得 201，原进程重新建立扫描服务，授权读取下载器并确认 SQLite 封禁历史，未重启该进程。回环确认同一个端口完成初始化和后续访问。
- 初次设置现在可以验证配置中的七种下载器类型和 SQLite、PostgreSQL、MySQL/MariaDB 连接。远程数据库测试只认证并运行 `SELECT 1`，应用启动时才执行 schema 准备。现有 SQLite/qBittorrent 流程已复测，其余 OOBE 组合、客户端自动发现及上线变更未全面验证。
- Windows `run --tray` 用实际 Explorer 托盘走过首次设置、原生扫描和退出，退出时观察到图标被删除。WebView2 夹具在同一进程创建窗口、加载回环 HTTP 及释放 HWND。Linux 已在隔离 D-Bus 会话验收托盘生命周期。macOS AppKit 和 WKWebView 通过两架构桌面 crate 编译和严格 Clippy，仍待运行态验收。[desktop.md](desktop.md)
- 根 `Dockerfile` 使用 Node 25、pnpm 12.21.0 和 Rust 1.96.0 构建 Vue 与原生服务，再交给 Debian Bookworm 运行镜像。Debian WSL Docker 26.1.5 上的 Linux amd64 镜像通过回环首次设置、封禁 SQLite 落库和 SIGTERM 退出码 0。镜像内 `command -v java` 无匹配。原 `/app/data` 路径沿用，`Dockerfile-Release` 接收已构建的同架构原生二进制和 WebUI。[Docker 多阶段构建](https://docs.docker.com/build/building/multi-stage/)
- `rust/packaging/debian/build.sh` 使用容器的 Linux 原生二进制和 WebUI 打成 `peerbanhelper_9.6.0_amd64.deb`。在独立 Bookworm 容器内 `dpkg -i`、`peerbanhelper --version` 和 `dpkg -r` 成功，`/var/lib/peerbanhelper/config` 链到 `/etc/peerbanhelper`，移除包保留配置。Debian 包的默认配置使用原生模板生成，回环检查确认省略 H2 和 Aviator 配置。用同一二进制构建的预编译 Docker 镜像也通过完整启动和 SIGTERM 检查。实际 systemd 启动、原 Debian 包升级和其他架构待实测。
- `rust/packaging/windows` 在独立 Bookworm 容器用 NSIS 3.08 构建了 Windows amd64 安装器。实际 Windows 临时路径静默安装、注册表及快捷方式校验、已安装程序的首次设置与封禁落库、静默卸载均通过，确认清理预览文件和注册项。快捷方式指向已有的 `%LOCALAPPDATA%\PeerBanHelper` 数据位置。旧安装器升级和发行签名待验。[NSIS 脚本参考](https://nsis.sourceforge.io/Docs/Chapter4.html)
- `rust/packaging/freebsd` 提供与现有数据路径匹配的原生 rc.d 和 pkg 构建，增加 FreeBSD 15.1 预览验收任务。DSM 工作流从 Cargo 读取版本，原有 SPK 启动器使用输入的原生容器镜像。FreeBSD VM 结果和 DSM 硬件验证均待取得。[FreeBSD pkg-create](https://man.freebsd.org/cgi/man.cgi?query=pkg-create&sektion=8)
- 默认 `build.sh` 构建 Vue 和 Rust，GitHub 推送、PR 和 Release 产物工作流切换到原生目标。Windows 和 Linux 的 x64/ARM64、macOS 的 x64/ARM64，以及 FreeBSD amd64 均有独立构建目标，尚未触发远端 CI。旧 JAR 镜像和 JVM 工作流作为人工触发的参考。原有注册表、镜像仓库、签名和 CDN 的对外发布未在本地迁移演练中触发。[GitHub 托管运行器](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
