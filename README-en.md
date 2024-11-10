[简体中文](./README.md) | [English](./README-en.md)

# PeerBanHelper

Automatically bans unwanted, leeching, and abnormal BT clients, with support for custom rules.

![page-views](https://raw.githubusercontent.com/PBH-BTN/views-counter/refs/heads/master/svg/754169590/badge.svg)

> [!NOTE]
> PeerBanHelper does not have a built-in update checker. Remember to check back regularly for new version updates, or Watch this repository (Custom -> Releases, Issues, and Discussions) to receive update notifications.  
> QQ Group: 932978658. If you need help during usage, you can communicate with others here. Alternatively, you can open a new issue in the [Issue Tracker](https://github.com/Ghost-chu/PeerBanHelper/issues).

> [!TIP]
> PeerBanHelper will work properly as long as it is correctly connected to your downloader. In most cases, no additional configuration is required.

> [!TIP]
> For best results, it is recommended to use it with our maintained IP rule library [PBH-BTN/BTN-Collected-Rules](https://github.com/PBH-BTN/BTN-Collected-Rules) and the [BTN Network](https://docs.pbh-btn.com/docs/btn/intro), although this is entirely optional.

| Status                                                                                                                                | Ban List                                                                                                                             | Ban Logs                                                                                                                             | Ban Ranks                                                                                                                        | Rule Statistics                                                                                                                            | Rule Subscription                                                                                                                        |
| --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| <img width="1280" alt="homepage" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/d7f7ea9f-70df-40f1-a782-260450972bc9"> | <img width="1280" alt="banlist" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/c3e139e6-eb82-423f-b083-1839713ec801"> | <img width="1280" alt="banlogs" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/00d8efcc-0dd7-4e05-bdeb-9444e14739d6"> | <img width="1280" alt="maxban" src="https://github.com/PBH-BTN/PeerBanHelper/assets/30802565/ae78ebb9-67f7-481a-9afc-7ced2c6a2534"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/9e4cd7b7-aaff-4b66-8d1d-ad4ef3466b1f"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/dc312186-9643-4f23-9d53-7b8e0852f228"> |

## Installing PeerBanHelper

View the [PeerBanHelper Documentation](https://docs.pbh-btn.com/docs/category/%E5%AE%89%E8%A3%85%E9%83%A8%E7%BD%B2)

## Supported Clients

- qBittorrent **version 4.5.0 or higher**
- BiglyBT (requires installation of the [plugin](https://github.com/PBH-BTN/PBH-Adapter-BiglyBT))
- Deluge (requires installation of the [plugin](https://github.com/PBH-BTN/PBH-Adapter-Deluge))
- Azureus(Vuze) (requires installation of the [plugin](https://github.com/PBH-BTN/PBH-Adapter-Azureus))
- Transmission **(not recommended; version 3.00-20 or higher)**
- BitComet **version 2.10 Beta6 [20240928] or higher**

## Important Notes

Please do not enable the "Allow multiple connections from the same IP address" option in your downloader, as this can interfere with PBH's data calculation and result in incorrect bans.  
If your downloader contains seeds from PT sites, it is recommended to enable "Ignore private seeds" when adding the downloader.

## Features

PeerBanHelper mainly consists of the following functional modules:

- [PeerID Blacklist](https://docs.pbh-btn.com/docs/module/peer-id)
- [Client Name Blacklist](https://docs.pbh-btn.com/docs/module/client-name)
- [IP/GeoIP/IP Type Blacklist](https://docs.pbh-btn.com/docs/module/ip-address-blocker)
- [False Progress Checker (provides heuristic client detection)](https://docs.pbh-btn.com/docs/module/progress-cheat-blocker)
- [Automatic Chain Banning](https://docs.pbh-btn.com/docs/module/auto-range-ban)
- [Multi-Dial Hunting](https://docs.pbh-btn.com/docs/module/multi-dial)
- Peer ID/Client Name Spoofing Check; implemented through the [AviatorScript Engine](https://docs.pbh-btn.com/docs/module/expression-engine)
- [Active Monitoring (provides local data analysis)](https://docs.pbh-btn.com/docs/module/active-monitoring)
- [Network IP Set Rule Subscription](https://docs.pbh-btn.com/docs/module/ip-address-blocker-rules)
- WebUI (currently supports: viewing active ban list, querying historical bans, top 50 most frequently banned IPs, rule subscription management, chart viewing, Peer list viewing)

Additionally, PeerBanHelper downloads the GeoIP library at startup. Once successfully loaded, it supports the following features:

- View IP location, AS information (ASN, ISP, AS name, etc.), and network type information (broadband, base station, IoT, data center, etc.) in the ban list.
- Ban IP addresses based on GeoIP information such as country/region, city, network type, ASN, etc.
- View GeoIP statistics

## Frequently Asked Questions

Please check the [FAQ list](https://docs.pbh-btn.com/docs/faq) before reporting issues.

## Install4j

PeerBanHelper uses [Install4j multi-platform installer builder](https://www.ej-technologies.com/products/install4j/overview.html) to package multi-platform installers. Thanks to ej-technologies for the open-source license. Click the link or the image below to download install4j.

[![Install4j](https://www.ej-technologies.com/images/product_banners/install4j_large.png)](https://www.ej-technologies.com/products/install4j/overview.html)

## Legal Text

Illegal websites and gray/black hat industries are prohibited from initiating any form of customer service requests to our development or support teams. It is strictly forbidden to use any services or products of the PBH-BTN team for any illegal activities, endangering national security, committing or assisting others in committing telecommunications crimes, etc.  
Users must not use any services or products of the PBH-BTN team to harm the interests of other individuals or organizations. Using any services or products of the PBH-BTN team in any situation that violates the rights and interests of individuals or organizations is not allowed.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=PBH-BTN/PeerBanHelper&type=Date)](https://star-history.com/#PBH-BTN/PeerBanHelper&Date)

## Credits

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

### WebUI

- [Vue](https://vuejs.org/)
- [ArcoDesign](https://arco.design/)
- [ECharts](https://echarts.apache.org/en/index.html)
