# PeerBanHelper 开发者指南

本指南面向修改 PBH 本体、WebUI、下载器适配器和发行包的开发者。仓库同时包含 JVM 应用、独立的 Vue 应用，以及可单独编译的规则插件示例。配置、命令和类名以当前插件重构分支为准。只开发外部插件时，可以直接从[插件开发手册](plugin-development.md)和[可运行的 demo](../examples/rule-plugin/)开始。

## 一、先跑通本地开发环境

JVM 端使用 JDK 25、Gradle Wrapper、Java 和 Kotlin。WebUI 使用 Vue 3、TypeScript、Vite、Pinia 和 Arco Design Vue。开发时可以分别启动两端；发行包会将构建好的 WebUI 随 JVM 应用一起交付。

| 目标 | 工作目录 | 命令和产物 |
| --- | --- | --- |
| 编译并运行 JVM 测试 | 仓库根目录 | `./gradlew test`；结果在 `build/reports/tests/test/`。Windows 使用 `.\gradlew.bat`。 |
| 安装前端依赖 | `webui/` | `pnpm install`。该目录有自己的锁文件、workspace 配置及依赖补丁。 |
| 开发页面 | `webui/` | `pnpm run dev`；默认将 API 代理到本机 9898 端口。可通过 `PBH_ENDPOINT` 选择测试后端。 |
| 检查前端 | `webui/` | `pnpm run lint`、`pnpm run type-check`、`pnpm test`。构建命令 `pnpm run build` 生成 `dist/`。 |
| 构建宿主和示例 | 仓库根目录 | `./gradlew build`；宿主 JAR 在 `build/libs/`，运行依赖在 `build/libraries/`，demo 在 `examples/rule-plugin/build/libs/`。 |

**第一次启动最容易遗漏两样东西。** 一是静态资源：完整宿主需要类路径中的 `static/`。二是运行库：按发行布局启动时，`libraries/` 和 `PeerBanHelper.jar` 放在同一级。下面的 PowerShell 流程在新的测试目录启动无界面实例。它使用单独的数据目录，适合验证首次初始化和重启。

```powershell
Push-Location webui
pnpm install
pnpm run build
Pop-Location

New-Item -ItemType Directory -Force src/main/resources/static | Out-Null
Copy-Item webui/dist/* src/main/resources/static/ -Recurse -Force
.\gradlew.bat jar
Copy-Item build/libs/PeerBanHelper.jar build/PeerBanHelper.jar -Force
$testData = Join-Path (Resolve-Path build) 'dev-data'
java "-Dpbh.datadir=$testData" -Dpbh.port=19898 -Dpbh.serverAddress=127.0.0.1 -jar build/PeerBanHelper.jar nogui
```

以上复制步骤适用于干净的构建目录；复验发布产物时使用新的工作副本，以免旧的资源哈希文件混入包中。打开 `http://127.0.0.1:19898` 完成首次设置。并行启动 Vite 调试这个实例时，在启动 Vite 的终端设置 `PBH_ENDPOINT=http://127.0.0.1:19898`。仓库的 shell 打包脚本会先清空静态资源目录，再复制前端产物。[Gradle 构建](../build.gradle.kts) · [WebUI 脚本](../webui/package.json) · [打包脚本](../build.sh)

## 二、跟一次完整启动和关闭

