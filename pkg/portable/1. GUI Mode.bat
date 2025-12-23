@echo off
setlocal
title PeerBanHelper (GUI Mode)

if defined JAVAW_EXEC (
    goto :LAUNCH
)

set "BUNDLED_JRE=%~dp0jre\bin\javaw.exe"
if exist "%BUNDLED_JRE%" (
    set "JAVAW_EXEC=%BUNDLED_JRE%"
    goto :LAUNCH
)

set "JAVAW_EXEC=javaw.exe"

:LAUNCH
start "" "%JAVAW_EXEC%" -XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true -XX:MaxRAMPercentage=85.0 -XX:+UseZGC -XX:SoftMaxHeapSize=386M -XX:ZUncommitDelay=1 -Xss512k -XX:+UseStringDeduplication -XX:-ShrinkHeapInSteps -Dsun.net.useExclusiveBind=false -Dpbh.release=portable -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar PeerBanHelper.jar
endlocal
