# 实验室设置与 HTTP API

负责人 parity，应用接线和平台功能由 root 负责。参考 Java `PBHLabController`、`Laboratory`、`Experiments`、请求 DTO 和 `webui/src/service/labs.ts`。

## 行为和配置

- 原生应用接入 `rust/app/src/lab_api.rs`，提供认证后的 `GET` 和 `POST /api/laboratory/config`、`GET /api/laboratory/experiments`、`GET /api/laboratory/isExperimentActivated` 和 `PUT /api/laboratory/experiment/{id}`。响应继续使用 `success`、`message`、`data`，实验列表按原条目顺序返回目标组、分组号、有效状态和本地化文案。演示模式下需写权限的三个路由拒绝已认证请求，两个只读路由可用。
- 对 `installation-id` 按 Java UTF-16 `String.hashCode()` 计算有符号 32 位哈希，组号为余数 `abs(hash % 5)`。缺失安装 ID 取 `???`。`sqlite_vacuum` 默认覆盖全部五组，`win32_empty_working_set` 覆盖第 1 和第 3 组。总开关优先，实验支持布尔显式覆盖和字符串 `default`。
- 请求 JSON 的读取独立于 `Content-Type`，Java 对异常 JSON 和错误字段类型返回本地化 500。`PUT` 回显字符串、布尔文本和数字的原始文本。`null` 或非布尔文本将实验切回分组默认值。
- 沿用 `config/laboratory.yml` 和版本 1。版本 0 迁移时启用母开关，保留已有实验和其他字段。首启创建缺失文件，更新通过共享配置文件锁和同目录临时文件原子替换。改写保留 YAML 值，重新序列化时原注释不保留。无效 YAML 返回错误并保留原文件，写入错误返回本地化 500。读接口读取当前磁盘配置，Java 参考进程内使用启动时加载的配置对象。
- `SQLiteDatabaseDriver` 的自动 VACUUM 调用在参考提交已被注释。Java Win32 定时工作集路径的初始时间戳与条件导致常规运行中始终提前返回。原生实现保留实验的开关和显示状态，对照现行生效路径。

## 验证

- `cargo +1.96.0 test -p peerbanhelper lab_api::tests --locked` 通过 4 项，覆盖 UTF-16 分组、版本迁移、空文件初始化、磁盘保留、认证、列表语言、路由、合法更新、数字原文、无内容类型的 JSON 和未知实验。
- Java 25 参考进程与原生 `peerbanhelper run` 使用隔离 SQLite、临时配置、回环下载器及拒绝公网转发的回环代理。23/23 项的完整 HTTP 状态码与 JSON 一致，重启后的 YAML 键和值一致。覆盖五个接口、匿名拒绝、非 ASCII 安装 ID 分组、中英文案、合法切换、带指数的数字文本、无内容类型的 JSON、错误字段和重启状态。
