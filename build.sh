#!/bin/sh
echo "Setup webui files.."
sh setup-webui.sh

echo "Prepare to build jar.."
mvn -B clean package --file pom.xml