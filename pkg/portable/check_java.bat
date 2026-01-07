@echo off
chcp 65001

:: Get language from 1st parameter
set "LANG=%~1"
:: Set require Java version
set "REQ_VER=25"

if /i "%LANG%"=="zh_CN" (
    set "MSG_NOT_FOUND_1=[Error] 在同目录下、JAVA_HOME和PATH下都没有找到Java。"
    set "MSG_NOT_FOUND_2=[Error] 您可能下载了Portable_nojava版本，请下载Portable版本。"
    set "MSG_VERSION_LOW=[Error] PeerBanHelper需要Java %REQ_VER%或以上版本，当前版本为{VER}。"
    set "MSG_PARSE_FAILED=[Error] 无法识别Java版本。"
) else (
    set "MSG_NOT_FOUND_1=[Error] Java not found in current directory, JAVA_HOME or PATH."
    set "MSG_NOT_FOUND_2=[Error] Portable_nojava.zip downloaded? Please download the Portable.zip instead."
    set "MSG_VERSION_LOW=[Error] PeerBanHelper requires Java %REQ_VER% or later. Current version is {VER}."
    set "MSG_PARSE_FAILED=[Error] Failed to recognize Java version."
)

:: Search Java in current directory (Portable.zip), JAVA_HOME and PATH
if exist "%~dp0jre\bin\java.exe" (
    set "SEARCH_BIN=%~dp0jre\bin"
) else if defined JAVA_HOME (
    set "SEARCH_BIN=%JAVA_HOME%\bin"
) else (
    for /f "delims=" %%i in ('where.exe java.exe 2^>nul') do (
        :: Extract the full folder path (Drive + Path)
        set "SEARCH_BIN=%%~dpi"
        goto :CHECK_VER
    )

    :: No Java in PATH
    echo %MSG_NOT_FOUND_1%
    echo %MSG_NOT_FOUND_2%
    exit /b 1
)

:CHECK_VER
:: Remove `\` at the end
if "%SEARCH_BIN:~-1%"=="\" set "SEARCH_BIN=%SEARCH_BIN:~0,-1%"
set "JAVA_EXE=%SEARCH_BIN%\java.exe"

:: Run 'java --version' and capture the output
:: Use findstr to filter the line containing "openjdk" or "java" (case-insensitive)
:: The `tokens=2` grabs the second word ("25" in "openjdk 25 2025-09-16") 
for /f "tokens=2" %%v in ('""%JAVA_EXE%" --version 2^>^&1 ^| findstr /i "openjdk java""') do (
    set "VER_STR=%%v"
    goto :PARSE_MAJOR
)

:PARSE_MAJOR
:: Remove quotes from the version string
set "VER_STR=%VER_STR:"=%"
for /f "delims=. tokens=1" %%m in ("%VER_STR%") do set "MAJOR_VER=%%m"

:: If `MAJOR_VER` is empty or not a number, error and exit
if "%MAJOR_VER%"=="" goto :ERROR_PARSE
echo %MAJOR_VER%| findstr /r "^[0-9][0-9]*$" >nul
if errorlevel 1 goto :ERROR_PARSE

:: Enable Delayed Expansion to safely replace placeholders within the `MSG_VERSION_LOW_DISPLAY`
setlocal enabledelayedexpansion
set "MSG_VERSION_LOW_DISPLAY=!MSG_VERSION_LOW:{VER}=%MAJOR_VER%!"
if !MAJOR_VER! LSS %REQ_VER% (
    echo !MSG_VERSION_LOW_DISPLAY!
    pause
    exit /b 1
)
endlocal

set "JAVA_BIN=%SEARCH_BIN%"
exit /b 0

:ERROR_PARSE
echo %MSG_PARSE_FAILED%
pause
exit /b 1
