# BiglyBT

负责人 `downloader`。实现位于 `rust/biglybt/`，分支 `codex/rust`。

## 行为依据

- Java 通用对照提交 `25be37332` 仍使用 `/bans` 一代协议。BiglyBT 单独采用此前获准的版本化协议，行为依据是 PBH `codex/biglybt-rewrite` 提交 `4a658f6a3` 的 `downloader/impl/biglybt/BiglyBT.java` 与配套 DTO。该分支拒绝缺少协议 2 的旧插件。
- 插件实装对照为 `PBH-Adapter-BiglyBT` 只读检出提交 `efdf087` 的 `Plugin.java`、`BanState.java` 和 `SnapshotResponse.java`。[插件 PR #6](https://github.com/PBH-BTN/PBH-Adapter-BiglyBT/pull/6)
- 协议均为 PBH 发往插件的 HTTP 请求。插件版本由 `GET /metadata` 核对，`POST /setconnector` 仅在插件保存 PBH 客户端标签。无需给 PBH 添加反向回调或 WebSocket。

## 实施与验证

- 令牌认证、只读探测、版本 2 验证、插件禁用过滤的状态、请求识别和失败分类。只读连接不会发送 connector 状态或修改列表和设置。
- 批量快照通过 `GET /v2/snapshot?includePrivate=...` 获取含空闲连接的 Peer。HTTP 507 时回退到协议 2 插件仍提供的 `GET /downloads?filter=4&filter=5&filter=8` 和逐种子 Peer 查询，404 视为种子已消失。保留 Peer ID、握手状态、原始地址及流量字段。
- 列表通过 `GET/PUT/PATCH /v2/bans`。增量包含插件实例、版本、新增、移除。409 后全量替换，重连时比较实例和版本并恢复期望状态。发布的旧式未版本化封禁写接口不在移植范围。
- 保留吞吐计数、BT 端口、限速和 Tracker 编辑路由。`cargo +1.96.0 test -p pbh-biglybt` 通过 5 项回环 HTTP 测试。格式检查和该 crate 的 Clippy `--all-targets -- -D warnings` 通过。
- 已在新的隔离 BiglyBT 4.0.0.0 控制台进程启动真实 2.1.0 插件，证实回环 `/metadata` 协议号为 2，`PUT /v2/bans` 可以提交 IPv4 和 IPv6 CIDR。完整原生应用烟测入口为 `python -B test/rust-parity/check_biglybt_scan.py target/debug/peerbanhelper.exe --core-jar <BiglyBT.jar> --plugin-jar <BiglyBT-PBH-Plugin.jar> --cli-jar <commons-cli.jar>`。
- 聚合应用重建后，上述原生应用烟测通过。观察模式保留真实插件预置的封禁和 revision，未打开 SQLite。可写模式清空测试列表，插件实例标识不变且 revision 增加 1，SQLite 创建成功。BiglyBT 测试进程结束后不留运行实例。

## 尚待验收

- 真实 Torrent/Peer 列表以及较高并发的快照流量仍需系统验证。原生应用与真实客户端的测试使用空快照，带非空种子的协议行为另由本地 HTTP 夹具覆盖。
- 已接入全局 NAT64、Teredo 和封禁范围设置。BiglyBT 批量模板里的无效 IP、空 Peer 或不完整记录会被跳过。[下载器地址映射](address-remapping.md)
