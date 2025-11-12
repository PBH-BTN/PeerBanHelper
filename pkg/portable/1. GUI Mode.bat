@echo off
title PeerBanHelper (GUI Mode)
start ./jre/bin/javaw.exe --enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true -XX:MaxRAMPercentage=85.0 -XX:+UseZGC -XX:+ZGenerational -XX:SoftMaxHeapSize=386M -XX:ZUncommitDelay=1 -Xss512k -XX:+UseStringDeduplication -XX:-ShrinkHeapInSteps -Dsun.net.useExclusiveBind=false -Dpbh.release=portable -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar PeerBanHelper.jar
