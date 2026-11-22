# BitComet

负责人 `downloader`。实现位于 `rust/bitcomet/`，分支 `codex/rust`，Java 参考提交 `25be37332`。

## 行为依据

- `downloader/impl/bitcomet/BitComet.java`、`BCEndpoint.java`、`crypto/BCAESTool.java` 和 `resp/` 定义 PBH 的实际请求与响应模型。
- BitComet 官方文档核对了 RNCryptor v3 登录、设备 Token、Peer 解封及 IP filter 文件导入。[认证](https://wiki-zh.bitcomet.com/webui_api%E8%B0%83%E7%94%A8%E6%8E%A5%E5%8F%A3/authentication/) · [任务详情](https://wiki-zh.bitcomet.com/webui_api%E8%B0%83%E7%94%A8%E6%8E%A5%E5%8F%A3/task-details/) · [配置](https://wiki-zh.bitcomet.com/webui_api%E8%B0%83%E7%94%A8%E6%8E%A5%E5%8F%A3/configuration/)

## 实施

- 保留 Java 的固定客户端 UUID、WebUI 识别头和邀请 Token → 设备 Token 路径。使用 RustCrypto 的标准 AES-CBC、PBKDF2 和 HMAC 实现官方报文格式。复用设备会话，401/403 后尝试重新登录，错误不回显认证材料。
- 只读连接需要在 BitComet 建立认证会话，但跳过配置、封禁和解封写入。可写连接在新设备会话检查并设置 blacklist 过滤模式。
- 任务以 50 项分页读取，只请求 BT 详情，详情并发上限为 3。保留隐私过滤、v1/v2 infohash、选中文件统计、千分比、Peer 分组以及优先采用地址内端口的规则。BitComet 某些版本未知的 Peer 计数使用 `-1`。
- 清理某一地址时按正式协议发送裸 IP，而非 Java 当前路径产生的 Lombok `PeerAddress.toString()` 调试文本。随后以 UTF-8 及 Base64 上传完整过滤列表，匹配 Java 的全量替换策略。
- 保留 Tracker 读取、全局速度和 TCP 监听端口配置。按 2.20 和 2.21 的接口变化分别选择统计字段及显示 token。原 Java 未实现 Tracker 编辑。

## 验证

- `cargo +1.96.0 test -p pbh-bitcomet` 通过 5 项测试，包括独立 Node/OpenSSL 生成的已知加密向量和 4 项本地 HTTP 契约测试。格式检查和该 crate 的 Clippy `--all-targets -- -D warnings` 通过。
- 测试覆盖非空 Peer、2.18 版本下限、错误密码、设备会话更新、只读不写、BT 任务分页、IPv6 内嵌端口、旧统计格式、解封与导入顺序、错误传播。
- `cargo +1.96.0 build -p peerbanhelper` 和 `python -B test/rust-parity/check_bitcomet_scan.py target/debug/peerbanhelper.exe` 通过。原生应用连接本地 BitComet 协议夹具，验证只读扫描、完整列表导入、重启、过期后按任务解封、重新导入空列表及 SQLite 历史保留。

## 尚待验收

- 真实 BitComet 版本矩阵仍待运行。导入 API 在一些版本异步应用，Rust 检查请求状态和响应错误码，和 Java 一样未轮询真实客户端的导入完成时间。
- 已接入全局 NAT64、Teredo 和封禁范围设置。AutoSTUN 地址映射仍待接入。[下载器地址映射](address-remapping.md)
- 原 Java 在持续抓取请求失败时继续请求下一页，也会因当前页无 BT 匹配而停止。Rust 返回 HTTP 或协议错误以便调度器重试，并在满页时继续扫描后续记录，避免忽略夹在其他任务之后的 BT 任务。
