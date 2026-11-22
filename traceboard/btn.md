# BTN 配置、IP 查询、规则和上报

`rust/btn` 对照 Java `btn/BtnNetwork.java`、`btn/ability/impl/BtnAbilityIpQuery.java`、`util/pow/PoWClient.java`、`PBHPeerController.java` 和现有 BTN IP 查询回归。

- BTN 默认关闭。启用后读取 `config-url`，核对当前协议 21 是否位于服务端声明的最小与最大版本间，保留 `min < 20` 的旧能力标记和数据提交配置。公开 `advertised_abilities` 表示服务端声明，IP 查询能力只有协议配置实际提供时才构造。
- 请求携带旧版和当前 BTN App ID/Secret 请求头及 `Authentication: Bearer <id>@<secret>`。收到 `401` 时最多用 `Authorization` 重试一次。缺失或示例凭据发送安装标识。默认请求时限 60 秒，构造、超时和上游状态错误分型。仅转发 `400`、`409`、`429` 的上游 `message` 字段，凭据头标记为敏感，网络错误去除 URL。
- 保留既有 query 参数及绝对 URL，支持相对于配置 URL 的子路由。旧版只声明 `endpoint` 时，lookup、评论、计数、iframe 等选项保持不可用。可用时支持查询、评论列表、创建、更新、删除和计数，空的 `204` 响应转换为各方法的空结果。评论分页大小夹到 `1..50`。
- 计数批次最多 50 条，重复输入只向上游请求一次，缓存零值且只请求缺项。缓存容量 4096、保留一分钟。批次在途时另一未命中批次返回 `429`。非整数、负数、超过 Java `long` 和过大指数都视为无效整批响应，失败数据不进入缓存。
- 带证明的请求通过服务端正式挑战接口获取挑战，计算大端 8 字节 nonce 并填写 `X-BTN-PowID`、`X-BTN-PowSolution`。已支持 SHA-1 和 SHA-2 算法，使用 8 秒工作窗口及单个证明计算槽。挑战服务返回错误、算法不支持或超时时返回错误。Java 的任意 JCA 算法、自动重定向和挑战失败后无证明继续提交尚无等价覆盖。客户端使用项目默认的直接连接方式，自定义代理随运行时配置移植。
- 旧 iframe URL 继续包含 `ip`、`appId`、`appSecret`、`hardwareId` 和 `installationId`，保留已广告的其他参数并作 URL 编码。此契约会把凭据放进 URL，构造函数仅供受保护的应用路由调用，URL 禁止记日志或缓存。应用优先复用原生硬件 UUID，缺失时使用 Java 安装标识兜底。调用者仍可通过 `with_hardware_id` 注入已获取的哈希。
- 已实现默认接口心跳 `POST {"ifaddr":"default"}`，和发送到指定本地源地址的认证心跳。默认提交不带 BTN 凭据，服务端要求证明时用 `type=heartbeat` 领取挑战。两种调用输出可选的 `external_ip`，并对外提供协商的毫秒周期和启动抖动元数据。
- 重配检查请求原配置 URL，按 `ability.reconfigure.version` 返回未变、更新或服务端停用，供运行时触发下一次协商。保留重配周期及抖动元数据，实测版本变化与整项能力撤回。

## IP 列表与在线规则

