Unicode true
RequestExecutionLevel user
Name "PeerBanHelper Native"
OutFile "${OUTPUT}"
Icon "${APP_ICON}"
UninstallIcon "${APP_ICON}"
InstallDir "$LOCALAPPDATA\Programs\PeerBanHelper Native"
InstallDirRegKey HKCU "Software\PeerBanHelper\Native" "InstallDir"
SetCompressor /SOLID lzma

!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\PeerBanHelperNative"

!include "MUI2.nsh"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "TradChinese"

Section "PeerBanHelper"
    SetShellVarContext current
    SetOutPath "$INSTDIR"
    File /oname=peerbanhelper.exe "${APP_BINARY}"
    File /oname=peerbanhelper.ico "${APP_ICON}"
    File /oname=LICENSE "${APP_LICENSE}"
    SetOutPath "$INSTDIR\webui\dist"
    File /r "${WEBUI_DIR}\*"

    SetOutPath "$INSTDIR"
    CreateShortcut "$SMPROGRAMS\PeerBanHelper Native.lnk" "$INSTDIR\peerbanhelper.exe" 'run --tray --data-dir "$LOCALAPPDATA\PeerBanHelper"' "$INSTDIR\peerbanhelper.ico"
    WriteUninstaller "$INSTDIR\uninstall.exe"
    WriteRegStr HKCU "Software\PeerBanHelper\Native" "InstallDir" "$INSTDIR"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "PeerBanHelper Native"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayVersion" "${APP_VERSION}"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "Publisher" "PeerBanHelper contributors"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
    WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
    SetShellVarContext current
    Delete "$SMPROGRAMS\PeerBanHelper Native.lnk"
    Delete "$INSTDIR\peerbanhelper.exe"
    Delete "$INSTDIR\peerbanhelper.ico"
    Delete "$INSTDIR\LICENSE"
    Delete "$INSTDIR\uninstall.exe"
    !include "${UNINSTALL_WEBUI}"
    RMDir "$INSTDIR\webui\dist"
    RMDir "$INSTDIR\webui"
    RMDir "$INSTDIR"
    DeleteRegKey HKCU "Software\PeerBanHelper\Native"
    DeleteRegKey HKCU "${UNINSTALL_KEY}"
SectionEnd
