# 首批 Rust 集成记录

## 范围与来源

- `rust/core` 移植 `PeerAction`、`DigestionSession` 的同 Peer 结果归并，以及 `IPBlackList.shouldBanPeer` 的握手、端口和 IP/CIDR 判断。支持文档中的 IPv4 点分掩码和 IPv4 映射地址。还原 Java 的全部 IP 表达式、GeoIP 条件、IP 缓存和所有其他规则仍待继续。
- `rust/app` 增加 `compare-decisions`，按阶段 2 约定从 JSONL 读入决策，保留同级同长的最后到达项。预览入口 `scan --data-dir <目录>` 使用 `config/config.yml`、`config/profile.yml` 和现行 SQLite 文件名，已接入标准及增强 qBittorrent。
- `scan` 以只读观察为默认。`--apply` 明确启用首条链路的真实副作用，当前覆盖本地 IP/端口、Peer ID 和客户端名称规则。元数据遵从 Java `BanMetadata`、`PeerWrapper` 和 `TorrentWrapper`，写入使用 SQLite 单事务保存历史和列表，再将列表同步到下载器。同步失败保留持久状态并在下一轮尝试全量同步。候选按启动全量、后续增量及到期全量清除次序下发，初版的首次封禁请求差异已修复。
- 字符串规则复用 `RuleParser` 的 FALSE 否决、最后命中和 UTF-16 长度，来源见 `rust/core/src/string_rules.rs`。正则使用原生 `fancy-regex` 引擎，Java 所有正则方言、Unicode 版本差异和复杂规则输出需要扩大实测语料。多规则同优先级同有效时长的可见原因，在 Java 由异步到达顺序决定，Rust 当前按执行顺序返回。
- 预览扫描增加反吸血迅雷预设、`AutoRangeBan` 和空闲连接保护。网段规则使用当前轮开始前的活跃封禁并排除仅断连条目。空闲连接保护复用 Java 的毫秒计算、状态比较和按批次过期条件，Rust 用单线程有序批次和明确的五分钟过期代替 Guava 软引用回收，跨种子调度、地址文本和所有时间线仍需差分。
- `scan --apply --webui-dir <目录>` 提供同进程 WebUI 静态文件、认证和 `GET /api/bans`，按时间分页、查询和翻译封禁原因。GeoIP、精确 Java `toString()` 搜索语义和人工封禁/解封 HTTP 操作待完成。运行 `serve` 启动独立的首次配置及 HTTP 预览。扫描进程对自身数据目录加写入锁，旧 Java 进程不会识别该锁，部署回退仍需单写入方编排。
- Rust 预览完整依赖构建先使用 Windows x86-64。`cargo 1.100.0` 在本机错误地尝试启动没有 `.exe` 后缀的构建脚本，退出 OS error 5。项目暂固定 Rust `1.96.0`，该版本在同一环境执行 Rust 核心和 SQLite 测试成功。

## 扫描、记录和管理接口

- 应用已接入标准 qBittorrent、增强版、Transmission、Deluge、Aria2Next、BiglyBT 及 BitComet。各适配器记录分别列出使用的真客户端或回环夹具及剩余协议差异。
- Peer 历史模块根据已有 profile 配置采集和清理 SQLite、PostgreSQL、MySQL/MariaDB 历史。Swarm 使用保持采样顺序的独立缓冲，首次进度和速度保持初值，随后更新速度峰值和计数累计值。会话启动按 Java 语义清空 Swarm，保留 ID 序列及 BTN 游标。Swarm 计数和详情在模块启用时注册；查询及 BTN 读取共用持久层。
- GeoIP 使用旧版数据目录、配置、语言及禁用开关。启动应用时优先加载本地库，持续写入模式后台更新并一次切换内存快照。观察和单次扫描仅读缓存。规则、历史和活跃 Peer 读取同一波次快照。HTTP 地区读取尚未与现行完整地区数据做对照。
- `PUT /api/bans` 和 `DELETE /api/bans` 接入主扫描的单写入队列。支持批量主机地址和 `DELETE ["*"]`。手动封禁采用 Java 的合成任务、原因及 100 年期限。请求经现有令牌或会话认证，演示模式拒绝写操作。CIDR 写入与 Java 的地址范围支持差异仍需补齐，单项移除会在下一轮同步。
- ActiveMonitoring 在立即启动、每分钟及正常关停采样已登录下载器。支持按现行 Java 字段控制滑动限速，继续使用所有下载器过去 24 小时总上传量，保留各下载器下载限制。发行模板和 Java 实际使用的阈值键拼法不一致，当前遵从运行时读入的 `daily-max-allowed-upload-traffic`。每日告警在数据库持久化并按日去重，测试覆盖告警已读后不会重复发布。流量与会话分析图表使用 PBH Plus 的既有 `basic` 权限。
- 日志历史和 SSE 共用有界原生缓冲和线性偏移。HTTP 开启、扫描完成及主要扫描告警写入缓冲。日志路由使用令牌或会话，演示模式拒绝两种读取。原有日志归档、级别动态设置及后台任务流尚未接入。
- 日清理处理 30 天告警和 `persist.ban-logs-keep-days` 下的封禁记录。应用会话采样已验证实际 qBittorrent 帧和仪表盘周指标。

