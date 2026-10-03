# Passive readiness snapshot. This script never starts, stops or controls either game.
param([ValidatePattern('^\d+\.\d+\.\d+$')][string]$MinecraftVersion='0.3.3',[ValidatePattern('^\d+\.\d+\.\d+$')][string]$GeometryDashVersion='0.3.2',[string]$GeometryDashStageRelative='bridge\gd-visual-next\experiment.minecraft_bridge.geode')
$ErrorActionPreference='Stop'
$outputs=[IO.Path]::GetFullPath($PSScriptRoot)
$mcProfilePath=Join-Path $outputs 'launcher\data\instances\gdbridge\.minecraft'
$nextJar=Join-Path $outputs ('bridge\minecraft\gd-minecraft-bridge-'+$MinecraftVersion+'.jar')
$gdStage=Join-Path $outputs $GeometryDashStageRelative
$gdInstalled='D:\SteamLibrary\steamapps\common\Geometry Dash\geode\mods\experiment.minecraft_bridge.geode'
function Fingerprint([string]$File){
    if(-not(Test-Path -LiteralPath $File -PathType Leaf)){return $null}
    $info=Get-Item -LiteralPath $File
    return [pscustomobject]@{path=$info.FullName;bytes=$info.Length;modified=$info.LastWriteTime.ToString('o');sha256=(Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$snapshot=& (Join-Path $outputs 'Inspect-MinecraftProfile.ps1') | ConvertFrom-Json
$mcStage=Fingerprint $nextJar
$gdStageInfo=Fingerprint $gdStage
$gdInstalledInfo=Fingerprint $gdInstalled
$bridgeJars=@(Get-ChildItem -LiteralPath (Join-Path $mcProfilePath 'mods') -File -Filter 'gd-minecraft-bridge*.jar')
$bridgeInstalled=if($bridgeJars.Count -eq 1){Fingerprint $bridgeJars[0].FullName}else{$null}
$loader=Get-Content -Raw -LiteralPath (Join-Path $outputs 'launcher\data\instances\gdbridge\mmc-pack.json') | ConvertFrom-Json
$shaderFiles=foreach($package in (Get-Content -Raw -LiteralPath (Join-Path $outputs 'shaders\manifest.json') | ConvertFrom-Json)){
    $folder=if($package.file.EndsWith('.jar')){'mods'}else{'shaderpacks'}
    $file=Join-Path $mcProfilePath ($folder+'\'+$package.file)
    $present=Test-Path -LiteralPath $file -PathType Leaf
    $hash=if($present){(Get-FileHash -LiteralPath $file -Algorithm SHA1).Hash.ToLowerInvariant()}else{$null}
    [pscustomobject]@{file=$package.file;version=$package.version;installed=$present;sha1Matches=($hash -eq $package.sha1)}
}
$statusFile=Join-Path $mcProfilePath 'config\gdbridge\status.json'
$statusAge=if(Test-Path -LiteralPath $statusFile){((Get-Date)-(Get-Item -LiteralPath $statusFile).LastWriteTime).TotalMilliseconds}else{$null}
$nextMCInstalled=($mcStage -and $bridgeInstalled -and $mcStage.sha256 -eq $bridgeInstalled.sha256)
$nextGDInstalled=($gdStageInfo -and $gdInstalledInfo -and $gdStageInfo.sha256 -eq $gdInstalledInfo.sha256)
[pscustomobject]@{
    checkedAt=(Get-Date).ToString('o')
    targetBatch=('Minecraft '+$MinecraftVersion+' + GD '+$GeometryDashVersion)
    stagedMinecraft=$mcStage
    stagedGeometryDash=$gdStageInfo
    installedMinecraft=$bridgeInstalled
    installedGeometryDash=$gdInstalledInfo
    installedBridgeJarCount=$bridgeJars.Count
    nextMinecraftInstalled=[bool]$nextMCInstalled
    nextGeometryDashInstalled=[bool]$nextGDInstalled
    runtime0_3Verified=$false
    runtimeVerified=$false
    loaderComponents=$loader.components
    shaderFiles=$shaderFiles
    shaderSelected=$snapshot.shaderSelected
    shaderEnabledInConfiguration=$snapshot.shaderEnabledInConfiguration
    shaderPackPresent=$snapshot.shaderPackPresent
    shaderOptions=$snapshot.shaderOptions
    processSnapshot=$snapshot.processSnapshot
    bridgePort18471Listening=(@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -eq 18471).Count -gt 0)
    statusFileAgeMs=$statusAge
    lastStoredStatus=$snapshot.gdBridgeStatus
    latestMinecraftScreenshot=$snapshot.latestMinecraftF2Screenshot
    recentShaderLog=$snapshot.recentShaderLog
    note='Stored status can survive game exit. Installation/configuration checks do not verify 0.3 gameplay or shader visuals.'
} | ConvertTo-Json -Depth 9
