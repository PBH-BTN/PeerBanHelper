# 封禁统计图表

- 依据 `PBHMetricsController`、`HistoryServiceImpl`、三种方言 `HistoryMapper.xml`、`TimeUtil`、`WebUtil` 和 WebUI `charts.ts`。`/api/statistic/analysis/field`、`/api/statistic/analysis/banTrends` 和 `/api/statistic/analysis/date` 都保持 `USER_READ` 权限。无须 Plus。
- `field` 支持按历史表已有字段及 `torrent_name` 分组计数，并对数值列分组求和。字段表达式来自固定白名单，下载器 ID 使用参数绑定。保留 Peer ID 前 8 个字符、空值排除、计数降序和严格大于百分比阈值。以数据库分组的原始数值计算比例，避免部分方言把数值 0 当作空字符串、把 INET 用作文本比较或使用有限整数位的 DECIMAL 比例。文本求和等无统一数值语义的组合返回错误。
- 两条日期路由只选中 `ban_at` 在含首尾的查询区间内的记录，随后按要求的封禁或解封时间归组。`banTrends` 使用当地日界，支持下载器过滤并按日界升序。PostgreSQL 保留 Java `OffsetDateTime` 读取 UTC 日期后附当地偏移的行为。`date` 非日封禁查询支持年、月、日、小时、分钟、秒和包含式比例阈值，年／月键保留 Java Calendar 第 0 日语义。旧 `banAt/day` 路径忽略比例与下载器并固定输出 `percent: 0`。
- 31 项默认存储测试已通过。SQLite、PostgreSQL 17.12 和 MariaDB 11.8.6 用同套夹具检查 Peer ID 前缀与空值、严格阈值、数值／小数求和、IPv6 及按封禁时间筛选解封数据。
- 原生程序的回环 HTTP 测试确认三路匿名返回 403，普通登录可读取。HTTP 响应验证字段白名单、计数／求和、带下载器的封禁趋势、空区间、旧日封禁输出、跨区间的解封时间，以及年和月的日期键。
- 应用日历边界单测、应用及存储严格 Clippy、原生应用构建均通过。PostgreSQL 与 MariaDB 验证实例已关闭。
