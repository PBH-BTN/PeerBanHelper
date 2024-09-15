#!/bin/bash

mkdir -p /tmp/pbh-packages
cp -av synopkg/* /tmp/pbh-packages
mkdir -p /toolkit
cd /toolkit || exit
git clone https://github.com/SynologyOpenSource/pkgscripts-ng
apt-get install cifs-utils python3 python3-pip
cd /toolkit/pkgscripts-ng/ || exit
git checkout DSM7.2
./EnvDeploy -v 7.2 -p apollolake
mkdir -p /toolkit/source

cp -av /tmp/pbh-packages/* /toolkit/source/
sed -i "s#@IMAGE@#${IMAGE}#g" /toolkit/source/PeerBanHelperPackage/target/app/docker-compose.yaml
#cat /toolkit/source/PeerBanHelperPackage/target/app/docker-compose.yaml
sed -i "s#@PBH_VERSION@#${PBH_VERSION}#g" /toolkit/source/PeerBanHelperPackage/INFO.sh
#cat /toolkit/source/PeerBanHelperPackage/INFO.sh
chmod +x /toolkit/source/PeerBanHelperPackage/INFO.sh
/toolkit/pkgscripts-ng/PkgCreate.py -v 7.2 -p apollolake -c PeerBanHelperPackage