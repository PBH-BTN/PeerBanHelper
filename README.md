# PeerBanHelper

> [!WARNING]
> 项目处于早期开发阶段，可能存在错误，请关注新版本更新日志以获取最新信息！

> [!NOTE]
> PeerBanHelper 没有内建的更新检查程序，记得时常回来看看是否有新的版本更新，或者 Watch 本仓库以接收版本更新通知

自动封禁不受欢迎、吸血和异常的 BT 客户端，并支持自定义规则。

<img width="1082" alt="Snipaste_2024-03-14_12-34-16" src="https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/e30b294e-2509-484d-a510-37c20a63b0c3">

## 小提示

使用右上角的小按钮，快速查看各个章节。

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/5615fd92-bd08-4528-b1f9-500db2516d53)

## 环境要求

PeerBanHelper 需要使用 Java 17 或更高版本前置运行环境。  
配置文件在所有平台上需要统一使用 UTF-8 编码编辑和保存。如果您的 Windows 版本较旧，不支持 UTF-8 保存，请使用第三方文本编辑器如 Visual Studio Code 或者 Notepad++。

## 支持的客户端

* qBittorrent
* Transmission
  
## 功能概述

PeerBanHelper 主要由以下几个功能模块组成：

* PeerID 黑名单（Transmission 不支持）
* Client Name 黑名单
* IP 黑名单
* 虚假进度检查器（提供启发式客户端检测功能）（Transmission不支持过量下载检测）
* 主动探测

### PeerID 黑名单

顾名思义，它根据客户端交换的 Peer ID 来封禁客户端。  
通过在列表中添加不受欢迎的客户端的 Peer ID，即可封禁对应客户端。

> [!WARNING]
> Transmission 由于 API 限制，无法使用此功能，请换用 Client Name 黑名单作为替代

```yaml
  # PeerId 封禁
  # 此模块对 Transmission 不起效
  peer-id-blacklist:
    enabled: true
    # 字符串匹配规则：
    # <匹配方式>@<规则内容> 不区分大小写
    # startsWith - 匹配开头
    # endsWith - 匹配结尾
    # contains - 包含子串
    # equals - 完全匹配
    # regex - 正则匹配
    # length - 长度匹配，规则内容填写整数形式的长度
    banned-peer-id:
      - "startsWith@-XL" # Xunlei 万恶之源
      - "startsWith@-SD"
      - "startsWith@-XF"
      - "startsWith@-QD" # QQDownload QQ旋风，假进度
      - "startsWith@-BN"
      - "startsWith@-DL"
      - "startsWith@-TS" # Torrentstorm
      - "startsWith@-FG" # FlashGet 快车
      - "startsWith@-TT" # 土豆，流媒体播放器
      - "startsWith@-NX" # Net Transport
      - "startsWith@-SP" # 比特精灵，默认启用反吸血导致不给其他客户端上传
      #- "startWith@FD6" # Free Download Manager，非标准 PeerId
      - "startsWith@-GT0002"  # BaiduNetdisk Offline Download
      - "startsWith@-GT0003"  # BaiduNetdisk Offline Download
      - "startsWith@-DT" # 恶意客户端 https://github.com/anacrolix/torrent/discussions/891
      - "contains@cacao"
```

### Client Name 黑名单

部分客户端（如 Aria 2）会使用其它 BT 客户端（如：Transmission）的 Peer ID 伪装自己，但客户端名称仍然是自己的真实名称，这种情况可通过 Client Name 黑名单进行封禁。

```yaml
  # 客户端名称封禁
  client-name-blacklist:
    enabled: true
    banned-client-name:
      - "startsWith@-XL00"
      - "contains@Xunlei"
      - "startsWith@TaiPei-Torrent"
      - "startsWith@Xfplay"
      - "startsWith@BitSpirit"
      - "contains@FlashGet"
      - "contains@TuDou"
      - "contains@TorrentStorm"
      - "contains@QQDownload"
      - "contains@github.com/anacrolix/torrent" # https://github.com/anacrolix/torrent/discussions/891
      - "startsWith@qBittorrent/3.3.15" # https://github.com/c0re100/qBittorrent-Enhanced-Edition/issues/432
      - "startsWith@dt/torrent"
      - "startsWith@DT"
      - "startsWith@go.torrent.dev" # BaiduNetdisk 离线下载
      - "startsWith@github.com/thank423/trafficConsume" # 完完全全的恶意客户端
      #- "startsWith@aria2" # 冒充 Transmission 的 PeerId
```

