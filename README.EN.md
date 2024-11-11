# PeerBanHelper
[简体中文](./README.md)

Automatically block unwanted, leeches and abnormal BT peers with support for customized and cloud rules.

![page-views](https://raw.githubusercontent.com/PBH-BTN/views-counter/refs/heads/master/svg/754169590/badge.svg)
## Introduction

Following function are provided by PeerBanHelper:

- [PeerID Blacklist](https://docs.pbh-btn.com/docs/module/peer-id)
- [Client Name Blacklist](https://docs.pbh-btn.com/docs/module/client-name)
- [IP/GeoIP/IP type Blacklist](https://docs.pbh-btn.com/docs/module/ip-address-blocker)
- [Fake progress checker (heuristic client detection)](https://docs.pbh-btn.com/docs/module/progress-cheat-blocker)
- [Auto range ban](https://docs.pbh-btn.com/docs/module/auto-range-ban)
- [Multi-dail ban](https://docs.pbh-btn.com/docs/module/multi-dial)
- Peer ID/Client Name camouflage check, powered by [AviatorScript Engine](https://docs.pbh-btn.com/docs/module/expression-engine)
- [Active monitoring(data analysis)](https://docs.pbh-btn.com/docs/module/active-monitoring)
- [IP set subscribe](https://docs.pbh-btn.com/docs/module/ip-address-blocker-rules)
- a mordern WebUI

In addition, PeerBanHelper downloads the GeoIP library at startup, and supports the following functions once it successful loaded:
- View IP address attribution, AS information (ASN, ISP, AS name, etc.), network type information (broadband, base station, IoT, data center, etc.) in the blocking list.
- Based on GeoIP information, block IP addresses by country/region, city, network type, ASN and so on.
- View GeoIP statistics

> [!TIP]
> For best results, it is recommended to work with the IP rule [PBH-BTN/BTN-Collected-Rules](https://github.com/PBH-BTN/BTN-Collected-Rules) and [BTN Network](https://docs.pbh-btn.com/docs/btn/intro) , but this is completely optional.


## Supported clients

- qBittorrent **4.5.0 or higher**
- BiglyBT([plugin](https://github.com/PBH-BTN/PBH-Adapter-BiglyBT) is required)
- Deluge([plugin](https://github.com/PBH-BTN/PBH-Adapter-Deluge) is required)
- Azureus(Vuze)([plugin](https://github.com/PBH-BTN/PBH-Adapter-Azureus) is required)
- Transmission **(deprected；3.00-20 or higher)**
- BitComet **v2.10 Beta6 [20240928] or higher**


# Screenshots

| Dashboard                                                                                                                             | Banlist                                                                                                                              | Banlogs                                                                                                                              | Rule subscribe                                                                                                                          |
| :------------------------------------------------------------------------------------------------------------------------------------ | :----------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------- |
| <img width="1280" alt="homepage" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/d7f7ea9f-70df-40f1-a782-260450972bc9"> | <img width="1280" alt="banlist" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/c3e139e6-eb82-423f-b083-1839713ec801"> | <img width="1280" alt="banlogs" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/00d8efcc-0dd7-4e05-bdeb-9444e14739d6"> | <img width="1280" alt="banMetrics" src="https://github.com/PBH-BTN/PeerBanHelper/assets/19235246/dc312186-9643-4f23-9d53-7b8e0852f228"> |

## Install

Please read the [docs](https://docs.pbh-btn.com/docs/category/%E5%AE%89%E8%A3%85%E9%83%A8%E7%BD%B2)


## FAQ

Before submit issue, please read the [FAQ](https://docs.pbh-btn.com/docs/faq)

## Support
Consider join our [Telegram](https://t.me/+_t3Nt5GZ6bJmYjBl) group.

## Declaration

Illegal websites and black and grey industries should not initiate any kind of manual service request to our organization's development or support staff; it is strictly prohibited to use any services or products of PBH-BTN team to engage in any illegal activities such as violating the law, endangering national security, committing or helping others to commit telecommunication crimes, and other illegal activities.  
Users are not allowed to carry out any activities that harm the interests of other individuals or organizations through any services or products of PBH-BTN Team. The use of any PBH-BTN Team services or products in violation of the rights and interests of any individual or organization is not permitted.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=PBH-BTN/PeerBanHelper&type=Date)](https://star-history.com/#PBH-BTN/PeerBanHelper&Date)

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

### WebUI

- [Vue](https://vuejs.org/)
- [ArcoDesign](https://arco.design/)
- [ECharts](https://echarts.apache.org/en/index.html)

### Install4j

PeerBanHelper use [Install4j multi-platform installer builder](https://www.ej-technologies.com/products/install4j/overview.html) to build its multi-platform installer. Thanks the open-source license provided by ej-technolgies. Click the link or the image below to download install4j.

[![Install4j](https://www.ej-technologies.com/images/product_banners/install4j_large.png)](https://www.ej-technologies.com/products/install4j/overview.html)