- 对照 Java `BtnAbilityIPAllowList`、`BtnAbilityIPDenyList`、`BtnAbilityRules`、`BtnRulesetParsed`、`BtnNetworkOnline` 和 `IPMatcher`。按 `min_protocol_version < 20` 协商旧 `rules`，现代协议读取 `rule_peer_identity`、`ip_allowlist`、`ip_denylist`。请求沿用协商认证头、原参数、`rev` 和各自的 PoW 类型。`204` 保留现有快照，HTTP 与解析失败不会发布不完整的在线规则集。
- IPv4、IPv6、IPv4 映射、CIDR、IPv4 掩码及常用 inet_aton 字面量走统一前缀匹配。DAT/eMule 的等级小于 `128` 时按 Java 行为取覆盖起止地址的最小单一前缀块。多条前缀命中时保留 `elementsContaining` 首节点的宽前缀说明，后出现的同一前缀覆盖先前说明。不支持的列表行跳过，Java 专用通配符和不规则地址范围尚未经真实服务验证。[Java 地址解析文档](https://seancfoley.github.io/IPAddress/IPAddress/apidocs/inet/ipaddr/IPAddressString.html) · [Java Trie 匹配文档](https://seancfoley.github.io/IPAddress/IPAddress/apidocs/inet/ipaddr/format/util/AssociativeAddressTrie.AssociativeTrieNode.html)
- 优先返回允许列表的 `SKIP`，再返回拒绝列表的 `BAN`。仅对非握手 Peer 执行身份规则，命中优先顺序为端口、IP、客户端名、Peer ID。文本规则复用 `pbh-core::StringRules`，包含同组显式 `FALSE` 否决。多组同时命中同一类型时，Rust 按组名排序以稳定结果，Java HashMap 对重叠组可能给出不同原因。输入和缓存中的 `script` 内容全量丢弃，导出缓存只写空对象，使旧版 Java 读取缓存时可以继续使用非脚本规则。
- 允许/拒绝列表可从现有两条 Java 元数据行恢复，在线规则复用 `btn.ability.rules.cache`。读取返回不可变快照，刷新串行并在成功后整体替换，快照可用于同一轮 Peer 检查。恢复入口只初始化空快照，防止旧缓存覆盖已刷新内容。存盘接入、规则更新事件、允许项解除历史封禁及通知，由应用层继续移植。

## 现代和旧版上报

- `Negotiated.reports` 提供类型化提交入口。以 `btn.submit`、广告能力及最小协议版本筛选可用入口。最小版本至少 20 时启用持久封禁和 Swarm。旧协议启用封禁快照和实时 Peer 快照，并在首次调度抖动之前等待一个周期。两种协议有 `submit_histories` 广告时可上报 Peer 历史。
- 五种上报使用各自的 DTO 和 gzip 压缩 POST，复用 BTN 认证与 401 重试。保留现代封禁的内嵌 JSON 字符串、Swarm 可空私有种子标记、旧 Peer 历史的默认端口 0 及私有标记 false、毫秒时间戳和 Java 历史 IP 文本格式。种子标识计算源字符串转小写，以小端 CRC32 十六进制作为盐，再计算 SHA-256。上传 DTO 无原始 info hash 字段。
- 现代封禁、Swarm 和 Peer 历史使用独立的 PoW 类型。Peer 历史的挑战类型为单数 `submit_history`。旧版实时 Peer 和封禁快照没有 PoW 步骤。Swarm 保留 Java 的 HTTP 200–399 受理规则。客户端统一禁止 HTTP 重定向，因而状态 3xx 不转发请求或数据。其他上报需 2xx。
- 封禁历史接收按 ID 递增的每页 100 条记录。调用层过滤旧数据中无法转换的记录，若选取的源页全部转换失败，提交空的 `bans` 并等待确认后再推进 `BtnAbilitySubmitBans.cursor`。Swarm 先持久化跟踪记录，Java 用 `last_time_seen >= millis` 和 `id > id` 读取每页 1000 条，按 `last_time_seen,id` 升序，确认后存 `BtnAbilitySubmitSwarm.cursor` 为 `毫秒,id`。
- Peer 历史读取 `btn.submithistory.timestamp`，缺失、无法解析或超过 30 天时使用当前时间。Java 方法请求每页 5000 条，MyBatis 全局上限是 1000。应用层只在服务端确认后更新游标，继续读取时用复合游标防止遗漏毫秒相同的多条记录，并可同时保留 Java 的时间戳元数据供旧版读取。
- 旧版封禁快照按完整 Peer DTO 去重，发送协商开始以来新出现且未确认的普通封禁。空快照不联网。收到成功响应后仅确认实际进入该批次的事件 ID，空批次及失败响应不推进水位。固定初始时间界限并包含同毫秒，避免遗漏在空轮询后才发布到快照或在请求期间出现的封禁。原生运行时重配保留仍活跃的已确认 ID。旧版快照继续使用 `BanMetadata.toString()` 对应字段的 SHA-256 生成 `ban_unique_id`。种子和 Peer 实时快照由扫描运行时提供。

已完成 BTN 客户端层的协商、规则、查询和上报请求。完整运行时的重载与失败重试、心跳网卡枚举、代理/直连、数据库游标、定时上报、规则更新事件和上线回放继续在应用层核对。外部 BTN 互操作未验证。

## 验证

`cargo test -p pbh-btn` 通过 11 项，其中 8 项绑定回环监听并校验实际 HTTP 请求。覆盖禁用状态、协商范围、旧版能力、新旧认证头、匿名安装标识、单次认证重试、IPv6/CIDR 编码、分页、评论写入、独立计数证明、挑战 nonce 校验、错误码、超时、iframe 字段、默认/绑定源地址心跳、重配版本、列表缓存、规则优先级、`204` 和错误时保留快照及脚本退役。定点整数、并发和地址用例分别验证指数溢出、`i64::MAX`、负值、未命中限流、历史 IPv4 表示和 IPv4 映射。`cargo fmt -p pbh-btn --check` 与 `cargo clippy -p pbh-btn --all-targets -- -D warnings` 通过。未连接外部 BTN 实例。

合入上报客户端后，`cargo test -p pbh-btn --locked` 通过 14 项，`cargo fmt -p pbh-btn --check` 与 `cargo clippy -p pbh-btn --all-targets -- -D warnings` 通过。上报回归验证现代及旧版 DTO 的完整解压 JSON、种子标识向量、认证重试、各证明类型、错误响应、仅 Swarm 接受 3xx、禁止重定向、旧快照筛选和去重、空批次行为、提交开关和旧游标 30 天窗口。测试只使用回环端口。

## BTN 模块状态接口

- `GET /api/modules/btn` 通过认证后读取后台轮询状态，复用 Java `PBHBtnController` 的响应和能力字段，对实际协商的能力显示最近一次完成时间、结果、规则版本与数量。状态返回配置连接结果和仅可见前五个 UTF-16 码元的凭据掩码。Java 参考运行时即使配置关闭 BTN 也会注册 BTN 组件，因此原生同样返回 `enabled:true`、`configSuccess:false` 和空能力列表。列表按固定顺序呈现，Java 的能力注册表遍历顺序不固定。
- 协商、规则和 IP 列表缓存恢复及远端更新、心跳、服务端重配、查询和上报记录实际结果。已协商的 IP 查询客户端供 WebUI 调用，避免轮询与 HTTP 处理器再次协商。新协议规则统计采用实际支持的 IP、Peer ID、客户端名和端口规则数，脚本计数始终为零。动态错误使用 HTTP 状态和原生错误摘要，避免在状态页传播上游错误正文。心跳地址在演示模式隐藏。服务端撤回重配能力时记录撤回状态并继续当前协商，Java 同样等待版本变化才重新协商。
- 进程内协商结果通过状态锁提交快照，读取状态不会重新请求 BTN。`cargo +1.96.0 check -p peerbanhelper --locked` 和 `cargo +1.96.0 test -p peerbanhelper btn_api::tests --locked` 通过。定向集成测试用回环服务验证未认证 403、协商失败后恢复、只读能力筛选、IP 拒绝列表失败及恢复、规则数量、实时查询结果、脱敏和无额外协商。
- Java 25 与原生 `peerbanhelper run` 的回环对照通过 5/5 项，覆盖关闭配置、匿名请求、只读协商、查询前后和 en-US、zh-TW 输出。能力按名称排序后比较所有字段，分别验证各能力更新时间为正数。Java 启动时若两个毫秒时间戳相等，可在下一轮重试前通过其现有重载接口触发协商，回环夹具这样取得了稳定快照。Rust BTN 协议测试复跑 14/14。

## 多网卡心跳

- 对照 Java `BtnAbilityHeartBeat`。`multi_if=false` 发送一个匿名 `POST {"ifaddr":"default"}`，使用已配置的 PBH 代理与绕过规则。`multi_if=true` 从本机枚举源地址，每个可用单播 IP 同时发送明确直连和应用配置代理策略的两个已认证请求。配置代理策略选定直连时仍保留两次尝试。绑定 TCP 源地址及选定连接目标的地址族，无法在同族绑定时记录失败。IPv6 请求使用 Java 的八段十六进制形式。
- 保留任何成功返回 `external_ip` 的整体成功判定，以最后完成的请求更新每个地址的结果，按源地址字符串排序后合并相同结果。多网卡汇总时限为 30 秒，到期取消尚未完成的请求。演示模式逐条隐藏结果。枚举保留回环、专用和普通全局单播地址，跳过未指定、多播、全局 IPv4 广播及无法在不带 zone 的地址上绑定的 IPv6 链路本地地址。重复地址合并，同族 DNS 查找与地址绑定不会回退到无绑定连接。
- 证明仍走协商的 `type=heartbeat` 请求。多网卡心跳在同一个 30 秒汇总期限内轮候复用单个证明计算槽，其他能力仍按原限额处理。使用原单槽即时拒绝接口时，回环夹具发现并发代理与直连中一路返回 429，已由排队修复。默认匿名请求只携证明头，绑定请求仍带 BTN 认证头。
- `cargo +1.96.0 test -p pbh-btn --offline --locked` 在 Windows 通过 16 项。回环夹具分别观测直连和显式 HTTP 代理接收的实际 TCP 源地址、请求字段及独立证明，覆盖匿名模式、IPv6 展开文本和跳过不适用地址。代理夹具就地响应请求，测试没有使用系统代理或转发到公网。
- `cargo +1.96.0 test -p peerbanhelper --bin peerbanhelper heartbeat --offline --locked` 通过 2 项。协商后使用实际本机枚举向回环服务发起请求，确认 `127.0.0.1` 的两个绑定任务和多网卡模式的状态聚合。独立测试确认每个源地址最后结果、重复结果合并和演示脱敏。

## 安装标识、协商重试与规则任务

- 匿名请求的 `X-BTN-InstallationID` 与 iframe `installationId` 沿用配置原文，安装 ID 缺失时两者均为空。硬件 ID 优先对系统硬件 UUID 文本做 SHA-256。系统 UUID 缺失时使用 `IID-<安装 ID>`，配置缺失和显式空分别对应 `IID-failed-to-retrieve` 和 `IID-`。保留 `with_proxy` 原有调用入口，应用使用可区分缺失值的构造入口。
- 首次协商失败间隔 600 秒重试，重配尝试失败后旧规则和轮询保持可用，到成功解析并恢复缓存后整体替换。正数随机启动抖动选择 `[0, 上界)`，保留零抖动作为立即运行。远端重配检查仍按协商周期执行。
- IP 允许列表、IP 拒绝列表和定向特征规则更新使用 Java 的后台任务名称。`BackgroundTasks` 随现有调度提供队列、执行和完成状态。Java 在这些任务内处理远端和解析错误，仍标记后台任务完成。原生保留同样的任务事件，并在 BTN 能力页记录实际失败。关停或重配期间取消未完成的任务。
- `cargo +1.96.0 test -p pbh-btn --offline --locked` 通过 17 项，新增安装 ID 缺失、显式为空、已有 ID 和原文 UUID 的四组回环向量。
- `cargo +1.96.0 test -p peerbanhelper --bin peerbanhelper btn --offline --locked` 通过 5 项。认证 SSE 回环验证三个规则任务的 zh-TW 文案和完成事件，同时验证已处理的 HTTP 503 仍保存在能力失败状态。另一项回环测试在空轮询之后发布封禁，再在首个上报确认期间发布同毫秒的第二条封禁，验证两条均上报且随后不重复提交。
- 应用进程验证 `python test/rust-parity/check_btn_reporting.py target/debug/peerbanhelper.exe` 和同命令带 `--legacy` 均通过。覆盖现代封禁与 Swarm 重试、确认后推进的游标、允许列表解封只产生一条 INFO 告警，以及旧版 Peer 快照与封禁重试和确认。

## BTN 请求级后台任务

- 按 Java 的实际任务入口，协商、远端重配检查、心跳、三类规则刷新、现代封禁、Swarm 和 Peer 历史上报在 `/api/tasks/live` 发布任务。Peer 历史任务适用于两种协商协议。旧版实时 Peer 快照、旧版封禁快照和 IP 查询直接执行。任务使用已有词条，保持不定进度和空状态文本。
- 重配首次延迟为一个协商周期加启动抖动。远端版本变化时，外层重配任务等待内层协商及能力接管，内层先完成。处理过的协商、重配、心跳、规则和 Peer 历史 HTTP 错误发布 `COMPLETED`，能力或配置结果继续显示失败。现代封禁与 Swarm 的错误传出任务，发布 `FAILED`。Peer 历史的本地持久化或编码异常同样发布 `FAILED`。配置无法构造客户端时保留启动校验，未进入实际协商请求。
- 已协商且允许提交的 Swarm 上报会读取已有持久快照。采集器存在时先刷新其缓冲内容。Java 在采集模块启用时重置历史表，BTN 本身在独立调度中读取记录。现代封禁和 Swarm 成功但本轮为空时保留 Java 的 0 条状态。
- Windows `cargo +1.96.0 test -p peerbanhelper --bin peerbanhelper btn --offline --locked` 通过 7 项，同一测试二进制完整复跑 32/32。回环观察挂起的两层重配任务均为运行中，放行后按内层、外层顺序完成。模拟心跳 502 和重配 503 的处理结果。另一夹具从 SQLite 发起三种上报，封禁与 Swarm 503 标为失败，Peer 历史 503 仍完成，放行后重试成功。该夹具关闭 Swarm 采集器并验证已有记录仍参与协商授权的上报。
- `cargo +1.96.0 clippy -p peerbanhelper --all-targets --offline --locked -- -D warnings` 通过。

## 尚待核对的 BTN 行为

- 原生已复用可读取的系统 UUID。Java 在 OSHI provider 不存在或返回 `null` 时才会为第一个合适的接口取 MAC，provider 存在时原样使用其 UUID 文本。现用接口枚举库只提供地址和接口标志，原生缺有效 UUID 时沿用 IID 兜底，部分 OSHI 占位值及 MAC 分支仍可能生成不同 ID。
- Java 的任意 JCA 摘要、部分挑战失败后无证明继续请求和自动重定向存在差异。原生维持单槽证明计算、8 秒计算窗口、受支持的 SHA 算法与禁用重定向，避免在重定向时转发凭据或报告。
- IP 列表继续保留先前记录的特殊地址语法与重叠匹配差异。真实 BTN 实例、实际双栈出口与真实代理环境尚未参与验收。