1. `MainJumpLoader` 处理 JVM 和平台入口事项，再交给 `Main`。数据目录、配置、日志、语言、平台和 HTTP 服务的早期初始化都可以从这里下断点。
2. HTTP 可以在 Spring 上下文准备期间开始监听。此时访问启动等待页面是正常阶段。排查启动问题时应区分“端口已经监听”“Spring 已刷新”“PBH 已注册模块”“SPA 已就绪”。
3. `AppConfig` 对宿主包执行组件扫描。`Main` 刷新上下文后取得 `PeerBanHelper` 并调用 `start()`。这里按明确顺序注册内置模块，启动 JVM 插件，再启动扫描、恢复封禁列表并发出服务事件。
4. `ModuleManagerImpl` 管理已登记模块。配置状态、注册事件是否被取消、模块是否真正启用都会影响可用性。新增内置模块应检查 `registerModules()` 的列表及顺序；类上存在 `@Component` 只能说明 Spring 可以创建它。
5. 关闭从 JVM shutdown hook 进入。宿主关闭扫描服务，停止和卸载插件，注销模块，再释放调度、下载器、数据库等服务。新增后台线程或连接时，应同时确定谁创建、谁停止、启动中途失败由谁回收。

配置重载、模块启停和进程重启是三个不同入口。改动某个参数时，先找它在运行时的读取点：每轮读取的值通常能直接生效，启动时复制到字段的值需要明确的刷新逻辑，连接池或监听端口的变化还涉及旧资源关闭。[启动代码](../src/main/java/com/ghostchu/peerbanhelper/Main.java) · [模块注册与关闭](../src/main/java/com/ghostchu/peerbanhelper/PeerBanHelper.java)

## 三、从下载器的一条 Peer 记录追到封禁

一轮 BanWave 由 `DownloaderServerImpl` 调度。`DigestionSession` 连接以下七个阶段。若在 UI 看到 Peer 却始终没有封禁，可沿着同一下载器、同一 torrent、同一 Peer 对照各阶段的输入和输出。

| 阶段 | 检查内容 |
| --- | --- |
| `DownloaderProviderOrgan` | 本轮有哪些可参与扫描的下载器？目标实例有没有被排除？ |
| `DownloaderLoginOrgan` | 认证、会话续期和下载器可用状态是否正常？ |
| `TorrentsFetchOrgan` | 目标任务是否被适配器返回？任务 ID、info hash 和私有任务过滤是否正确？ |
| `PeersFetchOrgan` | 该任务是否返回 Peer？地址、端口、Peer ID、客户端名和计数是否转换正确？ |
| `UpdateSnapshotOrgan` | 新快照是否已经发布？仪表盘展示的是哪一轮数据？ |
| `RunMonitorModuleOrgan` | 每个启用的批量监控模块接收该 torrent 的 Peer 列表，用于记录和观察。 |
| `RunCheckModuleOrgan` | 启用的规则逐 Peer 返回 `CheckResult`。检查忽略地址、线程安全声明、执行异常和规则自身条件。 |

`DigestionSession` 按下载器、任务和 Peer 汇总结果。`SKIP` 的优先级高于 `BAN`，其次为 `BAN_FOR_DISCONNECT` 和 `NO_ACTION`。相同动作优先级下比较时长，保留较长的候选。一次规则命中只能证明这条规则提出了封禁；解释最终行为还要看归并结果、当前封禁状态和向下载器下发的结果。正数时长使用规则值，宿主在应用结果时对其他值使用配置的封禁时长。

**如何验证新增规则。** 用一个确定命中的 Peer 和一个相邻的未命中 Peer 直接检查返回结果，然后让同样的输入经过检查阶段。再添加一条返回 `SKIP` 的规则验证冲突。涉及时间窗口时，另测首次采样、窗口边界、计数回落和服务重启。涉及规则来源展示时，追到封禁记录里的模块类名、规则、原因和结构化数据。[流水线实现](../src/main/java/com/ghostchu/peerbanhelper/banpipeline/) · [封禁调度和同步](../src/main/java/com/ghostchu/peerbanhelper/DownloaderServerImpl.java)

## 四、下载器与协议适配

下载器配置经 `DownloaderManagerImpl` 创建具体实例。公共契约在 `Downloader`，共享实现和能力标记也在同一包。适配器负责把协议对象转换为 `bittorrent/` 下的 torrent、peer、tracker 等宿主类型。WebUI 的下载器表单和能力展示同样是适配工作的一部分。[下载器源码](../src/main/java/com/ghostchu/peerbanhelper/downloader/) · [领域模型](../src/main/java/com/ghostchu/peerbanhelper/bittorrent/)

