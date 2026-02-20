@echo off
setlocal
title PeerBanHelper (Console)

call "%~dp0check_java.bat"
if errorlevel 1 (pause & exit /b 1)

set "JAVA_EXEC=%JAVA_BIN%\java.exe"

:: ============================================================
:: 1. Do NOT put any spaces BEFORE or AFTER the ^ character.
:: 2. Every new line MUST start with a space to separate args.
:: ============================================================
"%JAVA_EXEC%"^
 -XX:+UseCompactObjectHeaders^
 --enable-native-access=ALL-UNNAMED^
 -Djdk.attach.allowAttachSelf=true^
 -XX:MaxRAMPercentage=85.0^
 -XX:SoftMaxHeapSize=386M^
 -XX:+UseZGC^
 -XX:ZUncommitDelay=1^
 -Xss512k^
 -XX:+UseStringDeduplication^
 -XX:-ShrinkHeapInSteps^
 -Dsun.net.useExclusiveBind=false^
 -Dpbh.release=portable^
 -Dfile.encoding=UTF-8^
 -Dstdout.encoding=UTF-8^
 -Dstderr.encoding=UTF-8^
 -Dconsole.encoding=UTF-8^
 -jar PeerBanHelper.jar nogui

pause
endlocal
