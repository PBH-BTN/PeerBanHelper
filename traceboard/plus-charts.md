# Plus 图表

负责人 storage。参照 Java `PBHChartController`、`PeerRecordServiceImpl`、`PeerRecordMapper`、三方言的 `PeerRecordMapper.xml` 和 `HistoryMapper.xml`、`PeerFlag`、`TimeUtil` 与 WebUI 图表调用。Rust 实现在应用的 `chart_api.rs` 和存储的 `peer_charts.rs`。现有 `USER_READ` 和 Plus `basic` 许可门控覆盖全部七条图表路由。

- `geoIpInfo` 根据 `bannedOnly` 读取封禁 IP 或访问 Peer 地址，地址去重，时间首尾包含，可按下载器筛选。含前缀的地址取网络首地址。使用同一 GeoIP 快照，按数量输出 ISP、省份、地区和城市。GeoIP 禁用时保留原语言提示与 HTTP 200 失败响应。
- `trend` 按 Peer 最后见到和历史封禁的日期统计两组趋势，使用已验证的本地日界转换、时间排序和非空下载器筛选。
- `sessionDayBucket` 选整个会话均在区间内的 Peer 记录，将记录计入首末日期之间的每个 24 小时桶，通过 `I` 标志计入入站连接。桶键仍须落在请求的起止毫秒内。
- `sessionBetween` 对起止时间有交集的 Peer 地址去重。Java 原 SQL 使用数据库不存在的 `lastTimeSeen`、`firstTimeSeen` 列，并在全下载器查询时用等号匹配字面 `%`。Rust 使用实际列，并在未指定下载器时统计所有下载器。
- `clientAnalyse` 将区间内双向流量均为正的记录按 Peer ID 和客户端名分组，返回组总数、页数据、累计上传／下载和记录数。默认按上传量降序。仅固定的结果字段可排序，重复字段按 Java `Orderable` 的覆盖规则处理，值使用数据库绑定参数。通过实际列直接读取 Peer ID 和客户端名，避开 Java 数据映射的列别名不一致。

## 验证

- 应用及存储编译通过，32 项默认存储测试通过。共享夹具覆盖 IPv6、两类地址各自去重、含首尾和超出时间范围、会话包含与相交、转入转出流量过滤、分组、分页和排序白名单。MariaDB 发布表的时间字段仅保留秒，表外测试样本以整秒间隔构造。
- `cargo +1.96.0 test -p pbh-storage --test mysql --test postgres --offline -j 1 -- --ignored` 在 PostgreSQL 17.12 和 MariaDB 11.8.6 的临时回环实例均通过，两个实例已关闭。独立 MySQL 实例未验。
- Windows 原生 HTTP 回环验证五条路由。匿名访问全部返回 403，已认证且无许可返回 402。通过现有本机试用接口激活 Basic，重启后许可仍有效。验证禁用 GeoIP 的提示、测试 MMDB 对瑞典与 Google ASN 的汇总、封禁与 Peer 地址去重、两组日期趋势、会话相交与下载器筛选、入站日桶，以及客户端排序和分页。使用隔离配置、回环代理和测试数据库。
- 应用日期桶单测通过，覆盖首日未到查询下界时排除该桶、多个记录跨日重叠、只通过大写 `I` 标记计入入站。应用及存储的 `cargo clippy --all-targets -- -D warnings` 和本次 Rust 文件格式检查均通过。
