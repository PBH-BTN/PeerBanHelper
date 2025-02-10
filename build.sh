#!/bin/sh
echo "Setup webui files.."
current_wd=$(pwd)
static_dir="$(dirname $0)/src/main/resources/static"

rm -rf ${static_dir} || echo ""
cd ${current_wd}/webui
pnpm install
pnpm run build
cd ${current_wd}
cp -r webui/dist ${static_dir}

echo "Prepare to build jar.."
mvn -B clean package --file pom.xml
