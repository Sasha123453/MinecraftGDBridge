param([switch]$Disabled)
$ErrorActionPreference='Stop'
$outputs=[IO.Path]::GetFullPath($PSScriptRoot)
$mcProfilePath=Join-Path $outputs 'launcher\data\instances\gdbridge\.minecraft'
$portableJava=Join-Path (Split-Path -Parent $outputs) 'work\toolchains\jdk-17.0.20.1+1\bin\javaw.exe'
if(Get-Process -Name javaw -ErrorAction SilentlyContinue | Where-Object {$_.Path -eq $portableJava -or $_.Path -like 'P:\work\toolchains\jdk-17.0.20.1+1\bin\javaw.exe'}){throw 'Close the bridge Minecraft game before installing shader mods.'}
if(@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -eq 18471).Count){throw 'Minecraft bridge is still listening on port18471.'}
$manifest=Get-Content -Raw -LiteralPath (Join-Path $outputs 'shaders\manifest.json') | ConvertFrom-Json
$plan=@()
foreach($package in $manifest){
    $source=Join-Path $outputs ('shaders\staged\'+$package.file)
    if((Get-FileHash -LiteralPath $source -Algorithm SHA1).Hash -ne $package.sha1){throw "Shader package hash mismatch: $source"}
    $folder=if($package.file.EndsWith('.jar')){'mods'}else{'shaderpacks'}
    $plan += [pscustomobject]@{source=$source;target=(Join-Path $mcProfilePath ($folder+'\'+$package.file))}
}
foreach($relative in @('config\iris.properties','shaderpacks\ComplementaryReimagined_r5.9.3.zip.txt')){
    $plan += [pscustomobject]@{source=(Join-Path $outputs ('shaders\profile\'+$relative));target=(Join-Path $mcProfilePath $relative)}
}
foreach($item in $plan){if(-not(Test-Path -LiteralPath $item.source -PathType Leaf)){throw "Missing shader configuration: $($item.source)"}}
$backup=Join-Path $outputs ('backups\shaders-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
$written=@();$retired=@()
try {
    foreach($item in $plan){
        $fullTarget=[IO.Path]::GetFullPath($item.target)
        if(-not $fullTarget.StartsWith($mcProfilePath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Target outside isolated Minecraft profile: $($item.target)"}
        $relative=$fullTarget.Substring($mcProfilePath.Length+1)
        $prior=Join-Path $backup $relative
        if(Test-Path -LiteralPath $item.target -PathType Leaf){New-Item -ItemType Directory -Path (Split-Path -Parent $prior) -Force | Out-Null;Copy-Item -LiteralPath $item.target -Destination $prior}
        New-Item -ItemType Directory -Path (Split-Path -Parent $item.target) -Force | Out-Null
        $written+=[pscustomobject]@{target=$item.target;backup=$prior}
        Copy-Item -LiteralPath $item.source -Destination $item.target -Force
    }
    if($Disabled){$iris=Join-Path $mcProfilePath 'config\iris.properties';[IO.File]::WriteAllText($iris,(Get-Content -Raw -LiteralPath $iris).Replace('enableShaders=true','enableShaders=false'),[Text.UTF8Encoding]::new($false))}
    $keep=@($manifest | Where-Object {$_.file.EndsWith('.jar')} | ForEach-Object file)
    foreach($old in (Get-ChildItem -LiteralPath (Join-Path $mcProfilePath 'mods') -File | Where-Object {($_.Name -like 'iris-*.jar' -or $_.Name -like 'sodium-*.jar') -and $_.Name -notin $keep})){
        $target=Join-Path $backup ('mods\'+$old.Name)
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Move-Item -LiteralPath $old.FullName -Destination $target
        $retired+=[pscustomobject]@{target=$old.FullName;backup=$target}
    }
    foreach($package in $manifest){$folder=if($package.file.EndsWith('.jar')){'mods'}else{'shaderpacks'};$file=Join-Path $mcProfilePath ($folder+'\'+$package.file);if((Get-FileHash -LiteralPath $file -Algorithm SHA1).Hash -ne $package.sha1){throw "Installed shader hash mismatch: $file"}}
}catch{
    foreach($item in $written){if(Test-Path -LiteralPath $item.backup){Copy-Item -LiteralPath $item.backup -Destination $item.target -Force}else{Remove-Item -LiteralPath $item.target -Force -ErrorAction SilentlyContinue}}
    foreach($old in $retired){Copy-Item -LiteralPath $old.backup -Destination $old.target -Force}
    throw
}
Write-Host "Iris + Sodium + Complementary MEDIUM installed. Shaders enabled: $(-not $Disabled). Backup: $backup"
Write-Host 'Minecraft was not launched. Verify shader compilation, native GD visuals and latency after the next launch.'
