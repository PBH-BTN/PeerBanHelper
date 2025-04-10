# Packaging

此文档提供了关于打包 PeerBanHelper 的有用信息。

## 运行环境

PeerBanHelper 需要 64 位 Java 运行环境，在 Java 21 上编译，并设计在 Java 23 上运行。  
由于 Java 21 的一些已知错误 (JDK HttpClient selector manager closed)，通常不建议使用 Java 21 直接运行。但由于 Java 23
也多多少少有些问题，我们可能会倾向于在 Java 24 发布后升级到 Java 24。

PeerBanHelper 本身是跨平台的，但其跨平台的能力主要收到 SQLite JDBC 驱动程序的限制。我们正在使用 Xerial 的 SQLite JDBC
驱动程序（自维护的额外 loongarch64 分支），支持以下平台：

* Windows x86_64
* Windows x86
* Windows aarch64
* Windows armv7
* Mac x86_64
* Mac aarch64
* Linux x86_64
* Linux x86
* Linux aarch64
* Linux loongarch64
* Linux ppc64
* Linux riscv64
* Linux-Musl x86_64
* Linux-Musl x86
* Linux-Musl aarch64
* FreeBSD x86_64
* FreeBSD x86
* FreeBSD aarch64

你可以通过替换 lib 中的 sqlite 驱动程序 jar 文件来变更上述支持的平台。

其额外功能（如 JCEF），则可能并不完整支持上述所有架构，不过 PeerBanHelper 会处理这些异常并仅禁用受影响的模块。

## 开关参数

PeerBanHelper 支持从文件、环境变量、启动命令行（JVM虚拟机参数）加载开关属性（我们称之为 *ExternalSwitch*），PeerBanHelper
在内部大量使用此功能，总的概括如下：

* 从启动命令行加载
    * 使用 `-D` JVM 虚拟机参数可以更改开关参数的值，如：`-Dpbh.usePlatformConfigLocation=true`
* 从环境变量加载
    * 需要将 ` `, `-`, `.` 替换为 `_`，并且全部大写，如：`PBH_USEPLATFORMCONFIGLOCATION=true`
* 从文件加载
    * 在数据目录下创建一个 `flags.properties` 文件，文件内容格式为 `key=value`，如：`pbh.usePlatformConfigLocation=true`
    * 仅支持 ISO-8859-1 编码，如果包含 CJK 等编码集外字符，需要使用 Unicode 转义序列

### 更改打包模式

当你为 PeerBanHelper 创建程序包时，应首先修改打包属性字段，这会影响 PeerBanHelper 的部分行为。你可以通过 `pbh.release=deb`
开关参数来更改。我们约定打包属性名称应为全小写，如 `deb`, `rpm`, `aur`
等，能够准确反映打包的目标平台即可。尽管确有例外如：`install4j`, `docker`。

### 更改数据目录

PeerBanHelper 默认情况下将数据存储在运行目录下的 `data` 目录中。如果设置了 `pbh.usePlatformConfigLocation=true`
开关参数，则会存储到 `%LOCALAPPDATA%` (Windows 平台)，或者 `user.home` (Linux 平台)
，以及 `/Library/Application Support/.config/PeerBanHelper` (macOS 平台)。  
你可以通过 `-Dpbh.datadir=/path/to/data` JVM 虚拟机参数来更改数据目录到任意位置。

此功能对 enable-jcef 等标志文件无效。

### 单独更改配置文件目录

配置文件目录默认存储在 `./data/config`
目录下，跟随数据目录位置。但如果你需要将配置文件从数据目录内更改到其它位置，则可以试用 `pbh.configdir=/path/to/config`
开关参数。

### 单独更改日志文件目录

为了遵循 Linux/UNIX 文件系统规范，您可能希望将日志从 `./data/logs` 中移动到 `/var/log`
之类的地方，你可以通过设置 `pbh.logsdir=/path/to/logs` 开关参数来这样做。

### 设置默认用户语言

