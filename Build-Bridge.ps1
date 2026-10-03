param([ValidateSet('all','minecraft','gd')][string]$Target='all')
$ErrorActionPreference='Stop'
$settings=Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '.local.json') | ConvertFrom-Json
$runtimeRoot=[IO.Path]::GetFullPath($settings.runtimeRoot)
$toolchainRoot=Join-Path $runtimeRoot 'work/toolchains'
if($Target -in @('all','minecraft')) {
    $env:JAVA_HOME=Join-Path $toolchainRoot 'jdk-17.0.20.1+1'
    & (Join-Path $toolchainRoot 'gradle-8.8/bin/gradle.bat') --offline --no-daemon --gradle-user-home (Join-Path $runtimeRoot 'work/gradle-cache') --project-cache-dir (Join-Path $PSScriptRoot 'work/mc-project-cache') -p (Join-Path $PSScriptRoot 'outputs/bridge/minecraft') "-PgdbridgeBuildDirectory=$(Join-Path $runtimeRoot 'work/minecraft-build')" build
    if($LASTEXITCODE -ne 0){throw 'Minecraft build failed'}
    $mcVersion=(Select-String -LiteralPath (Join-Path $PSScriptRoot 'outputs/bridge/minecraft/build.gradle') -Pattern "^version = '([^']+)'$").Matches[0].Groups[1].Value
    $dist=Join-Path $PSScriptRoot 'outputs/bridge/minecraft/dist'
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $runtimeRoot "work/minecraft-build/libs/gd-minecraft-bridge-$mcVersion.jar") -Destination $dist -Force
}
if($Target -in @('all','gd')) {
    $python=Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
    & $python (Join-Path $PSScriptRoot 'outputs/bridge/gd-world-authority-next/build.py')
    if($LASTEXITCODE -ne 0){throw 'Geometry Dash build failed'}
}
