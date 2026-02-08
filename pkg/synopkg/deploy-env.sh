#!/bin/bash

mkdir -p /toolkit
cd /toolkit || exit
git clone https://github.com/SynologyOpenSource/pkgscripts-ng
cd /toolkit/pkgscripts-ng/ || exit
git checkout DSM7.2
./EnvDeploy -v 7.2 -p apollolake
chroot /toolkit/build_env/ds.apollolake-7.2 umount /proc
rm -rf /toolkit/build_env/ds.apollolake-7.2