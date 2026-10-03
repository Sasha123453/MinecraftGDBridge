#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$FixturePath,
    [string]$OutputPath
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Runtime-Paths.ps1')
$repoRoot=Split-Path -Parent $PSScriptRoot
$positionTolerance=0.005; $scalarTolerance=0.0001; $geometryTolerance=0.01
function Read-Stable([string]$Path) {
    if(-not(Test-Path -LiteralPath $Path -PathType Leaf)){throw "Required file missing: $Path"}
    $before=(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    $data=Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
    $after=(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if($before -ne $after){throw "File changed during read: $Path"}
    [pscustomobject]@{data=$data;hash=$after;written=(Get-Item -LiteralPath $Path).LastWriteTimeUtc;path=$Path}
}
function Resolve-OwnedFile([string]$Path,[string]$Folder,[bool]$MustExist) {
    $target=if([IO.Path]::IsPathRooted($Path)){[IO.Path]::GetFullPath($Path)}else{[IO.Path]::GetFullPath((Join-Path $repoRoot $Path))}
    $root=[IO.Path]::GetFullPath((Join-Path $repoRoot $Folder))
    if(-not[string]::Equals([IO.Path]::GetDirectoryName($target),$root,[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetExtension($target) -ne '.json'){throw "Expected a JSON file directly under $Folder."}
    $parent=Get-Item -LiteralPath $root
    if(($parent.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'Linked report/fixture directories are unsupported.'}
    if(Test-Path -LiteralPath $target){$item=Get-Item -LiteralPath $target;if($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'Expected a regular file.'}}elseif($MustExist){throw "Missing fixture: $target"}
    return $target
}
function Number($Value,[string]$Name) {
    if($null -eq $Value -or $Value -is [string] -or $Value -is [bool]){throw "Invalid numeric field: $Name"}
    $n=[double]$Value;if([double]::IsNaN($n) -or [double]::IsInfinity($n)){throw "Nonfinite field: $Name"};return $n
}
function Record-Transforms($Object) {
    if(-not $Object.data){return $null}
    $tokens=([string]$Object.data).TrimEnd(';').Split(',')
    if($tokens.Count % 2){throw 'Malformed native authoring record.'}
    $properties=@{};for($i=0;$i -lt $tokens.Count;$i+=2){if($properties.ContainsKey($tokens[$i])){throw 'Duplicate native record key.'};$properties[$tokens[$i]]=$tokens[$i+1]}
    foreach($required in @('1','2','3')){if(-not $properties.ContainsKey($required)){throw "Authoring record lacks key $required."}}
    $culture=[Globalization.CultureInfo]::InvariantCulture
    $rotation=if($properties.ContainsKey('6')){[double]::Parse($properties['6'],$culture)}else{0.0}
    $scale=if($properties.ContainsKey('32')){[double]::Parse($properties['32'],$culture)}else{1.0}
    if([double]::IsNaN($rotation) -or [double]::IsInfinity($rotation) -or [double]::IsNaN($scale) -or [double]::IsInfinity($scale)){throw 'Nonfinite authored transform.'}
    [pscustomobject]@{id=[int]$properties['1'];x=[double]::Parse($properties['2'],$culture);y=([double]::Parse($properties['3'],$culture)+90);rotation=$rotation;scale=$scale;flipX=($properties.ContainsKey('4') -and $properties['4'] -eq '1');flipY=($properties.ContainsKey('5') -and $properties['5'] -eq '1')}
}
function Compare-Pieces($Samples,$Objects) {
    $used=[bool[]]::new($Objects.Count);$details=[Collections.Generic.List[object]]::new()
    $indices=[int[]]::new($Samples.Count);for($i=0;$i -lt $indices.Count;$i++){$indices[$i]=-1}
    # Reserve every exact source match before assigning a mismatched diagnostic candidate.
    # Otherwise an earlier bad flip could consume the only good match of a later duplicate.
    for($i=0;$i -lt $Samples.Count;$i++){
        $sample=$Samples[$i]
        for($j=0;$j -lt $Objects.Count;$j++){
            if($used[$j]){continue};$object=$Objects[$j]
            if((Number $object.id 'id') -ne $sample.id -or [Math]::Abs((Number $object.x 'x')-$sample.x) -gt $positionTolerance -or [Math]::Abs((Number $object.y 'y')-$sample.y) -gt $positionTolerance){continue}
            $record=Record-Transforms $object
            if($null -ne $record -and $record.id -eq $sample.id -and [Math]::Abs($record.x-$sample.x) -le $positionTolerance -and [Math]::Abs($record.y-$sample.y) -le $positionTolerance -and [Math]::Abs($record.rotation-$sample.rotation) -le $scalarTolerance -and [Math]::Abs($record.scale-$sample.scale) -le $scalarTolerance -and $record.flipX -eq $sample.flipX -and $record.flipY -eq $sample.flipY){$indices[$i]=$j;$used[$j]=$true;break}
        }
    }
    for($i=0;$i -lt $Samples.Count;$i++){
        if($indices[$i] -ge 0){continue};$sample=$Samples[$i]
        for($j=0;$j -lt $Objects.Count;$j++){
            if($used[$j]){continue};$object=$Objects[$j]
            if((Number $object.id 'id') -eq $sample.id -and [Math]::Abs((Number $object.x 'x')-$sample.x) -le $positionTolerance -and [Math]::Abs((Number $object.y 'y')-$sample.y) -le $positionTolerance){$indices[$i]=$j;$used[$j]=$true;break}
        }
    }
    $matched=0;$positions=0;$degrees=0;$scales=0;$flips=0;$recordsMissing=0
    for($i=0;$i -lt $Samples.Count;$i++){
        $sample=$Samples[$i];$candidate=$indices[$i]
        if($candidate -lt 0){$details.Add([pscustomobject]@{id=$sample.id;x=$sample.x;y=$sample.y;positionMatch=$false;recordAvailable=$false;rotationMatch=$false;scaleMatch=$false;flipMatch=$false;sourceMatchUnchanged=$false});continue}
        $positions++;$record=Record-Transforms $Objects[$candidate]
        $rotationOK=$false;$scaleOK=$false;$flipOK=$false;$recordCenterOK=$false
        if($null -eq $record){$recordsMissing++}else{
            $rotationOK=[Math]::Abs($record.rotation-$sample.rotation) -le $scalarTolerance
            $scaleOK=[Math]::Abs($record.scale-$sample.scale) -le $scalarTolerance
            $flipOK=$record.flipX -eq $sample.flipX -and $record.flipY -eq $sample.flipY
            $recordCenterOK=$record.id -eq $sample.id -and [Math]::Abs($record.x-$sample.x) -le $positionTolerance -and [Math]::Abs($record.y-$sample.y) -le $positionTolerance
        }
        if($rotationOK){$degrees++};if($scaleOK){$scales++};if($flipOK){$flips++}
        $exact=$recordCenterOK -and $rotationOK -and $scaleOK -and $flipOK;if($exact){$matched++}
        $details.Add([pscustomobject]@{id=$sample.id;x=$sample.x;y=$sample.y;positionMatch=$true;recordAvailable=($null -ne $record);rotationMatch=$rotationOK;scaleMatch=$scaleOK;flipMatch=$flipOK;sourceMatchUnchanged=$exact})
    }
    [pscustomobject]@{matched=$matched;positionMatched=$positions;rotationMatched=$degrees;scaleMatched=$scales;flipMatched=$flips;recordsUnavailable=$recordsMissing;indices=$indices;details=$details.ToArray()}
}
function Compare-NumericTree($Left,$Right) {
    if($null -eq $Left -or $null -eq $Right){return $false}
    if($Left -is [System.Array] -or $Right -is [System.Array]){
        $a=@($Left);$b=@($Right);if($a.Count -ne $b.Count){return $false}
        for($n=0;$n -lt $a.Count;$n++){if(-not(Compare-NumericTree $a[$n] $b[$n])){return $false}};return $true
    }
    return [Math]::Abs((Number $Left 'geometry')-(Number $Right 'geometry')) -le $geometryTolerance
}
$fixtureFile=Read-Stable (Resolve-OwnedFile $FixturePath 'outputs/fixtures' $true)
$fixture=$fixtureFile.data
if($fixture.fixture -ne $true -or [int]$fixture.id -notin @(900000101,900000102,900000103)){throw 'Expected one of the original authored geometry fixtures.'}
$samples=@($fixture.fixtureMetadata.samples)
if($samples.Count -eq 0 -or $samples.Count -ne [int]$fixture.fixtureMetadata.sourceObjectCount){throw 'Fixture sample count is inconsistent.'}
$reportPath=if($OutputPath){Resolve-OwnedFile $OutputPath 'docs' $false}else{$null}
$manifestFile=Read-Stable (Join-Path (Get-BridgeRuntimeOutputs) 'bridge/runtime/world-authority-export.local.json')
$nativeFile=Read-Stable (Join-Path (Get-BridgeRuntimeOutputs) 'bridge/levels/level-0.json')
$manifest=$manifestFile.data;$native=$nativeFile.data
if($manifest.world -ne 'GDBridge-GeometryTests'){throw 'Stale/wrong world manifest: expected GDBridge-GeometryTests.'}
if($manifest.geometryAuthority -ne 'minecraft-world' -or $native.authority -ne 'minecraft-world' -or [int]$native.levelId -ne 0){throw 'Expected the native level-0 Minecraft-world export after F6.'}
if($manifestFile.written -lt $fixtureFile.written.AddSeconds(-2) -or $nativeFile.written -lt $manifestFile.written.AddSeconds(-2)){throw 'Export timestamps are stale relative to the fixture/world manifest (two-second filesystem margin).'}
if($null -eq $manifest.PSObject.Properties['objects'] -or $null -eq $native.PSObject.Properties['objects']){throw 'Flat exported/native objects arrays are required.'}
$stored=@($manifest.objects);$nativeObjects=@($native.objects)
$storedResult=Compare-Pieces $samples $stored
$nativeResult=Compare-Pieces $samples $nativeObjects
$geometryChecked=0;$geometryMismatches=0;$geometryUnavailable=0;$geometryDetails=[Collections.Generic.List[object]]::new()
$geometryFields=@('visualQuad','hitboxX','hitboxY','hitboxW','hitboxH','triangleVertices')
for($i=0;$i -lt $samples.Count;$i++){
    $available=0;$unavailable=[Collections.Generic.List[string]]::new();$failed=[Collections.Generic.List[string]]::new()
    if($storedResult.indices[$i] -lt 0 -or $nativeResult.indices[$i] -lt 0){$geometryUnavailable++;continue}
    $a=$stored[$storedResult.indices[$i]];$b=$nativeObjects[$nativeResult.indices[$i]]
    foreach($field in $geometryFields){
        if($null -eq $a.PSObject.Properties[$field] -or $null -eq $b.PSObject.Properties[$field]){$unavailable.Add($field);continue}
        $available++;$geometryChecked++;if(-not(Compare-NumericTree $a.$field $b.$field)){$geometryMismatches++;$failed.Add($field)}
    }
    if($available -eq 0){$geometryUnavailable++}
    $geometryDetails.Add([pscustomobject]@{id=$samples[$i].id;x=$samples[$i].x;y=$samples[$i].y;comparedFields=$available;unavailableFields=$unavailable.ToArray();mismatchedFields=$failed.ToArray()})
}
$sameCellReport=$null;$sameCellPassed=$true
if($null -ne $fixture.fixtureMetadata.sameCellPieceTest){
    $test=$fixture.fixtureMetadata.sameCellPieceTest;$owner=@($test.ownerCell)
    $targetCells=@($manifest.cells | Where-Object {$_.pos.Count -eq 3 -and $_.pos[0] -eq $owner[0] -and $_.pos[1] -eq $owner[1] -and $_.pos[2] -eq $owner[2]})
    $cellPieces=@();if($targetCells.Count -eq 1){$cellPieces=if($null -ne $targetCells[0].PSObject.Properties['compiledPieces']){@($targetCells[0].compiledPieces)}elseif($targetCells[0].compiled){@($targetCells[0].compiled)}else{@()}}
    $pairSamples=@($samples | Where-Object {$_.id -eq 1 -and [Math]::Abs($_.scale-0.25) -le $scalarTolerance -and [Math]::Abs($_.y-225) -le $positionTolerance -and ($_.x -eq 524.25 -or $_.x -eq 535.25)})
    $pairResult=Compare-Pieces $pairSamples $cellPieces
    $sameCellPassed=$targetCells.Count -eq 1 -and $pairSamples.Count -eq 2 -and $pairResult.matched -eq 2
    $sameCellReport=[ordered]@{ownerCell=$owner;expectedDistinctPieces=2;storedCellPieceCount=$cellPieces.Count;matchedDistinctPieces=$pairResult.matched;passed=$sameCellPassed}
}
foreach($file in @($fixtureFile,$manifestFile,$nativeFile)){if((Get-FileHash -LiteralPath $file.path -Algorithm SHA256).Hash -ne $file.hash){throw 'Files changed during verification; retry after exports finish.'}}
$unchanged=$storedResult.matched -eq $samples.Count -and $nativeResult.matched -eq $samples.Count
$passed=$unchanged -and $sameCellPassed -and $geometryMismatches -eq 0
$summary=[ordered]@{
    checkedAt=[DateTime]::UtcNow.ToString('o');fixtureId=$fixture.id;fixtureName=$fixture.name;authority='minecraft-world';world=$manifest.world
    passed=$passed;sourceMatchUnchanged=$unchanged;originalSourceCount=$samples.Count;storedTotal=$stored.Count;nativeTotal=$nativeObjects.Count
    storedMatched=$storedResult.matched;nativeMatched=$nativeResult.matched
    storedPositionMatched=$storedResult.positionMatched;nativePositionMatched=$nativeResult.positionMatched
    storedRotationMatched=$storedResult.rotationMatched;nativeRotationMatched=$nativeResult.rotationMatched
    storedScaleMatched=$storedResult.scaleMatched;nativeScaleMatched=$nativeResult.scaleMatched
    storedFlipMatched=$storedResult.flipMatched;nativeFlipMatched=$nativeResult.flipMatched
    authoredRecordUnavailable=[ordered]@{stored=$storedResult.recordsUnavailable;native=$nativeResult.recordsUnavailable}
    storedExtras=$stored.Count-$storedResult.positionMatched;nativeExtras=$nativeObjects.Count-$nativeResult.positionMatched
    geometryFieldsChecked=$geometryChecked;geometryMismatches=$geometryMismatches;geometryUnavailablePieces=$geometryUnavailable
    geometryFullyAvailable=($geometryUnavailable -eq 0 -and @($geometryDetails | Where-Object { $_.unavailableFields -contains 'visualQuad' -or $_.unavailableFields -contains 'hitboxX' -or $_.unavailableFields -contains 'hitboxY' -or $_.unavailableFields -contains 'hitboxW' -or $_.unavailableFields -contains 'hitboxH' }).Count -eq 0)
    sameCellPieces=$sameCellReport;storedSamples=$storedResult.details;nativeSamples=$nativeResult.details;geometry=$geometryDetails.ToArray()
    positionToleranceGDUnits=$positionTolerance;scalarTolerance=$scalarTolerance;geometryToleranceGDUnits=$geometryTolerance
    fixtureSHA256=$fixtureFile.hash;manifestSHA256=$manifestFile.hash;nativeSHA256=$nativeFile.hash
    manifestWrittenUtc=$manifestFile.written.ToString('o');nativeWrittenUtc=$nativeFile.written.ToString('o')
    limitation='Verifies fixture piece preservation and available stored-versus-native geometry metadata. Extra floor/continuation objects are reported separately. Missing metadata is not a geometry pass. Does not prove visual quality, normal completion, AUTO, or implicit-floor/ceiling authority.'
}
$json=$summary | ConvertTo-Json -Depth 12
if($reportPath){[IO.File]::WriteAllText($reportPath,$json+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))}
$json
if(-not $passed){throw 'Geometry fixture verification failed; inspect the reported piece/transform/metadata mismatches.'}
