#!/bin/sh

if [ -z $NATIVE_SUPPORT ]
then
  echo "The environment variable NATIVE_SUPPORT must be set."
fi

if [ -z $USE_NATIVE_IMAGE ]
then
  echo "The environment variable USE_NATIVE_IMAGE must be set."
fi

if [ $USE_NATIVE_IMAGE -eq 1 ]
then
  if [ $NATIVE_SUPPORT -eq 1 ]
  then
    chmod +x peerbanhelper-binary
    ./peerbanhelper-binary
  else
    echo "This PeerBanHelper image aren't included the native image."
  fi
else
  java -Xmx256M -XX:+UseSerialGC -jar PeerBanHelper.jar
fi