# 动态注册与扩展入口

开发规则、监控插件，从[完整 demo](../examples/rule-plugin/)开始。要把别的行为接进宿主，先确定**谁持有你交给它的对象，谁负责移除它**。一个插件子容器可以注入宿主的管理器。关闭子容器会销毁其 Bean，但宿主管理器中保存的回调、实例和连接各有自己的生命周期。

## 模块、事件与定时任务

| 需要接入的行为 | 注册时发生什么 | 停用与验证 |
| --- | --- | --- |
| PF4J 扩展与 Spring Bean | 插件通过 `@Extension` 生成索引。宿主启动插件时创建子上下文，把其中的 `FeatureModule` 实例交给模块管理器。插件只提供普通辅助 Bean 时，需要显式把它纳入子上下文。 | 交给宿主发现的模块只注册一次。用插件管理界面或接口停止、启动同一个插件，检查扩展只收到一次调用。[插件管理实现](../src/main/java/com/ghostchu/peerbanhelper/configuration/pf4j/PBHSpringPluginManager.java) |
| 功能模块、规则、批量监控 | 宿主用 `ModuleManager.register(Class)` 从根容器取 Bean，也可用 `register(module, beanName)` 登记已有实例。后者当前按实例判断是否已登记。配置允许时发布 `ModuleRegisterEvent`，监听者可取消本次启用，此时实例仍在清单里。 | `unregister(module)` 按实例移除、发布事件并调用禁用逻辑。规则和监控的在途调用由插件作用域协调，普通外部回调另行管理。[ModuleManagerImpl](../src/main/java/com/ghostchu/peerbanhelper/module/ModuleManagerImpl.java) |
| 程序事件 | 给监听方法标注 Guava 的 `@Subscribe`，用 `Main.getEventBus().register(listener)` 登记对象。按事件参数类型订阅，例如 `PeerBanEvent`、`PeerUnbanEvent`、`LivePeersUpdatedEvent`、BTN 规则更新、服务启动、日志或桌面刷新。注册后才能收到随后发布的事件。 | 用同一对象调用 `unregister(listener)`。模块基类禁用时尝试注销 `this`，单独创建的监听对象由创建者撤销。监听方法运行时须考虑发布线程，注销前已经进入的回调应能自行结束。[事件目录](../src/main/java/com/ghostchu/peerbanhelper/event/) |
| 配置重载 | 实现 `Reloadable`，启用时调用 `Main.getReloadManager().register(this)`。在 `reloadModule()` 中读取当前配置、验证参数并更新运行状态。 | 禁用时调用同一管理器的 `unregister(this)`。可在启用、停用、重新启用后各执行一次重载，检查第二次不调用，第三次只调用一次。[AutoRangeBan](../src/main/java/com/ghostchu/peerbanhelper/module/impl/rule/AutoRangeBan.java) |
| 周期工作 | 在模块内调用 `registerScheduledTask(task, initialDelay, period, unit)`，宿主以 fixed delay 调度，前一次结束后才开始计算下一次间隔。保存返回的 `ScheduledFuture` 可以提前终止。 | 调用 `cancelScheduledTask(future)` 或由基类禁用时统一取消。取消使用 `cancel(false)`，已开始的工作需要能返回。直接提交给其他线程池的工作由提交者管理。[AbstractFeatureModule](../src/main/java/com/ghostchu/peerbanhelper/module/AbstractFeatureModule.java) |
| PF4J 状态监听 | 插件管理器本身通过 `addPluginStateListener` 接入扩展查找器及日志监听器。适用于观察插件状态变化。模块的启用仍应看模块事件和配置。 | 临时观察者保存监听器，结束观察时使用 PF4J 的 `removePluginStateListener`。[PBHPluginManager](../src/main/java/com/ghostchu/peerbanhelper/configuration/pf4j/PBHPluginManager.java) |

## 下载器、网络与推送

**下载器实例。** 准备一个实现 `Downloader` 的实例，交给 `DownloaderManager.registerDownloader`。ID 或显示名称与现有项重复都会返回 `false`，应先处理返回值，再宣布可用。扫描方通过管理器获取快照，因此撤销登记后，已经取得快照的一轮扫描可能仍在用旧实例。`unregisterDownloader` 只从列表移除，连接等自有资源也要关闭。需要跨重启时，再考虑 `saveDownloaders()`，它会写出整个 `client` 配置。自定义类型要让 YAML 和 JSON 两条 `createDownloader` 路径都能构造，才能覆盖配置启动和管理端创建。[DownloaderManagerImpl](../src/main/java/com/ghostchu/peerbanhelper/downloader/DownloaderManagerImpl.java)

