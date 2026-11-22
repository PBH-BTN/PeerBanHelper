# Transmission

负责人 `downloader`。实现位于 `rust/transmission/`，分支 `codex/rust`，Java 参考提交 `25be37332`。

## 行为依据

- `downloader/impl/transmission/Transmission.java` 定义 4.1.0 最低版本、配置字段、扫描筛选、P2P Plain blocklist URL、失败提示 URL、监听端口、限速、流量统计和 tracker 设置。
- `cordelia/client/TrClient.java` 定义 HTTP Basic challenge、GET 协商 session ID、legacy JSON RPC 请求及遇到 409 后单次重试。4.1 还提供新的 JSON-RPC 2.0 协议，现有 Java 对照使用 Transmission 4 保留的 legacy 接口。[Transmission RPC 规范](https://github.com/transmission/transmission/blob/main/docs/rpc-spec.md)
- `TRTorrent.java` 与 `TRPeer.java` 定义种子总大小和进度换算、Peer ID 的 Base64 及 Latin-1 解码、原始 IP 键和缺失计数的 `-1` 哨兵。
- `module/impl/webapi/BlockListController.java` 定义 `/blocklist/p2p-plain-format`、随机短 UUID 标签、地址范围及 Transmission 空列表占位。

## 实施与验证

- Rust 实现按遗留 HTTP/RPC 顺序处理 cookie 无关的 session ID，并在多个并发调用之间复用已协商的值。第二次 409 后向上层返回失败。
- 只读连接鉴权和探测但跳过 session-set、blocklist-update 及全部可写管理入口。可写连接仅在列表关闭或源 URL 不同的情况下初始化 blocklist。重新加载时由 Transmission 请求 PBH 的 HTTP 接口，使用同一个状态源完成新增和解封。
- 封禁列表格式化保留默认的 IPv4 映射、IPv6 /52、NAT64 和 Teredo 处理，以及只对 Transmission User-Agent 生效的空列表占位。已覆盖 CIDR 输入的 IPv4 映射、IPv6 带范围和地址去重。
- `cargo +1.96.0 test -p pbh-transmission` 通过 5 项本地协议测试。真实 loopback HTTP 夹具验证 Basic challenge、自定义 RPC 路径、session 轮换与 409 上限、Torrent/Peer 映射、Blocklist 服务器回调和失败处理、限速端口与 tracker 操作。格式检查和 `cargo +1.96.0 clippy -p pbh-transmission --all-targets -- -D warnings` 通过。
- `python -B test/rust-parity/check_transmission_scan.py target/debug/peerbanhelper.exe` 通过应用入口验收。只读扫描命中但无写入，可写扫描驱动隔离的 Transmission 夹具从原生 PBH 的公开 HTTP 监听器下载包含 IPv4 及其 IPv6 映射的封禁列表。重启后到期解除，夹具只下载空列表占位，SQLite 封禁历史仍一致。

## 尚待对照

- 当前没有运行真实 Transmission daemon。Windows Docker API 管道未运行，WSL 中找不到 `transmission-daemon`。`cargo test` 使用独立回环 HTTP 服务模拟下载器的协议响应。
- Rust 端的限速查询使用 session-get 对应的 `speed-limit-*` 键。旧 Java 使用面向种子的字段名。修改 tracker 时 Rust 发送数值种子 ID，旧实现传其字符串形式。两处均需真实下载器对照。网络端拒绝 RPC 时 Rust 返回可供重试的错误，Java 部分设置方法仅写日志。
- 已接入全局 NAT64、Teredo 和封禁范围设置。AutoSTUN 地址映射仍待接入。下载器 URL 必须使用最终地址，Rust 不自动跟随 HTTP 重定向。[下载器地址映射](address-remapping.md)
- 对 Java 参考进程的差分、真实应用 WebUI 与下载器版本矩阵仍需后续系统验收。
