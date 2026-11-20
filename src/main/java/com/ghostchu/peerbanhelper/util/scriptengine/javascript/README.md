# GraalJS 规则（.js）

worker 用无参构造器 `new com.ghostchu.peerbanhelper.util.scriptengine.javascript.GraalJsScriptEngine()` 创建引擎。一个 worker 可以复用一个实例。父端负责 AMSI、模式选择和快照生成。受限模式的进程隔离、超时和资源回收由 worker 启动方负责。可信本地模式使用相同的语言权限。

规则是普通 ECMAScript 脚本，最后求值的表达式是规则结果。例如，下式对客户端名称以指定前缀开头的 Peer 返回 `true`。

```js
(peer.clientName ?? '').startsWith('Example/')
```

文件顶层的 `return` 是语法错误。需要多条 `return` 分支时，可以在脚本中定义函数，并把函数调用放在最后一个表达式。结果支持布尔、字符串、有限数值，以及映射为 Java `null` 的 `null` 和 `undefined`。建议使用规则管理器支持的布尔值、动作编号或原因字符串。对象、数组和函数不能作为规则结果。

快照的顶层键直接作为全局变量暴露，例如 `peer.peerAddress.ip`、`torrent.hash`、`downloader.name` 和 `banDuration`。嵌套 Map 转为无原型对象，List 转为从 0 开始的数组。输入支持字符串键、布尔、字符串、数字和空值；数值使用 JavaScript Number 的双精度语义。每次执行创建独立上下文，脚本可修改本次副本。宿主对象、Java 类查找、文件、网络和 Node.js 模块均不在规则 API 内。

安全整数范围为 `-(2^53 - 1)` 到 `2^53 - 1`。较大的相邻整数可能变成同一个 Number，例如字节计数抵达 `2^53` 后，增加 1 可能无法观察到。需要逐位比较的大整数应由快照提供方传入十进制字符串。

Spring 创建 bean 或查询扩展名时无需启动 Graal 运行时，首次编译或校验 JS 源码时才初始化。保存和语法检查只解析源码。worker 应保留 `compileScript` 返回的对象并为每个快照调用 `execute`，它持有共享 Graal 引擎的缓存源码。引擎默认关闭规则**结果**缓存，规则名取调用方传入的名称或文件名。
