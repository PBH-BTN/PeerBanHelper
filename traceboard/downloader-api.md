# 下载器管理 API

- 负责人 downloader，应用路由挂载与运行时重载由 root 接线。Java 参考 `25be37332` 的 `PBHDownloaderController`、`DownloaderManagerImpl`、各 `saveDownloader` 配置模型和 `JavalinWebContainer` 的权限判断。对照 `webui/src/service/downloaders.ts` 与仪表盘编辑弹窗的请求。
- `PUT /api/downloaders` 接受 `{id, config}` 并返回 201。下载器 ID 或名称重复返回 400。`PATCH /api/downloaders/{id}` 使用 URL ID，允许已有配置更新和缺失 ID 新建。`DELETE /api/downloaders/{id}` 找不到该 ID 返回 404。
- `POST /api/downloaders/test` 跳过暂停的下载器，其他下载器使用对应的只读登录与有效代理策略。登录失败返回 HTTP 200 与 `success: false`，仅返回适配器的错误摘要。共享首次设置页面的 JSON 字段转换，保存 Deluge 时保留 `raccoonfink/deluge` 类型并接受两种输入类型。
- 四条路由都通过应用共用的认证，演示模式拒绝写权限路由。对配置的编辑使用共用文件锁与原子替换，仅在编辑成功后通知扫描服务重载。原本 Java 在 PATCH 冲突时可能已经移除旧配置，Rust 让旧配置继续可用并返回同样的失败状态。

## 自动发现

- `POST /api/oobe/scanDownloader` 与 `POST /api/downloaders/scan` 使用 `pbh-network::discovery`。首次设置仅在 token 激活前可用，仪表盘遵循用户认证与演示模式写权限。返回 Java DTO 的 `host`、`port`、`pid`、`type` 与 `NEW`/`ADDED` 状态。
- 参考 `util/DownloaderDiscovery.java`、`IPAddressUtil.adaptIP` 和 `PBHOOBEController.handleOOBEScanDownloader`。只读取系统的 TCP LISTEN 表，用 `Server` 响应头识别 Transmission 和 BiglyBT，用认证挑战或页面正文识别 BitComet，用正文识别 qBittorrent。按已配置的本地端口标记 `ADDED`。通配监听地址经 `127.0.0.1` 请求，IPv6 主机在结果中带方括号。
- 凭系统 socket 清单提供 Windows、Linux、macOS、FreeBSD、OpenBSD 与 NetBSD 的实现，其他平台返回空结果。可见监听器与 PID 取决于操作系统和当前权限，macOS 及 BSD 尚未实机验证。 [listeners 平台文档](https://docs.rs/listeners/0.6.1/listeners/)
- 探测使用直连 HTTP，只跟随保持原主机和端口的重定向，单请求有 8 秒超时，最大 64 个并发，正文读取上限 512 KiB。Java 参考实现可使用全局代理和跨地址重定向。Rust 限定于本机列表中的目标。

## 验证

- Windows `python test/rust-parity/check_downloader_api.py target/debug/peerbanhelper.exe webui/dist` 通过。覆盖认证、只读测试、默认及显式 HTTP 代理、暂停、登录失败与凭据隐藏、双键去重、更新冲突保留旧项、补丁 URL ID、旧类型别名、删除、重载后读取和启用后实际请求 Peer。
- Windows `cargo +1.96.0 test -p pbh-network -p pbh-web` 通过 15 个测试，Debian WSL `cargo +1.96.0 test -p pbh-network` 通过 8 个测试。发现夹具使用显式提供的回环监听器，覆盖四类客户端、状态、字段、回环重定向和跨端口阻止，未对当前电脑的其他服务发请求。OOBE 初始化后返回 403，仪表盘无认证请求返回 403。
- Windows `cargo +1.96.0 clippy -p pbh-network -p pbh-web --all-targets -- -D warnings` 与 `cargo +1.96.0 build -p peerbanhelper` 均通过。原生应用构建有一项实验室模块的未使用方法警告。

## 待验证

- 远程适配器实际副作用由只读模式规避。Java 的测试登录可能修改下载器设置，原生测试只建立所需的登录会话和读取协议能力。部分 Java 登录结果文字更细，Rust 使用发行语言短语包装适配器的摘要。
