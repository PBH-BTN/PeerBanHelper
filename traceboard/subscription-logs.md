# 规则订阅更新日志存储

- 对照 Java `RuleSubLogEntity`、`RuleSubLogServiceImpl`、`IPBanRuleUpdateType`、`IPBlackRuleList` 和三方言 `rule_sub_log` 初始结构。沿用 `id,rule_id,update_time,count,update_type`，计数为有符号 32 位整数，更新类型写作 `AUTO` 或 `MANUAL`。
- `append_subscription_log` 存储成功更新事件。`subscription_logs` 按更新时间倒序，在同一只读快照返回过滤总数及页记录，相同时刻用 ID 倒序稳定分页。`latest_subscription_log` 按最大 ID 取最近插入记录，保留 Java 在更新时钟乱序时与日志列表的区别。可选 `rule_id` 使用精确匹配。
- `rust/app/src/storage.rs` 提供同名分发方法。订阅配置管理、刷新调度、规则生效及 HTTP 输出由应用线程接入。
- 29 项默认存储测试通过。SQLite 夹具验证自动／手动类型、按规则精确筛选、同一时间倒序分页、超出末页保留总数、重启及两种最新顺序。PostgreSQL 17.12 和 MariaDB 11.8.6 实库验证通过，并已停止临时实例。应用和存储严格 Clippy 通过。
