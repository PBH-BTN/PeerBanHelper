@echo off
setlocal
chcp 65001
title PeerBanHelper (GUI 模式)

call "%~dp0check_java.bat" zh_CN
if errorlevel 1 (pause & exit /b 1)

set "JAVAW_EXEC=%JAVA_BIN%\javaw.exe"

start "" "%JAVAW_EXEC%" -XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true -XX:MaxRAMPercentage=85.0 -XX:+UseZGC -XX:SoftMaxHeapSize=386M -XX:ZUncommitDelay=1 -Xss512k -XX:+UseStringDeduplication -XX:-ShrinkHeapInSteps -Dsun.net.useExclusiveBind=false -Dpbh.release=portable -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar PeerBanHelper.jar
endlocal
