# GeoIP 与 GeoCN

负责人 parity。Java 参考读取点为 `util/ipdb/IPDBManager.java`、`IPDB.java`、`IPGeoData.java`、`geocn/GeoCN1.java`、`geocn/GeoCN2.java`。应用侧消费者还包括 `IPBlackList`、`PersistMetrics`、Peer 记录、当前封禁、Peer 详情、下载器 Peer 列表、图表和 AutoSTUN。

## 原生读取

- `pbh-geoip::GeoIp::open(data_dir, locale)` 读取既有 `data/ipdb/geoip/GeoIP-City.mmdb`、`GeoIP-ASN.mmdb` 和 `GeoCN.mmdb`。三个来源独立加载，失败信息由 `load_errors()` 提供。缺失文件允许继续启动，文件损坏时保留原件供排障。读入内存避免 Windows 文件映射阻塞更新和重命名。
- `lookup(IpAddr)` 返回可序列化且可读取历史 JSON 的 `GeoData`。以 Java 的 `city`、`country`、`as`、`network` 字段输出，并按 `JsonUtil.Hidden` 保留公开字段和显式 null。城市行政字段、国家名称、ASN 组织、地址和前缀保留在 Rust 类型中，用于规则和图表。管理器初始化后全部来源均未命中时返回四个 null 的对象，启动前或强制关闭时由应用层表示管理器缺席。
- City 同时提供城市和地区。国家文件放在 City 路径时仍可读取地区，支持 Enterprise 的 City 和 Country 数据。名字按配置语言再 `en`，中国相关语言选择保留 Java TW/HK/MO 名称前缀逻辑。ASN 网络项及默认 ISP 从 ASN 数据提取。IPv4 映射 IPv6 先转为 IPv4。
- GeoCN 只在 City 的地区 ISO 为 `CN`、`TW`、`HK`、`MO` 时查询。修订 2 根据现有 `ok_data_level3.csv` 逐段匹配行政区，更新城市、ISP、网络类型和 ASN 前缀，保留已有 ASN 号码及组织。修订 1 用 `province/city/districts` 与最具体行政代码，更新非空 ISP 和原始中文网络类型。修订 2 的未知行政代码维持原数据。
- `IPBlackList.checkIPDB` 在 ASN 与地区字段均为空时直接通过。查询顺序为 ASN、地区、网络类型、城市。网络类型别名与城市匹配、API 写入和重载由应用层迁移。

## 原生更新与启动接入

- `pbh.forceDisableIPDB` 优先于初始化；其余实例原版异步初始化。旧缓存默认容量 300，按访问时间滑动过期 300 秒，JVM 软引用同时受内存回收影响。本地 Rust 读取使用不可变快照，运行时热重载和缓存策略仍需校验。
- 文件不存在时即使 `auto-update=false` 仍尝试补全，已有文件仅在开启更新且修改时间超过 45 天时下载。`database-city`、`database-asn`、`database-geocn` 选择远端文件名，分别默认 `GeoLite2-City`、`GeoLite2-ASN`、`GeoCN`，本地文件名固定。
- `pbh_geoip::update::DatabaseUpdater` 依次尝试 PBH-BTN GitHub Releases、`pbh-static.paulzzh.com/ipdb/`、`pbh-static.ghostchu.com/ipdb/` 的 `.mmdb.xz`。跟随 HTTPS 发布跳转，在原镜像发出 401 时按配置重试 Basic Auth。跨源跳转移除授权头，错误不包含远端 URL。沿用 15 秒连接时限及 3 分钟调用时限。
- 以 128 MiB 压缩输入、512 MiB 解压输出及 128 MiB 解码器内存为界流式解压。校验 XZ 完整性和完整 MMDB 树及数据记录，通过后才原子替换同目录目标。可接受可信来源提供的压缩 SHA-256，输出实际摘要、字节数及镜像序号。Java 的镜像协议没有必选 SHA-256 旁路请求。失败会移除临时文件，已有有效数据库继续使用。应用后台进度事件和启动重载继续整合。

## 验证

`cargo test -p pbh-geoip --offline` 通过 5 项，`cargo fmt -p pbh-geoip --check` 与 `cargo clippy -p pbh-geoip --all-targets --locked -- -D warnings` 通过。真实 MMDB 格式夹具使用 MaxMind 发布的合成 City 和 ASN 测试数据库，GeoCN 模拟两种修订的实际二进制记录结构。覆盖语言回退、IPv4/IPv6、ASN 网络、地区限制、两版 GeoCN 合并、坏文件隔离、JSON 字段和历史读取。未与生产 GeoCN 数据库或镜像交互。[MaxMind 固定版本夹具](https://github.com/maxmind/MaxMind-DB/tree/c454a21f31fd17f2843f16094e9afb3a14a42078/test-data) · [Rust MaxMind DB 读取器](https://github.com/oschwald/maxminddb-rust)

合入更新后，`cargo test -p pbh-geoip --locked` 通过 9 项，格式及严格 Clippy 通过。Windows 本机回环覆盖缺库补全、停用定期更新、超过 45 天的文件更新、镜像切换、已验证的写入替换、校验错误保留旧库、跨端口跳转认证隔离和临时文件清理。额外验证了 XZ 截断和压缩/解压大小限制。[Rust XZ 解码器](https://github.com/hasenbanck/lzma-rust2)
