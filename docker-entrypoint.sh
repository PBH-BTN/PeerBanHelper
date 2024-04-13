#!/bin/sh
BIN_FILE=/app/peerbanhelper-binary
if [ "$USE_NATIVE_IMAGE" -eq 1 ]
then
  if [ -f "$BIN_FILE" ]
  then
      echo "Launching PeerBanHelper via Native-Image binary file..."
      chmod +x $BIN_FILE
      exec peerbanhelper-binary
  else
    echo "PeerBanHelper binary file not exists but USE_NATIVE_IMAGE=1, please disable native image option or use native-image included image"
    exit 1
  fi
else
  echo "Launching PeerBanHelper via universal JAR..."
  if [ -f "$BIN_FILE" ]
  then
    echo "This image supports native-image, add USE_NATIVE_IMAGE=1 to use it (maybe buggy but saved a lots of system resources)"
  fi
  exe java -Xmx256M -XX:+UseSerialGC -jar PeerBanHelper.jar
fi