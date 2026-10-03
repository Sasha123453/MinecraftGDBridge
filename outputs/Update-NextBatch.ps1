param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$MinecraftSha256,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$GeometryDashSha256,
    [switch]$GDAlreadyInstalled
)
$ErrorActionPreference='Stop'
$outputs=[IO.Path]::GetFullPath($PSScriptRoot)
$mcSource=Join-Path $outputs 'bridge\minecraft\gd-minecraft-bridge-0.3.0.jar'
$gdSource=Join-Path $outputs 'bridge\gd\experiment.minecraft_bridge.geode'
$mcMods=Join-Path $outputs 'launcher\data\instances\gdbridge\.minecraft\mods'
$mcTarget=Join-Path $mcMods 'gd-minecraft-bridge-0.3.0.jar'
$gdTarget='D:\SteamLibrary\steamapps\common\Geometry Dash\geode\mods\experiment.minecraft_bridge.geode'
function AssertHash([string]$File,[string]$Expected){if(-not(Test-Path -LiteralPath $File -PathType Leaf) -or (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ne $Expected){throw "Package missing or hash mismatch: $File"}}
function AssertOutput([string]$File){if(-not [IO.Path]::GetFullPath($File).StartsWith($outputs+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Path outside outputs: $File"}}
if(-not $GDAlreadyInstalled -and (Get-Process -Name GeometryDash -ErrorAction SilentlyContinue)){throw 'Geometry Dash is still running.'}
$java=Join-Path (Split-Path -Parent $outputs) 'work\toolchains\jdk-17.0.20.1+1\bin\javaw.exe'
if(Get-Process -Name javaw -ErrorAction SilentlyContinue | Where-Object {$_.Path -eq $java -or $_.Path -like 'P:\work\toolchains\jdk-17.0.20.1+1\bin\javaw.exe'}){throw 'Minecraft is still running.'}
if(@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -eq 18471).Count){throw 'Minecraft bridge port18471 is still in use.'}
AssertHash $mcSource $MinecraftSha256;AssertHash $gdSource $GeometryDashSha256
if(-not(Test-Path -LiteralPath $mcMods -PathType Container)){throw 'Minecraft profile mods directory missing.'}
if(-not(Test-Path -LiteralPath (Split-Path -Parent $gdTarget) -PathType Container)){throw 'GD Geode mods directory missing.'}
if($GDAlreadyInstalled){AssertHash $gdTarget $GeometryDashSha256}
$backup=Join-Path $outputs ('backups\batch-0.3-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
AssertOutput $backup;AssertOutput $mcTarget
New-Item -ItemType Directory -Path $backup | Out-Null
$gdBackup=Join-Path $backup 'experiment.minecraft_bridge.geode'
if(-not $GDAlreadyInstalled -and (Test-Path -LiteralPath $gdTarget)){Copy-Item -LiteralPath $gdTarget -Destination $gdBackup}
$moved=@();$mcCopyStarted=$false;$gdCopyStarted=$false
try{
    # GD access is checked first. A denied external copy leaves Minecraft untouched.
    if(-not $GDAlreadyInstalled){$gdCopyStarted=$true;Copy-Item -LiteralPath $gdSource -Destination $gdTarget -Force;AssertHash $gdTarget $GeometryDashSha256}
    foreach($old in (Get-ChildItem -LiteralPath $mcMods -File -Filter 'gd-minecraft-bridge*.jar')){
        $oldBackup=Join-Path $backup $old.Name;AssertOutput $old.FullName;AssertOutput $oldBackup
        Move-Item -LiteralPath $old.FullName -Destination $oldBackup
        $moved += [pscustomobject]@{original=$old.FullName;backup=$oldBackup}
    }
    $mcCopyStarted=$true;Copy-Item -LiteralPath $mcSource -Destination $mcTarget;AssertHash $mcTarget $MinecraftSha256
    if(@(Get-ChildItem -LiteralPath $mcMods -File -Filter 'gd-minecraft-bridge*.jar').Count -ne 1){throw 'Minecraft must contain exactly one bridge JAR.'}
}catch{
    $failure=$_.Exception.Message;$rollbackErrors=@()
    if($mcCopyStarted -and (Test-Path -LiteralPath $mcTarget)){try{AssertOutput $mcTarget;Move-Item -LiteralPath $mcTarget -Destination (Join-Path $backup 'failed-gd-minecraft-bridge-0.3.0.jar')}catch{$rollbackErrors+=$_.Exception.Message}}
    foreach($old in $moved){try{Copy-Item -LiteralPath $old.backup -Destination $old.original -Force}catch{$rollbackErrors+=$_.Exception.Message}}
    if($gdCopyStarted -and (Test-Path -LiteralPath $gdBackup)){try{Copy-Item -LiteralPath $gdBackup -Destination $gdTarget -Force}catch{$rollbackErrors+=$_.Exception.Message}}
    throw "Batch install failed: $failure. Backup: $backup. Rollback errors: $($rollbackErrors -join ' | ')"
}
$receipt=[pscustomobject]@{installedAt=(Get-Date).ToString('o');batch='0.3';minecraftSha256=$MinecraftSha256.ToLowerInvariant();geometryDashSha256=$GeometryDashSha256.ToLowerInvariant();backup=$backup;gdExternalWriteSkipped=[bool]$GDAlreadyInstalled;runtimeVerified=$false;gamesLaunched=$false}
$receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputs 'batch-0.3-install.json') -Encoding utf8
$receipt | ConvertTo-Json
