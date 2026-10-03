#Requires -Version 7.0
<#
.SYNOPSIS
Loads native GD level data and imports its Minecraft geometry within the derived region.
.EXAMPLE
.\Import-GDLevel.ps1 -Path "$PSScriptRoot\bridge\levels\xo-easy-jukaras-source.json"
.EXAMPLE
.\Import-GDLevel.ps1 -Path 'C:\...\outputs\bridge\levels\downloaded-source.json' -EditableMinecraft
.NOTES
Default playback keeps the original native level. EditableMinecraft requests a server-world
export afterward. Exact geometry support is version-dependent and must be verified separately.
#>
param(
    [Parameter(Mandatory=$true)][string]$Path,
    [switch]$EditableMinecraft,
    [ValidateRange(1,180)][int]$WaitSeconds=120
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Runtime-Paths.ps1')
if(-not [IO.Path]::IsPathFullyQualified($Path)){throw 'Path must be an absolute downloaded source JSON under outputs/bridge/levels.'}
$levelsRoot=(Resolve-Path -LiteralPath (Join-Path (Get-BridgeRuntimeOutputs) 'bridge/levels')).ProviderPath
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
$profileRoot=Join-Path (Get-BridgeRuntimeOutputs) 'launcher/data/instances/gdbridge/.minecraft'
$statusPath=Join-Path $profileRoot 'config/gdbridge/status.json'
if(-not(Test-Path -LiteralPath $statusPath -PathType Leaf)){throw 'Minecraft bridge status is unavailable. Start the isolated bridge first.'}
$initialStatus=Get-Content -Raw -LiteralPath $statusPath | ConvertFrom-Json
if(-not $initialStatus.worldLoaded -or [string]$initialStatus.world -notin @('GDBridge-XO','GDBridge-GeometryTests','GDBridge-Reference')){throw 'Open an isolated bridge course world before importing.'}
$scenePath=Join-Path $profileRoot ('config/gdbridge/scene-'+[string]$initialStatus.world+'.json')
$readyTimer=[Diagnostics.Stopwatch]::StartNew()
while($initialStatus.worldOperationBusy -and $readyTimer.Elapsed.TotalSeconds -lt $WaitSeconds){
    Start-Sleep -Milliseconds 200
    $initialStatus=Get-Content -Raw -LiteralPath $statusPath | ConvertFrom-Json
}
if($initialStatus.worldOperationBusy){throw 'A previous Minecraft world operation did not finish; source import was not started.'}
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
                if((Get-Item -LiteralPath $scenePath).LastWriteTimeUtc -ge $importStarted -and [int]$scene.levelId -eq $levelId -and $status.editing -and -not $status.worldOperationBusy -and [string]$status.message -match '^Imported \d+ (cells;|Minecraft markers;|exact pieces in)'){$importStatus=$status;break}
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
    Write-Host 'Compiling actual Minecraft world pieces; exact transfer and native physics are verified separately.'
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
    note=if($EditableMinecraft){'Minecraft authoring export was explicitly queued within the derived GameplayRegion. Native GD controls its physics; queued export completion must be verified separately.'}else{'Original GD level remains active. Minecraft cells were imported within the derived GameplayRegion; no world-authority export was sent. Voxel conversion changes the course.'}
} | ConvertTo-Json -Depth 12