| 实现 | 修改协议时应一起核对 |
| --- | --- |
| qBittorrent / Enhanced Edition | 共用的 `AbstractQbittorrent`、增强版专有能力、认证、增量封禁及标准版的兼容性。 |
| Transmission | 适配器、同仓 `cordelia` RPC 模型、会话更新、种子和 Peer 字段以及 blocklist 更新。 |
| Deluge | 适配器、同仓 `raccoonfink.deluge` 客户端、扩展方法和历史配置类型别名。 |
| BiglyBT | Connector 的元数据、双向消息、批量封禁、解封和全量替换的响应。运行在下载器中的适配插件也需要匹配协议。 |
| BitComet | `BCEndpoint`、认证与加密、任务响应、Peer 映射和 IP 过滤操作。 |
| Aria2Next | JSON-RPC 请求与 bean、任务和速度统计，以及服务端是否提供 BT 封禁扩展。 |

**新增字段的最短路径**是先记录协议含义和缺失条件，再检查适配器转换、公共模型、快照或持久化、HTTP DTO 和前端模型。尤其要分清速率与累计量、原始地址与规范化地址、缺失的端口与数值零。若某项能力只有少数客户端支持，应明确标记能力，并验证其他适配器在字段缺失时仍能完成整轮扫描。

## 五、规则、监控和 BTN

| 模块族 | 入口与开发重点 |
| --- | --- |
| 静态匹配 | `IPBlackList`、`PeerIdBlacklist`、`ClientNameBlacklist` 使用地址或字符串规则。通用匹配器在 `util/rule/`，改变匹配语义时应覆盖所有调用方。 |
| 订阅 | `IPBlackRuleList`、`RuleSubController`、`RuleSubInfoService`、`RuleSubLogService` 分别连接有效规则、管理接口、来源和更新记录。模拟刷新失败，检查原有规则是否仍然可用。 |
| 行为检测 | `ProgressCheatBlocker` 跟踪进度，`MultiDialingBlocker` 检测多拨，`AutoRangeBan` 处理网段，`IdleConnectionDosProtection` 和 `AntiVampire` 使用连接行为。明确采样单位、时间基准、重置条件和判定范围。PCB 还涉及地址与网段状态持久化。 |
| 表达式脚本 | `ExpressionRule`、`util/scriptengine/` 和发行脚本决定后端实际执行语义。WebUI 的 Monaco、语法提示和解析测试也要随语言变更更新。 |
| 流量和连接分析 | `ActiveMonitoringModule` 与 `TrafficJournalService` 处理流量历史；`SessionAnalyseServiceModule` 与连接指标服务支撑图表。聚合时验证计数重置、时间桶、时间范围和显示时区。 |
| Peer 与 Swarm 历史 | `PeerRecordingServiceModule`、`SwarmTrackingModule` 写入不同用途的历史数据。验证同一 IP 在不同端口、种子和下载器上的关联，以及保留策略对查询和上报的影响。 |
| BTN 网络 | `BtnNetwork` 管理能力和配置，`btn/ability/impl/` 包含心跳、重配置、规则、IP 允许或拒绝列表、IP 查询以及封禁、历史和 Swarm 上报。`BtnNetworkOnline` 将网络规则接入本地判定。 |

以 BTN 新增一个查询字段为例，先检查能力是否已协商，再更新请求或响应 DTO，最后核对本地控制器和 WebUI 消费方。分别测试能力缺失、请求失败、响应缺字段和正常响应。涉及上报时对照实际序列化的字段和用户提交开关；本地已经记录一条数据，并不代表当前能力允许发送这条数据。[规则与监控模块](../src/main/java/com/ghostchu/peerbanhelper/module/impl/) · [BTN 协议实现](../src/main/java/com/ghostchu/peerbanhelper/btn/)