**NAT 地址翻译。** 将 `NatAddressProvider` 实例加入 `NatAddressProviderRegistry`，下一次 `translate` 会按列表顺序查询，第一个非空地址即为结果。提供器应对自己无法翻译的输入返回 `null`，让后续提供器继续处理。注册者保留同一实例并在停用时 `remove(provider)`。测试时放入两个能处理同一地址的提供器，再移除第一个，确认第二个开始生效。[NatAddressProviderRegistry](../src/main/java/com/ghostchu/peerbanhelper/util/traversal/NatAddressProviderRegistry.java)

**AutoSTUN 和端口映射。** 对指定下载器调用 `BTStunManager.register(downloader)`。已有实例、登录失败或缺少 `LIVE_UPDATE_BT_PROTOCOL_PORT` 能力都会返回 `false`。成功时管理器同时登记 STUN 实例和 NAT 地址提供器，撤销用 `unregister(downloader)`，它会移除两处记录并关闭实例。配置仍要求 AutoSTUN 时，健康检查可再次登记该下载器。单独申请网关映射走 `PBHPortMapper.mapPort`，保存端口和协议，释放时调用 `unmapPort`。两次操作都要查看异步布尔结果。[BTStunManager](../src/main/java/com/ghostchu/peerbanhelper/util/traversal/btstun/BTStunManager.java) · [端口映射实现](../src/main/java/com/ghostchu/peerbanhelper/util/portmapper/PBHPortMapperImpl.java)

**推送渠道。** `PushManager.addPushProvider(provider)` 将实例加入发送列表，后续 `pushMessage` 会遍历列表。列表允许重复添加，所以启用两次可能发出两份通知。关闭时用 `removePushProvider(provider)`。若渠道来自用户配置，`savePushProviders()` 会重写整个 `push-notification` 段，配置重载会从已有配置段重新构建列表。仅在内存注册的实例可能在重载后消失；新渠道类型要能由 YAML 和 JSON 工厂恢复，再接入设置表单。验证时分别测试一次发送、一次重载和一次进程重启。[PushManagerImpl](../src/main/java/com/ghostchu/peerbanhelper/util/push/PushManagerImpl.java)

## HTTP、实时连接与后台任务

以“页面显示任务进度”为例，一条请求链有三处登记，三处的撤销时机不同。

1. **路由。** 控制器在 `onEnable()` 中通过 `JavalinWebContainer.routes()` 登记 HTTP 或 SSE handler，并传入访问角色。例如后台任务流使用 `/api/tasks/live` 和 `Role.USER_READ`。容器直接返回 Javalin 的路由配置，PBH 没有提供按插件撤销路由的句柄。把插件实例方法直接交给长寿命路由会留下引用，重复启用也可能与已登记的路径冲突。需要热替换时，可将路由放在宿主侧，由 handler 每次解析当前可用的服务。[JavalinWebContainer](../src/main/java/com/ghostchu/peerbanhelper/web/JavalinWebContainer.java)
2. **连接。** SSE handler 先给新客户端发当前任务，再用 `registerSseManagement(client)` 加入广播列表并保持连接。基类在连接关闭时移除该客户端。前端切换 endpoint 或离开页面时应关闭旧流。验证断开后重连能收到初始快照，旧连接不再收取消息。[SSE 基类](../src/main/java/com/ghostchu/peerbanhelper/module/AbstractSSEFeatureModule.java) · [前端连接封装](../webui/src/service/eventstream.ts)
3. **生产事件的监听器。** `BackgroundTaskManager.addStatusListener(listener)` 订阅状态变化。控制器把监听器存在字段里，停用时用这个字段调用 `removeStatusListener`。这样解绑只影响这个订阅，其他客户端与后台工作可以继续。[后台任务控制器](../src/main/java/com/ghostchu/peerbanhelper/module/impl/webapi/PBHBackgroundTaskController.kt)

