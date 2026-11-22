# 阶段 0–2 · Java 基线、范围与行为验证

参考提交 `25be37332d769a1d09d5f62a5376fb3ea1ad339e`。实施分支 `codex/rust`。Java/Kotlin 现有代码用于只读取证，原有 Gradle 任务仅在开发期运行。

## 验证记录

| 检查 | 结果 |
| --- | --- |
| `./gradlew.bat test --no-daemon --console=plain` | 成功。Gradle 9.9.1，1m37s，JVM 测试实际执行 6 项，无失败、无跳过。`BtnAbilityIpQueryTest` 5 项，`PBHPeerControllerBtnQueryTest` 1 项。构建产生 `build/libs/PeerBanHelper.jar`。 |
| `python -B test/rust-parity/judge.py self-test` | 五类框架故障全部检出，并检出重复的下载器动作，验证了键顺序不影响结果、缺失和全错误均不算等价。 |
| 规则归并样本 | 10 个来源于 `PeerAction` 与 `DigestionSession` 的静态源代码夹具。对 `target/debug/peerbanhelper.exe compare-decisions` 比较结果为 10 项等价。 |
| qBittorrent 黑名单回放 | 从真 Java 进程和 Rust 进程分别取得请求及 SQLite 历史。初版 Rust 在首轮用全量列表替代增量封禁。Rust 修复启动全量同步后复比为 1 项等价。请求依次为空全量列表、增量封禁、空全量列表。两边均记录 `192.0.2.113:51413` 一次，时长 1400 ms。 |
| 效能基线、完整旧库升级矩阵、系统差分 | 待录制。现有 `test/test-db.sh` 只用日志判断启动成功，`test/fakeqb.py` 的 Peer 集合为空。 |

本机 JDK 25 位置 `C:\Users\ghost\.jdks\temurin-25.0.5`，PowerShell 默认 `JAVA_HOME` 仍为 JDK 21，原 Gradle 任务能选用 JDK 25 工具链。

## 第一批规则归并契约

来源为 `PeerAction.java` 与 `DigestionSession.java` 的归并循环。按同一下载器、种子、Peer 的到达顺序接收结果。优先级 `NO_ACTION < BAN_FOR_DISCONNECT < BAN < SKIP`。同级保留最大有符号毫秒时长。同级同长替换为后到项，保留该项的原因。无结果输出 `null`。这是单元边界，Java `RunCheckModuleOrgan` 并发产出结果，不能把注册顺序当成全局到达顺序。忽略地址在规则运行前由执行阶段发出 `SKIP`，且可能记录 NAT 配置告警，需单独验证。

测试命令和 JSONL 约定见 [`test/rust-parity/README.md`](../test/rust-parity/README.md)。

## 功能与兼容清单

以下为初始功能盘点，默认状态以 `PeerBanHelper.registerModules()`、`config.yml`、`profile.yml` 和实际能力协商为准。迭代状态见 [迁移看板](README.md) 和 [验证矩阵](validation.md)。

