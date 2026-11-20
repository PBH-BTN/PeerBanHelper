# PeerBanHelper JVM 插件开发手册

需要修改宿主模块、下载器、WebUI 或发行包时，请参阅[全项目开发者指南](developer-guide.md)。

本手册适用于包含插件生命周期重构的 PBH 构建。插件在 PBH 的 JVM 内运行，可以直接使用目标发行版的 Java 类型。下面以一个可以独立编译的规则插件为例，从创建工程一直走到部署、升级和卸载。[完整示例工程](../examples/rule-plugin/)

## 1. 从可运行的工程开始

准备 JDK 25。在 PBH 源码根目录运行以下命令，Windows 将 `./gradlew` 换成 `.\gradlew.bat`。

```sh
./gradlew :rule-plugin-demo:jar
jar tf examples/rule-plugin/build/libs/rule-plugin-demo-1.0.0.jar
```

检查 JAR 中的三个条目。根目录的 `plugin.properties` 描述插件，`example/pbh/DemoClientRule.class` 是业务实现，`META-INF/extensions.idx` 是编译期生成的扩展索引。将 JAR 复制到测试实例的数据目录下的 `plugins/`，启动 PBH。日志出现 `Started plugin demo-client-rule@1.0.0` 后，首批 Peer 即可使用这条规则。

创建自己的工程时，复制整个 `examples/rule-plugin/` 目录，修改包名、规则名、插件 ID 和版本，并同步调整 Gradle 项目名。在独立工程目录运行：

```sh
gradle jar "-PpbhHome=/path/to/PeerBanHelper"
```

`pbhHome` 指向同时包含 `PeerBanHelper.jar` 和 `libraries/` 的发行目录。示例使用 `compileOnly` 编译宿主提供的类型，并单独通过 `annotationProcessor` 启用 PF4J 索引生成器。发布前用 `jar tf` 核对内容。把 PBH、Spring 或 PF4J 的类重复打进插件可能导致同名类型由不同类加载器定义。自己引入的库则应检查发行包中是否实际可用，普通 Gradle `jar` 任务只打包本项目的类和资源。[示例构建脚本](../examples/rule-plugin/build.gradle.kts)

## 2. 描述符与兼容版本

在 `src/main/resources/plugin.properties` 中填写元数据。

```properties
plugin.id=demo-client-rule
plugin.version=1.0.0
plugin.provider=PeerBanHelper
plugin.description=Matches a synthetic client name.
plugin.requires=>=9.6.0 & <9.7.0
```

| 字段 | 作者应如何填写 |
| --- | --- |
| `plugin.id` | 使用唯一、稳定的 ID。管理操作、启用状态和其他插件的依赖声明都用它识别插件。 |
| `plugin.version` | 使用语义化版本。发布时同步更新描述符和构建版本。 |
| `plugin.requires` | 声明支持的 **PBH 宿主版本**。示例区间只是演示，发布时应以实际验证过的版本为准。同一版本号下的开发构建还应说明所需分支或提交。 |
| `plugin.dependencies` | 用逗号分隔插件 ID。例如 `shared-rules@>=1.2.0,geo-tools?` 声明一个必需依赖和一个可选依赖。 |
| `plugin.class` | 需要 PF4J 插件级启动或停止回调时填写入口类的全限定名。仅提供规则或监控扩展时可以省略。 |
| `plugin.provider`、`plugin.description`、`plugin.license` | 填写管理端可展示的作者、用途和许可证。 |

宿主按必需依赖关系解析并启动插件。缺少依赖、版本冲突或循环依赖会阻止相关插件启动。只有部分扩展需要可选插件时，将相关类型引用集中在该扩展类中，并使用 `@Extension(plugins = "geo-tools")`。宿主在加载该类之前检查目标插件是否已启动。

## 3. 编写规则和批量监控

**规则插件**继承 `AbstractRuleFeatureModule`，在具体类上标记 `@Extension`。用 `getName()` 提供显示名，用 `getConfigName()` 提供唯一、稳定的模块配置键。实现 `shouldBanPeer(Torrent, Peer, Downloader, PipelineTask<?>)`，返回本次检查的 `CheckResult`。标识可能在握手期间缺失，读取客户端名、Peer ID 或 flags 前要处理空值。[完整规则源码](../examples/rule-plugin/src/main/java/example/pbh/DemoClientRule.java)

