# 数据库存储

实施分支 `codex/rust`，Java 参考提交 `25be37332`。Rust 代码位于 `rust/storage/`。

## SQLite

- 参考 `databasent/driver/sqlite/SQLiteDatabaseDriver`、`databasent/service/impl/common/BanListServiceImpl`、`metric/impl/persist/PersistMetrics`、`mapper/sqlite/TorrentMapper.xml` 和 `db/migration/sqlite/`。当前库路径 `persist/peerbanhelper-nt.db`。
- 编译时包含五份现有 SQL，按 Flyway 版本、SQL 描述和 CRC32 校验现有迁移历史。补齐较旧的现代库 SQL，事务完成前不登记迁移。未知或更高版本、意外校验和、缺失关键表均返回诊断。
- `banlist` 继续使用文本地址和 `BanMetadata` JSON。保留未知对象字段。当前 API 支持单一 IPv4/IPv6 主机。CIDR 和带区域标识的地址会明确返回错误，等待扫描和下载器的地址范围契约。历史表 IPv6 使用 Java `InetAddress.getHostAddress` 的八段形式。
- `commit_ban_wave` 把历史写入和当前封禁快照作为一个事务。应用保证唯一写入方，且只传入本轮新增的完整封禁历史。可以独立写入历史来处理关闭封禁快照持久化的配置。

### ORMLite v2–v20

启动时发现 `persist/peerbanhelper.db` 且缺少 `migration_completed.marker`，从只读连接导入旧库。根据 Java `SQLiteSchemaUpgrader`，对 v2–v19 缺少的七个列提供原迁移默认值。逐列处理时间、IP、翻译对象、规则与模块关系和新列名。业务行和完成标记同时提交，成功后创建 Java 识别的外部标记。保留旧库，外部标记意外丢失时依据新库记录恢复。未知版本、未知表、无效记录、已有非空目的库及唯一键冲突阻止导入。旧版告警和封禁能解析时也会保留，Java 的若干旧版本曾执行清空。

| 验证 | 环境和结果 |
| --- | --- |
| `cargo +1.96.0 test -p pbh-storage --locked` | SQLite 10 项通过。覆盖版本 1.1 到 1.5、五条校验和、重启、事务回滚、较新 schema 拒绝、旧夹具导入及中断后恢复。新增合成 v2 缺省版本和 v19 缺少端口的样本，可验证数据和源文件保留。 |
| `cargo +1.96.0 clippy -p pbh-storage --all-targets --locked -- -D warnings` | 最终阶段 SQLite、PostgreSQL 和 MySQL/MariaDB 代码及测试均通过。 |
| 现有真实旧库 | 复制仓库 `test/test-db/persist/peerbanhelper.db` 进测试目录。迁移 9 封禁、9 条历史、2 种子、175 PCB 地址、173 PCB 网段、31 连接明细和其他记录。旧库内容的字节校验不变。构造晚期行错误后，先前复制的种子等全部回滚且不生成外部标记。 |
| Java 创建库到 Rust | 用 JDK 25 和该参考提交的真实 PBH JAR 迁移旧夹具。Rust CLI `scan --once --apply` 打开 Java 生成的数据库成功，预览配置禁用下载器及封禁列表写入。交叉结果见本机 `target/storage-java-_50_dncy/reference.sqlite3`。 |
| Rust 创建库到 Java | Rust CLI 从独立旧夹具创建库。Java Flyway 随后通过全部五项验证并报告 schema 已是最新。日志位于本机 `target/storage-rust-tvxom__7/java-roundtrip.log`。 |

跨实现比较中，全部 9 条原封禁 JSON 按字节相同。9 条历史记录的 JSON 在解析后相同，原始 `structured_data` 保留旧库整数拼写，Java 重新编码为小数。Rust 另外保留旧库两张 PCB 表共 348 行，Java 基线的 `DatabaseMigrationCoordinator.createMigrators` 没有注册它们。Rust 不依赖 GeoIP 下载完成导入，旧记录的 GeoIP 回填需等 GeoIP 服务移植。

已有现代 SQLite 数据目录完成 Windows Java → Rust → Java 单写入方回退演练。两次验收均持续回放并增加 qBittorrent 封禁，保留整份历史和五条 Flyway 校验。操作步骤与留存文件见 [SQLite 切换与回退](sqlite-cutover.md)。

