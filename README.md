# PeerBanHelper

自动封禁不受欢迎、吸血和异常的 BT 客户端，并支持自定义规则。

> [!NOTE]
> PeerBanHelper 没有内建的更新检查程序，记得时常回来看看是否有新的版本更新，或者 Watch 本仓库以接收版本更新通知  
> QQ 交流群（临时）：932978658，如果在使用过程中需要帮助，您可以在这里和他人一同交流。或者在 [Issue Tracker](https://github.com/Ghost-chu/PeerBanHelper/issues) 打开新问题

> [!TIP]
> ❤ PeerBanHelper 是一个社区项目，由贡献者们共同建设。点击[此处](https://github.com/PBH-BTN/PeerBanHelper/graphs/contributors)查看所有一同共建 PeerBanHelper 社区的人们。

| 主界面                                                                                                                                   | 封禁列表                                                                                                                                 | 封禁日志                                                                                                                                 | 封禁统计                                                                                                                                | 规则统计                                                                                                                                    | 规则订阅                                                                                                                                    |
|---------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| <img width="1280" alt="homepage" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/d7f7ea9f-70df-40f1-a782-260450972bc9"> | <img width="1280" alt="banlist" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/c3e139e6-eb82-423f-b083-1839713ec801"> | <img width="1280" alt="banlogs" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/00d8efcc-0dd7-4e05-bdeb-9444e14739d6"> | <img width="1280" alt="maxban" src="https://github.com/PBH-BTN/PeerBanHelper/assets/30802565/ae78ebb9-67f7-481a-9afc-7ced2c6a2534"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/9e4cd7b7-aaff-4b66-8d1d-ad4ef3466b1f"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/dc312186-9643-4f23-9d53-7b8e0852f228"> |

## 安装 PeerBanHelper

请选择您心仪的安装方式：

| Docker/Docker Compose | Windows | Linux |
| --- | ---- | ---- |
|  [查看](https://github.com/PBH-BTN/PeerBanHelper/wiki/Docker-%E9%83%A8%E7%BD%B2)   |  [查看](https://github.com/PBH-BTN/PeerBanHelper/wiki/Windows-%E6%89%8B%E5%8A%A8%E9%83%A8%E7%BD%B2)    |  [查看](https://github.com/PBH-BTN/PeerBanHelper/wiki/Linux-%E6%89%8B%E5%8A%A8%E9%83%A8%E7%BD%B2)    |

## 运行环境要求

* 一台支持 OpenJDK 的设备，可为 JVM 预留 200MB 内存，PBH（无GUI模式）至少需要 72MB Heap 内存来运行（-Xmx72M），长期稳定运行建议分配 128MB~256MB Heap RAM
* 由于 SQLite 数据库支持，CPU 架构需要是其中之一：aarch64, arm, armv6, armv7, ppc64, x86, x86_64，因此 riscv 等新兴 CPU 架构暂不兼容

## 支持的客户端

* qBittorrent
* Transmission **(3.00-20 或更高版本)**
* BiglyBT（需要安装[插件](https://github.com/PBH-BTN/PBH-Adapter-BiglyBT)）
  
## 功能介绍

PeerBanHelper 主要由以下几个功能模块组成：

* PeerID 黑名单（Transmission 不支持）
* Client Name 黑名单
* IP 黑名单
* 虚假进度检查器（提供启发式客户端检测功能）（Transmission不支持过量下载检测）
* 主动探测
* 自动 IP 段封禁
* WebUI （目前支持：活跃封禁名单查看，历史封禁查询，封禁最频繁的 Top 50 IP）

### PeerID 黑名单

顾名思义，它根据客户端交换的 Peer ID 来封禁客户端。  
通过在列表中添加不受欢迎的客户端的 Peer ID，即可封禁对应客户端。

> [!WARNING]
> Transmission 由于 API 限制，无法使用此功能，请换用 Client Name 黑名单作为替代

<details>

<summary>查看示例配置文件</summary>

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
      - '{"method":"CONTAINS","content":"-xl0019","hit":"FALSE"}' # 排除迅雷 0019
      - '{"method":"STARTS_WITH","content":"-xl"}'
      - '{"method":"STARTS_WITH","content":"-sd"}'
      - '{"method":"STARTS_WITH","content":"-xf"}'
      - '{"method":"STARTS_WITH","content":"-qd"}'
      - '{"method":"STARTS_WITH","content":"-bn"}'
      - '{"method":"STARTS_WITH","content":"-dl"}'
      - '{"method":"STARTS_WITH","content":"-ts"}'
      - '{"method":"STARTS_WITH","content":"-fg"}'
      - '{"method":"STARTS_WITH","content":"-tt"}'
      - '{"method":"STARTS_WITH","content":"-nx"}'
      - '{"method":"STARTS_WITH","content":"-sp"}'
      - '{"method":"STARTS_WITH","content":"-gt0002"}'
      - '{"method":"STARTS_WITH","content":"-gt0003"}'
      - '{"method":"STARTS_WITH","content":"-dt"}'
      - '{"method":"STARTS_WITH","content":"-tt"}'
      - '{"method":"STARTS_WITH","content":"-tt"}'
      - '{"method":"CONTAINS","content":"cacao"}'
      - '{"method":"STARTS_WITH","content":"-hp"}'
```
</details>


### Client Name 黑名单

部分客户端（如 Aria 2）会使用其它 BT 客户端（如：Transmission）的 Peer ID 伪装自己，但客户端名称仍然是自己的真实名称，这种情况可通过 Client Name 黑名单进行封禁。

<details>

<summary>查看示例配置文件</summary>

```yaml
  # 客户端名称封禁
  client-name-blacklist:
    enabled: true
    banned-client-name:
      - '{"method":"STARTS_WITH","content":"-xl00"}'
      - '{"method":"CONTAINS","content":"xunlei"}'
      - '{"method":"STARTS_WITH","content":"taipei-torrent"}'
      - '{"method":"STARTS_WITH","content":"xfplay"}'
      - '{"method":"STARTS_WITH","content":"bitspirit"}'
      - '{"method":"CONTAINS","content":"flashget"}'
      - '{"method":"CONTAINS","content":"tudou"}'
      - '{"method":"CONTAINS","content":"torrentstorm"}'
      - '{"method":"CONTAINS","content":"qqdownload"}'
      - '{"method":"CONTAINS","content":"github.com/anacrolix/torrent"}'
      - '{"method":"STARTS_WITH","content":"qbittorrent/3.3.15"}'
      - '{"method":"STARTS_WITH","content":"dt/torrent"}'
      - '{"method":"STARTS_WITH","content":"dt"}'
      - '{"method":"STARTS_WITH","content":"go.torrent.dev"}'
      - '{"method":"STARTS_WITH","content":"github.com/thank423/trafficconsume"}'
      - '{"method":"STARTS_WITH","content":"taipei-torrent"}'
      - '{"method":"STARTS_WITH","content":"hp/torrent"}'
      - '{"method":"STARTS_WITH","content":"hp"}'
```

</details>

### IP 黑名单

有的客户端（如迅雷离线下载服务器）会使用匿名模式连接，使用通用客户端名称（libtorrent）和通用 Peer ID（-LTXXXX-）来连接您，但封禁通用名称/Peer ID 会误伤不少正常客户端。  
对于这种情况，您可以直接封禁这些离线下载服务器的 IP 地址或 IP 段，或者使用的端口。

与 qBittorrent 等客户端内置的 IP 黑名单不同，PeerBanHelper 的 IP 黑名单允许您使用 CIDR 来表示一组 IP 地址，同时支持 IPV4 和 IPV6 的 CIDR 表示法，有效提升了 IP 封禁效率。

<details>

<summary>查看示例配置文件</summary>

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

</details>


### 虚假进度检查器

此模块可谓是 PeerBanHelper 的灵魂，有助于您在不更新规则的情况下，发现那些伪装过的异常客户端。  
其大体原理如下：

* 大部分吸血客户端都不会正常上报下载进度（如：进度一直是0%，或者每次连接进度都不同）
* 虚假进度检查器通过我们上传给此对等体的数据量，计算此对等体的最低真实进度
* 如果对等体汇报的进度比最低真实进度差别过大，或者给此对等体的总上传量超过了种子本身的体积很多
* 判定为异常客户端

> [!WARNING]
> Transmission 由于 API 限制，超量下载检测不起作用，暂时没有解决方案

<details>

<summary>查看示例配置文件</summary>

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

</details>

### 自动 IP 段封禁

批量部署的恶意客户端通常在同一个 IP 段下，PBH 现在允许用户分别为 IPv4 和 IPv6 设置一个前缀长度。在封禁发现的吸血客户端时，会将其所处 IP 地址的指定范围的其余 IP 地址均加入屏蔽列表，实现链式封禁。

<details>

<summary>查看示例配置文件</summary>

```yaml
  # 范围 IP 段封禁
  # 在封禁 Peer 后，被封禁的 Peer 所在 IP 地址的指定前缀长度内的其它 IP 地址都将一同封禁
  auto-range-ban:
    # 是否启用
    enabled: true
    # IPV4 前缀长度
    ipv4: 30 # /32 = 单个 IP，/24 = 整个 ?.?.?.x 段
    # IPV6 前缀长度
    ipv6: 64 # /64 = ISP 通常分配给家宽用户的前缀长度
```

</details>

### 多拨侦测

专业PCDN用户会在一台PCDN服务器上接入多条宽带，以此提升上传带宽，称为多拨。
这类用户的刷下载工具一般也较为复杂，会利用多条宽带不同的出口IP分散流量，对抗基于下载进度的吸血检测。
此模块对多拨下载现象进行侦测，发现同一网段集中下载同一种子，即予以全部封禁。
目前已知可能误伤的情况：小ISP的骨干网出口在同一网段，造成多拨假象。如果种子涉及的BT网络主体在大陆以外，请谨慎使用。

<details>

<summary>查看示例配置文件</summary>

```yaml
  multi-dialing-blocker:
    enabled: false
    # 子网掩码长度
    # IP地址前多少位相同的视为同一个子网，位数越少范围越大，一般不需要修改
    subnet-mask-length: 24
    # 对于同小区IPv6地址应该取多少位掩码没有调查过，64位是不会误杀的保险值
    subnet-mask-v6-length: 64
    # 容许同一网段下载同一种子的IP数量，正整数
    # 防止DHCP重新分配IP、碰巧有同一小区的用户下载同一种子等导致的误判
    tolerate-num: 3
    # 缓存持续时间（秒）
    # 所有连接过的peer会记入缓存，DHCP服务会定期重新分配IP，缓存时间过长会导致误杀
    cache-lifespan: 86400
    # 是否追猎
    # 如果某IP已判定为多拨，无视缓存时间限制继续搜寻其同伙
    keep-hunting: true
    # 追猎持续时间（秒）
    # 和cache-lifspan作用相似，对被猎杀IP的缓存持续时间，keep-hunting为true时有效
    keep-hunting-time: 2592000
```

</details>

## AviatorScript 脚本引擎

AviatorScript 脚本引擎提供了完整编程能力，您可以编写自己的脚本控制是否封禁，参见 [AviatorScript 脚本引擎 WIKI](https://github.com/PBH-BTN/PeerBanHelper/wiki/AviatorScript-%E8%84%9A%E6%9C%AC%E5%BC%95%E6%93%8E)

## 添加下载器

PeerBanHelper 能够连接多个支持的下载器，并共享 IP 黑名单。但每个下载器只能被一个 PeerBanHelper 添加，多个 PBH 会导致操作 IP 黑名单时出现冲突。

<details>

<summary>查看示例配置文件</summary>

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

</details>

## 常见问题

### PeerBanHelper 运行在 Docker 里时，下载器 IP 地址怎么填，127.0.0.1 不管用

如果您的 Docker 容器和下载器运行在同一台服务器上，且使用 桥接 网络模式（默认就是桥接），那么您不能使用 127.0.0.1。

前往 Container Manager，找到网络选项卡，查看 `bridge` 中的网关地址，使用网关地址作为下载器 IP。

![image](https://github.com/Ghost-chu/PeerBanHelper/assets/30802565/20d49093-bf99-41f6-971f-c0c574d493af)

### 非本机（例如：NAS上、Docker里）的 PBH 的 WebUI 打不开怎么办？

打开 `config.yml`，找到 `address`，它的默认值是 `127.0.0.1`（只有PBH所在机器可以访问），请更改为 `0.0.0.0` 开启外部访问。  
**对于下面的 prefix 选项，除非你知道那是干什么的，否则请保持默认，或者询问其它用户**

### 管理 Token 在哪里？

在 config.yml 中：

```yaml
# Http 服务器设置
server:
  # 监听端口
  http: 9898
  # 客户端远程 URL 设置
  # Docker 网络请改 host 模式使用或者设置容器端口暴露
  # 当客户端需要与 PBH 通信时，客户端的 URL 会被更改为 http://<address>:<http-port>/<client-api-route>
  address: 127.0.0.1
  # 在 PBH 需要给下载器传递地址时，将使用此地址传递，请确保此地址最终可被下载器访问，请【不要】以 / 结尾
  prefix: http://127.0.0.1:9898
  token: "*************************" # <-- 你的管理 Token
```

### Transmission 的有限支持

由于 Transmission 有以下问题，因此支持是有限的

* API 无法获取 PeerID，因此 PeerID 黑名单模块不起作用
* API 无法获取客户端累计上传下载量，因此 ProgressCheatBlocker 的过量下载检测不起作用
* API 设置黑名单只能让 Transmission 请求 URL 更新，因此 PBH 需要打开一个 API 端点，且您需要保证 Transmission 能够访问到它（可在 config.yml 中配置细节）
* API 设置黑名单时不会实时生效，必须使用某种手段使种子上已连接的对等体断开。PBH 会短暂的暂停您的 Torrent 然后恢复它。

## Install4j

PeerBanHelper 使用 [Install4j multi-platform installer builder](https://www.ej-technologies.com/products/install4j/overview.html) 打包多平台安装程序。感谢 ej-technolgies 的开放源代码许可证。点击链接或者下面的图片下载 install4j。

[![Install4j](https://www.ej-technologies.com/images/product_banners/install4j_large.png)](https://www.ej-technologies.com/products/install4j/overview.html)

## Credit

* [Cordelia](https://github.com/bochkov/cordelia)
* [IPAddress](https://github.com/seancfoley/IPAddress)
* [YamlConfiguration](https://github.com/bspfsystems/YamlConfiguration)
* [Guava](https://github.com/google/guava)
* [Gson](https://github.com/google/gson)
* [NanoHttpd](https://github.com/NanoHttpd/nanohttpd)
* [Apache Commons Lang3](https://commons.apache.org/proper/commons-lang/)
* [Slf4j](https://github.com/qos-ch/slf4j)