| 领域与来源 | 依赖及运行行为 | 必验夹具与当前覆盖 |
| --- | --- | --- |
| `MainJumpLoader`、`Main`、`PeerBanHelper`、`ExternalSwitch`、配置升级 | 路径和环境优先级、首次启动、重载、关机及三套语言 | 空数据目录、已有目录、配置更新、重载失败和重启。待做。 |
| `DownloaderServerImpl`、`DigestionSession`、`BanList` | 下载器、规则、时钟、存储、事件依赖。停用、超时、去重、预定封禁和解封、恢复同步 | 同 Peer 多轮、回退、时长优先、仅断连、部分失败、全量修复。正在实施。 |
| `IPBlackList`、`PeerIdBlacklist`、`ClientNameBlacklist`、`IPBlackRuleList` | 基础模型、地址段、文本匹配、端口、GeoIP、订阅拉取 | IPv4/IPv6、旧表达法、端口、嵌套文本规则、GeoIP 缺失、订阅刷新失败保留。IP 首批正在实施。 |
| `ProgressCheatBlocker`、`MultiDialingBlocker`、`AutoRangeBan`、`IdleConnectionDosProtection`、`AntiVampire` | 扫描周期、缓存、统计、既有 PCB 持久状态、动作归并 | 流量重置、时间边界、历史兼容、多个规则同时命中、周期并发。待做。 |
| 下载器工厂与标准/qBittorrent EE、Transmission、Deluge、BiglyBT、BitComet、Aria2Next | 统一 Peer/Torrent 领域模型，协商版本、会话、增量/全量动作及配置别名 | 真实客户端、模拟错误、回调、刷新、恢复和历史 alias。标准 qBittorrent 正在实施。 |
| `ActiveMonitoringModule`、`SessionAnalyseServiceModule`、`PeerRecordingServiceModule`、`SwarmTrackingModule` | 下载器状态、Peer 数据、采样窗口、数据库、后续 BTN 消费 | 序列回放、计数器归零、跨时区换日、回退和聚合。待做。 |
| `databasent`、`DatabaseSchemaInitializer`、`DatabaseMigrationCoordinator` | SQLite、PostgreSQL、MySQL/MariaDB；SQL 方言、类型处理、Flyway 历史、旧 SQLite 内容升级 | 全新库、真实旧 SQLite、现代库、迁移中断、回退交叉读写。SQLite 首批正在实施。 |
| `BtnNetwork`、`BtnNetworkOnline`、`ability/impl`、旧协议 DTO | 认证、启停、能力协商、心跳/重配、允许和拒绝列表、在线规则、查询、封禁/历史/Swarm 上报 | 受控服务器模拟响应、协议版本和失败时间线，核对序列、并发和重试。现有 Java 测试 6 项覆盖 IP 查询。 |
| `JavalinWebContainer`、`module/impl/webapi`、`webui/src` | 路由和角色、OOBE、静态资源、模块启用提示、令牌和会话、SSE 日志和任务 | 真实前端连接两端；认证、初始化、分页、深链接、代理前缀、切换端点和重连。待做。 |
| `util/ipdb`、`util/dns`、`util/push`、`pbhplus`、Alert | 文件下载与缓存、网络源、失败恢复、授权格式及告警历史 | MaxMind/GeoCN、DNS、许可证和密钥文件、Bark/Gotify/Ntfy/PushDeer/PushPlus/ServerChan/SMTP/Telegram/Webhook 渠道。待做。 |
| `util/traversal`、`util/portmapper`、`platform`、GUI 和桌面入口 | 平台网络栈、AutoSTUN、UPnP、转发、代理、托盘、内嵌 WebUI | 平台实测、运行时重载、套接字释放、窗口行为与无界面启动。待做。 |
| `.github/workflows`、`Dockerfile*`、`pkg`、`install4j` | 容器、便携包、安装器、Debian、FreeBSD、Synology，含 Windows ARM64 和 Win7 现有产物 | 用户原入口安装、升级、关闭、卸载及数据保留，验证发布产物和运行时无 JVM。待做。 |

`PTRBlacklist`、MCP 控制器和插件管理控制器在基线注册表中注释，不能把现有代码误算成默认入口。PF4J 本身仍通过 Spring 初始化，按用户决策退役，同步审查插件路径和迁移文档。

另两项已获准退役：

- H2 对应 `database.type` 与 OOBE/设置可选项。旧 H2 配置需要显示退役原因，并保护库文件，避免兼容升级把旧库当成空 SQLite。
- Aviator 对应 `ExpressionRule`、`/api/expression-engine/*`、发行 `.av` 脚本、WebUI `/script` 路由/菜单/编辑器、BTN 的 `allow-script-execute` 与规则集脚本对象。保留各渠道非脚本规则，专门验证 BTN 有脚本字段的响应仍能应用其非脚本内容。
