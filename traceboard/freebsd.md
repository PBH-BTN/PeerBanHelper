# FreeBSD 15.1 原生包验收

负责人 storage。验证对象为 `rust/packaging/freebsd/build.sh`、原生 rc.d 服务和带 WebUI 的 FreeBSD amd64 发行包。

## 验收环境

- 使用 Debian WSL2 的 KVM，QEMU 与光盘镜像工具仅安装在专用 Debian Docker 镜像内。实测 `/dev/kvm` 支持创建客户机。客户机使用独立磁盘，管理 SSH 仅监听 WSL 回环地址。
- 使用 FreeBSD 官方 `FreeBSD-15.1-RELEASE-amd64-BASIC-CLOUDINIT-ufs.qcow2.xz`。通过官方 `CHECKSUM.SHA256` 验证后解压，成功启动 FreeBSD 15.1-RELEASE amd64 内核。源码和已构建 WebUI 从 `codex/rust` 工作树复制到客户机。[FreeBSD 15.1 官方虚拟机镜像](https://download.freebsd.org/releases/VM-IMAGES/15.1-RELEASE/amd64/Latest/)

## 验证记录

客户机完成首次引导的 pkgbase 更新并重启为 FreeBSD 15.1-RELEASE-p7。初始 NoCloud 配置的 SSH 公钥未通过认证，最终通过客户机本地维护控制台登记演练公钥，继续使用仅回环可达的密钥认证。[FreeBSD 启动与维护控制台](https://docs.freebsd.org/en/books/handbook/boot/)

下载 Rust 1.96.0 时，QEMU 报告 `io-error`、虚拟磁盘 `nospace`。宿主 `/tmp` 是约 4.9 GiB 的 tmpfs。压缩镜像移出后客户机恢复，随后再次填满 tmpfs。将专用 qcow2 转移到 ext4 上的 `/var/tmp` 后，FreeBSD 检测到该测试磁盘存在 UFS 元数据不一致。客户机内 `fsck` 修复返回 0，重新引导、复装测试依赖并重新传入源码后继续构建。[FreeBSD fsck(8)](https://man.freebsd.org/cgi/man.cgi?manpath=FreeBSD+15.1-RELEASE&query=fsck&sektion=8)

旧源码快照的 Cargo 解析因 `gtk` 的 `v3_22` 特性失败。复验源码已移除该特性声明。宿主 Docker 服务随后收到 TERM 并重启，分离式客户机退出。复验将独立磁盘迁到空间充足的 D: 并做逐字节校验，以前台 QEMU 保持 WSL 会话。

- 源码 `27ba219e414b0173ae0a8ac36515ebde3860bbbb`。在 FreeBSD 15.1-RELEASE-p7 amd64 上使用原生 Rust 1.96.0 执行 `cargo build -p peerbanhelper --release --locked -j 4`，通过。`file` 确认产物是 FreeBSD 15.1 amd64 ELF，版本为 9.6.0。客户机中未安装 Java。
- `rust/packaging/freebsd/build.sh`、`pkg info -F`、`pkg add` 和已安装二进制的 `--version` 通过。包 ABI 为 `FreeBSD:15:amd64`，`ldd` 全部依赖可解析。原生程序与包内 WebUI 可用性通过已安装版本的回环首次配置测试，程序在同一进程中启用认证、扫描 qBittorrent 并写入 SQLite。
- `service peerbanhelper onestart`、`onestatus` 和 `onestop` 通过。默认数据目录中的回环 WebUI 可读取，停止后状态显示进程已退出。`pkg delete -y peerbanhelper` 移除了程序文件，保留默认数据目录的配置和持久化标记。
- FreeBSD `python3` 发行包将 SQLite 扩展单独打包。测试环境安装 `py312-sqlite3` 后 Python 夹具通过。CI 按 Python 版本安装对应的 `py*-sqlite3`，PeerBanHelper 本身使用内置 SQLite 库。
- 本机验收产物为 `target/freebsd-native-validation/peerbanhelper-9.6.0.pkg`，SHA-256 为 `0c977bdb1aac7cdf6d3db09ca34360c3e3b40794cc4ba3e821a49cd210572651`。同目录保留构建、包验收、源码和官方镜像校验记录。验收覆盖 amd64 本机虚拟客户机，未发布该包。
- 客户机通过 FreeBSD 正常关机，确认磁盘缓冲区刷写结束。专用 Docker 容器、镜像、临时磁盘和 SSH 密钥已清理，回环管理端口已关闭。