要让自己的工作出现在任务列表里，用 `addTask(task)` 提交，或使用 `addTaskAsync(task)` 取得完成结果。两者都会执行任务。管理器广播排队、运行和结束状态，随后按状态与保留时间清理记录。异步方法返回的 future 与执行协程分开创建；取消 future 不能作为工作已经停止的依据。长时间运行的插件任务应自行设计停止条件，在卸载测试里确认任务不再访问已关闭的资源。[BackgroundTaskManager](../src/main/java/com/ghostchu/peerbanhelper/util/backgroundtask/BackgroundTaskManager.kt)

## 文案、桌面与诊断

| 用途 | 加入和撤销 | 实际作用域 |
| --- | --- | --- |
| 添加翻译键 | `TextManager.register(locale, path, text)` 规范化 locale，按需加载语言，更新路径并部署到当前语言分发。相同键会覆盖原值，建议使用带插件 ID 的键。 | 更新作用于内存中的语言数据。语言管理器支持整份部署、销毁和重置，单键注册没有配套撤销句柄。验证切换语言及语言重载后的显示。[TextManager](../src/main/java/com/ghostchu/peerbanhelper/text/TextManager.java) · [语言分发实现](../src/main/java/com/ghostchu/peerbanhelper/text/LanguageFilesManagerImpl.java) |
| 翻译后处理 | 在 `TextManager.postProcessors` 中加入 `PostProcessor`，翻译按集合顺序执行处理器。停用时从集合中移除该对象。 | 这是共享的 `LinkedHashSet`。语言重载会清空集合，修改时还要协调正在遍历它的翻译调用。[TextManager](../src/main/java/com/ghostchu/peerbanhelper/text/TextManager.java) |
| 桌面标题标志 | 将 `DisplayFlag(id, priority, content)` 加入 `ExchangeMap.GUI_DISPLAY_FLAGS`。先按优先级、再按 ID 排序，相同排序键不会替换旧文案。更新时移除旧项再添加。 | 整个进程共享这组标志，插件停用时按自己的 ID 移除。观察窗口标题的刷新结果，并覆盖无图形界面的运行场景。[ExchangeMap](../src/main/java/com/ghostchu/peerbanhelper/exchange/ExchangeMap.java) · [标题消费者](../src/main/java/com/ghostchu/peerbanhelper/gui/impl/swing/mainwindow/component/WindowTitle.java) |
| 缓存统计 | `CacheHitMeter.register(name, cache)` 把 Guava 缓存登记到诊断报告。同名登记替换旧引用。需要命中率时，创建缓存时要开启统计。 | 管理器保存弱引用，失去强引用后可被回收，报告生成与周期清理会删除失效项。没有显式的按名注销方法。[CacheHitMeter](../src/main/java/com/ghostchu/peerbanhelper/util/meter/CacheHitMeter.java) |

## 由配置、协议或启动过程装配的实现

这些入口也会改变运行时行为。维护者需要知道哪一层负责重建它们，才能判断修改要经重载、重新连接还是重新构建才能生效。

- **表达式与订阅规则。** 表达式模块的写入、删除流程会重新加载脚本并编译。订阅控制器按规则 ID 管理来源，触发更新后由订阅规则模块维护匹配内容。新增一份脚本或一项订阅，使用对应的管理接口即可。扩展脚本语言则要修改 `ScriptEngineManager` 的私有扩展名映射，当前初始化时登记 `.av`。宿主暴露给 Aviator 的实例函数和静态函数由 `Main` 向求值器登记。[ExpressionRule](../src/main/java/com/ghostchu/peerbanhelper/module/impl/rule/ExpressionRule.java) · [订阅控制器](../src/main/java/com/ghostchu/peerbanhelper/module/impl/webapi/RuleSubController.java) · [引擎选择](../src/main/java/com/ghostchu/peerbanhelper/util/scriptengine/ScriptEngineManager.kt) · [函数注册](../src/main/java/com/ghostchu/peerbanhelper/Main.java)
- **BTN 能力。** 服务器配置决定本轮有哪些能力，`BtnNetwork` 卸载旧能力、清空映射，根据协商字段创建实现再加载。新增能力需要同时维护协议解析、构造和卸载逻辑。手动往某次协商产生的集合塞入对象，会在下一次配置同步时失效。[BtnNetwork](../src/main/java/com/ghostchu/peerbanhelper/btn/BtnNetwork.java)
- **实验与许可证校验。** `Laboratory.isExperimentActivated` 可为传入的实验建立默认配置，但管理端按 ID 修改实验时只接受 `Experiments` 枚举中的项目。许可证撤销验证器由 Spring 在构造 `RevolvableLicenseBackend` 时注入列表，已经构造的根容器服务不会重新查询后来启动的插件子容器。[Laboratory](../src/main/java/com/ghostchu/peerbanhelper/util/lab/Laboratory.java) · [许可证后端](../src/main/java/com/ghostchu/peerbanhelper/pbhplus/backend/RevolvableLicenseBackend.java)
- **序列化与局部回调。** 公用 Gson 的类型适配器注册在 `JsonUtil` 的 builder 上，随后创建共享实例。单次 HTTP 传输的进度回调则可经 `HTTPUtil.addProgressTracker(builder, listener)` 加入指定客户端的构建器。要观察单次传输，控制该客户端和请求的生命周期即可。[JsonUtil](../src/main/java/com/ghostchu/peerbanhelper/util/json/JsonUtil.java) · [HTTPUtil](../src/main/java/com/ghostchu/peerbanhelper/util/HTTPUtil.java)