## PostgreSQL

- 参考 `databasent/driver/postgres/PostgresDatabaseDriver`、`PGInetAddressTypeHandler`、`mapper/postgres/TorrentMapper.xml`、Flyway 13.13.0 PostgreSQL 历史定义和 `db/migration/postgres/` 八份原 SQL。
- `PostgresStore` 通过 Tokio 连接，适配 `INET`、`TIMESTAMPTZ`、`BOOLEAN` 和原有文本 JSON，应用同样传入 `BanRecord` 和 `BanHistoryEntry`。保持 SQLite 同步 API 独立。PostgreSQL TLS 使用平台证书存储，也允许传入额外信任根的连接器。
- 只监听本机的 PostgreSQL 17.12 临时实例从 Debian WSL 解包程序到 `/tmp/pbh-pg.hrbZiU`，不安装系统套件。Windows `127.0.0.1:38437` 已实测连通，检查完由 `pg_ctl -m fast -w stop` 正常停止。
- `cargo +1.96.0 test -p pbh-storage --locked --test postgres -- --ignored` 在 `PBH_TEST_POSTGRES_URL` 指向该实例时通过。覆盖全部八份 SQL、原生 IPv6 `INET`、正负毫秒 `TIMESTAMPTZ`、快照和历史回滚以及重新连接。
- Java 在 `pbh_sql_java` 应用全部八份迁移，Rust 随后打开并完成封禁快照写读和清空。反向在 `pbh_sql_rust` 用 Rust 创建库，Java Flyway 验证八份迁移并报告 `Schema "public" is up to date`。验证输出位于本机 `target/storage-rust-postgres-e2o_f9qk/java.log`。
- 主进程用独立 `pbh_rust_scan` 数据库完成模拟 qBittorrent 的扫描与重启回放，结束时查到一条当前封禁和一条历史。

## MySQL 和 MariaDB

- 参考 `databasent/driver/mysql/MySQLDatabaseDriver`、`OffsetDateTimeTypeHandlerForMySQL`、Flyway 13.13.0 MySQL 历史定义和 `db/migration/mysql/` 的五份原 SQL。用 UTC `DATETIME`、Java 格式地址及文本 JSON。新库选择 InnoDB，打开已有库时检查封禁及历史相关四表都启用事务引擎。
- MySQL DDL 启用隐式提交，迁移在每份 SQL 开始前先登记未成功，SQL 全部执行后更新成功。失败或中断状态会阻止自动重放，同一数据库的 Rust 迁移通过连接锁串行。
- MariaDB 11.8.6 从 Debian WSL 软件包解包到 `/tmp/pbh-mariadb.lVIzNV`，临时实例监听 `127.0.0.1:38438`。通过 `PBH_TEST_MYSQL_URL` 运行 `cargo +1.96.0 test -p pbh-storage --test mysql -- --ignored`，验证五份 SQL、UTC 日期、IPv6、重启、事务失败回滚及拒绝失败迁移。
- Rust 打开 Java 创建的 `pbh_sql_java` 并完成封禁快照写读。反向 Java Flyway 验证 Rust 创建的 `pbh_sql_rust` 的五份迁移并报告已是最新。验证输出位于本机 `target/storage-rust-mariadb-cpmlr5cp/java.log`。
- 主进程用独立 `pbh_rust_scan` 数据库完成 qBittorrent 扫描及重启回放，停止 MariaDB 前查到一条当前封禁和一条历史。两台临时数据库的 Windows 回环端口均已关闭。

## 未完成

- 早期 ORMLite 的真实生产样本、包含网段封禁的持久化行为、GeoIP 回填，以及其他业务模块的回退验收。
- 将老 ORMLite 文件导入服务器数据库、全业务表运行期读写、实际 TLS 证书接入，以及 PostgreSQL 14/16 和 MySQL 5.7 等额外版本矩阵。

## 封禁历史分页

