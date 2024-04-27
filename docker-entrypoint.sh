#!/bin/sh
# shellcheck shell=sh

chown -R "${PUID}":"${PGID}" /app

exec gosu "${PUID}":"${PGID}" dumb-init java -Xmx256M -XX:+UseSerialGC -jar PeerBanHelper.jar
