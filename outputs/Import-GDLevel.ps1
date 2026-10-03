#Requires -Version 7.0
<#
.SYNOPSIS
Loads downloaded native GD level data and imports its first-region Minecraft markers.
.EXAMPLE
.\Import-GDLevel.ps1 -Path "$PSScriptRoot\bridge\levels\xo-easy-jukaras-source.json"
.EXAMPLE
.\Import-GDLevel.ps1 -Path 'C:\...\outputs\bridge\levels\downloaded-source.json' -EditableMinecraft
.NOTES
Default playback keeps the full original native level. Marker authoring currently covers
Minecraft x=0..512, y=67..100. EditableMinecraft explicitly requests a Minecraft export;
it is not a lossless full-level conversion. Requires GD bridge 0.3.4 and MC relay 0.3.5.
#>
param(
    [Parameter(Mandatory=$true)][string]$Path,
    [switch]$EditableMinecraft,
    [ValidateRange(1,45)][int]$WaitSeconds=45
)
$ErrorActionPreference='Stop'
if(-not [IO.Path]::IsPathFullyQualified($Path)){throw 'Path must be an absolute downloaded source JSON under outputs/bridge/levels.'}
$levelsRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'bridge/levels')).ProviderPath
$sourcePath=(Resolve-Path -LiteralPath $Path).ProviderPath
$prefix=$levelsRoot.TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar
if(-not $sourcePath.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Source JSON must be under outputs/bridge/levels.'}
$sourceFile=Get-Item -LiteralPath $sourcePath
if($sourceFile.PSIsContainer -or $sourceFile.Extension -ne '.json' -or $sourceFile.Length -gt 64MB){throw 'Source must be a JSON file no larger than 64 MiB.'}
$source=Get-Content -Raw -LiteralPath $sourcePath | ConvertFrom-Json
if($source -isnot [pscustomobject]){throw 'Source must contain a JSON object.'}
$levelId=if($source.PSObject.Properties['id']){[int]$source.id}elseif($source.PSObject.Properties['levelId']){[int]$source.levelId}else{0}
if($levelId -le 0 -or -not $source.PSObject.Properties['levelString'] -or -not [string]$source.levelString -or ([string]$source.levelString).Length -gt 4MB -or ([string]$source.levelString).Contains([char]0)){throw 'Source requires a positive native level ID and a compressed levelString up to 4 MiB.'}
$controlScript=Join-Path $PSScriptRoot 'Control-Bridge.ps1'
$profileRoot=Join-Path $PSScriptRoot 'launcher/data/instances/gdbridge/.minecraft'
$statusPath=Join-Path $profileRoot 'config/gdbridge/status.json'
$scenePath=Join-Path $profileRoot 'config/gdbridge/scene-GDBridge-XO.json'
if(-not(Test-Path -LiteralPath $statusPath -PathType Leaf)){throw 'Minecraft bridge status is unavailable. Start the isolated bridge first.'}
$initialStatus=Get-Content -Raw -LiteralPath $statusPath | ConvertFrom-Json
if(-not $initialStatus.worldLoaded -or [string]$initialStatus.world -ne 'GDBridge-XO'){throw 'Open the isolated GDBridge-XO world before importing.'}
$prepared=[ordered]@{}
foreach($property in $source.PSObject.Properties){$prepared[$property.Name]=$property.Value}
$prepared.id=$levelId;$prepared.downloadMusic=$false
$preparedPath=Join-Path $levelsRoot ('level-'+$levelId+'-import-'+[guid]::NewGuid().ToString('N')+'.json')
[IO.File]::WriteAllText($preparedPath,($prepared | ConvertTo-Json -Depth 40 -Compress),[Text.UTF8Encoding]::new($false))
$blueprintPath=Join-Path $levelsRoot ('level-'+$levelId+'.json')
$loadStarted=[datetime]::UtcNow
$loadAck=& $controlScript -Action load-level-data -PayloadJson (@{path=$preparedPath} | ConvertTo-Json -Compress)
$timer=[Diagnostics.Stopwatch]::StartNew()
$blueprint=$null
while($timer.Elapsed.TotalSeconds -lt $WaitSeconds){
    if(Test-Path -LiteralPath $blueprintPath -PathType Leaf){
        try{
            if((Get-Item -LiteralPath $blueprintPath).LastWriteTimeUtc -ge $loadStarted){
                $candidate=Get-Content -Raw -LiteralPath $blueprintPath | ConvertFrom-Json
                if([int]$candidate.levelId -eq $levelId -and $candidate.PSObject.Properties['objects']){$blueprint=$candidate;break}
            }
        }catch{}
    }
    Start-Sleep -Milliseconds 200
}
if(-not $blueprint){throw "No fresh native blueprint for level $levelId appeared within ${WaitSeconds}s. The previous blueprint was not imported."}
$importStarted=[datetime]::UtcNow
$importAck=$null
$timer.Restart()
$importStatus=$null
try{
    $importAck=& $controlScript -Action import-blueprint -PayloadJson (@{path=$blueprintPath} | ConvertTo-Json -Compress)
    while($timer.Elapsed.TotalSeconds -lt $WaitSeconds){
        if((Test-Path -LiteralPath $scenePath -PathType Leaf) -and (Test-Path -LiteralPath $statusPath -PathType Leaf)){
            try{
                $scene=Get-Content -Raw -LiteralPath $scenePath | ConvertFrom-Json
                $status=Get-Content -Raw -LiteralPath $statusPath | ConvertFrom-Json
                if((Get-Item -LiteralPath $scenePath).LastWriteTimeUtc -ge $importStarted -and [int]$scene.levelId -eq $levelId -and $status.editing -and [string]$status.message -match '^Imported \d+ Minecraft markers; \d+ outside range$'){$importStatus=$status;break}
            }catch{}
        }
        Start-Sleep -Milliseconds 200
    }
    if(-not $importStatus){throw "Marker import did not finish for level $levelId within ${WaitSeconds}s."}
}finally{
    # Import pauses native gameplay through WorldEditor.toggle; always release that pause.
    $playAck=& $controlScript -Action build-mode -PayloadJson '{"active":false}'
}
$exportAck=$null
if($EditableMinecraft){
    Write-Warning 'Minecraft export replaces the physical objects inside its first 512-block authoring region; it is not a lossless full-level conversion.'
    $exportAck=& $controlScript -Action export
}
[pscustomobject]@{
    levelId=$levelId
    name=$blueprint.name
    source=$sourcePath
    preparedSource=$preparedPath
    nativeBlueprint=$blueprintPath
    nativePhysicalObjects=@($blueprint.objects).Count
    markerImport=$importStatus.message
    editableMinecraftRequested=[bool]$EditableMinecraft
    nativeLoadAcknowledgement=$loadAck
    markerImportAcknowledgement=$importAck
    nativePlayAcknowledgement=$playAck
    exportAcknowledgement=$exportAck
    note=if($EditableMinecraft){'Limited Minecraft authoring export was explicitly queued. Native GD controls its physics.'}else{'Full original GD level remains active. Minecraft markers cover only the supported first region; no custom level export was sent.'}
} | ConvertTo-Json -Depth 12