在默认情况下，PeerBanHelper 使用操作系统语言，但有时可能因为处于虚拟化环境（如
Docker），探测可能并不准确，您可以使用开关参数 `pbh.userLocale=zh-CN` 来覆盖默认识别的用户语言。

### 更改日志等级

默认的日志等级是 `INFO`，但如果你需要调整日志等级（例如调试用途），可以使用 `pbh.logLevel=DEBUG` 开关参数调整等级。

### 禁用 GUI

PeerBanHelper 默认情况下启用 GUI（如果探测到桌面环境），但如果你需要禁用 GUI，可以使用 `pbh.nogui=true`
开关参数，或者在启动命令行末尾传递 `nogui` 参数。

### 强制 Swing UI

PeerBanHelper 默认情况下启用 Swing UI（如果探测到桌面环境）。
开关参数，或者在启动命令行末尾传递 `swing` 参数。

### 强制 SWT UI

PeerBanHelper 默认情况下启用 SWT UI（如果探测到桌面环境）。但如果您希望强制使用 SWT UI，可以：
开关参数，或者在启动命令行末尾传递 `swt` 参数。

### 更改 API Token

注意：默认指定 API Token 将导致用户跳过 OOBE 阶段，这可能会导致不可预测的问题。该操作不受支持

通过 `pbh.apiToken=foobar` 可以修改 API Token，且优先级高于配置文件。

### 更改 WebServer 网卡监听地址

默认情况下，PeerBanHelper 监听 `0.0.0.0`，但你可以通过开关参数 `pbh.serverAddress=0.0.0.0` 来修改监听地址。  
这会影响所有使用 WebServer 的模块，包括 WebUI 等。

### 更改 WebServer 端口号

默认情况下，PeerBanHelper 使用 `9898` 端口，但你可以通过开关参数 `pbh.port=8080` 来修改端口号。

### 禁用 SQLite Pragma 设置

在启动 SQLite 数据库之后，PeerBanHelper 会立刻执行一段 PRAGMA SQL 查询，改变 SQLite
的默认行为以优化性能，但如果你需要禁用此功能，可以使用 `pbh.database.disableSQLitePragmaSettings=true` 开关参数。

### 禁用 SQLite Vacuum

PeerBanHelper 默认启用 SQLite 的 VACUUM
功能，以优化数据库性能，但如果你需要禁用此功能，可以使用 `pbh.database.disableSQLiteVacuum=true` 开关参数。

### 禁用 JCEF

当桌面环境支持且 JCEF 未被禁用时，会加载 JCEF 框架以显示 WebUI 控制台选项卡。如果你需要禁用
JCEF，可以使用 `pbh.nojcef=true` 开关参数。  
一旦禁用，将关闭所有 JCEF 有关的功能。

### 启用 JCEF DevTools

默认情况下，JCEF DevTools 是禁用的，但如果你需要启用 JCEF DevTools，可以使用 `jcef.dev-tools=true` 开关参数启用。  
除此以外，如果当前 PeerBanHelper 的 `pbh.release` 为 `LiveDebug` 或者构建是一个开发/测试/快照版本，JCEF DevTools 会自动启用。

### 启用 Debug 菜单

PeerBanHelper 图形界面主窗口有一个隐藏的 `--DEBUG--`
菜单，包含一些调试（或者未完成）的功能菜单，可以使用 `pbh.gui.debug-tools=true` 来强制启用。  
除此以外，如果当前 PeerBanHelper 的 `pbh.release` 为 `LiveDebug` 或者构建是一个开发/测试/快照版本，Debug 菜单会自动启用。

### 禁用 macOS 主题

在 macOS 系统上，PeerBanHelper 会自动切换到 macOS 的 FlatLaf 主题，你可以通过 `pbh.gui.macos-theme=false` 来禁用此功能。

### 禁用 PBH Plus 主题

如果你捐赠了 PeerBanHelper 且激活了 PBH Plus 捐赠 Badge，则会启用另一套 PBH Plus
主题，你可以通过 `pbh.gui.pbhplus-theme=false` 来禁用捐赠者主题。

