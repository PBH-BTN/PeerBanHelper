#!/bin/bash

cd /toolkit/pkgscripts-ng/
mkdir -p /toolkit/source

cp -av pkg/synopkg/* /toolkit/source/
sed -i "s#@IMAGE@#${IMAGE}#g" /toolkit/source/PeerBanHelperPackage/target/app/docker-compose.yaml
#cat /toolkit/source/PeerBanHelperPackage/target/app/docker-compose.yaml
# Use SPK_VERSION for Synology package version (numeric format like 9.2.3-1234)
# Fallback to PBH_VERSION if SPK_VERSION is not set (for backward compatibility)
PACKAGE_VERSION="${SPK_VERSION:-$PBH_VERSION}"
sed -i "s#@PBH_VERSION@#${PACKAGE_VERSION}#g" /toolkit/source/PeerBanHelperPackage/INFO.sh
#cat /toolkit/source/PeerBanHelperPackage/INFO.sh
chmod +x /toolkit/source/PeerBanHelperPackage/INFO.sh
/toolkit/pkgscripts-ng/PkgCreate.py -v 7.2 -p apollolake -c PeerBanHelperPackage