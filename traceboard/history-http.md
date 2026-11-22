# 封禁排行、历史查询和保留期

- 对照 `PBHBanController.handleRanks`、`HistoryServiceImpl`、三套 `HistoryMapper.xml`、`Pageable`、`PBHPage`、`PeerBanCount` 与 WebUI 排行组件。SQLite、PostgreSQL、MySQL/MariaDB 按地址统计封禁，按 `count DESC` 排序，同分不添加额外排序。非空 `filter` 使用带绑定参数的 `LIKE filter%`，保留 `%` 和 `_` 的匹配语义，空字符串与缺省相同。同一只读事务返回分组总数和分页结果，使用 MyBatis 页大小约定。输出 `peerIp` 和 `count`。
- PostgreSQL 使用 `host(ip)` 作为 `INET` 的匹配文本和输出地址。Java mapper 的 `INET LIKE VARCHAR` 在 PostgreSQL 无法直接解析。IPv6 在 SQLite/MySQL 沿用 Java 写入的文本，在 PostgreSQL 沿用原生主机文本。该查询不会改写现有记录。
- 对照 `PBHPeerController` 和 `PBHTorrentController`，复用存储的按 IP 或种子 ID 查询。四条封禁与访问历史路由需要 Plus，`/api/torrent/{infoHash}` 及排行仅需用户读取权限。未知种子返回本地化 404。按 Peer 查封禁的 DTO 保留可空上传与下载字段，按种子查封禁复用 Java 的数值 DTO。访问历史保留 Java 地址格式、各项流量、下载器身份和种子的可空私有状态。
- 对照 `PersistMetrics` 与 `HistoryServiceImpl.deleteExpiredLogs`，`cleanup_ban_history` 以最多 200 条的批次删除 `ban_at <= cutoff` 的历史记录。应用按 `persist.ban-logs-keep-days` 调度。相关种子、访问历史和当前封禁单独保存。
- 27 项默认存储测试及 PostgreSQL 17.12、MariaDB 11.8.6 实库用例通过。覆盖空排行、分组总数、IPv4/IPv6 前缀、通配符、末页、种子关联计数、可空私有标记及保留期边界。SQLite 另验证 201 条跨批次清理，其他数据仍可读取。临时服务均已停止，存储及应用严格 Clippy 通过。
- 在临时 SQLite 和回环 qBittorrent 夹具上运行原生程序，确认排行榜区分非授权 403、空筛选与空结果，并保留既定排序。种子详情计数和未知种子 404 通过。四条历史路由在本地 Plus 试用前为 402，授权后均为 200，返回选定数据与翻页总数。测试包含流量空值在 Peer 封禁和种子封禁之间的 DTO 差异。没有使用外部许可证服务。

## 种子搜索和 Peer 概览

- `/api/torrent/query` 和 `/api/peer/info/{ip}` 延续 Java `USER_READ` 权限。WebUI `GetTorrentInfoList` 使用 `keyword`、`page`、`pageSize` 和 `orderBy`，`GetIPBasicData` 使用 URL 编码地址。两个入口读取本地数据，不执行规则订阅刷新。Plus 仍控制另外四条历史明细路由。
- 三种数据库在只读快照查询种子总数、页项目及封禁／访问计数。关键字匹配名称或 info hash 的任意片段，保留原 LIKE 通配符。普通列排序保留首次出现位置和最后指定的方向，以默认 `id DESC` 收尾。首个计数字段优先于其他排序，以 `id DESC` 处理同分。普通列排序先取页，再查询该页的计数。返回 Java `TorrentInfoDTO` 的字段，缺省大小按 0。
- Peer 概览从一条只读查询得到封禁次数、种子访问次数、流量累加及首末见。仅封禁的 Peer 仍为 `found: true`，无访问记录时流量为 `-1`、首末见为 `0`。接受 IPv4/IPv6、IPv4 附端口和方括号 IPv6 附端口，按 Java 地址格式输出。使用当前 GeoIP 快照和 BTN 协商 watch 值，查看概览不发 BTN 查询。PTR 使用三秒超时的系统反向解析。Java 曾用设置的 DNS/DoH 解析器直接查询 IP 文本的 PTR，当前 Rust 实现按规范查询反向域，暂未接入项目自选 DNS/DoH 列表。
- 新增 SQLite 用例及 PostgreSQL 17.12、MariaDB 11.8.6 真库用例已通过，覆盖名称／哈希匹配、通配符、排序优先级、分页和三类 Peer 汇总状态。
- 原生 HTTP 回环夹具验证两条接口匿名请求均返回 403，普通读取令牌可访问。种子查询验证默认排序、通配符及计数优先级。Peer 概览验证带端口的 IPv4 和方括号 IPv6、历史汇总、无访问记录默认值及返回的回环主机名。BTN 夹具只收到配置协商请求，`btnQueryAvailable` 随协商置为真。GeoIP 禁用时返回 `null`。应用和存储严格 Clippy 通过。
