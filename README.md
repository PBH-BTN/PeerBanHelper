# PeerBanHelper

> [!WARNING]
> 项目处于早期开发阶段，可能存在错误，请关注新版本更新日志以获取最新信息！

自动封禁不受欢迎、吸血和异常的 BT 客户端，并支持自定义规则。

<img width="1177" alt="Snipaste_2024-02-07_22-20-37" src="https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/f0fb97a5-1db8-4517-8dc8-d728b8df8237">

## 环境要求

PeerBanHelper 需要使用 Java 17 或更高版本前置运行环境。

## 支持的客户端

* qBittorrent
* 暂不支持 Transmission，缺少 Ban IP 的 API

## 功能概述

PeerBanHelper 主要由以下几个功能模块组成：

* PeerID 黑名单
* Client Name 黑名单
* IP 黑名单
* 虚假进度检查器（提供启发式客户端检测功能）


### PeerID 黑名单

顾名思义，它根据客户端交换的 Peer ID 来封禁客户端。  
通过在列表中添加不受欢迎的客户端的 Peer ID，即可封禁对应客户端。

```yaml
  # PeerId 封禁
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
      - "startsWith@-GT0002" # 无限下载文件分片 https://github.com/anacrolix/torrent/discussions/891
      - "startsWith@-GT0003" # 无限下载文件分片 https://github.com/anacrolix/torrent/discussions/891
      - "contains@cacao"
```

### Client Name 黑名单

部分客户端（如 Aria 2）会使用其它 BT 客户端（如：Transmission）的 Peer ID 伪装自己，但客户端名称仍然是自己的真实名称，这种情况可通过 Client Name 黑名单进行封禁。

```yaml
  # 客户端名称封禁
  client-name-blacklist:
    enabled: true
    banned-client-name:
      - "contains@github.com/anacrolix/torrent" # https://github.com/anacrolix/torrent/discussions/891
      - "startsWith@qBittorrent/3.3.15" # https://github.com/c0re100/qBittorrent-Enhanced-Edition/issues/432
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
    block-excessive-clients: true
    # 过量下载计算阈值
    # 计算方式是： 是否过量下载 = 上传总大小 > (种子总大小 * excessive-threshold)
    excessive-threshold: 1.5
```

## 如何使用

使用此命令启动 PeerBanHelper：

```shell
java -Xmx256M -XX:+UseG1GC -XX:+UseStringDeduplication -jar <JAR文件>
```

运行后，生成 `config.yml` 和 `profile.yml`，配置后再次使用相同命令启动 PeerBanHelper 即可。  
注意：如果您修改了配置文件，想让它生效的话，请重启 PeerBanHelper（对于Docker用户来说：重启容器）。

## 添加下载器

PeerBanHelper 能够连接多个支持的下载器，并共享 IP 黑名单。但每个下载器只能被一个 PeerBanHelper 添加，多个 PBH 会导致操作 IP 黑名单时出现冲突。

```yaml
# 客户端设置
client:
  # 名字，可以自己起，会在日志中显示
  my-really-good-best-ever-bittorrent-downloader:
    # 客户端类型
    # 支持的客户端列表：
    # qBittorrent
    # 其它也许以后会加，但 Transmission 是没戏了，WebAPI 没办法给 Transmission 加黑 IP
    type: qBittorrent
    # 客户端地址
    endpoint: "http://ip:8085"
    # 登录信息（暂不支持 Basic Auth）
    # 用户名
    username: "username"
    # 密码
    password: "password"
```


## Docker 支持

Docker 镜像为：`ghostchu/peerbanhelper`，不定期更新。  
如需使用 docker-compose 启动，请参见仓库的 docker-compose.yml 文件。
