#!/bin/sh
current_wd=$(pwd)
cd 'src/main/resources'
rm -rf static
git clone --depth 1  --branch gh-pages "https://github.com/PBH-BTN/pbh-fe.git" static
cd static && echo "WebUI Version: $(git rev-parse --short HEAD)"
cd $current_wd