| 检查结果 | 适用场景 |
| --- | --- |
| `pass()` / `NO_ACTION` | 本规则没有命中，其他规则仍可提出封禁。 |
| `BAN_FOR_DISCONNECT` | 需要通过封禁触发断开连接。该动作具有独立的展示和上报语义。 |
| `BAN` | 正常封禁。示例传入 `60_000` 毫秒，并提供模块类、规则名、原因和结构化数据。 |
| `SKIP` | 对该 Peer 给出高优先级的跳过决定。普通未命中应返回 `pass()`。 |

扫描结果按动作优先级归并，`SKIP` 高于 `BAN`，其后依次是 `BAN_FOR_DISCONNECT` 和 `NO_ACTION`。给用户可见的封禁原因应说明实际命中条件。结构化数据适合保存用于解释命中的字段。默认情况下宿主对同一规则实例的检查加锁；只有确认共享状态可以并发访问时才覆盖 `isThreadSafe()` 并返回 `true`。

**批量监控插件**可以继承 `AbstractFeatureModule`，实现 `BatchMonitorFeatureModule` 并标记 `@Extension`。在 `onPeersRetrieved(Downloader, Torrent, List<? extends Peer>, PipelineTask<?>)` 中处理某个下载器上一个种子的 Peer 快照。它适合计数、观察或汇总，不返回封禁结果。下载器超时可能导致某批数据缺席，监控逻辑应允许采样中断，并自行保护跨批次共享状态。[监控接口](../src/main/java/com/ghostchu/peerbanhelper/module/BatchMonitorFeatureModule.java)

## 4. 依赖注入、配置与资源归属

宿主读取索引，在插件自己的 Spring 上下文中创建扩展。普通插件可以通过构造函数接收宿主 Bean、同插件已注册的 Bean 和 `org.pf4j.PluginWrapper`。例如，可从注入的 wrapper 调用 `getPluginId()` 取得描述符 ID。索引只列出声明的扩展；普通辅助类可由扩展直接创建，或通过显式 Spring 配置提供。宿主根容器不会自动扫描插件包。

插件 ID 和模块配置键各有用途。一个插件可以提供多条规则，每条可配置规则应使用自己的配置键，覆盖 `isConfigurable()` 返回 `true`，再通过 `getConfig()` 读取对应段。例如，配置键为 `acme-client-rule` 时，在现有 `profile.yml` 的 `module` 下合并：

```yaml
module:
  acme-client-rule:
    enabled: true
    client-name: "PeerBanHelper-Demo/1.0"
```

`enabled` 决定模块是否启用，`client-name` 是这个例子中由插件自行解释的键。缺少可配置模块的配置段时，基类按禁用处理。因此，部署后应分别核对插件状态和模块可用状态。业务参数若在启用时缓存，应明确何时重新读取，并在使用说明中写清重载或重启步骤。

| 时机 | 适合执行的工作 |
| --- | --- |
| 插件入口 `start()` | 初始化插件级资源。此时宿主仍在准备本插件的扩展上下文。 |
| 模块 `onEnable()` | 读取配置、初始化模块资源、建立订阅。可通过基类的 `registerScheduledTask` 注册定时工作。无初始化需求可沿用默认实现。 |
| 扫描回调 | 使用已准备好的状态处理快照。为外部 I/O 设置超时，避免长时间占用扫描和停用流程。 |
| 模块 `onDisable()` | 取消自行建立的订阅，关闭自建线程池、连接和文件。清理逻辑应能处理初始化只完成了一部分的情形。 |
| 插件入口 `stop()` | 释放插件级资源。随后宿主关闭为扩展创建的子上下文。 |

