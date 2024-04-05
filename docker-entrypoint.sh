#!/bin/sh
if [ -z $USE_NATIVE_IMAGE ]
then
  echo "The environment variable USE_NATIVE_IMAGE must be set."
fi

if [ $USE_NATIVE_IMAGE -eq 1 ]
then
  chmod +x peerbanhelper-binary
  ./peerbanhelper-binary
else
  java -Xmx256M -XX:+UseSerialGC -jar PeerBanHelper.jar
fi