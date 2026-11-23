# 旧 YAML 配置迁移

## 行为来源

- 参照 `PBHConfigUpdater` 对版本标记和 `@UpdateScript` 的运行顺序，以及 `MainConfigUpdateScript`、`ProfileUpdateScript` 的各版本转换。缺省内容来自同一版本的发行 `config.yml` 和 `profile.yml`。本实现位于 `rust/app/src/config_migrations.rs`，测试在 `rust/app/tests/config_migrations.rs`。
- 主配置迁移到 v48，覆盖早期下载器配置、令牌及安装 ID、代理旧主机字段与代理模式 1→0、2→1、3→2，推送配置、BTN 历史域名、Plus 密钥列表、STUN、数据库类型、GeoCN、Teredo 与 NAT64。下载器 v32 生成 UUID 并保留显示名称，返回旧名称到 UUID 的映射。基线 Java SQLite 迁移不再使用该映射，Rust 转换也不修改数据库引用。
- 规则配置迁移到 v42，覆盖字符串匹配规则转 JSON、排除顺序和模式属性、废弃主动探测、PCB／多拨配置、网络类型表转列表、订阅 URL，以及 Peer 分析的数据保留期、清理周期和 Swarm 开关。保留各步骤新增和删除的内置匹配规则。
- 两个迁移入口都处理 YAML 树。版本验证和所有步骤成功后才替换输入树。无版本按 0 解析，适合显式选择旧格式进行转换。现有 Rust 接受精简的无版本配置，自动入口只应对声明版本的文件应用历史步骤。未来版本保持原状。调用方负责迁移备份和持久化，新生成的安装 ID 及下载器 UUID 需要在重启后保持稳定。

## 范围和差异

- 文件迁移只操作 YAML，并保留旧 banlist dump、JCEF、去中心化数据目录与 Aviator 脚本。v37 识别既有 SPK compose 的 `PBH_PKG_SYNOLOGY` 标记，将旧 `172.*` 下载器端点迁移到回环，保持端口和路径。旧 H2 数据源解析为 `h2`，保留原连接设置以便原生运行时明确拒绝。PF4J 和 Aviator 不启用。
- 主配置 v42 仅在数据库段缺失时填充默认值，避免覆盖已有数据库目标。规则配置 v32 保留旧 Swarm 段供 v34 使用，当前发行模板已无旧段。v34 只用确实存在的旧值覆盖新模板默认值。
- 旧 `length@` 规则的整数无效时返回错误，保留原输入和版本，方便修复后重试。未识别的旧匹配方法仍按 Java 跳过。

## 验证

- 9 项迁移用例通过，覆盖仓库内真实 v46 主配置样本、合成 v0/v26 主配置、旧引擎数值映射、DSM 主机网络升级、v1 和 v28 规则样本、否定匹配属性、数据保留字段移动、未来版本和失败后的输入保留。真实样本沿原 CI 流程将数据库类型占位符替换为 `sqlite`。Rustfmt 和迁移测试目标的严格 Clippy 均通过。
- 已通过 `cargo +1.96.0 test -p peerbanhelper --test config_migrations --offline -j 1` 和原生 `scan --apply --once` 两次启动验证。后者用 v26 主配置与 v1 规则配置，在磁盘转换为 v48／v42 后成功读取回环 qBittorrent 的 Peer，保留了已退役探测段。两份备份与原文件逐字节一致，第二次启动保留 UUID、升级后文件和备份。
