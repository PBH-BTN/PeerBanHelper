# FreeBSD 15.1 原生包验收

负责人 storage。验证对象为 `rust/packaging/freebsd/build.sh`、原生 rc.d 服务和带 WebUI 的 FreeBSD amd64 发行包。

## 验收环境

- 使用 Debian WSL2 的 KVM，QEMU 与光盘镜像工具仅安装在专用 Debian Docker 镜像内。实测 `/dev/kvm` 支持创建客户机。客户机使用独立磁盘，管理 SSH 仅监听 WSL 回环地址。
- 使用 FreeBSD 官方 `FreeBSD-15.1-RELEASE-amd64-BASIC-CLOUDINIT-ufs.qcow2.xz`。通过官方 `CHECKSUM.SHA256` 验证后解压，成功启动 FreeBSD 15.1-RELEASE amd64 内核。源码和已构建 WebUI 从 `codex/rust` 工作树复制到客户机。[FreeBSD 15.1 官方虚拟机镜像](https://download.freebsd.org/releases/VM-IMAGES/15.1-RELEASE/amd64/Latest/)

## 验证记录

客户机完成首次引导的 pkgbase 更新并重启为 FreeBSD 15.1-RELEASE-p7。初始 NoCloud 配置的 SSH 公钥未通过认证，最终通过客户机本地维护控制台登记演练公钥，继续使用仅回环可达的密钥认证。源码已传入客户机，正在安装锁定版本编译所需工具。[FreeBSD 启动与维护控制台](https://docs.freebsd.org/en/books/handbook/boot/)