## 六、配置、数据库和数据升级

`config.yml` 是全局配置模板，`profile.yml` 是模块配置模板。增加配置项时，搜索现有使用点，明确键名、类型、单位、默认值和重载策略。然后检查 `PBHConfigUpdater` 及主配置、模块配置更新脚本。只改发行模板会让已安装实例与全新安装的行为出现差异。修改 WebUI 设置表单时，还要测试“打开旧配置、保存其他字段、重新打开”的路径。[发行模板](../src/main/resources/) · [配置升级](../src/main/java/com/ghostchu/peerbanhelper/config/)

**添加一列并通过历史 API 展示的操作顺序**

1. 在 `databasent/table/` 找到实体，确认新列允许为空还是需要默认值。对旧记录定义可解释的值，避免新代码读到旧数据时拆箱失败。
2. 在 `src/main/resources/db/migration/` 的相应方言目录增加新的 Flyway 版本。SQLite、PostgreSQL、MySQL 使用各自 SQL；H2 使用项目的 MySQL 路径，仍应单独验证。发布过的迁移保留原文，以维持校验和。
3. 对照 `databasent/mapper/`、资源 mapper 与 service。写入、按 ID 读取、列表查询和聚合可能使用不同 SQL。涉及 IP、JSON 和日期时核对 TypeHandler，以及索引和排序使用的存储表示。
4. 检查旧 SQLite 转换流程。旧格式经 `DatabaseMigrationCoordinator` 等迁移代码导入；Flyway 能在空数据库建表，只覆盖其中一条路径。
5. 修改 HTTP 输出和前端类型，准备空库、带历史记录的库和迁移完成后再次启动的库。对同一记录比较升级前后的数量、关联和查询结果。

**选择正确的数据源。** 当前封禁由运行中的封禁列表维护，并有相应持久化；历史封禁通过 `HistoryService` 查询。用户手工解封后，历史页仍有记录是两个页面本身的语义。类似地，Peer 历史、Swarm 跟踪、连接指标和流量日志各有自己的服务与粒度。写统计前明确问题是“现在有多少”“这一段时间发生多少次”还是“涉及多少个不同对象”。[数据层](../src/main/java/com/ghostchu/peerbanhelper/databasent/) · [数据库资源](../src/main/resources/db/) · [方言 mapper](../src/main/resources/mapper/)

## 七、HTTP API、权限和后台操作

`JavalinWebContainer` 负责 HTTP、静态资源、认证和异常处理；具体接口主要由 `module/impl/webapi/` 的控制器在模块启用阶段注册。控制器通常返回 `StdResp`。实现新接口时先确认它属于哪个模块、该模块是否实际注册、需要什么角色，再选择路径和请求形式。

| 接口族 | 从哪里读 |
| --- | --- |
| 登录、首次初始化、能力信息 | `PBHAuthenticateController`、`PBHOOBEController`、`PBHMetadataController`。至少覆盖令牌正确、令牌失效和未初始化实例。 |
| 下载器、封禁和历史 | `PBHDownloaderController`、`PBHBanController`、`PBHPeerController`、`PBHTorrentController`。列表和明细需一致处理地址、info hash、过滤、页码及排序。 |
| 指标、图表、告警和推送 | `PBHMetricsController`、`PBHChartController`、`PBHAlertController`、`PBHPushController`。推送渠道由 `util/push/` 实现；配置保存与测试发送应分别验证。 |
| 运行信息、维护和网络功能 | `PBHGeneralController`、`PBHUtilitiesController`、`PBHLabController`、`PBHAutoStunController`、`PBHBtnController`。区分查询运行状态和触发有副作用的操作。 |
| 长任务和日志流 | `PBHBackgroundTaskController`、`PBHLogsController`、`AbstractSSEFeatureModule` 与前端 `service/eventstream.ts`。检查客户端断开、切换端点、重连、任务终止和订阅释放。 |
| 许可证与插件 | `PBHPlusController` 映射授权情况，`PBHPluginController` 提供插件状态与管理操作。分别测试只读请求和写操作。 |