### IP 黑名单

有的客户端（如迅雷离线下载服务器）会使用匿名模式连接，使用通用客户端名称（libtorrent）和通用 Peer ID（-LTXXXX-）来连接您，但封禁通用名称/Peer ID 会误伤不少正常客户端。  
对于这种情况，您可以直接封禁这些离线下载服务器的 IP 地址或 IP 段，或者使用的端口。

与 qBittorrent 等客户端内置的 IP 黑名单不同，PeerBanHelper 的 IP 黑名单允许您使用 CIDR 来表示一组 IP 地址，同时支持 IPV4 和 IPV6 的 CIDR 表示法，有效提升了 IP 封禁效率。

```yaml
  # IP 地址/端口 封禁
  ip-address-blocker:
    enabled: true
    # IP，支持 CIDR，其语法大致如下：
    # ::/64
    # a:b:c:d::a:b/64
    # a:b:c:d:e:f:1.2.3.4/112
    # 1.2.3.4/16
    # 1.2.255.4/255.255.0.0
    ips:
    #- 8.8.8.8
    #- 9.9.9.9
    # 端口
    ports:
    #- 2003
```

### 虚假进度检查器

此模块可谓是 PeerBanHelper 的灵魂，有助于您在不更新规则的情况下，发现那些伪装过的异常客户端。  
其大体原理如下：

* 大部分吸血客户端都不会正常上报下载进度（如：进度一直是0%，或者每次连接进度都不同）
* 虚假进度检查器通过我们上传给此对等体的数据量，计算此对等体的最低真实进度
* 如果对等体汇报的进度比最低真实进度差别过大，或者给此对等体的总上传量超过了种子本身的体积很多
* 判定为异常客户端

> [!WARNING]
> Transmission 由于 API 限制，超量下载检测不起作用，暂时没有解决方案

```yaml
  # 假进度检查
  progress-cheat-blocker:
    enabled: true
    # Torrent 小于此值不进行检查（单位：字节），对等体可能来不及同步正确的下载进度
    minimum-size: 50000000
    # 最大差值，单位百分比（1.0 = 100% 0.5=50%）
    # PeerBanHelper 根据 BT 客户端记录的向此对等体实际上传的字节数，计算该对等体的最小下载进度
    # 并与对等体汇报给 BT 客户端下载进度进行比较
    # 如果对等体汇报的总体下载进度远远低于我们上传给此对等体的数据量的比例，我们应考虑客户端正在汇报假进度
    # 默认值为：8%
    # 即：假设我们上传了 50% 的数据量给对方，对方汇报自己的下载进度只有 41%，差值大于 8%，进行封禁
    # 对于自动识别迅雷、QQ旋风的变种非常有效，能够在不更新规则的情况下自动封禁报假进度的吸血客户端
    maximum-difference: 0.08
    # 进度倒退检测
    # 默认：最多允许倒退 5% 的进度
    # (考虑到有时文件片段在传输时可能因损坏而未通过校验被丢弃，我们允许客户端出现合理的进度倒退)
    # 设置为 -1 以禁用此检测
    rewind-maximum-difference: 0.05
    # 禁止那些在同一个种子的累计下载量超过种子本身大小的客户端
    # 过量下载检测不支持 Transmission
    block-excessive-clients: true
    # 过量下载计算阈值
    # 计算方式是： 是否过量下载 = 上传总大小 > (种子总大小 * excessive-threshold)
    excessive-threshold: 1.5
```

### 主动探测

