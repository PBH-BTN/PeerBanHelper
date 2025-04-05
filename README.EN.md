# PeerBanHelper
[简体中文](./README.md)

Automatically ban unwelcome, leeching, and abnormal BT clients with support for custom rules.

![page-views](https://raw.githubusercontent.com/PBH-BTN/views-counter/refs/heads/master/svg/754169590/badge.svg)
## Features

PeerBanHelper consists of the following core modules:

- [PeerID Blacklist](https://docs.pbh-btn.com/docs/module/peer-id)
- [Client Name Blacklist](https://docs.pbh-btn.com/docs/module/client-name)
- [IP/GeoIP/IP Type Blacklist](https://docs.pbh-btn.com/docs/module/ip-address-blocker)
- [Fake Progress Checker (Progress Cheat Blocker)](https://docs.pbh-btn.com/docs/module/progress-cheat-blocker)
- [Automatic Chain Banning](https://docs.pbh-btn.com/docs/module/auto-range-ban)
- [Multi-Dial Tracking](https://docs.pbh-btn.com/docs/module/multi-dial)
- Peer ID/Client Name Spoofing Detection via [AviatorScript Engine](https://docs.pbh-btn.com/docs/module/expression-engine)
- [Active Monitoring (Local Data Analysis)](https://docs.pbh-btn.com/docs/module/active-monitoring)
- [IP Set Rules Subscription](https://docs.pbh-btn.com/docs/module/ip-address-blocker-rules)
- WebUI (Current Features: Active Banlist, Ban History, Top 50 Most Banned IPs, Rule Subscription Management, Charts, Peer List)

Additionally, PeerBanHelper downloads the GeoIP database on startup. Once loaded, it supports:

- Viewing IP geolocation, AS info (ASN, ISP, AS Name), and network type (Broadband, Cellular, IoT, Data Center, etc.) in ban lists
- Banning IPs by country/region, city, network type, ASN, etc.
- GeoIP statistics visualization

> [!TIP]
> For optimal performance, we recommend using our maintained IP rule repository [PBH-BTN/BTN-Collected-Rules](https://github.com/PBH-BTN/BTN-Collected-Rules) and [BTN Network](https://docs.pbh-btn.com/docs/btn/intro), though this is entirely optional.

## Supported Clients

> [!CAUTION]
> All Docker-based downloaders must use host network driver. PBH requires real peer IPs - bridge mode is unsupported!

- qBittorrent/qBittorrent **v4.5.0 or newer** (XDown not supported)
- BiglyBT (requires [plugin](https://github.com/PBH-BTN/PBH-Adapter-BiglyBT))
- Deluge (requires [plugin](https://github.com/PBH-BTN/PBH-Adapter-Deluge))
- Transmission **(v4.1.0-beta2 or newer required)**
- BitComet **v2.10 Beta6 [20240928] or newer** (LTSeed anti-leech unsupported due to BitComet's limitation)

PeerBanHelper only supports traditional IPv4/IPv6 anti-leech. I2P/Tor connections are ignored.

# Screenshots

| Main UI                                                                                                                              | Ban List                                                                                                                           | Ban Logs                                                                                                                           | Ban Statistics                                                                                                                     | Rule Statistics                                                                                                                     | Rule Subscriptions                                                                                                                   |
| ------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| <img width="1280" alt="homepage" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/d7f7ea9f-70df-40f1-a782-260450972bc9"> | <img width="1280" alt="banlist" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/c3e139e6-eb82-423f-b083-1839713ec801"> | <img width="1280" alt="banlogs" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/00d8efcc-0dd7-4e05-bdeb-9444e14739d6"> | <img width="1280" alt="maxban" src="https://github.com/PBH-BTN/PeerBanHelper/assets/30802565/ae78ebb9-67f7-481a-9afc-7ced2c6a2534"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/9e4cd7b7-aaff-4b66-8d1d-ad4ef3466b1f"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/dc312186-9643-4f23-9d53-7b8e0852f228"> |

## Installation

See [PeerBanHelper Documentation](https://docs.pbh-btn.com/docs/category/%E5%AE%89%E8%A3%85%E9%83%A8%E7%BD%B2)

## FAQ

Before reporting issues, check [FAQ](https://docs.pbh-btn.com/docs/faq)

## Need Help?
Join our [QQ Group](https://qm.qq.com/cgi-bin/qm/qr?k=w5as_wH2G1ReUrClreCYhR69XiNCuP65&jump_from=webapi&authKey=EyjMX7Pwc77XLM51V6FEcR7oXnG8fsUbSFqYZ4PPiEpq32vBglJn/jFvpc3LFDhn)!

## Disclaimer

**By using this software, you agree to:**

Illegal websites and black/grey industries shall not request any form of manual service from PBH-BTN developers/supporters. Strictly prohibited to use any PBH-BTN assets (including but not limited to code, images, programs, BTN rulesets) for illegal activities, endangering national security, telecom fraud, or assisting others in such acts.  
Users shall not use PBH-BTN assets to harm other individuals/organizations. Any usage infringing rights of others is prohibited.  
Users bear full responsibility for consequences arising from software usage. Developers assume no liability.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=PBH-BTN/PeerBanHelper&type=Date)](https://star-history.com/#PBH-BTN/PeerBanHelper&Date)

## Tools

We use these excellent tools during development. Thanks to the companies/projects for providing open-source licenses:

### Install4j

PeerBanHelper uses [Install4j multi-platform installer builder](https://www.ej-technologies.com/products/install4j/overview.html). Thanks to ej-technologies for the OSS license. Click the link or image below for install4j.

[![Install4j](https://www.ej-technologies.com/images/product_banners/install4j_large.png)](https://www.ej-technologies.com/products/install4j/overview.html)

### JProfiler

PeerBanHelper uses [JProfiler all-in one Java profiler](https://www.ej-technologies.com/jprofiler) for performance analysis. Thanks to ej-technologies for the OSS license. Click the link or image below for JProfiler.

[![JProfiler](https://www.ej-technologies.com/images/product_banners/jprofiler_large.png)](https://www.ej-technologies.com/jprofiler)

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

### WebUI

- [Vue](https://vuejs.org/)
- [ArcoDesign](https://arco.design/)
- [ECharts](https://echarts.apache.org/en/index.html)
