# qBittorrent 与 Enhanced Edition

负责人 `downloader`。实现位于 `rust/qbittorrent/`，分支 `codex/rust`，Java 参考提交 `25be37332`。

## 行为依据

- `src/main/java/com/ghostchu/peerbanhelper/downloader/impl/qbittorrent/AbstractQbittorrent.java` 和 `impl/QBittorrentConfigImpl.java` 定义现有 YAML 字段、常规会话与 Bearer Key、分页、属性补全、Peer 过滤、封禁和下载器管理操作。
- `impl/enhanced/QBittorrentEE.java` 与 `QBittorrentEEPeer.java` 定义 Enhanced Edition 的已隔离 Peer 过滤、`shadow_ban_enabled` 检查及独立增量接口和全量配置键。
- `downloader/AbstractDownloader.java` 和 `util/IPAddressUtil.java` 定义登录退避与地址转换。已有 IPAddress 5.6.2 JAR 对照确认 `203.0.113.1` 的 IPv6 映射序列化是 `::ffff:cb00:7101`，对应 IPv4 /30 映射是 `::ffff:cb00:7100/126`。
- [qBittorrent Web API](https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-5.0)) · [API Key 认证](https://github.com/qbittorrent/qBittorrent/wiki/API-Key-Authentication-(%E2%89%A5v5.2.0))

## 实施及验证

- 使用 reqwest 和原生 Rust 数据结构，保留反代路径、原始 Cookie 名称与路径、Basic challenge、API Key 和有上限的登录退避。`read_only` 与 `enhanced_read_only` 可鉴权并读取快照，不修改设置、封禁或 Tracker。密码鉴权仍可能创建服务端会话。
- 保留活动及全量种子分页、哈希去重、按种子属性缓存和 Peer 原始键。覆盖 HTTP/Web Seed、空地址和匿名网络过滤、IPv4 映射与默认 NAT64 转换。
- 纯新增可发增量请求。出现移除、重新同步或先前副作用结果不明时由调用方请求全量替换。全量列表支持 IPv4 对应的映射地址、NAT64、Teredo 和按版本能力启用的范围映射。增强版的 Shadow Ban 使用独立键和接口。
- 已提供流量计数、限速、监听端口和 Tracker 的协议方法。上层 WebUI、监控及 AutoSTUN 的接入各自由相应迁移阶段验收。
- `cargo +1.96.0 test -p pbh-qbittorrent` 的 6 个本地 HTTP 测试通过，覆盖登录、Cookie 续期、Bearer、Basic 与退避、只读访问、分页、Peer 模型、写入失败、增量转全量、增强版和管理 API。Windows 首次启动测试程序一度出现 os error 5，重跑正常。
- `cargo +1.96.0 clippy -p pbh-qbittorrent --all-targets -- -D warnings` 通过。混合下载器测试确认增量表单可保留来自 Transmission 的裸 IP，与 Java `TRPeer` 及 `PeerWrapper` 的原始键一致。

## 尚待对照

- 本地 HTTP 夹具验证了 Rust 侧协议，真实 qBittorrent 两个版本系列与原 Java 进程端到端差分仍待验证。
- Rust 当前禁止自动跨 URL 重定向，Java OkHttp 允许。配置应填写 qBittorrent 的最终服务地址。通过带重定向的反代时需补兼容验证。
- 全局 `ip-remapping` 和 `banlist-remapping` 已接入应用。Java 的旧平台环境开关、AutoSTUN 地址映射和下载器全局代理仍待验收。[下载器地址映射](address-remapping.md)
- 新 Rust 连接统一校验应用版本，Java 部分已有会话分支可能跳过最低版本判定。尚未完整复现松散版本解析的所有输入。
- 空增量请求以及 Tracker 中空的删除或增加阶段当前省略，Java 也可能发送空请求。Tracker 分组按 tier 稳定排序，Java 的 HashMap 迭代顺序随输入而异。
