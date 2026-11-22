# Tracker 批量替换

负责人 `downloader`，应用路由由 `root` 接入。实施分支 `codex/rust`。Java 参考提交 `25be37332`。

## 行为依据

- `module/impl/webapi/PBHUtilitiesController#handleReplaceTracker` 注册 `/api/utilities/replaceTracker`，接受 `from`、`to` 和可选下载器 ID 列表。读取所有任务，包括停止和私有种子。命中时逐 URL 按字面替换，保留 tier 及相对顺序。
- `getTrackers`、`setTrackers` 定义实际下载器行为。qBittorrent、Transmission 和 BiglyBT 有写接口。Aria2 Next 和 BitComet 可以读取列表，Java 的 setter 为空，控制器仍将匹配任务计入返回数。Deluge 的 Java tracker 列表为空。

## 实施

- 原生 `rust/app/src/tracker_api.rs` 通过 `POST /api/utilities/replaceTracker` 复用扫描中各下载器的会话。返回 Java 的数值结果和本地化文案。空白字符串保持原有字面语义，空字符串、缺失字段和无效 JSON 返回 400。省略或传空的下载器列表应用到全部已配置可登录下载器。
- 限制每个下载器最多八个并发任务。相邻 HTTP 批量操作按请求串行，以避免同一应用并发读写同一组 Tracker。某一下载器或种子失败时记录错误并继续其他任务。沿用 Aria2 Next、BitComet 的无写入计数行为。
- qBittorrent 的现有协议先删后加。写入失败不会计数；服务器在删除成功后拒绝添加时可能已改变该种子的 Tracker。路由复用这一适配器契约。

## 验证

- `cargo +1.96.0 test -p peerbanhelper tracker_api::tests` 通过。本地 HTTP 服务模拟 qBittorrent 与 Aria2 Next，核对非法输入、选择过滤、停止任务、字面多次替换、伪 Tracker 过滤、qBittorrent 请求顺序、Aria2 Next 无写入计数和英文响应。未连接真实 Tracker 或下载器。
- Tracker 代码格式检查及 `cargo +1.96.0 clippy -p peerbanhelper --all-targets -- -D warnings` 通过。
