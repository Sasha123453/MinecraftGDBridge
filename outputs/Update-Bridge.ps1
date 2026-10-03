param([switch]$Launch)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath($PSScriptRoot)
$gdSource = Join-Path $workspace 'bridge\gd\experiment.minecraft_bridge.geode'
$mcSource = Join-Path $workspace 'bridge\minecraft\gd-minecraft-bridge-0.2.0.jar'
$gdTarget = 'D:\SteamLibrary\steamapps\common\Geometry Dash\geode\mods\experiment.minecraft_bridge.geode'
$mcMods = Join-Path $workspace 'launcher\data\instances\gdbridge\.minecraft\mods'
$gdHash = '02AE97D662578B8C1D015223B7407FE543D81F0E6A28DDACDCB856446B0112DB'
$mcHash = 'EC0F431080EF0CD502A2296D7DCEAB00EEB77E66EF72C89255C9EF11D56A3546'
function Assert-Hash([string]$File, [string]$Expected) {
    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) { throw "Missing package: $File" }
    if ((Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ne $Expected) { throw "Package hash mismatch: $File" }
}
function Assert-WorkspacePath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($workspace + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path outside outputs workspace: $full"
    }
}
if (Get-Process -Name GeometryDash -ErrorAction SilentlyContinue) { throw 'Close Geometry Dash before updating.' }
$listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
if (@($listeners | Where-Object { $_.Port -eq 18471 }).Count) { throw 'Close the bridge Minecraft game before updating (port18471 is in use).' }
Assert-Hash $gdSource $gdHash
Assert-Hash $mcSource $mcHash
if (-not (Test-Path -LiteralPath (Split-Path -Parent $gdTarget) -PathType Container)) { throw 'Installed GD Geode mods folder was not found.' }
if (-not (Test-Path -LiteralPath $mcMods -PathType Container)) { throw 'Minecraft bridge profile mods folder was not found.' }
$backup = Join-Path $workspace ('backups\bridge-update-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
Assert-WorkspacePath $backup
Assert-WorkspacePath $mcMods
New-Item -ItemType Directory -Path $backup | Out-Null
if (Test-Path -LiteralPath $gdTarget -PathType Leaf) {
    Copy-Item -LiteralPath $gdTarget -Destination (Join-Path $backup 'experiment.minecraft_bridge.geode')
}
$oldJars = @(Get-ChildItem -LiteralPath $mcMods -File -Filter 'gd-minecraft-bridge*.jar')
$movedJars = @()
$mcTarget = Join-Path $mcMods 'gd-minecraft-bridge-0.2.0.jar'
$mcCopyStarted = $false
try {
foreach ($old in $oldJars) {
    Assert-WorkspacePath $old.FullName
    $oldBackup = Join-Path $backup $old.Name
    Assert-WorkspacePath $oldBackup
    Move-Item -LiteralPath $old.FullName -Destination $oldBackup
    $movedJars += $old
}
    Copy-Item -LiteralPath $gdSource -Destination $gdTarget -Force
    Assert-WorkspacePath $mcTarget
    $mcCopyStarted = $true
    Copy-Item -LiteralPath $mcSource -Destination $mcTarget -Force
    Assert-Hash $gdTarget $gdHash
    Assert-Hash $mcTarget $mcHash
} catch {
    $gdBackup = Join-Path $backup 'experiment.minecraft_bridge.geode'
    if (Test-Path -LiteralPath $gdBackup) { Copy-Item -LiteralPath $gdBackup -Destination $gdTarget -Force }
    if ($mcCopyStarted -and (Test-Path -LiteralPath $mcTarget)) {
        $failedTarget = Join-Path $backup 'failed-gd-minecraft-bridge-0.2.0.jar'
        Assert-WorkspacePath $mcTarget; Assert-WorkspacePath $failedTarget
        Move-Item -LiteralPath $mcTarget -Destination $failedTarget
    }
    foreach ($old in $movedJars) { Copy-Item -LiteralPath (Join-Path $backup $old.Name) -Destination $old.FullName -Force }
    throw
}
Write-Host "Bridge packages updated. Previous versions: $backup"
Write-Host 'New authoring/native graphics features still require a live F7/F6 test.'
if ($Launch) { & (Join-Path $workspace 'Start-Bridge.ps1') }