普通已认证读取通常使用 `Role.USER_READ`，变更状态使用 `Role.USER_WRITE`；Plus 功能还涉及授权检查。路径含动态参数时，检查固定操作名是否会被参数路由匹配。对批量接口检查各项结果，例如插件管理响应中的每项 `value` 和 `message`。[HTTP 容器](../src/main/java/com/ghostchu/peerbanhelper/web/) · [控制器](../src/main/java/com/ghostchu/peerbanhelper/module/impl/webapi/)

## 八、WebUI 开发实战

以封禁历史为例，从 `views/data-view/banlog/` 找表格与分页事件，跟到 `service/banLogs.ts`。服务先等待 `useEndpointStore().serverAvailable`，再用当前 endpoint 拼接 `/api/bans/logs`，把 UI 的 `sorter` 写成查询参数 `orderBy`，附上通用请求头并检查登录状态。新增筛选项需要打通页面状态、服务参数、后端查询、总数和分页响应。只在当前页过滤会导致总数和下一页内容与用户预期不符。[请求实现](../webui/src/service/banLogs.ts) · [历史页面](../webui/src/views/data-view/banlog/)

| 页面 | 主要协作对象及验收场景 |
| --- | --- |
| 应用外壳、登录、OOBE | `App.vue`、`router/`、`stores/endpoint.ts`、`views/login/`、`views/oobe/`。覆盖恢复会话、切换实例、初始设置中断后重进。 |
| 仪表盘 | 下载器列表、编辑表单、任务与 Peer 弹窗共同使用 `service/downloaders.ts`。模拟单下载器失败和轮询期间关闭弹窗。 |
| 当前封禁、排行、历史 | `banlist/`、`ranks/`、`data-view/` 分别关注当前状态、聚合和历史明细。解封后检查对应视图刷新；快速切换筛选时留意旧响应。 |
| 图表 | `charts/` 使用 ECharts 和图表服务。对照后端时间桶，测试空时间段、跨天、时区和无权限场景。 |
| 规则管理与脚本 | `rule-management/` 维护本地和订阅规则；`custom-script/` 编辑并提交脚本。语法高亮正确仍需验证后端编译和求值。 |
| 设置 | `settings/` 下分全局配置、模块 profile、实验室和运行信息。推送、BTN、IP 数据库和 AutoSTUN 表单应对照各自后端模型。 |

复用组件放在 `components/`，共享状态放在 Pinia stores，HTTP 类型放在 `api/model/`。用户可见文案同步检查简中、繁中和英文。验证部署时至少覆盖 Vite 代理、后端托管、带路径前缀和深层路由直接刷新。使用自定义 endpoint 测试一次，可发现硬编码同源地址的问题。[WebUI 源码](../webui/src/)

## 九、桌面、平台与共享服务