## WebUI 中的注册

| 入口 | 谁登记、何时生效 | 修改后如何检查 |
| --- | --- | --- |
| Vue、Pinia 与路由 | `main.ts` 用 `app.use` 安装 Pinia、i18n 与 router，满足监控配置条件时还会安装 Sentry 的 Pinia 插件。页面路由在 router 文件定义，`afterEach` 登记导航结束回调。当前 WebUI 通过前端构建发布这些入口。 | 冷启动、切换服务器、直接刷新深层链接各走一次。JVM 插件启动不会在已经打开的浏览器里安装 Vue 插件。[main.ts](../webui/src/main.ts) · [路由](../webui/src/router/index.ts) |
| 自动刷新 | 请求接入 `useAutoUpdatePlugin` 或 `useFirstPageOnlyAutoUpdatePlugin` 后，刷新通道为该请求保存回调；组件卸载时发送 `unmount` 移除登记。store 还监听页面可见性和网络恢复事件。 | 打开列表、切页、离开并重新进入，检查活动页刷新且旧回调不再发请求。[autoUpdate.ts](../webui/src/stores/autoUpdate.ts) |
| 编辑器语言与补全 | 脚本编辑器在挂载时向 Monaco 登记语言、Monarch 分词器和补全提供器。这些登记影响共享 Monaco 语言服务。 | 连续打开编辑器两次检查补全项是否重复。扩展时可在共享服务初始化时登记一次，或保存 API 返回的 disposable 并按所有权释放。[脚本编辑器](../webui/src/views/custom-script/components/editor/index.vue) |
| 图表组件和渲染器 | 图表通过 ECharts 的 `use([...])` 安装本图使用的图形、组件和 SVG 渲染器。添加一种新图形时同步引入并登记对应实现。 | 直接打开相应图表页，确认独立加载也能渲染；再切换页面确认图例、提示和坐标轴正常。[流量图](../webui/src/views/charts/components/traffic.vue) |

## 一次完整的停用与重启验收

以同时提供规则、状态监听和 NAT 地址翻译的插件为例，在测试实例按以下顺序检查。它能发现只看“插件已停止”状态时容易漏掉的引用。

1. 启用插件。让 demo 规则处理一批输入，触发一次后台任务状态变化，再查询一个可翻译的地址。分别记录规则次数、单个状态变化对应的回调次数和翻译结果。
2. 停用插件。模块由插件管理器撤销；插件自己从任务管理器移除监听器，并从 NAT 注册表移除提供器。再触发同一组输入，确认规则和监听器都没有新调用，地址查询继续交给其他提供器。
3. 在同一进程再次启用并重试。每个输入应只命中新实例一次。若有重复，用注册时保存的对象核对撤销时传入的引用，尤其检查临时 lambda 和方法引用。
4. 在登记到一半时模拟启用失败。例如规则已启用，下载器却因名称冲突返回 `false`。只撤销本次已经成功的登记，原有同名下载器仍应可用。最后重载配置并重启进程，分别确认临时实例和要求持久化的配置符合预期。
