# 验收与剩余兼容性

分支 `codex/rust`，参考 Java `25be37332`。用户确认退出 H2、PF4J、Aviator 和 JVM 运行时。下表记录开发环境取得的证据。

| 验收 | 结果 |
| --- | --- |
| Rust 全量测试 | Debian WSL 原生工作区 193 项通过，另有 4 项专用环境测试。运行 `cargo test --workspace --locked --no-fail-fast`。Windows 已执行 189 项并全部通过，系统以错误 5 拒绝启动 BiglyBT `web_api` 测试二进制，独立重试相同。该套件在 Linux 通过，Windows 的 WebView2 忽略项另行通过。 |
| Vue 生产构建 | `pnpm run build` 通过 TypeScript 和 Vite，保留既有大包提示。|
| Java 行为对照 | Java 基线 6 项通过。规则归并 10 条、qBittorrent 首轮与封禁到期 1 条，Swarm HTTP 7 条、实验室 23 条及 BTN 状态 5 组均有对照证据。 |
| 下载器 | 七种配置通过回环协议或进程测试。另用真实 BiglyBT 和 Aria2Next 验证隔离配置及封禁。 |
| 存储与切换 | SQLite、PostgreSQL 17、MariaDB 11.8、独立 MySQL 8.4 实测。最新 MySQL 存储与图表用例在一次性 MySQL 8.4 容器上再通过。已有库和 ORMLite 升级后的库两次完成独占写入、双向封禁历史与完整列表重放。|
| Windows 桌面与包 | 托盘首次运行、扫描、正常退出和图标清除通过。WebView2 加载本机页面通过。NSIS amd64 安装、已安装进程和带注册清理的卸载通过。ARM64 桌面 crate 通过严格 Clippy，整应用交叉检查受当前主机缺少 ARM64 C 工具链阻挡。 |
| Linux 桌面与包 | SNI 托盘、首次配置和扫描通过隔离 D-Bus 会话，带 WebKitGTK 的实际进程也通过 Xvfb 窗口打开和正常退出。容器与 Debian 包通过隔离安装，日志轮转通过原生用例。|
| FreeBSD 15.1p7 amd64 | 官方镜像客户机中原生发布构建、`pkg add`、已安装服务首次设置和封禁落库、rc.d 启停及保留数据卸载通过。 |
| 运行基线 | 原生 OOBE、保持套接字和会话、重载保留封禁、BTN 新旧上报、日志流、推送与订阅有本地进程测试。18 条原生 CI 脚本通过 Bash 或 PowerShell 语法检查。 |

## 仍需验证或存在差异

- Windows ARM64、macOS 实际窗口、DSM、Debian 原位升级和远端 CI 的结果。旧 Windows 版本、原安装器升级、发布签名及正式外部渠道待验。
- Java 原生桌面的独立日志面板、系统托盘的次要快捷动作、完整多线程堆栈及 JVM 内存转储没有对应的完整 Rust 行为。窗口使用 WebUI 登录，原生运行时已提供网页日志和进程内调用栈。按路径参数归一化扫描源码得到 85 个 Java 路径，缺失的为堆转储和两个未默认注册的 PF4J 控制器路径。此检查仅核对路径名称。
- BTN 官方实例、在线 Plus、实际镜像、外部推送和真实 LAN 路由设备尚待受控实测。BTN 地址语法、部分挑战兼容、旧客户端支持边界及 Java SQL 差异记录在各模块文件。
- 对照夹具覆盖已经录制的输入。剩余差分包含长时间高负载、不同下载器和网络故障组合，以及 PostgreSQL/MySQL/MariaDB 的应用级回退演练。

本地提交前检查 Git 暂存区只包含实施文件、公开夹具与记录。原 Java/Kotlin 源码保留为参考。正式切换顺序和备份检查见 [SQLite 切换记录](sqlite-cutover.md)。
