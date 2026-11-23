# PeerBanHelper Rust 迁移看板

- 实施分支 `codex/rust`，Java 参考提交 `25be37332`（9.6.0）。Java 和 Kotlin 源码按仓库 `AGENTS.md` 作为只读参考。任何生产入口、发布产物或运行时依赖最终都需脱离 JVM。
- 用户已授权独立迭代及子代理分工，明确放弃 H2、PF4J 和 Aviator。Aviator 用户脚本和 BTN 下发脚本均不运行，需追踪编辑器、脚本 API 和能力提示的退役路径。SQLite/PostgreSQL/MySQL/MariaDB、下载器、BTN 其余能力、WebUI 和现有发布场景仍在范围内。
- 在本目录记录每项工作的负责方、Java 来源、实施路径、测试命令与结果、已知差异和下一步。每个子代理先读本页，再编辑自己的记录。以测试证据标记完成，保留未验项目。
- 分阶段方法参考 [migrate-to-rust](https://github.com/SynthLuvr/migrate-to-rust/tree/main/framework)。Java 仅用于开发期取证与行为对照。首批以小单元验证双实现比较工具，再连接标准 qBittorrent、IP 规则和 SQLite。

| 工作 | 负责人 | 状态 | 记录 |
| --- | --- | --- | --- |
| 阶段 0–2 参考版、功能清单、比较工具 | parity | 首批通过，逐功能扩围 | [parity.md](parity.md) |
| qBittorrent 标准版和增强版 | downloader | 首批已过本地协议测试 | [qbittorrent.md](qbittorrent.md) |
| Transmission | downloader | 首批已过协议和应用测试 | [transmission.md](transmission.md) |
| Deluge | downloader | 首批已过协议和应用测试 | [deluge.md](deluge.md) |
| Aria2Next | downloader | 已通过协议及回环真客户端测试 | [aria2next.md](aria2next.md) |
| BiglyBT 协议 2 | downloader | 已通过协议及回环真客户端测试，按既有决定保留新版协议 | [biglybt.md](biglybt.md) |
| BitComet | downloader | 已通过协议和应用测试 | [bitcomet.md](bitcomet.md) |
| 下载器静态 IP 映射策略 | downloader / root | 共用策略接入七种配置并通过定向验证 | [address-remapping.md](address-remapping.md) |
| BTN 协商、IP 查询、规则与上报 | parity / root | 回环已验证新旧上报、重试、允许列表解封、多网卡心跳和请求级任务事件 | [btn.md](btn.md) |
| GeoIP | parity / root | 原生查询、镜像回退和三类下载任务通过回环，真实镜像待验 | [geoip.md](geoip.md) |
| AutoSTUN 和 UPnP | downloader / root | 协议、转发、配置切换及关停已在回环验证，真实路由器待验 | [autostun.md](autostun.md) |
| SQLite、PostgreSQL、MySQL/MariaDB | storage / root | 已运行 SQLite、PostgreSQL 17、MariaDB 11.8 和独立 MySQL 8.4 存储验证 | [storage.md](storage.md) |
| SQLite 单写入方切换与回退 | storage / root | 现代及旧 ORMLite 数据两次完成 Java → Rust → Java 演练 | [sqlite-cutover.md](sqlite-cutover.md) |
| Swarm 跟踪和 HTTP 读取 | storage / parity / root | 原生采样、写入和 BTN 已接入，HTTP 已过双运行时样本 | [swarm-http.md](swarm-http.md) |
| 流量日记、限速、会话统计 | storage / root | 三引擎存储、qBittorrent 流量测试及会话仪表盘已验 | [integration.md](integration.md) |
| 告警与推送 | storage / root | 告警读写和回环 Webhook/SMTP 已验，外部渠道待验 | [alerts.md](alerts.md) |
| 排行、种子和 Peer 历史 | storage | 排行、种子详情和授权历史读取通过回环，继续补齐查询 | [history-http.md](history-http.md) |
| PBH Plus | parity | Java/Rust 本机试用双向兼容，在线签名缓存通过回环验证，官方服务待实测 | [plus.md](plus.md) |
| HTTP、WebUI 引导和认证 | parity / root | 原生首次设置、令牌、会话、配置重载和 WebUI 生产构建通过 | [web.md](web.md) |
| 设置重载及旧 YAML | root / storage | 原生重载和两次旧 YAML 启动通过，备份与原文件逐字节一致 | [settings.md](settings.md)、[config-migrations.md](config-migrations.md) |
| 下载器管理和本机发现 | downloader | 管理回环通过，发现协议和过滤通过注入测试 | [downloader-api.md](downloader-api.md) |
| 代理与 DNS | downloader / root | HTTP、SOCKS、UDP/TCP PTR 与 DoH 已验回环 | [proxy.md](proxy.md)、[settings.md](settings.md) |
| 后台任务、实时日志及滚动日志 | parity / root | 定向 SSE 已验，GeoIP 三任务已与 Java 对照，日志轮转和原生持久日志通过 | [background-tasks.md](background-tasks.md)、[settings.md](settings.md) |
| 图表 | storage | 三组通用统计和七组 Plus 图表已接入，记录了 Java SQL 差异 | [metric-charts.md](metric-charts.md)、[plus-charts.md](plus-charts.md) |
| 实验室 | parity | 五组实验的 23 条 HTTP 响应与 Java 参考进程匹配 | [laboratory.md](laboratory.md) |
| 核心规则、运行入口和当前封禁页面 | root | 封禁读写、下发失败恢复已验证，继续扩围 | [integration.md](integration.md) |
| 原生便携包和桌面入口 | root / downloader | Windows 安装、托盘和 WebView2 已验，Linux SNI 已验，macOS 两架构桌面编译已验 | [native-startup.md](native-startup.md)、[desktop.md](desktop.md) |
| 原生 Linux 容器和 Debian 包 | root | Docker 首次设置、封禁落库和 SIGTERM 已验，Debian 包隔离安装移除已验 | [native-startup.md](native-startup.md) |
| FreeBSD 原生包 | root / storage | FreeBSD 15.1p7 amd64 原生构建、装包、首次配置与扫描、rc.d 和保留数据卸载均通过 | [freebsd.md](freebsd.md) |
| 单独发布与剩余兼容差异 | root | 源码和预编译构建入口已转原生，外部发布和实机矩阵待验 | [validation.md](validation.md) |

SQLite 单写入方验证和回退演练已有实测证据。外部连接、不同平台的桌面和生产发布仍按 [验证矩阵](validation.md) 追踪。
