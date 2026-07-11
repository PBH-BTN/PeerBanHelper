#!/bin/bash

mkdir -p /toolkit
cd /toolkit || exit
git clone https://github.com/SynologyOpenSource/pkgscripts-ng
cd /toolkit/pkgscripts-ng/ || exit
git checkout DSM7.2

cp include/python/pkgdeploy.py pkgdeploy.py.bak

sed -i 's|self.adjust_chroot(platform)|continue|g' include/python/pkgdeploy.py

./EnvDeploy -v 7.2 -p apollolake
chroot /toolkit/build_env/ds.apollolake-7.2 umount /proc

cp pkgdeploy.py.bak include/python/pkgdeploy.py

sed -i 's|toolkit.clean()|#toolkit.clean()|g' EnvDeploy
sed -i '/self.__check_tarball_exists()/d' include/python/pkgdeploy.py
sed -i '/self.deploy_base_env(platform)/d' include/python/pkgdeploy.py
sed -i '/self.deploy_env(platform)/d' include/python/pkgdeploy.py
sed -i '/self.deploy_dev(platform)/d' include/python/pkgdeploy.py

rm -rf .git
rm -rf /toolkit/pkgscripts-ng/toolkit_tarballs