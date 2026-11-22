param(
    [Parameter(Mandatory)] [string] $Binary,
    [Parameter(Mandatory)] [string] $WebUi,
    [Parameter(Mandatory)] [string] $Output
)

$ErrorActionPreference = 'Stop'
$binaryPath = (Resolve-Path -LiteralPath $Binary).Path
$webUiPath = (Resolve-Path -LiteralPath $WebUi).Path
if (!(Test-Path -LiteralPath (Join-Path $webUiPath 'index.html') -PathType Leaf)) {
    throw 'WebUI directory must contain index.html.'
}
$packagePath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Output)
if (Test-Path -LiteralPath $packagePath) {
    throw "Package staging directory already exists at $packagePath."
}
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$staticPath = Join-Path $packagePath 'webui/dist'
New-Item -ItemType Directory -Path $staticPath | Out-Null
Copy-Item -LiteralPath $binaryPath -Destination $packagePath
Get-ChildItem -LiteralPath $webUiPath -Force | Copy-Item -Destination $staticPath -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination $packagePath
Copy-Item -LiteralPath (Join-Path $root 'LICENSE') -Destination $packagePath
