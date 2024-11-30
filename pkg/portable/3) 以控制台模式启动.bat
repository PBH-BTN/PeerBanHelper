@echo off
chcp 65001
title PeerBanHelper
start ./jre/bin/java.exe -Xmx512M -Xss512k -XX:+UseG1GC -XX:+UseStringDeduplication -Djava.awt.headless=true -XX:+ShrinkHeapInSteps -Dpbh.release=portable -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar PeerBanHelper.jar nogui
pause