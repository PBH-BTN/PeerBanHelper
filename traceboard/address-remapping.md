# 下载器地址映射

负责人 `downloader`。实施分支 `codex/rust`，Java 参考提交 `25be37332`。

## 行为依据

- `downloader/AbstractDownloader#addressTranslate` 定义 AutoSTUN、Teredo、NAT64、IPv4 映射的顺序。`wrapper/PeerAddress` 为下载器原始地址保留独立字段。
- `util/IPAddressUtil#remapBanListAddress` 定义默认 IPv4 映射对、Teredo 对应地址、NAT64 提取、可选的 IPv4/IPv6 封禁前缀，以及列表去重。`module/impl/webapi/BlockListController` 将同一策略转换为 Transmission P2P Plain 范围。
- 使用 Java 构建缓存中的 ipaddress 5.6.2 和 JShell 核对跨表示 CIDR。`::ffff:c000:200/120` 映射为 `192.0.2.0/24`，NAT64 `/120` 提取相同长度的 IPv4 `/24`。保留更宽的既有封禁前缀。

## 实施

- `rust/core/src/address_remapping.rs` 集中提供 `AddressPolicy`。空 NAT64 前缀列表关闭此翻译。Teredo 开关仅控制快照 Peer 的 IPv4 和 UDP 端口转换，封禁翻译仍使用 Java 的原有规则。
- qBittorrent 标准版和增强版继续以未修改的原始键进行增量封禁，仅对全量替换使用版本协商后的网段能力。保留原 `pbh_qbittorrent::AddressPolicy` 导出。
- Deluge、Aria2 Next、BiglyBT 和 BitComet 的 Peer 和封禁共享同一策略。Transmission 的 Peer 及 P2P Plain 文本范围也共享策略，保留其空封禁表兼容值。
- 所有适配器提供 `with_address_policy`，既有构造和默认配置保留。Transmission 另提供 `render_p2p_blocklist_networks_with_policy`，用于向 HTTP 输出传入同一应用配置。

## 验证

- 核心和六个下载器 crate 的协议测试均通过，包含自定义 NAT64、可选 Teredo 端口、网段能力、禁用 IPv6 扩展、映射 IPv4、已有范围和去重回归。涉及的 crate 均通过 `cargo +1.96.0 fmt --check` 和 `cargo +1.96.0 clippy --all-targets -- -D warnings`。
- `cargo +1.96.0 build -p peerbanhelper` 通过。默认与自定义策略的 `python -B test/rust-parity/check_transmission_scan.py target/debug/peerbanhelper.exe` 均通过。加上 `--custom-address-policy` 时，原生扫描匹配经自定义 NAT64 前缀翻译的 Peer，Transmission 从应用下载 IPv4 `/30` 和对应 IPv6 映射范围，重启后过期清理仍成功。Deluge 和 BitComet 应用烟测重跑通过。

## 尚待验收

- AutoSTUN 运行时生成的动态外部地址映射尚待平台功能与扫描配置接线。当前策略覆盖静态映射。
- WebUI 的 `PeerAddress` 响应仍缺少 Java 使用的原始端口与 NAT64/Teredo 翻译详情。扫描使用翻译后的端口，并保留下载器原始封禁键。
- 语义相同的 IPv6 地址可能使用不同的零压缩形式，保持了先前 Rust 适配器的 wire 格式。真实下载器的完整版本矩阵仍待验证。
