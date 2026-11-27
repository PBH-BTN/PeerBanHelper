# Docker 托管更新

PeerBanHelper 后端负责检查更新和授权安装。宿主机上的更新脚本使用现有 Compose 配置重建 `peerbanhelper` 服务。未运行宿主机脚本时，WebUI 仍可检查和提示更新。

1. 将 Compose 文件放在宿主机专用目录。将 `/app/data` 的绑定目录单独设置为数据子目录，例如 `./data:/app/data`。迁移现有部署时保留当前数据和配置，并继续使用原 Compose 项目名。
2. 在 Docker 宿主机上将 `update-host.sh` 放到容器不能写入的位置。用能够运行 `docker compose` 的账户启动脚本。工作目录、Compose 配置及其引用的环境文件都应由宿主机管理。
3. 在 WebUI 页脚打开「软件更新」，选择所需的更新方式。

```sh
bash /opt/pbh/update-host.sh /opt/pbh/compose.yaml /opt/pbh/data /opt/pbh/update-state
```

可用宿主机服务管理器让脚本常驻。若容器使用自定义 UID，请使该 UID 能写入数据目录下的 `docker-update`。脚本成功更新后将镜像引用保存在 `/opt/pbh/update-state/compose.update.yaml`。此后在宿主机手工操作 Compose 时应一并传入该覆盖文件，或先将其中的镜像引用合并回主 Compose 文件。

托管安装使用稳定发行版发布的 Docker Hub 和阿里云镜像摘要。两个镜像分别由发布工作流构建，因此摘要可能不同。Docker Hub 拉取失败后会按配置顺序依次尝试镜像，每个镜像引用只试一次。

可在脚本命令末尾添加第四个参数，指定宿主机镜像列表文件，例如 `/opt/pbh/mirrors.txt`。配置文件中的每行由镜像仓库和摘要来源组成。镜像若同步 Docker Hub 的发布产物，填 `hub`。若同步阿里云发布产物，填 `mirror`。未指定文件时，默认备用仓库是阿里云。

```text
registry.cn-hangzhou.aliyuncs.com/ghostchu/peerbanhelper mirror
mirror.example.com/ghostchu/peerbanhelper hub
```

install4j 的备用源列表位于 PBH 主配置的 `updates.install4j-mirrors`。按顺序填入 HTTPS 目录 URL，每个目录需要提供 `updates.xml` 和对应安装包。修改配置后重载 PBH。
