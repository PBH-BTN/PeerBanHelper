@echo off
chcp 65001
title PeerBanHelper (GUI 模式)
start ./jre/bin/javaw.exe -XX:+UseZGC -XX:+ZGenerational -XX:SoftMaxHeapSize=256M -XX:ZUncommitDelay=1 -Xss512k -XX:+UseStringDeduplication -XX:+ShrinkHeapInSteps -Dsun.net.useExclusiveBind=false -Dpbh.release=portable -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar PeerBanHelper.jar
