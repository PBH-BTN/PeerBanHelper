# 后台任务 SSE

负责人 parity，应用路由和任务生产者接线 root。Java/Kotlin 参考为 `PBHBackgroundTaskController`、`BackgroundTaskDTO`、`AbstractSSEFeatureModule`、`BackgroundTaskManager` 和 `BackgroundTask`。WebUI 共用 `service/eventstream.ts` 的 EventSource 客户端通过查询参数提供令牌。已有 `rust/app/src/journal.rs` 继续承载独立日志历史与日志实时流。

## 实现

- `rust/app/src/background_tasks.rs` 维护后台任务注册表，向受保护的 `GET /api/tasks/live` 提供有初始快照的默认 SSE 消息。有效任务先行，已完成或失败次之，组内保持最新优先。布置快照与订阅时持有同一锁，实时更新不会落在两个步骤之间。消费者落后时再次接收当前快照。
- 事件为 `{type:"UPDATED",task:{id,title,statusText,status,barType,progress,current,max}}`。标题和状态按各连接的 `Accept-Language` 及配置的兜底语言渲染。未知总量或零总量使用生产者指定的进度类型，进度按 Java 的 `current / max` 求值，`max=0` 时为 0。
- 生产者使用 `BackgroundTasks::begin` 上报 `QUEUED` 和 `RUNNING`。任务句柄发布进度、成功或失败，未完成即释放时发布取消。保留成功任务一分钟，失败和取消保留三十分钟，快照和更新前清理过期项。Rust 服务继续通过各自的调度入口运行，跟踪器跟踪生命周期和可见进度。
- SSE 定期发送保活注释，断开时释放广播订阅。关机时取消剩余任务并关闭流，避免服务关停等待仍保持的 SSE 响应。日志端点保留 `USER_WRITE` 对演示模式的处理，任务端点只读。

## 接入与验证

- GeoIP 对实际进入更新流程的每个数据库创建独立任务。按 Java 行为显示当前镜像、解压写入量、压缩响应长度和校验阶段，继续使用镜像回退。读取服务共享的代理策略；直连模式禁用系统和环境代理。已是最新的文件没有更新事件。BTN 和订阅复用相同的任务句柄。
- `cargo test -p peerbanhelper background_tasks::tests --locked` 通过 3 项，覆盖认证、各客户端语言、连接快照、进度、失败、取消、关机流结束、过期清理、订阅释放和慢消费者恢复。`cargo test -p pbh-geoip --offline` 通过 10 项，新增逐次写入的解压进度验证。
- Java 参考进程使用拒绝所有目标连接的回环代理，读取了三个失败的 GeoIP 任务。en-US 与 zh-TW 同时返回其相同任务 ID 和全部八个任务字段。标题中的 `{}` 与 Java 参考一致，因原调用未提供标题参数。
- 重建后的 Rust 进程使用本地 YAML HTTP 代理相同方式验证。匿名请求得到 403，认证 SSE 在 en-US 与 zh-TW 返回相同的三个失败任务 ID、字段和 Java 标题。九次上游 CONNECT 均由测试回环代理拒绝。
- 首次原生进程测试启动了代理修复前的旧二进制。零回环 CONNECT 和实际下载进度表明该次可能直连了公开 GeoIP 镜像。进程已停止，临时数据已清除，配置中无镜像账户凭据。后续夹具检查二进制晚于代理实现源文件后再运行。
