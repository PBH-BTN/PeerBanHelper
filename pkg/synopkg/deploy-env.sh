#!/bin/bash

cd /toolkit || exit
git clone https://github.com/SynologyOpenSource/pkgscripts-ng
cd /toolkit/pkgscripts-ng/ || exit
git checkout DSM7.2

cp include/python/pkgdeploy.py pkgdeploy.py.bak

sed -i 's|self.adjust_chroot(platform)|continue|g' include/python/pkgdeploy.py

./EnvDeploy -v 7.2 -p apollolake

cp pkgdeploy.py.bak include/python/pkgdeploy.py