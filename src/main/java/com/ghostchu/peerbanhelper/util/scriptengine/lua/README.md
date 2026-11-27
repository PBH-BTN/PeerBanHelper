# LuaJ 规则（.lua）

worker 用无参构造器 `new com.ghostchu.peerbanhelper.util.scriptengine.lua.LuaJScriptEngine()` 创建引擎。受限与可信本地模式使用相同的快照和 Lua 权限。父端负责 AMSI 和快照生成，受限 worker 的进程隔离、超时和资源回收由启动方负责。

规则使用 LuaJ 3.0.1 的 Lua 5.2 源码语法，取第一项返回值。下例根据客户端名称返回布尔值。

```lua
return string.sub(peer.clientName or "", 1, 8) == "Example/"
```

顶层快照键是全局变量，例如 `peer.peerAddress.ip`、`torrent.hash`、`downloader.name` 和 `banDuration`。Map 复制为字符串键的 table，List 复制为从 1 开始的 table，空值转为 `nil`。列表中的空值会形成空洞，`ipairs` 会在第一个空洞处停止。数字使用双精度语义，绝对值超过 `2^53 - 1` 时，相邻整数可能无法区分。返回值支持布尔、有限数值、字符串和 `nil`；建议显式返回规则管理器支持的布尔值、动作编号或原因字符串。

可用库为 `math`（省略随机数及种子）、`string`（省略 `dump`）和 `table`。基础函数包括 `assert`、`error`、`ipairs`、`next`、`pairs`、`rawequal`、`rawget`、`rawlen`、`rawset`、`select`、`tonumber`、`tostring` 和 `type`。不提供文件、网络、包加载、动态加载字节码、调试、协程或 Java 桥接。

语法检查只编译源码。worker 应保留 `compileScript` 返回的对象并为每个快照调用 `execute`。每次执行从同一个 `Prototype` 创建闭包，使用独立的全局表、标准库表和快照副本。引擎默认关闭规则**结果**缓存，规则名取调用方传入的名称或文件名。
