# 原生持久告警与 WebAPI

- 负责 `rust/storage/src/alerts*`、`rust/app/src/alert_api.rs` 和 `Database` 告警接口。对照 `AlertManagerImpl`、`AlertServiceImpl`、`AlertMapper`、`PBHAlertController`、`TranslationComponentTypeHandler` 和 WebUI `service/alert.ts`。Java 源码仅用于读取契约。
- 沿用原 `alert` 表的创建及已读时间、严重等级、标识符和标题及正文 JSON。保留翻译组件中的嵌套参数，HTTP 响应按请求语言渲染。未读列表按创建时间倒序，相同时间按 ID 倒序。各数据库保持原表的时间精度。
- 普通告警按未读状态去重，已读后允许用相同标识重新发布。每日流量告警使用包含已读的去重模式。SQLite 使用立即事务，PostgreSQL 使用事务内表锁，MySQL/MariaDB 使用当前数据库命名锁。并发 Rust 发布只插入一条，原 Flyway 表结构不变。清理删除 `create_at <= cutoff` 的已读和未读告警。应用按原设计设定 30 天保留期。
- HTTP 提供 `GET /api/alerts`、`PATCH /api/alert/{id}/dismiss`、`POST /api/alert/dismissAll` 和 `DELETE /api/alert/{id}`。单条已读返回已有记录是否命中，重复调用仍成功。删除不存在的 ID 返回成功。演示模式拒绝写请求。
- 推送渠道与桌面通知由应用接续。当前存储返回实际插入结果，可以用同一结果决定是否发送。
- `cargo +1.96.0 test -p pbh-storage --locked -j 1` 通过 23 项默认测试。SQLite 验证重复发布、并发去重、读过后再次发布、包含已读的去重、翻译内容和清理边界。原 ORMLite 夹具中的未读 `WARN` 告警可通过原生查询读出，源文件字节一致。
- PostgreSQL 17.12 和 MariaDB 11.8.6 共用真实库集成用例通过，覆盖两个连接竞争发布、读写行为和保留期。Rust 存储严格 Clippy 通过，两个临时数据库已停止。
- 在回环 qBittorrent 夹具和临时 SQLite 下，使用原生二进制实测全部四条告警 HTTP 路由、匿名请求 403、英文翻译、30 天清理、重复单条已读、缺失 ID、批量已读及演示模式拒绝写入。未调用实际外部推送渠道。

## 应用推送

- `rust/app/src/push.rs` 移植九类配置及其请求形状。Webhook 支持占位符、URL 编码、JSON 内容转义和自定义头。SMTP 使用 lettre 处理认证、TLS、邮件编码和投递。SMTP 部分投递模式通过各收件人的单独 SMTP 信封实现，保留相同 MIME 邮件头。部分收件人被拒时的服务器行为仍需对照。[lettre 的 SMTP 传输说明](https://docs.rs/lettre/latest/lettre/transport/smtp/struct.AsyncSmtpTransport.html)
- `push_api.rs` 接入现有 WebUI 的查询、创建、更新、删除和测试。共享 YAML 文件编辑器持锁读取并原子替换，避免与 Plus 或 AutoSTUN 写入互相覆盖。测试请求需要用户会话或令牌。演示模式拒绝更改及测试，列表隐藏配置内容。
- `check_push.py` 验证认证、Webhook URL 和头部编码、渠道配置变更以及隔离 SMTP 投递。流量测试中的每日告警只触发一次真实回环 Webhook。其它渠道、SMTP TLS 和真实服务尚未部署验证。