此模块允许 PeerBanHelper 除了被动的从下载器获取数据外主动出击。  
通常恶意客户端的攻击者会[使用脚本来批量部署攻击服务器并开放一个特定端口用于批量管理](https://github.com/anacrolix/torrent/discussions/891#discussioncomment-8759734)。这给了我们通过特征识别恶意攻击者的机会。  
主动探测（ActiveProbing）模块能够向连接到您的下载的 Peer 执行 ICMP Ping、TCP 连通性测试以及 HTTP(S) 请求，并根据连通性和 HTTP 状态码封禁 Peer。

```yaml
  # 主动探测
  # 一些批量部署的恶意客户端的 WebUI/System Dashboard/或者你发现的其它特征服务 通常被固定在一个特定端口上以便批量管理
  # 此功能将尝试发送请求到 Peer 的指定端口以主动探测这些特征服务
  # 如果对端响应了我们的请求状态码，Peer 将被封禁
  # 注意：这只是一个临时解决方案，通常不建议使用
  active-probing:
    # 默认情况下禁用
    # 启用此功能将导致运行内存 (RAM) 的使用量显著上升
    enabled: false
    # 最大允许的缓存条目
    # 过小的值将影响性能，过大的值将消耗更多 RAM
    # 最好设置为你的【所有】下载器的最大连接数的 3 倍
    max-cached-entry: 3000
    # 当多久没有使用到此缓存条目时，应将其从内存中移出？
    # 单位：秒，默认值：1小时（28800）
    expire-after-no-access: 28800
    # 主动探测超时，最好设置为一个大于 1000 但小于 5000 的值。
    # 过大的值将影响封禁速度
    # 过小的值将导致模块完全失效
    # 检测时将多预留 5 毫秒用于处理返回值
    # 单位：毫秒
    timeout: 3000
    # 支持下面的格式
    # TCP@12345 - 使用 TCP 方式探测指定的 12345 端口是否开放，若开放则封禁
    # PING - 使用 PING 探测对端是否响应 ICMP 包，若响应则封禁（不推荐使用，因为有相当多的 Seedbox 和软路由会响应 ICMP。但是这是一个检测是否为家用 IP 的好方法，因为家用网关设备通常不响应 ICMP 包）
    # HTTP@/subpath/subpath2@12345@200 向 http://peer-ip:12345/subpath/subpath2 发送一个 HTTP 请求，如果对端使用 200 响应了此 HTTP 请求，则封禁；注：请求会跟随30x重定向
    #       (1) /subpath/subpath2 - 路径参数，用于参与构造 HTTP 请求的 URL，你也可以设置为空格，这样就不会添加到 URL 中
    #                         (2) 12345 - 端口号，你也可以设为空格 （如：HTTP@/subpath/subpath2@ @200），这样 PBH 就不会在 URL 中添加端口号
    #                               (3) 200 - HTTP 状态码，只有响应您指定的状态码，此规则才生效。你也可以设置为全大写的 ANY 来匹配所有状态码。有关状态码的更多信息，参见：https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Status
    # HTTPS@/subpath/subpath2@12345@200@true - 与 HTTP 的几乎相同，但使用 HTTPS 方式访问
    #       (1) /subpath/subpath2 - 路径参数，用于参与构造 HTTP 请求的 URL，你也可以设置为空格，这样就不会添加到 URL 中
    #                         (2) 12345 - 端口号，你也可以设为空格 （如：HTTPS@/subpath/subpath2@ @200），这样 PBH 就不会在 URL 中添加端口号
    #                               (3) 200 - HTTP 状态码，只有响应您指定的状态码，此规则才生效。你也可以设置为全大写的 ANY 来匹配所有状态码。有关状态码的更多信息，参见：https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Status
    #                                    (4) true - 忽略 SSL 证书错误，设置为 false 将在请求时验证 SSL 证书
    probing:
      - HTTP@/subpath/subpath2@80@200 # https://github.com/anacrolix/torrent/discussions/891#discussioncomment-8761335
      - HTTPS@/subpath/subpath2@443@200@true # https://github.com/anacrolix/torrent/discussions/891#discussioncomment-8761335
    # 对 HTTP(S) 探测请求指定 User-Agent
    http-probing-user-agent: "PeerBanHelper-PeerActiveProbing/%s (github.com/Ghost-chu/PeerBanHelper)"
```

## 添加下载器

PeerBanHelper 能够连接多个支持的下载器，并共享 IP 黑名单。但每个下载器只能被一个 PeerBanHelper 添加，多个 PBH 会导致操作 IP 黑名单时出现冲突。

```yaml
# 客户端设置
client:
  # 名字，可以自己起，会在日志中显示，只能由字母数字横线组成，数字不能打头
  qbittorrent-001:
    # 客户端类型
    # 支持的客户端列表：
    # qBittorrent
    # Transmission
    # 其它也许以后会加
    type: qBittorrent
    # 客户端地址
    endpoint: "http://ip:8085"
    # 登录信息（暂不支持 Basic Auth）
    # 用户名
    username: "username"
    # 密码
    password: "password"
    # Basic Auth - 不知道这是什么的话，请保持默认
    basic-auth:
      user: ""
      pass: ""
  transmission-002:
    type: Transmission
    endpoint: "http://127.0.0.1:9091"
    username: "admin"
    password: "admin"
```

## 手动部署

### Windows 手动部署

从 [Eclipse Adoptium 网站](https://adoptium.net/zh-CN/temurin/releases/?package=jdk&os=windows)下载 Java JDK，版本必须大于等于 Java 17，下载时请选择 `.msi` 格式的安装包。

运行 MSI 安装包，遇到图中页面时，点击所有条目前面的磁盘小图标，全部选择 “整个功能将安装在本地硬盘上”，随后一路下一步安装到系统中。

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/f0428971-5724-4e84-b34c-52c3ae0d1629)

新建一个文件夹，下载 PeerBanHelper 的最新版本 JAR 问卷，并放入你新创建的文件夹中。  

新建一个 `start.bat` 批处理文件，使用记事本打开，并复制下面的内容保存：

```bat
@echo off
title PeerBanHelper
:main
java -Xmx512M -XX:+UseG1GC -XX:+UseStringDeduplication -jar PeerBanHelper.jar
timeout /t 5 /nobreak >nul
echo Restarting...
goto main
```

完成后，双击 start.bat 启动 PeerBanHelper 即可。

### Linux 手动部署

我相信 Linux 用户可以自己搞定这一切 ;)，如有需要，你还可以配置为系统服务并开机自启。

