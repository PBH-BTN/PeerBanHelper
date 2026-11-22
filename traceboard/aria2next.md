# Aria2 Next

负责人 `downloader`。实现位于 `rust/aria2next/`，分支 `codex/rust`，Java 参考提交 `25be37332`。

## 行为依据

- `downloader/impl/aria2next/Aria2Next.java` 与 `bean/A2Task.java`、`A2Peer.java`、`A2GlobalOptions.java` 定义产品探测、标准 JSON-RPC、任务和 Peer 映射、全量封禁及配置读写。仓库通过 `DownloaderManagerImpl.java` 注册 `aria2next`。
- 沿用 Java 的 UUID 请求 ID、`token:<secret>` Authorization 头和 JSON 参数位置。HTTP 请求连接配置给定的完整 RPC URL。RPC 状态检查响应错误字段与请求 ID，诊断不包含服务端自由文本或带鉴权信息的 URL。
- [Aria2 Next RPC 扩展](https://github.com/AnInsomniacy/aria2-next/blob/main/docs/rpc-extensions.md) 说明 `product`、计数器格式、可替换的 IP/CIDR 封禁列表与返回的规则计数和版本。[aria2 RPC 手册](https://aria2.github.io/manual/en/html/aria2c.html#aria2.tellWaiting) 给出等待和停止任务的分页参数。

## 已实施及验证

- 原生只读和可写连接，验证 `aria2-next` 产品和已有鉴权退避。观察者可以运行版本、任务、Peer 和选项查询，不改写下载器。
- 任务过滤普通 HTTP 下载，保留 Java 元数据未就绪时的文件名回退。Peer 保留原始裸 IP 键，按 Latin-1 解码百分号编码 ID，按 Java 标志位输出文本，并向应用暴露独立的 `handshaking`。
- 保留 `setBtPeerBlocklist` 全量替换与清空，匹配 Java 的监听端口、限速字段及数字参数。支持 JSON 数字与十进制字符串响应。
- Java 遗留的 `tellWaiting` 和 `tellStopped` 读取先按原参数发送，在返回无效参数错误后改用官方分页参数。用真实 3.0.8 验证只发字段列表时返回错误码 1，而传入偏移与数量后得到任务列表。
- `cargo +1.96.0 test -p pbh-aria2next` 的 4 项回环协议测试通过，覆盖头/参数、快照、产品区分、只读与全量封禁、旧参数回退、鉴权和失败传递。`cargo +1.96.0 fmt -p pbh-aria2next --check` 和该 crate 的 Clippy `--all-targets -- -D warnings` 通过。
- 真实 [Aria2 Next 3.0.8 Windows 发布](https://github.com/AnInsomniacy/aria2-next/releases/tag/v3.0.8) 在隔离临时目录运行。二进制 SHA-256 与发布资产元数据一致，为 `3e8763f67a7ce6d14f35239bbe88e3b0ee40c7a123424c0fe0b2f2232541c3a1`。进程仅监听回环 RPC，禁用 DHT 和本地 Peer 发现。RPC 直接探测覆盖 IPv4、IPv4 映射 IPv6、IPv6 CIDR、清空以及数字端口和限速。随后原生 PBH `scan --once` 保持原列表的一条规则和 revision 1，无持久层写入。`scan --apply --once` 实际清空列表，使规则数变为 0、revision 变为 2，并创建临时 SQLite。

## 尚待验收

- 真实 Aria2 Next 测试未建立真实 Torrent/Peer 连接，具体 Peer 字段和带真实 Peer 的封禁仍以协议夹具为证。旧版本 2.x、暂时缺失封禁扩展的 3.0.0–3.0.7 和最新平台组合需要版本矩阵。
- Java 适配器始终报告 `private=false`，保存但不使用 `ignore-private`；Rust 保留该行为。Aria2 Next 新版报告 `privateTorrent`，后续如果启用需单独验收。
- 已接入全局 NAT64、Teredo 和封禁范围设置。连接目标使用最终 RPC URL，禁止自动跟随重定向。[下载器地址映射](address-remapping.md)
