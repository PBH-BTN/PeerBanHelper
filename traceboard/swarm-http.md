# Swarm 跟踪 HTTP

负责人 parity，路由装配 root。Java 参考为 `module/impl/monitor/SwarmTrackingModule.java`、`databasent/table/tmp/TrackedSwarmEntity.java`、`util/query/{Pageable,Orderable,PBHPage}.java` 及 `JavalinWebContainer` 的 Gson 适配。

- `rust/app/src/swarm_api.rs` 提供受保护的 `GET /api/modules/swarm-tracking` 和 `GET /api/modules/swarm-tracking/details`。应用在模块启用时注册路由及元数据，扫描启动时重置 Swarm 临时数据，再开放 HTTP。数量入口保留裸对象 `{"trackedSwarmSize":...}`，明细入口保留 `StdResp` 和 `page/size/total/results`。
- 明细输出实体的 camelCase 字段，时间使用 Unix 毫秒，IPv6 文本使用 Java 非缩写表示，保留 null 字段和当前下载器 ID。SQLite、PostgreSQL、MySQL 使用原生存储的同一实体模型查询。
- 分页沿用默认第 1 页和大小 10、首个同名参数、全局上限 1000、负大小不做总数查询和越界空页。按请求中的 `orderBy` 顺序解析，识别 `desc` 与 `descend`，后项覆盖相同字段的方向。Java 的 `Orderable(Map.of())` 无默认字段，Rust 存储在缺失 ID 排序时使用 ID 升序稳定分页。物理列名和 `tracked_swarm.` 限定受白名单约束。非合法字段名称返回 400，合法但未知的列返回原接口通用 500，无需执行失败 SQL。

## 验证

- 开发参考 JDK 25，Java JAR `build/libs/PeerBanHelper.jar`。在两份隔离 SQLite 数据目录中启动 Java 与 `peerbanhelper scan --apply --webui-dir`，使用既有 `test/rust-parity/capture_qbittorrent.py` 的回环下载器和测试配置。使用同一 Peer、种子和下载器 ID，设置 250 毫秒跟踪写盘间隔。七个请求的状态码、JSON 结构和值一致，覆盖计数、有序明细、越界分页、1000 条上限、负大小不计总数、分页输入错误和未知列。只规范化每个进程产生的 `firstTimeSeen`、`lastTimeSeen`，两者均先验证为有序的正整数。
- `cargo test -p peerbanhelper swarm_routes_keep_java_shapes_and_paginate_with_repeated_orders` 已通过。SQLite 回环测试还验证 IPv6 及 nullable 字段、多列和重复排序、精确错误消息。实际差分运行使用 SQLite，PostgreSQL、MySQL HTTP 端点仅经共享调用链和编译校验。
