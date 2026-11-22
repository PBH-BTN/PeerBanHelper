# AutoSTUN 与连接地址映射

负责人 `downloader`。Rust 实现在 `rust/traversal/`，分支 `codex/rust`。Java 参考提交 `25be37332`。

## 来源与边界

- `com.cdnbye.core.nat` 提供 UDP Binding、重传和 NAT 分类。`StunManager` 使用已配置服务器列表并缓存结果。
- `util/traversal/forwarder/TCPForwarderImpl` 以连接上游下载器时使用的本地地址及端口作为反查键，将其映射至真实的下游 Peer。`NatAddressProviderRegistry` 将反查能力提供给下载器，地址转换发生在 Teredo、NAT64 之前。
- 按标准核对旧格式 CHANGE-REQUEST、现代 XOR-MAPPED-ADDRESS、RESPONSE-ORIGIN、OTHER-ADDRESS 和 FINGERPRINT。[RFC 3489](https://www.rfc-editor.org/rfc/rfc3489) · [RFC 5780](https://www.rfc-editor.org/rfc/rfc5780) · [RFC 8489](https://www.rfc-editor.org/rfc/rfc8489)

## 实施

- `BindingRequest` 提供旧版与带 magic cookie 的现代格式。`decode_response` 按事务校验类型、长度、属性填充和可选的 FINGERPRINT，解析 IPv4/IPv6、普通映射、XOR 映射和服务器响应码。旧版请求沿用 Java 的前四字节为零，其余 96 位和现代格式一样使用系统随机数。
- `UdpStunClient::binding` 返回映射地址，`probe` 复用单个 UDP 源端口，保留 Java 的八种 NAT 分类和缺省的三次发送、一秒重发间隔、五秒总时间预算。普通事务要求响应来自目标地址。改变源地址的事务只接受已在首轮声明的地址组合，校对报文内来源与实际 UDP 源地址。
- `ConnectionMappings::register` 通过上游本地 socket 反查下游 Peer。IPv4-mapped IPv6 地址共用 IPv4 键。连接所持登记对象释放时删除映射，保证后续回收同一端口后可重新登记。
- Java 通过报文中的 SOURCE-ADDRESS 判断换源，并接受匹配事务 ID 的其他来源。Rust 使用实际 UDP 源地址，忽略与已声明端点不符的回复。Test III 收到未换源的回复时返回 `Unknown`，旧 Java 在该分支只验证是否收到回复。UDP 托管配置选择旧版或现代协议仍待接线。

## 验证

- `cargo +1.96.0 test -p pbh-traversal` 通过 5 项。IPv4 和 IPv6 使用独立标准向量。回环 UDP 测试覆盖每种 NAT 判定、同一源端口重传、伪来源或错误事务丢弃、解析失败、异常状态码及动态映射移除。[RFC 5769](https://www.rfc-editor.org/rfc/rfc5769)
- `cargo +1.96.0 fmt -p pbh-traversal --check` 与 `cargo +1.96.0 clippy -p pbh-traversal --all-targets -- -D warnings` 通过。网络测试均限本机回环。

## UPnP IGD

- 行为参考 `util/portmapper/PBHPortMapperImpl`、`org/bitlet/weupnp/GatewayDiscover` 和 `GatewayDevice`。`IgdClient::new(Ipv4Addr)` 绑定选定接口，`discover(Duration)` 发送 IPv4 SSDP 请求并重试，可用 `discover_at(SocketAddrV4, Duration)` 对指定目标发起发现。[UPnP 设备架构](https://openconnectivity.org/upnp-specs/UPnP-arch-DeviceArchitecture-v2.0-20200417.pdf)
- 解析 SSDP 状态和大小写不敏感的头，去重描述 URL。读取设备 XML 时优先 WANIPConnection 2、WANIPConnection 1、WANPPPConnection 1，并解析 `URLBase` 及相对的 `controlURL`。描述与控制地址均限定为 SSDP 回复方的 IPv4 字面量，关闭 HTTP 重定向和代理。XML 禁用 DTD 并限制响应大小。[UPnP IGD 设备和服务定义](https://openconnectivity.org/developer/specifications/upnp-resources/upnp/internet-gateway-device-igd-v-2-0/)
- `Gateway::{is_connected, external_address, add_mapping, delete_mapping}` 实现实际 SOAP 操作。`PortMapping` 包含内外端口、TCP/UDP、描述和租期，零租期对应 Java 默认。`MappingLease::refresh` 重申映射，`remove` 先核对该端口仍指向登记的本地地址和端口。已不存在的映射可重复删除。网关拒绝有限租期时返回 725，由调用方决定是否改用永久租期。
- `cargo +1.96.0 test -p pbh-traversal` 共 10 项通过。新增 5 项覆盖 SSDP、XML、回环 HTTP/SOAP 映射生命周期、状态码、冲突、改派后的删除保护和重定向拒绝。测试没有接入真实网关。
- 后续由应用负责枚举接口、网卡变化后的重新发现、多网关的成功映射登记、定期续期和关停清理。本次仅提供按接口控制和租约操作。旧版接受带主机名的设备 URL，当前仅支持能与回复方 IPv4 核对的地址。实际厂商和固件兼容性仍待验证。

## TCP 转发器

- 参考 `util/traversal/forwarder/TCPForwarderImpl`、`iohandler/` 和 `table/ConnectionStatistics`。`TcpForwarder::start` 接收应用已绑定的监听器，只向预先校验的本机或局域网下载器端点发起连接。IPv4 回环友好绑定按 Java 顺序尝试原始端口、随机端口和系统默认源地址。普通连接始终适用，友好映射仅用于 IPv4 Peer 和回环上游。
- 保留同一来源 IP 最多一个连接、建立中占位、封禁检查及 30 秒复查。`start_with_policy` 接收调用方的封禁判定，`disconnect_banned` 支持立即关闭新封禁的连接。可选全局并发限制包括正在建立的连接。单次上游建连缺省限时 30 秒，可独立配置空闲超时。关停等待监听器、连接、地址映射全部释放。
- 使用 Tokio 双向转发和反压。收到单向 EOF 后只关闭对应写入方向，另一方向继续转发。Java 通常在一侧断开时关闭另一侧。Rust 按实际成功写入的字节统计双向流量，保留连接和最近活动时间。`snapshot` 返回每流及汇总统计，动态地址反查表在连接建立后登记，在退出时释放。[Tokio 双向转发接口](https://docs.rs/tokio/latest/tokio/io/fn.copy_bidirectional.html)
- Tokio 使用平台原生事件队列，运行模式报告 `EPOLL`、`KQUEUE`、`IOCP` 或 `DEFAULT`。Java 单独启停 NIO、epoll、kqueue、io_uring 的开关和 SO_REUSEPORT 监听配置尚未复刻。调用方传入已设置 socket 选项的监听器。[Mio 平台事件队列](https://github.com/tokio-rs/mio#features)
- `cargo +1.96.0 test -p pbh-traversal` 通过 16 项，其中 6 项 TCP 回环验证双向流量、半关闭、连接统计、接口地址反查、封禁取消、来源及并发限制、连接失败、空闲超时及容错前置条件。格式和严格 Clippy 检查通过。主动连到失败的目标使用回环，无真实外网操作。连接超时分支使用 Tokio deadline，回环用例验证拒绝连接的清理行为。

## TCP STUN 隧道

- 参考 `util/traversal/stun/TcpStunClient`、`StunSocketTool`、`tunnel/StunTcpTunnelImpl` 和 `btstun/BTStunInstance`。`TunnelListener::bind` 先保留端口，可让应用在探测之前建立 UPnP 映射。`establish` 返回该同一监听器及 `TcpTunnel`。TCP STUN 和保活都从相同本地端口连出。Windows 使用 `SO_REUSEADDR`，支持的 Unix 平台同时启用 `SO_REUSEPORT`。[Winsock 端口复用](https://learn.microsoft.com/en-us/windows/win32/winsock/using-so-reuseaddr-and-so-exclusiveaddruse)
- TCP Binding 复用现有现代 STUN 编解码，按报头读取完整帧。验证事务后，根据配置顺序切换服务器。默认先完成一轮，每服务器预算五秒。可配置额外轮次，默认轮间等待十秒。Java 自行无限重试，Rust 返回无可用服务器或最后失败，供应用决定重启时机。[RFC 8489 TCP 传输](https://www.rfc-editor.org/rfc/rfc8489#section-6.2.2)
- 可达性测试使用一次性路径，要求请求实际抵达保留的监听器，外部请求收到 HTTP 204 后才进入可用状态。应用可以关闭测试。保活沿用 Java 的 HTTP HEAD、首发一秒、间隔十秒和连续五次失败上限。Rust 校验完整 HTTP 响应，在 EOF 或超时时重新连接，连续失败达到上限后结束任务。`TcpTunnel::{snapshot,wait_stopped,shutdown}` 为运行状态及释放提供入口。
- Windows 和 Debian WSL2 上的 `cargo +1.96.0 test -p pbh-traversal` 均通过 20 项。新增四项隔离回环测试覆盖同端口监听和出站、分段 STUN 响应、错误事务换服务器、失败轮次、HTTP keepalive 复用及重建、失败状态和错误的 204。库的格式、编译和严格 Clippy 通过。无公网 STUN、保活服务器或真实网关操作。
- 应用需要提供已解析的 STUN 和保活端点，负责 UPnP 租约、转发器、下载器监听端口，以及失败重试和关闭顺序。当前每个监听器绑定一个地址族，STUN 和保活目标使用相同地址族。当前回环测试验证 IPv4，实际 NAT hairpin 和平台矩阵仍待验收。

## 下载器隧道运行时

- `rust/app/src/auto_tunnel.rs` 通过 `AutoTunnels::start` 为选定下载器管理独立隧道。只有持续运行且允许写入的应用入口才能显式启用。暂停、只读和缺少 Java 实时端口更新能力的 Deluge 不会发起 TCP 隧道探测。qBittorrent 两版、Transmission、Aria2 Next、BiglyBT、BitComet 复用同一登录实例读取和更新 BT 端口。
- 解析局域网上游，登录并读取原端口，再预留本地监听端口，按通往 STUN 的 IPv4 接口尝试 UPnP。执行 STUN 和可达性验证后启动转发，外部端口有变化时才写下载器。应用维护共同的实时封禁集合和地址映射，提供重启、封禁连接断开、查询状态和关停入口。
- 原生运行时每五秒重试启动失败的实例。运行时连续 11 次下载器检查失败后重建。正常关停与 Java 一样保留已设的下载器端口，回收 TCP 监听、保活和本次成功建立的 UPnP 映射。启动端口写入返回错误或中途收到取消时，会核对下载器上的端口，仍为本次写入值时尝试恢复原值。
- `cargo +1.96.0 test -p peerbanhelper auto_tunnel::tests` 通过三项。本机 qBittorrent、TCP STUN、保活和 BT 转发夹具覆盖启用条件、实时记录、封禁断开、人工重启、关停、部分成功端口写入回滚及启动写入期间取消。回环 STUN 不触发 SSDP。修改文件的格式检查和 `cargo +1.96.0 clippy -p peerbanhelper --all-targets -- -D warnings` 通过。
- `cargo +1.96.0 test -p peerbanhelper -p pbh-traversal` 通过 28 项。`python -B test/rust-parity/check_autostun_config.py target/debug/peerbanhelper.exe` 通过。原生 CLI 从 `auto-stun.enabled: false` 起步，经已认证 `PUT` 启用指定下载器并在扫描轮次应用，完成实际回环 TCP Binding 和保活，随后通过 API 重建隧道，再用 `PUT` 停用并确认监听端口关闭。夹具设定 `stun.upnp: false`。

## 尚待移植

- UDP 配置校验、主机解析、服务器失败轮换、启动和每小时状态刷新。现有协议核心接受已解析端点，可由主应用选择再次探测。
- 真实网关发现、UPnP 续期与多网卡迁移还需部署验收。应用当前在每次建立隧道时按所选路由接口重新发现，使用 Java 默认的永久映射。
- Netty 专用 I/O 模式开关、真实外部 NAT、多平台和实际下载器实时切换的验收仍待进行。应用重新加载配置及控制器和 WebUI 的行为由各自条目追踪。