## 验证记录

| 检查 | 结果 |
| --- | --- |
| `cargo test -p pbh-core -p peerbanhelper` | 通过 5 项，覆盖握手跳过、端口优先、CIDR、点分掩码、字符串优先级、递归条件、正则全匹配和 UTF-16 长度。 |
| Java 测试、裁判自检 | 见 `parity.md`。 |
| 构建与决策归并比较 | `cargo build -p peerbanhelper` 成功，10 个静态契约样本无差异。 |
| Java 与 Rust 真实请求、历史对照 | qBittorrent 回环 mock 接收相同的启动全量空列表、增量封禁和到期全量清理。两个 SQLite 都记录同一地址、端口及 1400 ms 时长。`capture_qbittorrent.py --rust` 生成的结果与 Java 运行样本比较通过，1/1。 |
| `python -B test/rust-parity/check_scan_recovery.py target/debug/peerbanhelper.exe` | 只读观察无写入；增量下发收到 HTTP 503 后重启，原封禁全量下发，历史维持一条。 |
| `python -B test/rust-parity/check_transmission_scan.py target/debug/peerbanhelper.exe` | 通过真实回环 HTTP 下载封禁列表，重启后过期封禁移除，保留历史，返回空列表兼容占位。 |
| `python -B test/rust-parity/check_deluge_scan.py target/debug/peerbanhelper.exe` | 通过标准与旧别名、增量封禁和到期解封。 |
| PostgreSQL 与 MariaDB 应用接入 | 在隔离 PostgreSQL 17.12、MariaDB 11.8.6 上扫描并重启，两个实例各保留一条历史和一个活跃封禁，且应用从持久状态全量重发。临时实例已停止。 |
| PCB 进度状态与短时断连 | 单元测试覆盖下载器计数器重置、等待窗口边界及快测规则优先级。原生 CLI 以回环 qBittorrent 快测，确认 SQLite 记录累计上传，当前封禁标有断连且历史仍为空。 |
| BTN 规则及缓存接入 | 原生应用按协商周期拉取规则、允许列表和拒绝列表，使用现有 Metadata 键保存缓存，后台重配可替换定时任务。允许名单命中可解除现有封禁。全链路与真实 BTN 尚未对照。 |
| `python -B test/rust-parity/check_btn_reporting.py target/debug/peerbanhelper.exe` | 封禁及 Swarm 上报收到 HTTP 503 后各自重试相同批次，服务端确认后才持久化游标，后续周期没有重复发送。 |
| 原生 WebUI 与当前封禁查询 | 首次配置与生产静态资源验证见 `web.md`；扩展的 `check_scan_recovery.py` 验证活跃服务器拒绝未认证读取，返回真实封禁及其翻译，并能直接刷新 `/banlist`。 |
| 手动封禁和 Peer 记录 | 同一回环夹具通过 SQLite Peer 写入、未认证 PUT 拒绝、100 年手动封禁、指定地址解封及全清，封禁历史保留两条。 |
| `python -B test/rust-parity/check_traffic.py target/debug/peerbanhelper.exe` | qBittorrent 连续三次采样经历计数器重置，SQLite 基线及峰值保持一致，滑动上传限速更新，下载设置维持原值。 |
| 原生 CI | `.github/workflows/rust-preview.yml` 增加 Ubuntu/Windows 构建、格式、Clippy、测试及差分回归。GitHub 上的首次运行待验证。 |

## 全面替代前应补齐

当前首条链路是开发预览。下载器请求恢复、进程间单写约束、真实旧库双向回退、现有 WebUI 操作、余下协议、数据库、规则、监控、桌面和分发，按总看板跟踪。配置读写、下发错误和持久化行为均需继续与 Java 基线核对。