## Docker 部署

Docker 镜像为：`ghostchu/peerbanhelper`。  
如需使用 docker-compose 启动，请参见仓库的 docker-compose.yml 文件。

> [!IMPORTANT]
> 如果您设置了 Docker 镜像源，拉取的镜像可能严重过期。需要显式指定明确的版本号，版本号可在 [DockerHub](https://hub.docker.com/r/ghostchu/peerbanhelper/tags) 找到。

### 使用 Docker CLI 启动

```shell
sudo docker run -d --name peerbanhelper -p 9898:9898 -v ${PWD}/peerbanhelper-data/:/app/data/ ghostchu/peerbanhelper:最新版本号
```

### 使用 Docker Compose 文件启动

请参见仓库的 docker-compose.yml 文件，使用 `docker-compose up` 快速部署。

### 在群晖 DSM 上，使用 Container Manager 启动

首先，为 PBH 创建文件夹，用于存放 PBH 的配置文件。

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/8ee3a716-f192-4392-8362-c7c6a1f6e11f)

在 Container Manager 中，选择项目，点击新增按钮，来源选择 “创建 docker-compose.yml”（请务必先选择来源，否则后续操作将覆盖已设置的内容）。

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/ba742cc3-583e-4798-8947-f72ccc892164)

随后，点击 `设置路径` 按钮，配置 Docker Compose 的位置到我们刚刚创建的好的文件夹：

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/fa0efd6c-182c-43ea-99b1-5116ba55fbc1)

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/642f8d48-69e5-4fa3-b5d6-92750100996d)

从仓库的 `docker-compose.yml` 文件中复制所有内容，并粘贴到编辑框中：**（需要特别注意的是，如果你配置了 Docker 镜像源，则需要手动指定最新版本号，否则你可能拉取到一个史前版本的镜像，最新的版本号可以在[这里](https://github.com/Ghost-chu/PeerBanHelper/releases/latest)找到）**

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/bafd7b87-8c0e-4c65-81f0-b8ce378f5071)

如果询问你是否设置网页门户，请**不要启用**：

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/849ed06a-ddc9-4ac5-bb89-a6062b5fe36d)

一路下一步，启动容器。首次启动完成后，配置文件应该会自动生成，配置好配置文件后再次重启 Docker 容器即可使用。

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/758356d6-6cd0-42c4-a011-fbf5a66ebebd)


## 常见问题

### PeerBanHelper 运行在 Docker 里时，下载器 IP 地址怎么填，127.0.0.1 不管用

如果您的 Docker 容器和下载器运行在同一台服务器上，且使用 桥接 网络模式（默认就是桥接），那么您不能使用 127.0.0.1。

前往 Container Manager，找到网络选项卡，查看 `bridge` 中的网关地址，使用网关地址作为下载器 IP。

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/20d49093-bf99-41f6-971f-c0c574d493af)

### Transmission 的有限支持

由于 Transmission 有以下问题，因此支持是有限的

* API 无法获取 PeerID，因此 PeerID 黑名单模块不起作用
* API 无法获取客户端累计上传下载量，因此 ProgressCheatBlocker 的过量下载检测不起作用
* API 设置黑名单只能让 Transmission 请求 URL 更新，因此 PBH 需要打开一个 API 端点，且您需要保证 Transmission 能够访问到它（可在 config.yml 中配置细节）
* API 设置黑名单时不会实时生效，必须使用某种手段使种子上已连接的对等体断开。PBH 会短暂的暂停您的 Torrent 然后恢复它。
