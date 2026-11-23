# 设置、重载和网络查询

- Rust `general_api.rs` 保留 WebUI `config`、`profile` 读取、部分更新以及 `/api/general/reload` 的授权入口。新增设置先按类型和规则校验，通过后由共用配置锁写入整份 YAML。互斥锁避免下载器、推送、订阅和设置请求同时写出半份配置。保存保留未涉及的值，序列化会去掉注释。旧版本升级保存 `*.before-rust-vN.yml` 原始文件。
- 读取沿用 API 下划线键和历史字符串规则列表的对象表示，写入恢复原有主配置键和规则字符串。热重载时保留活跃封禁、手动操作队列、全局暂停、计数以及单写入锁。停止旧实时流、保存统计状态，再构建新运行时。整个过程保留同一个监听套接字和未换令牌的会话，换令牌后丢弃旧会话。数据库及 HTTP 绑定地址和端口标记 `REQUIRE_RESTART`，其余修改标记 `SCHEDULED`。
- Windows 回环 `check_settings_reload.py` 验证拒绝非法设置且文件逐字节保留、201 的状态负载、日志 SSE 结束和同进程监听恢复、跨重载保留仅在内存中的封禁和暂停。`check_subscriptions.py` 验证授权订阅管理、历史日志、扫描命中和 HTTP 503 后保留本地缓存。Rust 启用和手动刷新由统一任务流报告。
- 系统反向解析延续系统解析器，新增 `resolvers.use-system` 和显式 `resolvers.servers`。`dns.rs` 以 Hickory 生成及解析 DNS 报文。两个定向回环测试覆盖错误 ID、UDP 截断后的 TCP 重试、PTR 缓存与自定义路径的 DoH 二进制请求。HTTPS 远端服务和 DNS 权威链路待实测。[Hickory DNS](https://github.com/hickory-dns/hickory-dns) [RFC 8484](https://www.rfc-editor.org/rfc/rfc8484)
- Windows 配置 `performance.windows-ecoqos-api` 连到 Win32 当前进程电源节流与优先级。实验室两项开关保留现有标签和分组。Java 工作集定时器的实际清理被 `lastRun` 判定挡住，Rust 保留该开关的配置和可见状态。[Win32 SetProcessInformation](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setprocessinformation)
- `/api/general/stacktrace` 返回当前 Rust 执行线程的调用栈，保留文本和 JSON 两种响应形式。JVM 专属 HPROF 只显示在 Vue 连接旧 Java 服务时，对应原生内存分析尚缺。
- `reachability.rs` 定期通过同一出站代理尝试全局与中国连通性检查。初始值未知，测试用 `PBH_DISABLE_NETWORK_PROBES=true` 禁止请求公网。两个回环响应证实 `2xx` 与 `503` 判定。
- `journal_file` 在 `data/logs/latest.log` 追加日志，沿用 Java 的按日或 20 MiB 滚动格式和保留近七天、至少五份备份的行为。`PBH_LOGSDIR` 控制落盘目录。原生覆盖文件锁与 gzip 归档，首次运行的回环测试核对进程日志与持久日志的监听地址。
- 同一原生服务可用既有环境变量 `PBH_DATADIR`、`PBH_CONFIGDIR`、`PBH_SERVERADDRESS`、`PBH_PORT` 与 `PBH_APITOKEN`。Windows 进程夹具验证分离的配置与数据路径、监听覆盖、鉴权覆盖、封禁落库及跨重载。磁盘保留原始 YAML 监听设置和令牌，空环境令牌沿用 YAML 值。`PBH_USEPLATFORMCONFIGLOCATION` 提供可选用户目录，显式 `--data-dir` 优先。Rust 将 `localhost` 解析为回环地址，Java 对该字面值使用监听通配地址。