| 子系统 | 修改时跟踪的入口及边界 |
| --- | --- |
| 桌面和无界面运行 | `gui/PBHGuiManager`、`gui/impl/console/`、`gui/impl/swing/`。Swing 窗口、托盘和 SWT 浏览器涉及不同事件线程。验证关闭窗口、退出程序、浏览器资源释放和 `nogui` 启动。 |
| 操作系统与 JNI | `platform/` 选择平台实现，`platform-natives/` 存放按系统与架构区分的资源，`cpp-src/` 存放 Windows JNI 源码。涉及 EcoQoS、工作集和 AMSI 的逻辑，应覆盖平台能力缺失的分支。 |
| GeoIP、DNS、HTTP | `util/ipdb/` 管理本地库、更新和查询，`util/dns/`、`HTTPUtil`、`IPAddressUtil` 处理网络工具。测试更新中断后旧库可用，以及文件句柄在 Windows 上能否释放。 |
| NAT 和端口映射 | `util/traversal/`、`util/portmapper/`、AutoSTUN 控制器与实验室页共同处理探测、映射和转发。覆盖失败重试、重新配置、端口占用和停用后的连接释放。 |
| PBH Plus | `pbhplus/` 包含许可证解析、下载、本地密钥、后端和校验器。能力变化既影响 API，也影响 WebUI 的可见状态。 |
| 指标、告警和通知 | `metric/`、`alert/`、`util/push/` 分别负责记录、告警生命周期和外部渠道。添加通知前确认重复事件的标识、已读行为和发送失败处理。 |
| 事件、配置交换和本地化 | `event/` 按程序、模块、扫描和 GUI 等划分，`exchange/` 提供进程内交换，`text/` 管理翻译与后处理。发布事件前确认监听方的线程、注册时点、撤销时点和参数含义。 |
| 公共工具与外部协议代码 | `util/` 还包含后台任务、JSON/JSON-RPC、查询、日志、计量、缓存、加密、实验开关和遥测。部分下载器、STUN、UPnP、主题探测依赖以内嵌源码存在。改公共工具前先搜索调用者，尤其注意跨网络、数据库、UI 复用的数据表示。 |

## 十、测试、排障和发布

**验证下载器适配。** 仓库提供基于 Flask 的 qBittorrent 模拟器，可在装有 Flask 的 Python 环境运行 `python test/fakeqb.py`，默认监听 `127.0.0.1:8080`。用隔离的 PBH 实例连接它，可以重复观察登录、读任务和读 Peer。要验证其他客户端的特有能力，应使用对应协议夹具或实际客户端。[模拟器](../test/fakeqb.py)

**验证数据库升级。** `test/test-db/` 包含旧 SQLite 数据及测试配置；CI 脚本会移动夹具、替换引擎占位符，并启动真实宿主。手工复现应在一次性工作副本中进行。CI 分别覆盖 SQLite、H2 和带数据库服务的 MySQL、PostgreSQL 作业。数据库改动应同时看 JUnit 结果、宿主启动日志和升级后数据，而不能只看编译是否通过。[夹具脚本](../test/test-db.sh) · [JVM CI](../.github/workflows/build_gradle.yml)

| 症状 | 建议的定位顺序 |
| --- | --- |
| 宿主编译通过但启动时报找不到静态目录 | 核对 `webui/dist/index.html`、`src/main/resources/static/index.html`、最终 JAR 的 `static/index.html`。 |
| API 返回成功但 UI 显示旧数据 | 检查 endpoint、实际请求参数、返回体分页元数据，再检查 Pinia、轮询和页面刷新逻辑。 |
| 扫描记录里有规则命中，下载器仍连着 Peer | 对照本轮最终动作、实际封禁时长、适配器能力与下发结果，并确认是否观察了同一地址和下载器。 |
| 新安装正常，老安装出错 | 比较升级后的 YAML、Flyway 历史、旧库迁移标记和旧实体字段。使用旧库副本重现。 |
| IDE 中能工作，发行包中失败 | 检查资源是否进 JAR、运行库目录布局、平台二进制架构，以及打包工作流实际下载的产物。 |

发布链路先构建 WebUI，再将静态资源纳入 JVM 构建。源码容器由 `Dockerfile` 分阶段构建；`Dockerfile-Release` 消费已构建的 JAR 和依赖。Debian 包、FreeBSD 包、便携包、Synology SPK 的文件分别位于 `pkg/deb/`、`pkg/pkg/`、`pkg/portable/`、`pkg/synopkg/`。图形安装器位于 `install4j/`。改启动参数、JRE 或文件布局时，要逐个核对相关发行方式的入口、数据挂载、权限、升级保留行为和退出方式。[发行工作流](../.github/workflows/) · [系统包与启动脚本](../pkg/) · [图形安装器](../install4j/)
