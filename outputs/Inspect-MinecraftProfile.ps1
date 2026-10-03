$ErrorActionPreference='Stop'
$mcProfilePath=Join-Path $PSScriptRoot 'launcher\data\instances\gdbridge\.minecraft'
function Properties([string]$File){
    $values=@{}
    if(Test-Path -LiteralPath $File -PathType Leaf){foreach($line in (Get-Content -LiteralPath $File)){if($line -match '^\s*([^#!\s][^=]*)=(.*)$'){$values[$matches[1].Trim()]=$matches[2].Trim()}}}
    return $values
}
$iris=Properties (Join-Path $mcProfilePath 'config\iris.properties')
$pack=if($iris.ContainsKey('shaderPack')){$iris.shaderPack}else{$null}
$shaderOptions=if($pack){Properties (Join-Path $mcProfilePath ('shaderpacks\'+$pack+'.txt'))}else{@{}}
$statusPath=Join-Path $mcProfilePath 'config\gdbridge\status.json'
$status=if(Test-Path -LiteralPath $statusPath){Get-Content -Raw -LiteralPath $statusPath | ConvertFrom-Json}else{$null}
$screenshots=Join-Path $mcProfilePath 'screenshots'
$latestScreenshot=if(Test-Path -LiteralPath $screenshots){Get-ChildItem -LiteralPath $screenshots -File -Filter '*.png' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 FullName,LastWriteTime,Length}else{$null}
$latestLog=Join-Path $mcProfilePath 'logs\latest.log'
$shaderLog=if(Test-Path -LiteralPath $latestLog){@(Get-Content -LiteralPath $latestLog -Tail 200 | Where-Object {$_ -match '(?i)iris|shader.*pack|shader.*error|failed.*shader|complementary' } | Select-Object -Last 12)}else{@()}
[pscustomobject]@{
    checkedAt=(Get-Date).ToString('o')
    processSnapshot=@(Get-Process -Name GeometryDash,javaw,prismlauncher -ErrorAction SilentlyContinue | Select-Object ProcessName,Id,Path)
    profile=$mcProfilePath
    mods=@(Get-ChildItem -LiteralPath (Join-Path $mcProfilePath 'mods') -File | Select-Object Name,Length)
    shaderSelected=$pack
    shaderEnabledInConfiguration=($iris.enableShaders -eq 'true')
    shaderPackPresent=($pack -and (Test-Path -LiteralPath (Join-Path $mcProfilePath ('shaderpacks\'+$pack))))
    shaderOptions=$shaderOptions
    gdBridgeStatus=$status
    latestMinecraftF2Screenshot=$latestScreenshot
    recentShaderLog=$shaderLog
    note='Configuration and logs are passive evidence; shader visuals require a real Minecraft screenshot.'
} | ConvertTo-Json -Depth 7
