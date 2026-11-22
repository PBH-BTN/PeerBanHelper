# SQLite 切换与回退

负责人 storage。参考 Java `BanListServiceImpl`、`PersistMetrics`、`PBHBanController`、SQLite Flyway 迁移和 Rust `scan` 的数据目录写入锁。适用范围是同一代 Flyway SQLite schema，验证使用同分支 Java 参考提交 `25be37332` 及当前 Rust 构建。

## 演练

先构建 Java 参考 JAR、其 `build/libraries` 依赖，以及 Rust 原生可执行文件。演练使用 Python 3.11 或更高版本。提供 `--source-data` 时，使用已完全停机的现代 SQLite 测试数据集。省略该选项时，脚本用仓库里的 ORMLite 夹具副本让 Java 创建同代现代库。只把现有目录的 `persist` 树复制到测试实例，测试配置使用专用令牌、回环 WebUI 和回环 qBittorrent。

```powershell
python -B test/rust-parity/check_sqlite_cutover.py `
  --java <JAVA_25> --rust target/debug/peerbanhelper.exe `
  --source-data <STOPPED_SQLITE_DATA_DIR> `
  --artifacts target/sqlite-cutover-rehearsal
```

脚本先让 Java 封禁回环夹具中的 Peer，并等待过期封禁清理完成，确认 API 与磁盘记录。Java 正常退出后把其配置和现代库复制到 Rust 工作目录，再分别用 Rust、新启动的 Java 对同一个工作目录写入。每次交接前复位模拟下载器封禁列表，验证后继进程重新下发完整列表。Rust 和回退后的 Java 各提交一条手动封禁，对照读写前后的历史行、Flyway 版本与校验和。脚本保留阶段日志、每次交接的完整配置和数据库树、SQLite backup API 生成的独立数据库镜像及校验结果。

Windows 使用私有控制台的 Ctrl+C，进程退出前等待其关闭数据库。信号发送辅助进程仅附加到演练启动的窗口。Unix 使用 SIGTERM，并检查每个进程退出状态。[Microsoft AttachConsole](https://learn.microsoft.com/en-us/windows/console/attachconsole) · [Microsoft GenerateConsoleCtrlEvent](https://learn.microsoft.com/en-us/windows/console/generateconsolectrlevent)

## 实际交接顺序

1. 停止 Java 及其自动重启入口，等待进程和数据库连接关闭。备份整个数据目录，包括配置、现代库、可能存在的 WAL/SHM、旧库和迁移标记。记录当前封禁数、几条代表性历史记录、下载器状态和数据库版本。
2. 在独立副本验证 Rust，确认同一批代表性数据可读，启动后实际回放已有封禁，新增历史及封禁同时落盘。确认只有 Rust 服务连接实际数据目录和下载器后才切换。Rust 的本机写入锁只供 Rust 进程识别，交接依赖服务编排。
3. 回退时先停 Rust 并备份当前目录，保留切换后新增数据。使用已演练的 Java 版本打开同代、同校验和的 SQLite 库，核对新增历史和两边产生的封禁能重新下发。若版本或校验和不在已测试范围，先保留当前目录并在其副本验证，必要时恢复切换前完整备份。恢复旧快照会回到备份时刻。

## 验证记录

- Windows 上使用 Java 25.0.5 和 Rust 原生程序运行两次。已有现代库来自之前由 Java 生成的 `target/storage-java-_50_dncy`，传入前有 9 条历史。第二次从仓库 ORMLite 副本重新创建现代库。两次从 Java 切换到 Rust 再回退 Java 后，历史数均为 10 → 11 → 12。保留全部原有历史列值、五个 Flyway 版本及校验和。每次备份均通过 `PRAGMA integrity_check`。Rust 和 Java 的两次回放均包含交接时有效的封禁，Java 回退还能继续写入。Java 两次均执行完整停止钩子，Rust 正常退出。
- 仓库 ORMLite 原件、已迁移输入的配置与数据库树、工作目录里的主配置和规则配置均通过 SHA-256 前后对照。已有现代库的工作副本同时保留了原 ORMLite 文件。两次验收的保留目录分别为 `target/sqlite-cutover-20261123-settled` 和 `target/sqlite-cutover-20261123-settled-legacy`，含 `result.json`、三阶段日志、完整备份与独立数据库镜像。
- 探查时若在 Java 清理过期封禁之前停机，Rust 第一次全量下发可能包含仍持久化的过期地址，随后第一轮扫描清理。最终验收等待停机前过期记录处理稳定。Java 启动也将全量回放与加载、过期清理排在独立任务中，尚未针对这一启动窗口做两边时序差分。
- 该演练覆盖选定 SQLite 表、封禁 API 与 qBittorrent 同步。生产数据、数据库跳版本、服务器数据库回退及其他下载器的同类回退未验。
