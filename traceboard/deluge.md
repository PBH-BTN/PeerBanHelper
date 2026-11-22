# Deluge

负责人 `downloader`。实现位于 `rust/deluge/`，分支 `codex/rust`，Java 参考提交 `25be37332`。

## 行为依据

- `downloader/impl/deluge/Deluge.java` 及 `DownloaderManagerImpl.java` 定义登录流程、插件能力、下载器配置、下载快照及外部同步。配置承认 `deluge` 和历史类型 `raccoonfink/deluge`，Java 配置保存使用历史类型。
- `raccoonfink/deluge/DelugeServer.java`、`DelugeRequest.java` 和 `responses/PBHActiveTorrentsResponse.java` 定义旧式 Web JSON-RPC 消息结构、字段名及参数层级。实际插件实现可对照 [PBH-Adapter-Deluge 的 RPC 源码](https://github.com/PBH-BTN/PBH-Adapter-Deluge/blob/main/deluge_peerbanhelperadapter/core.py)。
- `util/StrUtil.java` 和 `bittorrent/peer/PeerFlag.java` 定义 Peer ID 的十六进制与 Latin-1 转换、八字符截断以及 Peer 标志输出。种子和 Peer 进度均按 Java 路径除以 100。

## 已实施

- 原生 HTTP JSON-RPC、会话 Cookie、密码验证及失败退避。只读模式可以鉴权及读取，禁止设置和封禁写入。登录要求原版列出的四个插件能力。
- 原生扫描、进度、原始 Peer 键、IPv4 映射和默认 NAT64 转换。保留现有 `getTorrents` / `getAllTorrents` 都使用活动快照的行为。Java 读取 `ignore-private` 配置但未在该适配器应用此过滤。
- 增量和全量封禁都使用规范化的 Peer IP，增量仅用于纯新增，存在移除、恢复或强制刷新时全量替换。RPC 失败向调用方返回，调度器可按全量方式恢复。
- 保留插件当前封禁列表、会话流量计数、限速查询及设置。Tracker 编辑和 BT 端口更新在 Java Deluge 适配器没有实际实现。
- `cargo +1.96.0 test -p pbh-deluge` 通过 4 项回环 HTTP 测试，验证 Cookie、能力探测、观察模式、结构化字段、写入异常后的全量恢复，以及限速和退避。`cargo +1.96.0 fmt -p pbh-deluge --check` 与该 crate 的 `cargo +1.96.0 clippy --all-targets -- -D warnings` 通过。
- `python -B test/rust-parity/check_deluge_scan.py target/debug/peerbanhelper.exe` 通过原生应用端到端测试。用 `deluge` 配置完成只读扫描和 IPv4 增量封禁，然后用 `raccoonfink/deluge` 重启，验证持久化恢复、到期解封、全量替换和历史连续性。

## 待验收

- 真实 Deluge WebUI 与插件版本矩阵、Java 参考进程差分仍需在对应环境执行。
- Java 默认地址映射可能发出 IPv6 CIDR。插件将每一项作为起止相同的参数交给 libtorrent IP filter。Rust 保留现有请求数据，实际插件对这些 CIDR 的行为待验证。
- 已接入全局 NAT64、Teredo 和封禁范围设置。AutoSTUN 地址映射仍待接入。[下载器地址映射](address-remapping.md)
- HTTP 重定向要求配置最终 WebUI 地址。认证、插件能力和副作用的错误返回类型由 Rust 显式表示。Java 版本会把部分失败记录到日志后当作空列表或成功处理。