- 对照 `PBHBanController.handleLogs`、`HistoryServiceImpl.getBanLogs`、`Orderable`、`Pageable`、`BanLogDTO` 和 WebUI `getBanlogs`。旧 `/api/bans/logs` 只接收页码、大小和排序。历史服务中按 IP、种子 ID 筛选的语义通过单独的 `BanHistoryFilter` 保留。
- SQLite、PostgreSQL 和 MySQL/MariaDB 共享 `BanHistoryQuery`、记录和结果类型。按数据库原字段排序，在同一只读事务快照中取筛选后的总数和页数据。现有默认 `ban_at DESC` 保留，增加 `id DESC` 使相同时刻的多页结果稳定。`torrentInfoHash` 排序取关联种子的 `info_hash`，现有 Java 的同名映射指向历史表中不存在的列。HTTP 层负责翻译规则和描述，以及解析下载器信息。
- `SqliteHistoryReader::open` 对现有库使用只读标志，无自动建库或迁移。SQLite 测试对照查询前后的数据库文件字节，覆盖默认顺序、二级排序、重复排序字段处理、超出末页仍保留总数和 IPv6/种子筛选。其余数据库通过启动时已创建的连接执行只读查询。
- 根据 `DatabaseDriverConfig` 及 MyBatis-Plus 3.5.18 的 `PaginationInnerInterceptor` 保留最多 1000 条、负页码归到第 1 页、负页大小省略计数的行为。SQLite 夹具覆盖这些边界。PostgreSQL 17.12 和 MariaDB 11.8.6 的真实实例测试已验证普通正页参数下的 IPv6 筛选、毫秒时间和关联种子，之后停止了两个临时实例；负页大小只做了 SQLite 实测。

## 进度作弊检测状态

- 对照 `PCBAddressServiceImpl`、`PCBRangeServiceImpl`、`ProgressCheatBlocker` 及三套 `PCBAddressMapper.xml` 和 `PCBRangeMapper.xml`。复用 `pbh_core::PcbState`。地址按 IP、端口、种子 ID 和下载器读写，网段按 Java 原有格式字符串、种子 ID 和下载器读写。
- SQLite、PostgreSQL 和 MySQL/MariaDB 使用原表结构。冲突写入保留首见时间并替换各项可变计数。两表批量刷新采用一个事务。清理按最后观察时间严格小于阈值；显式删除按种子加地址或网段，涵盖各下载器和端口。旧库可空流量字段读为 0，与 Java 基本类型默认值一致。
- `cargo +1.96.0 test -p pbh-storage --test pcb` 的两项 SQLite 测试通过。PostgreSQL 17.12、MariaDB 11.8.6 真库测试验证 IPv6、写入和冲突更新、首见时间保留、读回、删除及精确清理边界，两台临时实例已停止。SQLite 另覆盖重启、键隔离及后半批次失败时整体回滚。规则调度和应用端验证由主任务接入。

## 元数据和 BTN 缓存

- 对照 `MetadataServiceImpl`、`MetadataEntity` 和 BTN allowlist、denylist、rules 及上传游标调用方。沿用 `metadata(k,v)` 和原有键名。读取批次在同一快照，返回的映射保留现存 SQL NULL 并省略不存在的键。
- 写入批次以事务更新指定键，保持其他元数据。按键排序避免缓存版本与内容交叉写入时的锁顺序反转。同一批次同名键取最后一个值。
- SQLite 两项专项测试通过，覆盖版本与内容更新、缺失键与 SQL NULL、保留无关键、重启后读取，以及后半批次失败时回滚。PostgreSQL 17.12 和 MariaDB 11.8.6 真实库测试也验证了 NULL、缺失键和更新失败回滚，两个临时服务已停止。`cargo +1.96.0 test -p pbh-storage -j 1` 的 16 项默认测试和严格 Clippy 均通过。BTN 应用接线由主任务接续。

## Peer 访问历史与上报游标