### 禁用测试先行者主题

如果当前 PeerBanHelper 的 `pbh.release` 为 `LiveDebug` 或者构建是一个开发/测试/快照版本，PeerBanHelper
会启用测试先行者主题，你可以通过 `pbh.gui.insider-theme=false` 来禁用测试先行者主题。

### 自订 LookAndFeel

PeerBanHelper 使用 FlatLaf 作为默认的 LookAndFeel，但你可以通过 `pbh.gui.theme-light=classname`
和 `pbh.gui.theme-dark=classname` 来自订 LookAndFeel，两者只有在都不是空的情况下生效。如果输入的值是无效值，则可能导致非预期的问题。

### 禁用 AviatorScript 环境安全检测

当用户通过 WebUI 编辑 AviatorScript 时，PeerBanHelper
会检查脚本或者环境是否安全，并阻止可能的不安全操作。但如果你需要禁用此功能，可以使用 `pbh.please-disable-safe-network-environment-check-i-know-this-is-very-dangerous-and-i-may-lose-my-data-and-hacker-may-attack-me-via-this-endpoint-and-steal-my-data-or-destroy-my-computer-i-am-fully-responsible-for-this-action-and-i-will-not-blame-the-developer-for-any-loss=true`
开关参数禁用此检查。

### [未使用] 禁用 PortMapper 失败重试自动刷新

当 PeerBanHelper 使用 PortMapper 创建端口映射时，如果失败，PeerBanHelper
会自动重试，但如果你需要禁用此功能，可以使用 `pbh.portMapper.disableRefreshFailRetry=true` 开关参数。

### 禁用 JCEF 沙盒保护

CEF 默认使用沙盒技术避免 Web 内容危害本地计算机安全，但这会使用额外资源。你可以通过 `jcef.no-sandbox` 开关参数禁用 JCEF
沙盒保护。

### JCEF 忽略 SSL 证书错误

默认情况下，JCEF 会检查 SSL 证书是否有效，但如果你需要忽略 SSL 证书错误，可以使用 `jcef.ignore-ssl-cert=true` 开关参数忽略证书错误。

### 禁用 JCEF GPU 加速

默认情况下，JCEF 会尝试使用 GPU 硬件加速，但在部分平台上可能出现问题。如果你需要禁用 JCEF GPU
加速，可以使用 `jcef.disable-gpu=true` 开关参数禁用。

### 禁止 JCEF 本地文件访问

默认情况下，PBH 会允许 JCEF 会访问本地文件系统，但如果你需要禁止 JCEF
访问本地文件系统，可以使用 `jcef.allow-universal-access-from-file=false` 开关参数禁止访问。

### 禁止 JCEF 媒体流访问

默认情况下，PBH 会允许 JCEF 访问媒体流（如 WebSocket, M3U8 等等），如果你需要禁止 JCEF
访问媒体流，可以使用 `jcef.allow-media-stream=false` 开关参数禁止访问。

### 启用 JCEF 拼写检查

默认情况下 PBH 会禁用 JCEF 自带的拼写检查功能，但如果你需要启用 JCEF
拼写检查，可以使用 `jcef.disable-spell-checking=false` 开关参数启用。

### 允许 WebServer CORS 策略

默认情况下，PeerBanHelper 的 WebServer 不允许跨域请求，但如果你需要允许跨域请求，可以使用 `pbh.allowCors=true`
开关参数启用。此功能也可通过配置文件启用。

## 标志文件

标志文件是一组位于程序运行目录下的文件，通过文件存在/文件不存在控制一些额外行为，以便与 install4j 等安装程序集成。

### 启用 JCEF

文件名称：enable-jcef.txt

### 禁用更新检查

文件名称：disable-update-check.txt  
注：这会禁用更新检查，但已计划的更新仍可能会在启动时安装。

### 更改检查并发等级

默认情况下最多允许 32 个并发检查，你可以通过 `pbh.checkParallelism=32` 开关参数来更改并发等级。  
注意：过高的并发等级可能耗尽堆内存导致程序卡死。