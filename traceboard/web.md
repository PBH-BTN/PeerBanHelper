# 原生 WebUI HTTP 基础层

实现位于 `rust/web`。对照 `PBHAuthenticateController.java`、`PBHMetadataController.java`、`JavalinWebContainer.java`、`web/wrapper/StdResp.java`、后端三套消息翻译，以及 WebUI 的 `service/login.ts`、`service/version.ts` 和 `api/model/manifest.ts`。

- `/api/metadata/manifest` 公开响应 `success/message/data`，携带构建信息、可用模块、安装标识和分析配置。启用模块列表由调用方提供。
- 登录、退出和受保护路由支持 JSON 响应契约。未配置令牌返回 `303` 和 `Location: /init`，错误登录令牌返回 `401`，缺失受保护访问身份返回 `403`。服务器使用会话 Cookie 或现有 Bearer/query token 顺序认证，客户端 Cookie 只持有会话 ID。
- 失败限制以 IPv4 `/24` 或 IPv6 `/50` 和 15 分钟窗口计数，第 12 次尝试返回 `429`。使用实际 TCP 远端地址分组，部署在反向代理时对受信代理来源地址的处理仍待配置与验收。
- WebUI 静态目录可指向现有 Vite 构建输出，GET 请求支持 SPA 深层路由回退。Java 匹配不到 API 时由 `EndpointNotFound` 返回 `405` JSON，Rust 保留该行为。
- `router_with_routes` 支持显式传入公开及需认证的路由。Transmission 的下载端封禁列表通过公开路由安装。

## 首次配置

`router_with_setup` 接受显式现有 YAML 配置文件路径。仅在启动时令牌为空白时注册四条 OOBE 路由，完成后同一进程立即启用新令牌并阻止再次初始化。qBittorrent 标准/增强版和 Transmission 使用只读客户端验证登录，SQLite 用临时内存库验证。当前向导只接受 SQLite 和禁用 BTN 的首次配置，其他后端、下载器发现，以及扫描器进程的即时重载待实现。

处理顺序为验证请求、联网检查下载器、确认磁盘配置仍未初始化、提交，再启用令牌。使用同目录临时文件保留文件权限并同步内容，最后调用 `tempfile::persist` 替换原文件，Windows 使用 `MoveFileExW` 的替换语义。并行初始化由互斥锁串行，且数据库类型和下载器验证失败时磁盘文件保持原样。重新序列化保留无关 YAML 值，原注释和排版会变化。配置错误和鉴权失败不回显令牌、密码、原 YAML 或下游响应。

待补全模块 API、SSE、许可证角色、平台 GUI 静默登录、可配置的语言默认值与登录安全开关，以及真实 WebUI 联调。会话采用本进程内存存储，登录更新会话 ID，退出清除会话；分析开关当前仅出现在 manifest，不注入 Java 版 HTML 中的远程分析脚本。

## 验证

- `cargo test -p pbh-web` 通过 7 项，覆盖公开 manifest、初始设置与错误令牌响应、会话和 Bearer/query 优先级、退出、失败阈值、静态文件、SPA 深链接、API 路由未找到时的 JSON 响应、公开集成路由和并行初始化。OOBE 测试使用回环 qBittorrent/Transmission 服务和临时 YAML，验证只读测试请求、远端数据库拒绝、仅一次原子写入及令牌即时启用。
- `cargo fmt -p pbh-web --check`、`cargo clippy -p pbh-web --all-targets -- -D warnings` 均通过。
- 在 `25be37332` 的 Gradle JAR、JDK 25、临时 SQLite 和回环监听下，实测 Java manifest `200`、错误登录 `401`、正确登录 `200` 且设置会话 Cookie、退出 `200`、未知 API `405`。错误和成功文案随 `Accept-Language: en-US` 返回约定消息。Java 退出清除会话属性，Rust 会同时移除 Cookie。
- `cargo build -p peerbanhelper` 后通过原生 `serve` 入口实测临时 YAML 的首次配置。依次观察到初次登录 `303`、模拟 qBittorrent 验证 `200`、初始化 `201`、重复初始化 `403`、新令牌登录 `200` 且带 `JSESSIONID`。已确认下载器配置落盘并关闭测试进程。

## Vue WebUI 退役项

- 删除首次向导和运行设置中的 H2 选项以及相应类型。SQLite、MySQL/MariaDB 和 PostgreSQL 继续使用原有数据库模型及 `/api/oobe/testDatabaseConfig`。现有 H2 数据库文件的处理仍由迁移入口负责。
- 删除 `/script` 导航、页面、编辑和规则开关。WebUI 已无 `/api/expression-engine/scripts`、`/api/expression-engine/editable` 或 `/api/expression-engine/{id}` 客户端调用。移除专用 Aviator 解析器、语法测试和 ANTLR 前端依赖，Monaco 继续供 Webhook JSON 表单使用。
- 删除 BTN 脚本执行设置，保留 BTN 启停、数据提交确认、URL、App ID、App Secret 和 IP 查询。WebUI 的 `/api/modules/btn`、`/api/peer/btnQuery/{ip}` 及 IP 查询服务继续保留，仍需要原生后端的对应实现。
- Node `24.21.0`、pnpm `12.9.1` 上通过冻结锁文件离线安装、16 个修改源文件的 `oxfmt` 和 `oxlint`、`pnpm run build` 中的 Vue 类型检查与 Vite 构建。构建仍报告大体积供应商代码分块。成品 JavaScript 没有已退役的脚本表示。
- 使用 Rust `serve --webui-dir webui/dist` 实测 `/settings` 深链接和主 JavaScript 文件均返回 `200`，旧脚本 API 返回 `405` JSON。该检查覆盖 HTTP 静态交付，浏览器表单操作和未实现 API 的联调仍待做。
