@echo off
title PeerBanHelper JVM Crash Error Handler
echo Please wait while the error handler is being started...
setlocal

:: 确保传入了 PID 参数
if "%~1"=="" (
    echo Error: PID argument is required.
    echo Usage:  %~nx0 ^<PID^>
    goto :eof
)

set "PID=%~1"
set "DEST_DIR=%LOCALAPPDATA%\PeerBanHelper"
set "HS_ERR_FILENAME=hs_err_pid%PID%.log"
set "GUI_EXE_NAME=PeerBanHelper-GUI.exe"
set "GUI_PARAM=crashRecovery:%PID%"

echo.
echo ----------------------------------------------------
echo Copying crash-report for PID: %PID% to dest.
echo Working directory: %cd%
echo Dest directory: %DEST_DIR%
echo ----------------------------------------------------
echo.

:: 1. 检查并创建目标目录
if not exist "%DEST_DIR%" (
    echo Target directory not exists: %DEST_DIR%
    md "%DEST_DIR%"
    if errorlevel 1 (
        echo Error: Unable to create %DEST_DIR%. Permission denied?
        goto :eof
    )
)

:: 2. 尝试从当前工作目录复制
if exist "%HS_ERR_FILENAME%" (
    echo Copying from current directory: %HS_ERR_FILENAME%
    copy "%HS_ERR_FILENAME%" "%DEST_DIR%\"
    if errorlevel 0 (
        echo Copied %HS_ERR_FILENAME% to %DEST_DIR%
        goto :success
    ) else (
        echo Error: Failed to copy file from current directory!
    )
)

echo.

:: 3. 尝试从用户临时目录复制
set "TEMP_DIR=%TEMP%"

if exist "%TEMP_DIR%\%HS_ERR_FILENAME%" (
    echo Copying from temporary directory: %HS_ERR_FILENAME%
    copy "%TEMP_DIR%\%HS_ERR_FILENAME%" "%DEST_DIR%\"
    if errorlevel 0 (
        echo Copied %HS_ERR_FILENAME% to %DEST_DIR%
        goto :success
    ) else (
        echo Error: Failed to copy file from temporary directory!
    )
)
echo.
echo Warning: Not found %HS_ERR_FILENAME% in any excepted directory.

goto :finish

:success
echo.
echo Crash report collected successfully.
echo.
goto finish

:finish
echo.
echo Attempting restart PeerBanHelper...
if exist "%~dp0\%GUI_EXE_NAME%" (
    start "" "%~dp0\%GUI_EXE_NAME%" "%GUI_PARAM%"
) else (
    echo Not found %GUI_EXE_NAME% in work directory: %cd%
)

endlocal