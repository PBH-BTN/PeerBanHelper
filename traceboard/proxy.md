# 原生 HTTP 代理

- 负责人 downloader，应用的全局配置及 GeoIP、BTN、规则订阅、推送接线由 root 和对应模块负责人维护。
- Java 参考 `25be37332` 的 `util/HTTPUtil.java`、`config/MainConfigUpdateScript.java` 和适配器 HTTP 配置。运行时以 `0` 直连、`1` HTTP、`2` SOCKS 为准，旧配置升级会将系统代理改为直连并迁移旧模式数值。参考包的配置模板注释仍显示旧数值。
- `rust/network` 将以上选择逻辑应用于异步与同步 reqwest builder。直连显式禁用环境或系统代理。非代理主机表达式按 `|` 切分，对 `*` 和 `?` 作 Java 相同的替换，并匹配完整 hostname。跳过无效表达式，报告其数量。拒绝在代理主机字段嵌入凭据并使用不含输入内容的错误。
- 对可识别的 IPv4 字面量保留 Java `IPAddress.isLocal()` 的 RFC1918、链路本地、未指定、链路和管理域多播及 SSM 例外。`127.0.0.1` 由发行配置里的 `127.*` 放行。用参考版 Gradle 缓存中的 Guava 与 IPAddress 源码、JShell 验证了 Java URI 的带方括号 IPv6 形式不会命中字面量检查。IPv6 可用配置中的主机表达式放行。
- 七种下载器类型采用共享代码，并保留各适配器默认直连入口。Java 的 Transmission `cordelia/client/TrClient.java` 直接使用 `HTTPUtil.newBuilder()`，因此原生应用的 Transmission 使用全局策略。其余适配器的 `newBuilderForDownloader()` 默认绕开全局代理，原生应用保留开关。

## 验证

- Windows `cargo +1.96.0 test -p pbh-network -p pbh-qbittorrent -p pbh-transmission -p pbh-deluge -p pbh-aria2next -p pbh-biglybt -p pbh-bitcomet` 通过 36 个代理与适配器测试，七 crate 的 `clippy --all-targets -- -D warnings` 通过。现有 qBittorrent 夹具补充显式代理登录，校验 Cookie 与只读无设置写入。
- Debian WSL 的独立 `cargo +1.96.0 test -p pbh-network` 通过 5 个测试。回环 HTTP 校验异步与同步 builder 的绝对请求目标、绕过表达式、直连和 HTTPS CONNECT 请求行，回环 SOCKS5 校验代理侧域名解析请求。独立 crate 显式启用 reqwest `rustls-tls`，确保 SOCKS 连接器有适用的 HTTP transport。
- `python test/rust-parity/check_downloader_api.py target/debug/peerbanhelper.exe webui/dist` 验证配置了 HTTP 代理时 qBittorrent 默认直连，显式禁用 `pbh.downloader.bypassproxy` 后同一登录通过代理。

## 已知行为范围

- SOCKS 使用 SOCKS5 和代理端 DNS。JRE 可能为旧 SOCKS4 服务协商降级，Rust 这一分支目前未实现。Java Pattern 与 fancy-regex 的少数高级语法存在差异。
- CONNECT 已验证至代理，现有夹具通过失败响应终止隧道。成功的隧道内 TLS 和真实代理部署待进一步验证。
