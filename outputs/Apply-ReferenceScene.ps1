#Requires -Version 7.0
<#
.SYNOPSIS
Stages and applies the numbered sunny reference plans plus the bounded grass route floor.
.NOTES
The controller must open GDBridge-Reference and import reference-auto-source first.
This helper never loads/imports/restarts a level, changes the camera, or drives the desktop.
GD is deliberately left paused after application. ACK alone is not world-operation proof.
#>
param([ValidateRange(60,3600)][int]$TotalTimeoutSeconds=600,[ValidateRange(1,10)][int]$FreshStatusSeconds=5)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Runtime-Paths.ps1')
$runtimeOutputs=Get-BridgeRuntimeOutputs
$runtimeLevels=[IO.Path]::GetFullPath((Join-Path $runtimeOutputs 'bridge/levels'))
$profile=Join-Path $runtimeOutputs 'launcher/data/instances/gdbridge/.minecraft'
$statusPath=Join-Path $profile 'config/gdbridge/status.json'
$scenePath=Join-Path $profile 'config/gdbridge/scene-GDBridge-Reference.json'
$sceneFolder=Join-Path $PSScriptRoot 'scenery/reference-scene'
$receiptPath=Join-Path $sceneFolder 'application.local.json'
$controlScript=Join-Path $PSScriptRoot 'Control-Bridge.ps1'
$timer=[Diagnostics.Stopwatch]::StartNew()
$receipt=[ordered]@{schema='gdbridge-reference-application-v1';startedUtc=[DateTime]::UtcNow.ToString('o');state='preparing';plans=@();completedPlans=0;normalGameplayZ0Touched=$false;routeFloorRecolored=$false;renderVerified=$false;geometryVerified=$false;autoVerified=$false;geometryDashLeftPaused=$false}
function Save-Receipt {
    $temp=$receiptPath+'.tmp';[IO.File]::WriteAllText($temp,($receipt | ConvertTo-Json -Depth 16),[Text.UTF8Encoding]::new($false));[IO.File]::Move($temp,$receiptPath,$true)
}
function Assert-Deadline {if($timer.Elapsed.TotalSeconds -ge $TotalTimeoutSeconds){throw "Reference scene exceeded ${TotalTimeoutSeconds}s deadline."}}
function Read-FreshStatus([datetime]$After=[datetime]::MinValue) {
    Assert-Deadline
    if(-not(Test-Path -LiteralPath $statusPath -PathType Leaf)){return $null}
    $stamp=(Get-Item -LiteralPath $statusPath).LastWriteTimeUtc
    if($stamp -lt $After -or ([DateTime]::UtcNow-$stamp).TotalSeconds -gt $FreshStatusSeconds){return $null}
    try{$status=Get-Content -Raw -LiteralPath $statusPath | ConvertFrom-Json}catch{$script:lastReadError=$_.Exception.Message;return $null}
    if($null -eq $status){return $null}
    foreach($field in @('worldLoaded','world','editing','worldOperationBusy','worldOperation','worldOperationProgress','worldOperationTotal','authoringRegion','message')){if($null -eq $status.PSObject.Properties[$field]){return $null}}
    if(-not $status.worldLoaded){return $null}
    if([string]$status.world -cne 'GDBridge-Reference'){throw 'Expected the isolated GDBridge-Reference world.'}
    if($status.worldOperation -eq 'failed' -or [string]$status.message -match '\bfailed:'){throw "World operation failed: $($status.message)"}
    [pscustomobject]@{data=$status;timestampUtc=$stamp}
}
function Wait-Ready([bool]$Idle=$false) {
    while($true){$current=Read-FreshStatus;if($current -and (-not $Idle -or -not $current.data.worldOperationBusy)){return $current};Start-Sleep -Milliseconds 100}
}
function Invoke-Bridge([string]$Action,[hashtable]$Payload=@{}) {
    Assert-Deadline;$remaining=[Math]::Floor($TotalTimeoutSeconds-$timer.Elapsed.TotalSeconds)
    if($remaining -lt 1){throw 'No deadline remaining for native request.'}
    $nonce=[guid]::NewGuid().ToString('N');$sentAt=[DateTime]::UtcNow
    $raw=& $controlScript -Action $Action -Nonce $nonce -PayloadJson ($Payload | ConvertTo-Json -Depth 10 -Compress) -WaitSeconds ([Math]::Min(10,$remaining))
    try{$reply=$raw | ConvertFrom-Json}catch{throw "Invalid acknowledgement for $Action"}
    if($reply.action -cne $Action -or $reply.nonce -cne $nonce -or $reply.acknowledgement.nonce -cne $nonce -or $reply.acknowledgement.action -cne $Action -or $reply.acknowledgement.error){throw "Incorrect or failed nonce acknowledgement for $Action"}
    [pscustomobject]@{reply=$reply;sentAt=$sentAt}
}
function Wait-Scenery([datetime]$After,[int]$ExpectedCells,[string]$Nonce) {
    $observedBusy=$false
    while($true){
        Assert-Deadline;$current=Read-FreshStatus -After $After
        if($current){
            if($current.data.worldOperationBusy){
                $observedBusy=$true
                if($current.data.worldOperation -cne 'Building Minecraft scenery'){throw "Concurrent world job: $($current.data.worldOperation)"}
                if([int]$current.data.worldOperationTotal -ne $ExpectedCells){throw 'World job total differs from the validated plan.'}
            }elseif([int]$current.data.worldOperationTotal -eq $ExpectedCells -and [int]$current.data.worldOperationProgress -eq $ExpectedCells -and [string]$current.data.message -match '^Scenery plan applied: \d+ cells; gameplay unchanged$'){
                # Equal-sized plans can leave the previous completion in status until this job starts.
                if(-not $observedBusy -and ($ExpectedCells -ge 1000 -or $current.timestampUtc -lt $After.AddMilliseconds(500))){Start-Sleep -Milliseconds 100;continue}
                $ackPath=Join-Path $profile 'config/gdbridge/control-status.json'
                try{$ack=Get-Content -Raw -LiteralPath $ackPath | ConvertFrom-Json}catch{$ack=$null}
                if($ack -and $ack.nonce -ceq $Nonce -and $ack.action -ceq 'scenery' -and $ack.result -ceq 'Scenery plan queued'){return $current}
                if($ack -and $ack.nonce -cne $Nonce){throw 'Another controller replaced the scenery nonce before completion.'}
            }
        }
        Start-Sleep -Milliseconds 100
    }
}
function Assert-Point($Point) {
    if(@($Point).Count -ne 3){throw 'Scenery coordinates require three integers.'}
    foreach($n in $Point){if($null -eq $n -or $n -is [string] -or $n -is [bool] -or [double]$n -ne [Math]::Truncate([double]$n) -or [double]::IsNaN([double]$n) -or [double]::IsInfinity([double]$n)){throw 'Scenery coordinates must be finite integers.'}}
    if($Point[1] -lt 50 -or $Point[1] -gt 255 -or $Point[2] -lt -16 -or $Point[2] -gt 24){throw 'Scenery coordinates exceed bounded world range.'}
}
function Measure-Plan($Plan,[bool]$Floor) {
    if($Plan.targetWorld -cne 'GDBridge-Reference' -or $Plan.cleanupOwnedBackWalls){throw 'Wrong scene target or unrelated legacy cleanup.'}
    $count=0L
    foreach($kind in @('boxes','terrainFloor')){
        if($null -eq $Plan.PSObject.Properties[$kind]){continue}
        foreach($box in @($Plan.$kind)){
            Assert-Point $box.from;Assert-Point $box.to
            if($kind -eq 'boxes' -and [Math]::Min($box.from[2],$box.to[2]) -le 0 -and [Math]::Max($box.from[2],$box.to[2]) -ge 0){throw 'Normal scenery intersects gameplay Z0.'}
            if($kind -eq 'terrainFloor'){
                if(-not $Floor -or [Math]::Max($box.from[1],$box.to[1]) -gt 66 -or $box.block -notin @('minecraft:stone','minecraft:deepslate','minecraft:cobblestone','minecraft:bedrock','minecraft:dirt','minecraft:grass_block')){throw 'Invalid bounded route-floor recolor.'}
            }
            $count+=([Math]::Abs($box.from[0]-$box.to[0])+1L)*([Math]::Abs($box.from[1]-$box.to[1])+1L)*([Math]::Abs($box.from[2]-$box.to[2])+1L)
        }
    }
    if($null -ne $Plan.PSObject.Properties['plants']){foreach($plant in @($Plan.plants)){Assert-Point $plant.at;if($plant.at[2] -eq 0){throw 'Plant touches gameplay Z0.'};$count++}}
    if($count -lt 1 -or $count -gt 16384 -or $count -ne [long]$Plan.cellBudget){throw "Incorrect or oversized cell budget: $count"}
    return $count
}
function Stage-File([string]$Filename,[string]$ExpectedHash) {
    if([IO.Path]::GetFileName($Filename) -cne $Filename -or -not $Filename.EndsWith('.json')){throw 'Unexpected source filename.'}
    $source=Join-Path $sceneFolder $Filename
    $hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if($hash -cne $ExpectedHash.ToLowerInvariant()){throw "Manifest hash mismatch: $Filename"}
    $data=Get-Content -Raw -LiteralPath $source | ConvertFrom-Json
    $destination=Join-Path $runtimeLevels $Filename
    Copy-Item -LiteralPath $source -Destination $destination -Force
    if((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -cne $hash){throw "Staged hash mismatch: $Filename"}
    [pscustomobject]@{file=$Filename;path=$destination;sha256=$hash;data=$data}
}
try{
    $manifest=Get-Content -Raw -LiteralPath (Join-Path $sceneFolder 'manifest.json') | ConvertFrom-Json
    $batchCount=@($manifest.applyOrder).Count
    if($manifest.schema -cne 'gdbridge-reference-scene-manifest-v1' -or $batchCount -lt 1 -or $batchCount -gt 16){throw 'Expected a bounded reference manifest.'}
    New-Item -ItemType Directory -Path $runtimeLevels -Force | Out-Null
    $source=Stage-File $manifest.source.file $manifest.source.sha256
    if($source.data.fixture -ne $true -or [int]$source.data.id -ne 900000104 -or -not $source.data.levelString){throw 'Expected original native reference source 900000104.'}
    $prepared=@();$normalTotal=0L
    for($i=1;$i -le $batchCount;$i++){
        $entry=$manifest.applyOrder[$i-1];if($entry.file -cne ('reference-scene-{0:D2}.json' -f $i)){throw 'Reference plan order mismatch.'}
        $staged=Stage-File $entry.file $entry.sha256
        if($staged.data.batchIndex -ne $i -or $staged.data.batchCount -ne $batchCount){throw 'Reference plan batch metadata mismatch.'}
        $count=Measure-Plan $staged.data $false;if($count -ne [long]$entry.cells){throw 'Manifest budget differs from plan.'}
        $normalTotal+=$count;$prepared+=[pscustomobject]@{index=$i;file=$staged.file;path=$staged.path;sha256=$staged.sha256;cells=$count;floor=$false;data=$staged.data}
    }
    if($normalTotal -ne [long]$manifest.totalRequestedCells){throw 'Reference total budget mismatch.'}
    $floor=Stage-File $manifest.optionalRouteFloor.file $manifest.optionalRouteFloor.sha256
    $floorCount=Measure-Plan $floor.data $true
    $prepared+=[pscustomobject]@{index=($batchCount+1);file=$floor.file;path=$floor.path;sha256=$floor.sha256;cells=$floorCount;floor=$true;data=$floor.data}
    $receipt.stagedSource=[ordered]@{file=$source.file;path=$source.path;sha256=$source.sha256;levelId=$source.data.id}
    $receipt.stagedPlans=@($prepared | Select-Object index,file,path,sha256,cells,floor)
    $receipt.totalRequestedCells=$normalTotal+$floorCount;$receipt.state='staged';Save-Receipt
    $ready=Wait-Ready
    if(-not(Test-Path -LiteralPath $scenePath -PathType Leaf)){throw 'Import the staged native reference source into GDBridge-Reference before applying.'}
    $savedScene=Get-Content -Raw -LiteralPath $scenePath | ConvertFrom-Json
    if([int]$savedScene.levelId -ne [int]$source.data.id -or [string]$savedScene.levelString -cne [string]$source.data.levelString){throw 'Imported Minecraft scene does not match the staged reference source.'}
    if($null -eq $ready.data.PSObject.Properties['staleAgeMs'] -or [double]$ready.data.staleAgeMs -gt 2000){throw 'Fresh native GD telemetry is required before applying scenery.'}
    $region=$ready.data.authoringRegion;$minX=[Math]::Max(-16,[int]$region.minX-16);$maxX=[Math]::Min(4095,[int]$region.maxX+16);$maxY=[Math]::Min(255,[Math]::Max(160,[int]$region.maxY+8))
    foreach($plan in $prepared){foreach($kind in @('boxes','terrainFloor','plants')){if($null -eq $plan.data.PSObject.Properties[$kind]){continue};$allowedMaxX=if($kind -eq 'terrainFloor'){$maxX}else{[Math]::Max($maxX,110)};foreach($element in @($plan.data.$kind)){foreach($point in $(if($kind -eq 'plants'){,@($element.at)}else{@($element.from,$element.to)})){if($point[0] -lt $minX -or $point[0] -gt $allowedMaxX -or $point[1] -gt $maxY){throw 'Imported authoring region does not cover the scene bounds.'}}}}}
    $pause=Invoke-Bridge 'gd-pause' @{active=$true};$receipt.geometryDashLeftPaused=$true;$receipt.pauseNonce=$pause.reply.nonce
    # Native resume closes only GameMenuScreen. Unrelated/loading screens are never dismissed.
    $null=Invoke-Bridge 'resume-world';$null=Wait-Ready $true
    $receipt.state='applying';Save-Receipt
    foreach($plan in $prepared){
        $null=Wait-Ready $true;$ack=Invoke-Bridge 'scenery' @{path=$plan.path}
        if($ack.reply.acknowledgement.result -cne 'Scenery plan queued'){throw 'Scenery command was not queued.'}
        $finished=Wait-Scenery $ack.sentAt ([int]$plan.cells) $ack.reply.nonce
        $receipt.plans+=[ordered]@{index=$plan.index;file=$plan.file;sha256=$plan.sha256;requestedCells=$plan.cells;nonce=$ack.reply.nonce;statusTimestampUtc=$finished.timestampUtc.ToString('o');result=$finished.data.message;routeFloor=$plan.floor}
        $receipt.completedPlans=$plan.index;if($plan.floor){$receipt.routeFloorRecolored=$true};Save-Receipt
        Write-Host ('Reference scene {0}/{1}: processed {2} cells' -f $plan.index,$prepared.Count,$plan.cells)
    }
    $receipt.state='complete';$receipt.finishedUtc=[DateTime]::UtcNow.ToString('o');$receipt.elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,1);Save-Receipt
    [pscustomobject]@{state='complete';appliedPlans=$prepared.Count;receipt=$receiptPath;stagedSource=$source.path;geometryDashLeftPaused=$true;renderVerified=$false} | ConvertTo-Json
}catch{
    $receipt.state='failed';$receipt.error=$_.Exception.Message;if($script:lastReadError){$receipt.lastStatusReadError=$script:lastReadError}
    $receipt.finishedUtc=[DateTime]::UtcNow.ToString('o');try{Save-Receipt}catch{}
    throw
}
