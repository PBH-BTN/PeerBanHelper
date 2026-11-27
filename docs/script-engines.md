# 自定义 Peer 规则

在 WebUI 的「自定义脚本」中新建规则，或把文件放入数据目录的 `scripts/`，然后重载配置。文件扩展名决定语言，未知扩展名不执行。仅旧 BTN 协议中没有扩展名的规则名按 AviatorScript 处理。

| 文件 | 示例：封禁上报指定客户端名的 Peer | 执行语义 |
| --- | --- | --- |
| `.av` | `return peer.clientName == 'Example';` | AviatorScript；保留发行脚本的元数据头 |
| `.js` | `peer.clientName?.toLowerCase() === 'example'` | GraalJS，取最后一个表达式的值；没有 Node.js 或浏览器 API |
| `.lua` | `return peer.clientName == 'Example'` | LuaJ，使用 Lua 5.2 系语法，返回第一个值 |

返回 `false` 或 `0` 放行，`true` 或 `1` 封禁，`2` 跳过其他规则。非空字符串是封禁原因；以 `@` 开头的字符串则是跳过原因。异常或超限不会产生封禁。JavaScript 与 Lua 的数字按双精度传入，超过 2⁵³ − 1 的计数不保证精确。

## 脚本能看到什么

- `peer`：`peerId`、`clientName`、`peerAddress.ip`、`peerAddress.port`、`peerAddress.address`（IP 字符串）、`downloaded`、`uploaded`、`downloadSpeed`、`uploadSpeed`、`progress`、`flags`、`handshaking`。
- `torrent`：`id`、`name`、`hash`、`size`、`completedSize`、`progress`、`rtUploadSpeed`、`rtDownloadSpeed`、`private`、`seeding`。
- `downloader`：仅 `id`、`name`、`type`；`banDuration` 是当前封禁时间（毫秒）。快照不含下载器凭据、服务器实例或共享存储。修改快照不改变 PBH 状态。

## 沙箱与升级

`profile.yml` 的 `module.expression-engine.sandbox` 默认是 `true`，包括旧安装升级。编译和执行都在受限 worker 中完成；无法建立隔离就不加载规则，不会回退到宿主 JVM。不同规则和本地／BTN 两种来源不共享 worker。超时、协议错误或超限的 worker 会被终止。

| 部署 | 启用沙箱前检查 |
| --- | --- |
| Windows 发行包 | 使用 Docker Desktop 的本地 Linux 引擎，保留 `PeerBanHelper.jar` 与同级 `libraries/`。预先运行下面的命令；PBH 不会自动拉取镜像。 |
| Linux | 需要受支持的 x86-64 glibc 布局、非 root 用户、可用的非特权 user namespace、`bwrap`（支持 `--disable-userns`）及 `prlimit`。仅安装命令不代表内核允许隔离。 |
| 其他部署 | 原生 macOS、FreeBSD、Linux ARM64 和官方 root/musl 容器（含使用此镜像的群晖包）目前没有可用的隔离启动器。不要为了运行规则而给 PBH 容器添加特权或挂载 Docker socket。 |

```sh
docker pull eclipse-temurin@sha256:b8705e936964c228dfce359e63dbf08f6c69cb380ed1d576b1f6b1064f15567d
```

只有在**全部本地脚本都由管理员审查**时才应关闭本地沙箱。关闭后脚本在 PBH 的 JVM 中运行，不再有进程级 CPU、内存和执行时限保护；可信 Aviator 脚本恢复历史 Java 对象、反射与工具函数。JavaScript 和 Lua 仍只接收快照；关闭隔离不是开启 Node.js 或 Lua 系统库。**BTN 远程脚本始终隔离**，不会继承本地开关。

切换后重载配置，检查「自定义脚本」列表及日志。未加载的源文件仍可查看或删除；沙箱不可用时保存会返回 HTTP 503，不会覆盖原文件。依赖 `server`、`moduleInstance`、`ramStorage` 或 Java 方法的旧规则需要改成读取快照，或在确认可信后由管理员显式关闭本地沙箱。

每个 worker 一次只处理一个规则的一次调用。默认最多保留 6 个进程；可用 JVM 参数 `-Dpbh.script.sandbox.max-workers=N` 调整（1–16）。空闲 2 分钟后回收；正在执行的规则有 5 秒预算，首次启动另有 60 秒预算。适合把判定写成无状态函数，不要依赖上一次执行留下的数据。
