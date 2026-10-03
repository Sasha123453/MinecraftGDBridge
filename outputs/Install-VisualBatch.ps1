param(
    [Parameter(Mandatory=$true)][string]$MinecraftPackage,
    [ValidateSet('gd-visual-next','gd-visibility-next','gd-import-next','gd-baseline-next','gd-safe-import-next','gd-audit-next','gd-hazard-visuals-next','gd-geometry-metadata-next','gd-coordinate-fix-next','gd-transition-next','gd-world-authority-next')][string]$GeometryDashStage='gd-coordinate-fix-next',
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$MinecraftSha256,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$GeometryDashSha256
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Runtime-Paths.ps1')
$taskOutputs=[IO.Path]::GetFullPath($PSScriptRoot)
$runtimeOutputs=[IO.Path]::GetFullPath((Get-BridgeRuntimeOutputs))
$mcSource=[IO.Path]::GetFullPath((Join-Path $taskOutputs ('bridge\minecraft\'+$MinecraftPackage)))
$gdSource=Join-Path $taskOutputs ('bridge\'+$GeometryDashStage+'\experiment.minecraft_bridge.geode')
$mcMods=Join-Path $runtimeOutputs 'launcher\data\instances\gdbridge\.minecraft\mods'
$mcTarget=Join-Path $mcMods ([IO.Path]::GetFileName($MinecraftPackage))
$gdTarget='D:\SteamLibrary\steamapps\common\Geometry Dash\geode\mods\experiment.minecraft_bridge.geode'
function AssertHash([string]$File,[string]$Expected) {
    if(-not(Test-Path -LiteralPath $File -PathType Leaf) -or (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ne $Expected){throw "Package missing or hash mismatch: $File"}
}
function AssertTaskOutput([string]$File) {
    $resolvedFile=[IO.Path]::GetFullPath($File)
    if(-not $resolvedFile.StartsWith($taskOutputs+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -and -not $resolvedFile.StartsWith($runtimeOutputs+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Path outside project/runtime outputs: $File"}
}
AssertTaskOutput $mcSource; AssertTaskOutput $mcTarget; AssertTaskOutput $gdSource
if([IO.Path]::GetExtension($mcSource) -ne '.jar'){throw 'Minecraft package must be a JAR.'}
if(Get-Process -Name GeometryDash -ErrorAction SilentlyContinue){throw 'Close Geometry Dash before installation.'}
if(@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -eq 18471).Count){throw 'Minecraft bridge is still running.'}
$javaBinary=Join-Path (Get-BridgeRuntimeRoot) 'work\toolchains\jdk-17.0.20.1+1\bin\javaw.exe'
if(Get-Process -Name javaw -ErrorAction SilentlyContinue | Where-Object {-not $_.Path -or $_.Path -eq $javaBinary -or $_.Path -like 'P:\work\toolchains\jdk-17.0.20.1+1\bin\javaw.exe'}){throw 'Minecraft is running, or its process path could not be verified.'}
AssertHash $mcSource $MinecraftSha256; AssertHash $gdSource $GeometryDashSha256
if(-not(Test-Path -LiteralPath $mcMods -PathType Container) -or -not(Test-Path -LiteralPath (Split-Path -Parent $gdTarget) -PathType Container)){throw 'Installed game directories are missing.'}
$batchBackup=Join-Path $taskOutputs ('backups\visual-batch-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
AssertTaskOutput $batchBackup
New-Item -ItemType Directory -Path $batchBackup | Out-Null
$oldMc=@(Get-ChildItem -LiteralPath $mcMods -File -Filter 'gd-minecraft-bridge*.jar')
foreach($old in $oldMc){Copy-Item -LiteralPath $old.FullName -Destination (Join-Path $batchBackup $old.Name)}
Copy-Item -LiteralPath $gdTarget -Destination (Join-Path $batchBackup 'experiment.minecraft_bridge.geode')
$mcWritten=$false
try {
    Copy-Item -LiteralPath $gdSource -Destination $gdTarget -Force
    AssertHash $gdTarget $GeometryDashSha256
    foreach($old in $oldMc){AssertTaskOutput $old.FullName; Move-Item -LiteralPath $old.FullName -Destination (Join-Path $batchBackup ('installed-'+$old.Name))}
    $mcWritten=$true; Copy-Item -LiteralPath $mcSource -Destination $mcTarget -Force
    AssertHash $mcTarget $MinecraftSha256
    if(@(Get-ChildItem -LiteralPath $mcMods -File -Filter 'gd-minecraft-bridge*.jar').Count -ne 1){throw 'Expected exactly one Minecraft bridge JAR.'}
} catch {
    $batchFailure=$_.Exception.Message
    if($mcWritten -and (Test-Path -LiteralPath $mcTarget)){Move-Item -LiteralPath $mcTarget -Destination (Join-Path $batchBackup 'failed-minecraft.jar')}
    foreach($old in $oldMc){Copy-Item -LiteralPath (Join-Path $batchBackup $old.Name) -Destination $old.FullName -Force}
    Copy-Item -LiteralPath (Join-Path $batchBackup 'experiment.minecraft_bridge.geode') -Destination $gdTarget -Force
    throw "Visual batch failed and previous packages restored: $batchFailure. Backup: $batchBackup"
}
$batchReceipt=[pscustomobject]@{installedAt=(Get-Date).ToString('o');minecraftPackage=$MinecraftPackage;minecraftSha256=$MinecraftSha256;geometryDashStage=$GeometryDashStage;geometryDashSha256=$GeometryDashSha256;backup=$batchBackup;runtimeVerified=$false}
$batchReceipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutputs 'visual-batch-install.json') -Encoding utf8
$batchReceipt | ConvertTo-Json
