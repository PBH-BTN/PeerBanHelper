#!/bin/sh
BIN_FILE=/app/peerbanhelper-binary
if [ "$USE_NATIVE_IMAGE" -eq 1 ]
then
  if [ -f "$BIN_FILE" ]
  then
      echo "$BIN_FILE exist"
  else
    echo "PeerBanHelper binary file not exists but USE_NATIVE_IMAGE=1, please disable native image option or use native-image included image"
    exit 1
  fi
  echo "Launching PeerBanHelper via Native-Image binary file..."
  chmod +x $BIN_FILE
  BIN_FILE
else
   echo "Launching PeerBanHelper via universal JAR..."
  java -Xmx256M -XX:+UseSerialGC -jar PeerBanHelper.jar
fi