停用时，宿主先停止分派新的规则、监控和受管定时回调，再等待在途调用退出。等待超过五秒会报告超时，保留旧实例的资源；待回调结束后重试停止或卸载。重启后的插件获得新的激活范围，旧扫描队列中的任务无法通过旧注册调用新实例。自行向宿主注册的原始 HTTP 路由、外部监听器或线程需要作者提供对应的撤销机制。

需要自定义管理端展示时，可以继承 `PBHPlugin` 提供显示名、说明和菜单。宿主在启动时保存这些信息，在列表请求时按请求语言翻译。菜单中的相对端点需要插件另外实现。

## 5. 安装、管理与升级

将可信插件安装到目标实例的数据目录。容器部署应使用挂载的数据卷。JVM 插件以 PBH 进程权限执行。启动后通过带 Bearer 令牌的 `GET /api/plugins` 查看 ID、状态、版本要求、依赖和错误信息。写操作使用 `POST /api/plugins/operate`，请求体为 JSON；管理端应检查返回数组中每项的 `value` 和 `message`。

```json
{"action":"stop","identifiers":["demo-client-rule"]}
```

| 操作 | identifiers 的含义 | 结果 |
| --- | --- | --- |
| `load` | 插件目录直接子项的文件名，例如 `rule-plugin-demo-1.0.0.jar` | 加载磁盘上已安装的插件并尝试解析依赖。 |
| `start` / `stop` | 插件 ID | 激活或停止插件；停止必需依赖会连带停止依赖它的运行中插件。 |
| `enable` / `disable` | 插件 ID | 修改启用状态。禁用会尝试停止插件。启用后可显式执行 `start` 以立即运行。 |
| `unload` | 插件 ID | 停止并卸载插件，释放类加载器，保留磁盘文件。 |
| `delete` | 插件 ID | 执行删除流程并移除已安装插件文件。 |

**热升级顺序**为停止、卸载、替换 JAR、按文件名加载、按 ID 启动。若有下游插件，记录并核对它们的状态。确认列表中的版本和状态，再请求 `GET /api/general/checkModuleAvailable?module=demo-client-rule` 核对模块；最后用已知输入检查实际行为。回滚时按同一顺序装回保存的上一版 JAR。[可复制的 HTTP 命令](../examples/rule-plugin/README.md)

## 6. 验证与排障

### 实践：把固定规则改为可配置规则

复制 demo 后，保留 `getName()` 和四参数检查入口，把 `getConfigName()` 改为 `acme-client-rule`，使用第 4 节的 YAML。删除示例的 `DEMO_CLIENT` 常量，在规则类内加入以下字段与方法：

```java
private volatile String clientName;

@Override
public boolean isConfigurable() {
    return true;
}

@Override
public void onEnable() {
    String configured = getConfig().getString("client-name", "").trim();
    if (configured.isEmpty()) {
        throw new IllegalArgumentException("acme-client-rule requires client-name");
    }
    clientName = configured;
}
```

在 `shouldBanPeer` 开头用 `String target = clientName;` 取得本次检查使用的配置，将原方法中的所有 `DEMO_CLIENT` 引用替换为 `target`。先启动一个配置段缺失的实例，此时插件可以启动，但模块不可用。然后添加配置段并重启，用命中名称、不同名称、空名称各检查一次。最后将名称改成另一值并重启，确认旧名称放行、新名称命中。这个实现只在模块启用时读取参数；若要在模块持续启用时响应配置编辑，还需设计独立的重载入口。

### 实践：观察一批 Peer 而不产生封禁

在 demo 的 `src/main/java/example/pbh/` 下添加 `PeerBatchMonitor.java`。同一描述符可以携带多个扩展。下面的监控模块用于观察一次回调对应哪个下载器、哪个种子以及多少条 Peer 记录：

```java
package example.pbh;

import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BatchMonitorFeatureModule;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Extension
public final class PeerBatchMonitor extends AbstractFeatureModule implements BatchMonitorFeatureModule {
    private static final Logger LOG = LoggerFactory.getLogger(PeerBatchMonitor.class);

    @Override
    public String getName() { return "Demo peer batch monitor"; }

    @Override
    public String getConfigName() { return "demo-peer-batch-monitor"; }

    @Override
    public void onPeersRetrieved(Downloader downloader, Torrent torrent,
                                 List<? extends Peer> peers, PipelineTask<?> task) {
        LOG.debug("Downloader {} torrent {} observed {} peers",
                downloader.getId(), torrent.getId(), peers.size());
    }
}
```

