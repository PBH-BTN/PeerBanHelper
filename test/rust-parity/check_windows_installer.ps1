param(
    [Parameter(Mandatory)] [string] $Installer
)

$ErrorActionPreference = 'Stop'
$installerFile = (Resolve-Path -LiteralPath $Installer).Path
$productKey = 'HKCU:\Software\PeerBanHelper\Native'
$uninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\PeerBanHelperNative'
$startMenu = [Environment]::GetFolderPath('Programs')
$shortcut = Join-Path $startMenu 'PeerBanHelper Native.lnk'
if ((Test-Path -LiteralPath $productKey) -or (Test-Path -LiteralPath $uninstallKey) -or (Test-Path -LiteralPath $shortcut)) {
    throw 'The native product is already registered for this user. Run this check under a fresh user profile.'
}

$installDir = Join-Path ([System.IO.Path]::GetTempPath()) ('pbh-native-install-' + [Guid]::NewGuid().ToString('N'))
$uninstaller = Join-Path $installDir 'uninstall.exe'
try {
    # NSIS requires /D as the last, unquoted parameter, including when the directory contains spaces.
    $process = Start-Process -FilePath $installerFile -ArgumentList "/S /D=$installDir" -WindowStyle Hidden -PassThru
    if (-not $process.WaitForExit(60000) -or $process.ExitCode -ne 0) {
        throw 'The silent installer did not complete successfully.'
    }
    if ((Get-ItemPropertyValue -LiteralPath $productKey -Name InstallDir) -ne $installDir -or
        -not (Test-Path -LiteralPath $shortcut -PathType Leaf)) {
        throw 'The installer did not register the preview or create its shortcut.'
    }
    $env:PBH_DISABLE_NETWORK_PROBES = 'true'
    python -B "$PSScriptRoot/check_native_startup.py" "$installDir/peerbanhelper.exe" "$installDir/webui/dist"
    if ($LASTEXITCODE -ne 0) { throw 'The installed native service did not complete setup and a scan.' }
}
finally {
    if (Test-Path -LiteralPath $uninstaller -PathType Leaf) {
        $process = Start-Process -FilePath $uninstaller -ArgumentList '/S' -WindowStyle Hidden -PassThru
        if (-not $process.WaitForExit(30000) -or $process.ExitCode -ne 0) {
            throw 'The silent uninstaller did not complete successfully.'
        }
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        while ((Test-Path -LiteralPath $installDir) -and [DateTime]::UtcNow -lt $deadline) {
            Start-Sleep -Milliseconds 100
        }
        if ((Test-Path -LiteralPath $installDir) -or (Test-Path -LiteralPath $shortcut) -or
            (Test-Path -LiteralPath $productKey) -or (Test-Path -LiteralPath $uninstallKey)) {
            throw 'The uninstaller left registered or installed files.'
        }
    }
}

Write-Output 'Windows installer registration, installed service and uninstall passed.'
