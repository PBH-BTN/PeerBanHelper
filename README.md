# PeerBanHelper
[English](./README.EN.md)

自动封禁不受欢迎、吸血和异常的 BT 客户端，并支持自定义规则。

![page-views](https://raw.githubusercontent.com/PBH-BTN/views-counter/refs/heads/master/svg/754169590/badge.svg)

PeerBanHelper 是一个开放源代码的个人网络防火墙安全软件。通过连接支持的应用程序（如：BitTorrent 客户端软件）的 Web API 接口获取受保护应用的连接信息，识别其中可能包含潜在安全威胁的连接并通知对应的应用程序主动断开其连接。  

## 功能介绍

PeerBanHelper 主要由以下几个功能模块组成：

- [PeerID 黑名单](https://docs.pbh-btn.com/docs/module/peer-id)
- [Client Name 黑名单](https://docs.pbh-btn.com/docs/module/client-name)
- [IP/GeoIP/IP 类型 黑名单](https://docs.pbh-btn.com/docs/module/ip-address-blocker)
- [虚假进度检查器（提供启发式客户端检测功能）](https://docs.pbh-btn.com/docs/module/progress-cheat-blocker)
- [自动连锁封禁](https://docs.pbh-btn.com/docs/module/auto-range-ban)
- [多拨追猎](https://docs.pbh-btn.com/docs/module/multi-dial)
- Peer ID/Client Name 伪装检查；通过 [AviatorScript 引擎](https://docs.pbh-btn.com/docs/module/expression-engine) 实现
- [主动监测（提供本地数据分析功能）](https://docs.pbh-btn.com/docs/module/active-monitoring)
- [网络 IP 集规则订阅](https://docs.pbh-btn.com/docs/module/ip-address-blocker-rules)
- WebUI （目前支持：活跃封禁名单查看，历史封禁查询，封禁最频繁的 Top 50 IP，规则订阅管理，图表查看，Peer 列表查看）

此外，PeerBanHelper 会在启动时下载 GeoIP 库，成功加载后支持以下功能：

- 在封禁列表中查看 IP 归属地，AS 信息（ASN、ISP、AS 名称等），网络类型信息（宽带、基站、物联网、数据中心等）
- 基于 GeoIP 信息按国家/地区、城市、网络类型、ASN 等封禁 IP 地址
- 查看 GeoIP 统计数据

> [!TIP]
> 为获得最佳效果，建议配合我们维护的 IP 规则库 [PBH-BTN/BTN-Collected-Rules](https://github.com/PBH-BTN/BTN-Collected-Rules) 和 [BTN 网络](https://docs.pbh-btn.com/docs/btn/intro) 一起食用，不过这是完全可选的。


## 支持的客户端

> [!CAUTION]
> 所有安装在 Docker 中的下载器，必须使用 host 网络驱动程序。PBH 需要下载器能获得 Peer 的真实 IP，不能使用 bridge 桥接模式！

- qBittorrent/qBitorrent，**4.5.0 或更高版本**，不支持 XDown
- BiglyBT（需要安装[插件](https://github.com/PBH-BTN/PBH-Adapter-BiglyBT)）
- Deluge（需要安装[插件](https://github.com/PBH-BTN/PBH-Adapter-Deluge)）
- Transmission **(4.1.0-beta2 或更高版本)**
- BitComet **v2.10 Beta6 [20240928] 或更高版本** (不支持 P2SP LTSeed 长效种子反吸血，因为 BitComet 暂时无法封禁长效连接)

PeerBanHelper 仅支持对传统 IPv4 或 IPv6 地址的反吸血，如遇 I2P 或者 Tor 连接将主动忽略。

# 截图

| 主界面                                                                                                                                | 封禁列表                                                                                                                             | 封禁日志                                                                                                                             | 封禁统计                                                                                                                            | 规则统计                                                                                                                                | 规则订阅                                                                                                                                |
| ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| <img width="1280" alt="homepage" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/d7f7ea9f-70df-40f1-a782-260450972bc9"> | <img width="1280" alt="banlist" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/c3e139e6-eb82-423f-b083-1839713ec801"> | <img width="1280" alt="banlogs" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/00d8efcc-0dd7-4e05-bdeb-9444e14739d6"> | <img width="1280" alt="maxban" src="https://github.com/PBH-BTN/PeerBanHelper/assets/30802565/ae78ebb9-67f7-481a-9afc-7ced2c6a2534"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/9e4cd7b7-aaff-4b66-8d1d-ad4ef3466b1f"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/dc312186-9643-4f23-9d53-7b8e0852f228"> |

## 安装 PeerBanHelper

查看 [PeerBanHelper 文档](https://docs.pbh-btn.com/docs/category/%E5%AE%89%E8%A3%85%E9%83%A8%E7%BD%B2)

## 常见问题

在报告问题前，请先检查 [常见问题列表](https://docs.pbh-btn.com/docs/faq)

## 需要帮助？
考虑加入我们的 QQ 群！

1 群：932978658

或者：[Telegram](https://t.me/+_t3Nt5GZ6bJmYjBl)

## 声明

**使用本软件意味着你同意以下声明：** 

违法网站和黑灰产请勿向我组织开发或支持人员发起任何形式的人工服务请求；严禁使用 PBH-BTN 团队的任何成果（包括但不限于代码，镜像，程序，BTN规则集等）从事任何违法违规、危害国家安全、实施或帮助他人实施电信犯罪等非法活动。  
用户不得通过 PBH-BTN 团队的任何成果（包括但不限于代码，镜像，程序，BTN规则集等）进行任何损害其它个人或组织的利益的活动。在任何违反个人或组织权益的情况下使用 PBH-BTN 团队的任何服务、产品均不被允许。
用户使用本软件造成的任何后果，由用户自行承担，与本软件开发者无关。

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=PBH-BTN/PeerBanHelper&type=Date)](https://star-history.com/#PBH-BTN/PeerBanHelper&Date)

## Tools

在 PeerBanHelper 的开发过程中，我们使用到了许多优秀的专业工具。感谢下面的公司或项目慷慨的提供开源许可证：

### Install4j

PeerBanHelper 使用 [Install4j multi-platform installer builder](https://www.ej-technologies.com/products/install4j/overview.html) 打包多平台安装程序。感谢 ej-technolgies 的开放源代码许可证。点击链接或者下面的图片下载 install4j。

[![Install4j](https://www.ej-technologies.com/images/product_banners/install4j_large.png)](https://www.ej-technologies.com/products/install4j/overview.html)

### JProfiler

PeerBanHelper 使用 [JProfiler all-in one Java profiler](https://www.ej-technologies.com/jprofiler) 对程序进行性能分析与优化。感谢 ej-technolgies 的开放源代码许可证。点击链接或者下面的图片下载 JProfiler。

[![JProfiler](https://www.ej-technologies.com/images/product_banners/jprofiler_large.png)](https://www.ej-technologies.com/jprofiler)

### 纯真 CZ88.NET 社区版 IP 库

纯真(CZ88.NET)自2005年起一直为广大社区用户提供社区版IP地址库，只要获得纯真的授权就能免费使用，并不断获取后续更新的版本。如果有需要免费版IP库的朋友可以前往纯真的官网进行申请。

纯真除了免费的社区版IP库外，还提供数据更加准确、服务更加周全的商业版IP地址查询数据。纯真围绕IP地址，基于 网络空间拓扑测绘 + 移动位置大数据 方案，对IP地址定位、IP网络风险、IP使用场景、IP网络类型、秒拨侦测、VPN侦测、代理侦测、爬虫侦测、真人度等均有近20年丰富的数据沉淀。


## Credit

### Backend

- [Cordelia](https://github.com/bochkov/cordelia)
- [IPAddress](https://github.com/seancfoley/IPAddress)
- [YamlConfiguration](https://github.com/bspfsystems/YamlConfiguration)
- [libby](https://github.com/AlessioDP/libby)
- [AviatorScript](https://github.com/killme2008/aviatorscript)
- [javalin](https://javalin.io/)
- [deluge-java](https://github.com/RangerRick/deluge-java)
- [jSystemThemeDetector](https://github.com/Dansoftowner/jSystemThemeDetector)
- [Methanol](https://github.com/mizosoft/methanol)
- [Flatlaf](https://github.com/JFormDesigner/FlatLaf)
- [GeoIP2](https://dev.maxmind.com/geoip)
- [ormlite](https://ormlite.com/)
- [SimpleReloadLib](https://github.com/Ghost-chu/SimpleReloadLib)
- [portmapper](https://github.com/offbynull/portmapper)
- [xz](https://github.com/tukaani-project/xz-java)
- [commonmark](https://github.com/commonmark/commonmark-java)
- [oshi](https://github.com/oshi/oshi)
- [semver4j](https://github.com/vdurmont/semver4j)
- [commonmark](https://github.com/commonmark/commonmark-java)
- [dnsjava](https://github.com/dnsjava/dnsjava)
- [SWT](https://eclipse.dev/eclipse/swt/)
- [simple-java-mail](https://www.simplejavamail.org/)
- [PF4J](https://pf4j.org/)
- [completable-futures](https://github.com/spotify/completable-futures)
- [cdnbye/NatTypeDetector](https://github.com/cdnbye/NatTypeDetector)

### WebUI

- [Vue](https://vuejs.org/)
- [ArcoDesign](https://arco.design/)
- [ECharts](https://echarts.apache.org/en/index.html)