重新构建并检查索引里同时出现规则类和监控类。在测试实例为该 logger 开启 DEBUG，让下载器返回一个带 Peer 的任务，再观察日志。相同 Peer 连续两轮出现会计入两次观察；若业务需要唯一 Peer 数，应自行定义标识、去重范围和过期策略。停用整个插件后，两种扩展都应从可用模块中消失。

### 用管理接口逐步验收

在 PowerShell 中可以保留每一步的响应，避免只检查 HTTP 状态码。以下命令作用于当前测试实例中已加载的 demo，令牌从环境变量读取：

```powershell
$base = 'http://127.0.0.1:19898'
$headers = @{ Authorization = "Bearer $env:PBH_TOKEN" }
$id = 'demo-client-rule'
$plugins = Invoke-RestMethod "$base/api/plugins" -Headers $headers
$plugins.data | Where-Object id -eq $id | Select-Object id, version, state, errorMessage

foreach ($action in 'stop', 'start') {
    $body = @{ action = $action; identifiers = @($id) } | ConvertTo-Json
    $result = Invoke-RestMethod "$base/api/plugins/operate" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
    $result.data | Select-Object pluginId, value, message
    (Invoke-RestMethod "$base/api/general/checkModuleAvailable?module=demo-client-rule" -Headers $headers).data
}
```

对于原始 demo，预期第一次模块检查为 `false`，第二次为 `true`。如果采用上面的可配置规则，请将查询参数改为模块键 `acme-client-rule`，同时保持操作请求中的插件 ID 与描述符一致。测试升级时，在 `unload` 成功后替换磁盘文件，用新文件名执行 `load`，再按 ID 执行 `start`；最后读取列表中的版本，避免误测旧 JAR。

### 自动化验证与常见故障

在仓库根目录运行 `./gradlew test --tests '*PluginLifecycleTest'`。该测试加载实际打包的 demo，并覆盖首批 Peer 检查、启动失败回滚、依赖故障隔离、在途调用和重启后旧回调失效。开发自己的插件时，应另外覆盖业务条件的命中、未命中、空值，以及目标宿主版本。[集成测试源码](../src/test/java/com/ghostchu/peerbanhelper/configuration/pf4j/PluginLifecycleTest.java)

| 现象 | 从哪里查起 |
| --- | --- |
| 插件列表中找不到 ID | 确认是正在运行实例的插件目录。检查 JAR 根目录的描述符、重复 ID、版本格式和宿主加载日志。 |
| 插件已加载但无法启动 | 查看 `requires`、依赖版本、缺少或被禁用的必需依赖，以及列表中的 `errorMessage`。 |
| 插件显示 STARTED，规则却不可用 | 用 `jar tf` 核对扩展索引，确认处理器已启用、规则标记了 `@Extension`，再核对模块配置键和 `enabled`。 |
| 创建扩展时提示找不到 Bean | 检查依赖是否已注册到插件或父上下文，检查构造器类型是否来自目标宿主。 |
| 出现 `ClassCastException`、`NoSuchMethodError` 或 `AbstractMethodError` | 核对编译时与运行时的宿主版本，检查插件 JAR 中重复的宿主类，针对目标发行包重新编译。 |
| 停用超时或文件暂时无法替换 | 在日志或线程栈中定位仍在运行的检查和 I/O。确认停止和卸载成功，再替换文件。 |

**从旧插件迁移**时，可先保留原描述符和插件 ID，将规则改为四参数 `shouldBanPeer` 并添加 `@Extension`，然后移除启动线程、固定延时、按 Bean 名称查找管理器和反射注册。曾从宿主根容器获取扩展实例的代码，改为插件内构造器注入，或通过 PF4J 的 `getExtensions(扩展类型, 插件ID)` 查询已启动插件。迁移后至少走一遍首次扫描、停止、重新启动和卸载，观察规则是否只执行一次。