- 对照 `PeerRecordServiceImpl`、三个 `PeerRecordMapper.xml`、Peer 和 Torrent 访问历史接口及旧 BTN `BtnAbilitySubmitHistory`。复用种子 upsert。`peer_records` 的键为地址、种子 ID、下载器，最新记录覆盖端口。按原 mapper 累计上传及下载，从旧计数器重置后加入新值；较早观察忽略，保留首次时间及首次保存的 GeoIP。
- SQLite、PostgreSQL、MySQL/MariaDB 支持批量写入、按 IP 或种子 ID 分页、按阈值清理。分页与总数使用同一快照和原 MyBatis 页大小规则。Java 两个控制器的默认排序字段使用无保证顺序的 `Map.of`；Rust 固定按最后观察时间、地址、端口与 ID 收尾。PeerID 按前八个 UTF-16 单元取前缀，遇到代理对边界保留完整字符。
- BTN 访问历史按 `(last_time_seen_ms, id)` 排序与续传，最多 1000 行。旧标量时间戳可作为 `(timestamp, i64::MAX)` 导入，遇到同一毫秒的多行仍能逐页推进。PostgreSQL 按毫秒归一排序以覆盖旧库亚毫秒行。现代 BTN 封禁上报可用 `BanHistoryFilter::AfterId` 查询 `h.id > cursor`。
- SQLite `cargo test -p pbh-storage --test peer_records --test ban_history` 已通过，覆盖计数增长与重置、重复/乱序观察、IPv6、端口变化、首见与 GeoIP 保留、两个分页维度、同毫秒数据的游标续读及封禁 ID 边界。数据上传是在调用端，客户端和时段聚合查询仍待移植。
- PostgreSQL 17.12 与 MariaDB 11.8.6 实库通过写入和重置、旧观察忽略、IPv6 筛选、分页续读和到期清理。PostgreSQL 夹具另注入同毫秒不同微秒的旧行，完整推进两条记录后成功结束。两个回环临时实例均已停止。
- 本阶段结束时存储默认测试 17 项和严格 Clippy 全部通过。

## 当前会话 Swarm 跟踪

- 对照 `SwarmTrackingModule`、`TrackedSwarmServiceImpl`、`TrackedSwarmEntity`、三个 `TrackedSwarmMapper.xml` 及 `BtnAbilitySubmitSwarm`。SQLite、PostgreSQL、MySQL/MariaDB 沿用 `tracked_swarm` 表。IP、端口、info hash 与下载器共同确定一条会话记录。首次观察固定种子属性、双方进度、原始速率与首见时间。后续观察更新 Peer 标识、客户端和标记，累加流量增量，在计数器重置时加上新的计数，并保留峰值速率。写入批次按观察顺序在一次事务中合并。
- `reset_tracked_swarm` 按 Java 模块启用行为用 `DELETE` 清理会话行，保留生成 ID 的序列及 `BtnAbilitySubmitSwarm.cursor`。BTN 读取保持原条件 `last_time_seen >= cursor.time AND id > cursor.id`，按最后观察时间和 ID 排序，单次最多 1000 行。已成功上报的旧 ID 即使之后更新也不再次进入该查询。MySQL/MariaDB 使用原表的秒级 `DATETIME`，SQLite 保留毫秒，PostgreSQL 的 `TIMESTAMPTZ` 按原始时间排序并输出毫秒游标。
- 计数和详情分别支撑 Swarm 概览及 WebAPI。详情分页在同一只读快照取总数和记录，使用原 MyBatis 页大小规则，白名单列可按 `orderBy` 排序。Rust 以 ID 收尾保证分页稳定，Java 此接口未声明默认排序。
- `cargo +1.96.0 test -p pbh-storage --locked -j 1` 通过 19 项默认测试。两项新 SQLite 用例覆盖会话合并、端口和下载器区分、重开、同毫秒游标、时间与 ID 的独立边界、清理后序列和游标延续、详情分页及批次失败回滚。
- `cargo +1.96.0 test -p pbh-storage --locked -j 1 --test postgres --test mysql -- --ignored` 在回环 PostgreSQL 17.12 与 MariaDB 11.8.6 上通过，覆盖上述三方言共享合并和游标夹具、独立会话重置与事务回滚。两个临时数据库进程已正常停止。`cargo +1.96.0 clippy -p pbh-storage --all-targets --locked -j 1 -- -D warnings` 通过。Java 与 Rust 的 Swarm HTTP 响应及 BTN 真实服务端行为由应用阶段验证，独立 MySQL 版本尚未实测。

## ActiveMonitoring 流量日记

