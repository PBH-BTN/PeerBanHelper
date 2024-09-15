#!/bin/bash
# Copyright (c) 2000-2020 Synology Inc. All rights reserved.

source /pkgscripts/include/pkg_util.sh

package="peerbanhelper"
version="@PBH_VERSION@"
displayname="PeerBanHelper"
os_min_ver="7.0-40000"
maintainer="PBH-BTN"
thirdparty="yes"
beta="true"
#arch="$(pkg_get_platform)"
arch="noarch"
description="自动封禁不受欢迎、吸血和异常的 BT 客户端，并支持自定义规则。PeerId黑名单/UserAgent黑名单/IP CIDR/假进度/超量下载/进度回退/多播追猎/连锁封禁/伪装检测 支持 qBittorrent/Transmission/Deluge/BiglyBT/Vuze(Azureus)"
dsmuidir="ui"
[ "$(caller)" != "0 NULL" ] && return 0
pkg_dump_info
