param(
    [ValidateRange(60,3600)][int]$TotalTimeoutSeconds=600,
    [ValidateRange(1,10)][int]$FreshStatusSeconds=5
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Runtime-Paths.ps1')
$runtimeOutputs=Get-BridgeRuntimeOutputs
$runtimeLevels=[IO.Path]::GetFullPath((Join-Path $runtimeOutputs 'bridge/levels'))
$statusPath=Join-Path $runtimeOutputs 'launcher/data/instances/gdbridge/.minecraft/config/gdbridge/status.json'
$sceneFolder=Join-Path $PSScriptRoot 'scenery/xo-full-scene'
$receiptPath=Join-Path $PSScriptRoot 'scenery/application.local.json'
$controlScript=Join-Path $PSScriptRoot 'Control-Bridge.ps1'
$timer=[Diagnostics.Stopwatch]::StartNew()
$receipt=[ordered]@{schema='gdbridge-xo-scene-application-v1';startedUtc=[DateTime]::UtcNow.ToString('o');state='preparing';plans=@();completedPlans=0;gameplayChanged=$false;renderVerified=$false;geometryDashLeftPaused=$true}

function Save-Receipt {
    $parent=Split-Path -Parent $receiptPath
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    $temporary=$receiptPath+'.tmp'
    [IO.File]::WriteAllText($temporary,($receipt | ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporary,$receiptPath,$true)
}
function Assert-Deadline {
    if($timer.Elapsed.TotalSeconds -ge $TotalTimeoutSeconds){throw "Scene application exceeded the ${TotalTimeoutSeconds}s total deadline."}
}
function Read-FreshStatus([datetime]$After=[datetime]::MinValue) {
    Assert-Deadline
    if(-not(Test-Path -LiteralPath $statusPath -PathType Leaf)){return $null}
    $stamp=(Get-Item -LiteralPath $statusPath).LastWriteTimeUtc
    if($stamp -lt $After -or ([DateTime]::UtcNow-$stamp).TotalSeconds -gt $FreshStatusSeconds){return $null}
    # Minecraft replaces this small JSON file frequently. An incomplete write
    # is retried; persistent invalid JSON is reported at the total deadline.
    try{$status=Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json}catch{$script:lastStatusReadError=$_.Exception.Message;return $null}
    # A concurrent truncation can parse as null without throwing. Likewise,
    # incomplete records must be retried rather than treated as another world.
    if($null -eq $status){return $null}
    foreach($field in @('worldLoaded','world','worldOperationBusy')){if(-not $status.PSObject.Properties[$field]){return $null}}
    if(-not $status.worldLoaded -or $status.world -cne 'GDBridge-XO'){throw 'Open the isolated GDBridge-XO world before applying scenery.'}
    if($status.worldOperation -eq 'failed' -or [string]$status.message -match '\bfailed:'){throw "Minecraft world operation failed: $($status.message)"}
    [pscustomobject]@{data=$status;timestampUtc=$stamp}
}
function Wait-Idle {
    while($true){Assert-Deadline;$current=Read-FreshStatus;if($current -and -not $current.data.worldOperationBusy){return $current};Start-Sleep -Milliseconds 100}
}
function Wait-ReadyWorld {
    while($true){Assert-Deadline;$current=Read-FreshStatus;if($current){return $current};Start-Sleep -Milliseconds 100}
}
function Invoke-Bridge([string]$Action,[hashtable]$Payload=@{}) {
    Assert-Deadline
    $remaining=[Math]::Floor($TotalTimeoutSeconds-$timer.Elapsed.TotalSeconds)
    if($remaining -lt 1){throw 'Scene application deadline reached before sending the next native request.'}
    $wait=[Math]::Min(10,$remaining)
    $raw=& $controlScript -Action $Action -PayloadJson ($Payload | ConvertTo-Json -Depth 10 -Compress) -WaitSeconds $wait
    try{$reply=$raw | ConvertFrom-Json}catch{throw "Invalid acknowledgement for ${Action}: $raw"}
    if($reply.action -cne $Action -or -not $reply.acknowledgement){throw "Missing native acknowledgement for $Action"}
    $reply
}
function Wait-Scenery([datetime]$After,[int]$ExpectedCells) {
    $seenBusy=$false
    while($true){
        Assert-Deadline;$current=Read-FreshStatus -After $After
        if($current){
            if($current.data.worldOperationBusy){
                if($current.data.worldOperation -cne 'Building Minecraft scenery'){throw "Unexpected concurrent operation: $($current.data.worldOperation)"}
                if([int]$current.data.worldOperationTotal -ne $ExpectedCells){throw "Scenery operation total differs from validated plan: $($current.data.worldOperationTotal) versus $ExpectedCells"}
                $seenBusy=$true
            }elseif($seenBusy){
                if([int]$current.data.worldOperationProgress -ne $ExpectedCells -or [int]$current.data.worldOperationTotal -ne $ExpectedCells -or [string]$current.data.message -notmatch '^Scenery plan applied: \d+ cells; gameplay unchanged$'){throw "Scenery ended without a successful completion status: $($current.data.message)"}
                return $current
            }
        }
        Start-Sleep -Milliseconds 100
    }
}

try {
    $manifest=Get-Content -LiteralPath (Join-Path $sceneFolder 'manifest.json') -Raw | ConvertFrom-Json
    if(@($manifest.applyOrder).Count -ne 15){throw 'Expected exactly 15 ordered XO scenery plans.'}
    New-Item -ItemType Directory -Path $runtimeLevels -Force | Out-Null
    # Validate and stage the complete batch before touching either game's state.
    $prepared=@()
    for($index=1;$index -le 15;$index++){
        Assert-Deadline;$entry=$manifest.applyOrder[$index-1];$filename='xo-scene-{0:D2}.json' -f $index
        if($entry.file -cne $filename){throw "Unexpected plan order at ${index}: $($entry.file)"}
        $source=Join-Path $sceneFolder $filename
        $hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        if($hash -cne [string]$entry.sha256){throw "Source checksum mismatch: $filename"}
        $plan=Get-Content -LiteralPath $source -Raw | ConvertFrom-Json
        if($plan.targetWorld -cne 'GDBridge-XO' -or $plan.batchIndex -ne $index -or $plan.batchCount -ne 15){throw "Wrong target or sequence: $filename"}
        $cells=0L
        foreach($box in @($plan.boxes)){
            if($box.from.Count -ne 3 -or $box.to.Count -ne 3){throw "Invalid scenery box: $filename"}
            if([Math]::Min($box.from[2],$box.to[2]) -le 0 -and [Math]::Max($box.from[2],$box.to[2]) -ge 0){throw "Scenery plan touches gameplay Z=0: $filename"}
            $cells+=([Math]::Abs($box.from[0]-$box.to[0])+1L)*([Math]::Abs($box.from[1]-$box.to[1])+1L)*([Math]::Abs($box.from[2]-$box.to[2])+1L)
        }
        if($plan.PSObject.Properties['plants']){foreach($plant in @($plan.plants)){if($plant.at.Count -ne 3 -or $plant.at[2] -eq 0){throw "Invalid plant or gameplay Z=0: $filename"}};$cells+=@($plan.plants).Count}
        if($plan.PSObject.Properties['terrainFloor']){throw "This scenery batch must not alter the gameplay floor: $filename"}
        if($plan.cleanupOwnedBackWalls){$cells+=300}
        if($cells -lt 1 -or $cells -gt 16384 -or $cells -ne $entry.cells -or $cells -ne $plan.cellBudget){throw "Scenery cell budget mismatch: $filename ($cells)"}
        $destination=[IO.Path]::GetFullPath((Join-Path $runtimeLevels ('xo-full-scene-{0:D2}.json' -f $index)))
        if(-not $destination.StartsWith($runtimeLevels+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Staging path escapes native levels directory.'}
        Copy-Item -LiteralPath $source -Destination $destination -Force
        if((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -cne $hash){throw "Staged checksum mismatch: $filename"}
        $prepared+=[pscustomobject]@{index=$index;file=$filename;path=$destination;sha256=$hash;cells=[int]$cells}
    }
    $receipt.sourceBlueprint=$manifest.sourceBlueprint;$receipt.sourceSha256=$manifest.sourceSha256;$receipt.totalRequestedCells=$manifest.totalRequestedCells;$receipt.stagedPlans=$prepared;$receipt.state='applying';Save-Receipt
    $null=Wait-ReadyWorld
    $null=Invoke-Bridge 'gd-pause' @{active=$true}
    $null=Invoke-Bridge 'resume-world'
    $ready=Wait-Idle
    if(-not $ready.data.PSObject.Properties['authoringRegion']){throw 'Minecraft bridge does not expose the current authoring region.'}
    $region=$ready.data.authoringRegion;$required=$manifest.requiredSceneryBounds
    $minX=[Math]::Max(-16,[int]$region.minX-16);$maxX=[Math]::Min(4095,[int]$region.maxX+16);$maxY=[Math]::Min(255,[Math]::Max(160,[int]$region.maxY+8))
    if($required.minX -lt $minX -or $required.maxX -gt $maxX -or $required.minY -lt 50 -or $required.maxY -gt $maxY){throw 'Current Minecraft authoring region does not cover the complete XO scenery. Import the complete level before applying this batch.'}
    foreach($plan in $prepared){
        $null=Wait-Idle;$queuedAt=[DateTime]::UtcNow
        $ack=Invoke-Bridge 'scenery' @{path=$plan.path}
        $finished=Wait-Scenery -After $queuedAt -ExpectedCells $plan.cells
        $receipt.plans+= [ordered]@{index=$plan.index;file=$plan.file;sha256=$plan.sha256;requestedCells=$plan.cells;nonce=$ack.nonce;statusTimestampUtc=$finished.timestampUtc.ToString('o');result=$finished.data.message}
        $receipt.completedPlans=$plan.index;Save-Receipt
        Write-Host ('XO scenery {0}/15: processed {1} cells' -f $plan.index,$plan.cells)
    }
    $null=Wait-Idle;$receipt.state='complete';$receipt.finishedUtc=[DateTime]::UtcNow.ToString('o');$receipt.elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,1);Save-Receipt
    [pscustomobject]@{state='complete';appliedPlans=15;receipt=$receiptPath;elapsedSeconds=$receipt.elapsedSeconds;geometryDashLeftPaused=$true} | ConvertTo-Json
}catch{
    $failure=$_.Exception.Message
    if($script:lastStatusReadError){$failure+=' Last status read error: '+$script:lastStatusReadError}
    $receipt.state='failed';$receipt.error=$failure;$receipt.finishedUtc=[DateTime]::UtcNow.ToString('o');$receipt.elapsedSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,1)
    try{Save-Receipt}catch{throw ($failure+' Receipt writing also failed: '+$_.Exception.Message)}
    throw $failure
}