- 对照 `ActiveMonitoringModule`、`TrafficJournalServiceImpl`、`TrafficJournalEntity`、三套 `TrafficJournalMapper.xml`、`TimeUtil` 和 `PBHChartController.handleTrafficClassic`。沿用已有 `traffic_journal_v3`，按小时起点和下载器唯一键合并。每小时保留第一笔数据与协议计数，分别更新四项计数峰值。下载器计数器重置后降低的值不会冲减该小时的用量。下一个小时重新取基线。`upsert_traffic_samples` 以事务处理采样批次，无额外迁移。
- `traffic_hours` 查询头尾均包含的小时区间。全部下载器的增量同小时相加，指定下载器时在原 mapper 约定下将负增量限制为 0。保留已有数据库的 UTC 时间表示、MySQL/MariaDB 秒级 `DATETIME` 和有符号查询结果。PostgreSQL 查询采用有效的列名语法，原 mapper 的反引号在 PostgreSQL 中无法解析。
- `aggregate_traffic_days` 按应用传入的当地日界分组、求和并排序，保留仅有采样记录的日期。数据库接口不执行本地时区换算。应用层负责一分钟采样、停止时最后一次采样、`/api/chart/traffic`、每日流量告警和滚动 24 小时限速。
- `cargo +1.96.0 test -p pbh-storage --locked -j 1` 通过 21 项默认测试。流量日记用例覆盖同一小时重置、跨小时重新取基线、多下载器和空白筛选、区间边界、非整小时偏移的跨日聚合、重启和失败回滚。仓库 ORMLite 原始夹具的独立复测确认现有流量行的有符号增量在迁移后仍可查询，源文件字节一致。
- `cargo +1.96.0 test -p pbh-storage --locked -j 1 --test postgres --test mysql -- --ignored` 在 PostgreSQL 17.12 和 MariaDB 11.8.6 的回环临时实例上通过，覆盖相同流量基线、筛选、日聚合与回滚验证。两个实例已停止。存储 `fmt --check` 和严格 Clippy 通过。独立 MySQL、HTTP 图表与运行期调度需应用阶段继续验证。

## SessionAnalyse 连接会话统计

- 对照 `SessionAnalyseServiceModule`、`PeerConnectionMetricsServiceImpl`、`PeerConnectionMetricsTrackServiceImpl`、三套 track mapper、`PeerFlag`、`PBHMetricsController` 和 `PBHChartController.handleSessionAnalyse`。沿用 `peer_connection_metrics` 与 `peer_connection_metrics_track`。观察按当地日界、下载器、种子、IP 加端口去重。重复观察更新 Peer ID、客户端与标记，原种子关系从共用 upsert 得到。
- `global_total_connections_count` 用含头尾的日期区间汇总已持久化会话供 `weeklySessions` 使用。`connection_metrics` 可指定下载器，按原 DTO 的 17 项计数合并同日下载器数据，日界降序，空结果返回空列表。`cleanup_connection_metrics` 删除日界小于或等于阈值的汇总行。
- `aggregate_peer_sessions` 在同一事务读取跟踪、归档指标并消耗已归档行，失败后可以重试。保留 Java 当前行为。当天已有汇总在重采样后累加，`local_not_interested` 在同日合并时保持旧值。晚到的非当天跟踪只有在汇总行缺失时写入，已存在的汇总仍保留。无标记观察只计总数，连接来源依次按 DHT、PeX、LSD 和其他来源归类。
- 应用层提供同名 `Database` 方法。扫描器负责排除握手 Peer、计算当地日界以及按 profile 调度，图表与仪表盘由调用方接线。
- 25 项默认存储测试通过，覆盖采样键去重、旧库 31 条会话总数、17 项指标分类、区间边界、同日合并和晚到汇总、清理及失败回滚后重试。PostgreSQL 17.12 和 MariaDB 11.8.6 真库测试复用同套指标夹具，另验证并发聚合只消耗一次。两个临时实例已停止。存储格式检查和严格 Clippy 通过。
- 原生 `scan --apply --once` 在回环 qBittorrent 夹具上持久化一条总连接和一条远端接受传输指标，清空已汇总跟踪。再次启动后，认证 `/api/statistic/counter` 返回 `weeklySessions: 1`，未激活 Plus 的 `/api/chart/sessionAnalyse` 返回 402。授权后的图表由 Plus 阶段继续实测。
## MySQL 8.4 独立验证

- 用官方 `mysql:8.4` 容器运行 MySQL Community Server 8.4.13，端口仅发布至回环，测试后停止并清理容器。Windows 原生存储测试 `cargo +1.96.0 test -p pbh-storage --locked --offline --test mysql -- --ignored` 实际通过 1/1，使用临时数据库执行全部 MySQL 测试夹具。验证覆盖独立 MySQL，另有 MariaDB 11.8.6 运行证据。
