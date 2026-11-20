# PeerBanHelper 规则插件示例

扩展接口、配置、热升级和旧插件迁移见[插件开发者手册](../../docs/plugin-development.md)。

这个插件匹配客户端名称恰好为 `PeerBanHelper-Demo/1.0` 的 Peer，返回 60 秒封禁。对其他客户端和缺失名称的 Peer 返回放行检查结果（`NO_ACTION`），后续规则仍可正常判断。

## 构建和安装

在 PeerBanHelper 仓库根目录使用 JDK 25 和 Gradle Wrapper。Windows 使用 `gradlew.bat`。

```sh
./gradlew :rule-plugin-demo:jar
```

将 `examples/rule-plugin/build/libs/rule-plugin-demo-1.0.0.jar` 复制到目标实例的 `data/plugins/` 后启动 PBH。请使用包含此次插件改动的宿主构建。首次扫描前日志会显示 `Started plugin demo-client-rule@1.0.0`。

也可以将本目录单独复制出去，在示例目录使用 Gradle 构建。将 `pbhHome` 指向包含 `PeerBanHelper.jar` 和 `libraries/` 的目标发行目录。

```sh
gradle jar -PpbhHome=/path/to/PeerBanHelper
```

## 试运行

在 PBH 仓库执行以下命令，可以用合成 Peer 验证命中、未命中、停用和重新启动。测试会加载实际生成的插件 JAR。

```sh
./gradlew test --tests '*PluginLifecycleTest'
```

在运行中的实例上，用已配置的令牌查看插件状态，停止并重新启动同一插件。自定义部署请替换下面的服务地址。

```sh
curl -H "Authorization: Bearer $PBH_TOKEN" http://127.0.0.1:9898/api/plugins
curl -H "Authorization: Bearer $PBH_TOKEN" -H 'Content-Type: application/json' \
  -d '{"action":"stop","identifiers":["demo-client-rule"]}' http://127.0.0.1:9898/api/plugins/operate
curl -H "Authorization: Bearer $PBH_TOKEN" -H 'Content-Type: application/json' \
  -d '{"action":"start","identifiers":["demo-client-rule"]}' http://127.0.0.1:9898/api/plugins/operate
```

## 写自己的插件

- 在 `src/main/resources/plugin.properties` 声明唯一 ID、版本和兼容的 PBH 版本区间。构建直接以目标宿主及其随附库作为编译依赖。发布的 JAR 只包含插件自己的类、描述符和 PF4J 生成的 `META-INF/extensions.idx`。
- 示例的 `DemoClientRule` 继承宿主已有的 `AbstractRuleFeatureModule`，并用 `@Extension` 声明。PBH 通过插件自己的 Spring 上下文创建扩展并注册模块。可在构造函数中注入宿主服务、同插件的其他扩展以及本插件的 `PluginWrapper`。
- 可配置规则沿用 `profile.yml` 的 `module.<getConfigName()>.enabled`。需要定时执行的工作可使用基类的 `registerScheduledTask`。插件自己创建的线程、连接或外部订阅应在 `onDisable()` 中关闭。
- 停用会先停止向旧实例分派规则、批量监控和受管定时回调，再等待正在执行的回调退出。若五秒内仍有调用在运行，管理接口会返回该插件的超时信息并保留其资源。待调用完成后可重新执行 `stop` 或 `unload`。
- 已有 PF4J 入口类仍可用 `plugin.class` 声明。迁移规则时，给规则加上 `@Extension`，删除原来的手工注册和启动延时。当前 `shouldBanPeer` 接收第四个参数 `PipelineTask`。依赖宿主内部类型的旧 JAR 应针对目标版本重新编译并运行测试。
- 扩展 Bean 属于各自的插件上下文。旧代码如果通过宿主根容器查找插件 Bean，可改用本插件内的构造器注入，或通过 PF4J 的 `getExtensions(扩展类型, 插件ID)` 获取已启动插件的扩展。旧式 `PBHPlugin` 的显示名、描述和菜单在每次启动时读取，管理端按请求语言翻译。
