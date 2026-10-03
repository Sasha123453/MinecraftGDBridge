[CmdletBinding()]
param(
    # Optional public summary, restricted to a JSON file directly inside this repository's docs directory.
    [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Runtime-Paths.ps1')
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path (Get-BridgeRuntimeOutputs) 'bridge/runtime/world-authority-export.local.json'
$nativePath = Join-Path (Get-BridgeRuntimeOutputs) 'bridge/levels/level-0.json'
$tolerance = 0.005
$culture = [Globalization.CultureInfo]::InvariantCulture

function Read-StableJson([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required export is missing: $Path" }
    $before = Get-FileHash -LiteralPath $Path -Algorithm SHA256
    $data = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
    $after = Get-FileHash -LiteralPath $Path -Algorithm SHA256
    if ($before.Hash -ne $after.Hash) { throw "Export changed while being read; retry after it completes: $Path" }
    [pscustomobject]@{ Data = $data; Hash = $after.Hash; Written = (Get-Item -LiteralPath $Path).LastWriteTimeUtc }
}
function Read-FiniteNumber($Object, [string]$Name) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value -or $property.Value -is [string] -or $property.Value -is [bool]) {
        throw "Missing or nonnumeric object field: $Name"
    }
    $number = [double]$property.Value
    if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) { throw "Nonfinite object field: $Name" }
    return $number
}
function Get-PositionKey([int]$Id, [double]$X, [double]$Y) {
    # Integer cell centers are exact; adjacent buckets also cover roundoff at a bucket boundary.
    return '{0}|{1}|{2}' -f $Id, $X.ToString('F2', $culture), $Y.ToString('F2', $culture)
}
function Test-CellDimensions($Expected, $Actual) {
    if ([int]$Expected.id -eq 8) {
        # Native spikes deliberately have forgiving hitboxes (for example 6 x 12), not 30 x 30 visuals.
        if ($null -eq $Expected.PSObject.Properties['w'] -or $null -eq $Expected.PSObject.Properties['h']) { return $true }
        if ($null -eq $Actual.PSObject.Properties['w'] -or $null -eq $Actual.PSObject.Properties['h']) { return $false }
        return ([Math]::Abs((Read-FiniteNumber $Actual 'w') - (Read-FiniteNumber $Expected 'w')) -le $tolerance -and
                [Math]::Abs((Read-FiniteNumber $Actual 'h') - (Read-FiniteNumber $Expected 'h')) -le $tolerance)
    }
    if ([int]$Expected.id -ne 1) { return $true }
    $scale = if ($null -ne $Expected.PSObject.Properties['scale']) { Read-FiniteNumber $Expected 'scale' } else { 1.0 }
    if ([Math]::Abs([Math]::Abs($scale) - 1.0) -gt $tolerance) { return $true }
    if ($null -eq $Actual.PSObject.Properties['w'] -or $null -eq $Actual.PSObject.Properties['h']) { return $false }
    return ([Math]::Abs((Read-FiniteNumber $Actual 'w') - 30.0) -le $tolerance -and
            [Math]::Abs((Read-FiniteNumber $Actual 'h') - 30.0) -le $tolerance)
}

$reportPath = $null
if ($OutputPath) {
    $docsPath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot 'docs'))
    $reportPath = if ([IO.Path]::IsPathRooted($OutputPath)) { [IO.Path]::GetFullPath($OutputPath) } else {
        [IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputPath))
    }
    if (-not [string]::Equals([IO.Path]::GetDirectoryName($reportPath), $docsPath, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetExtension($reportPath) -ne '.json') { throw 'OutputPath must be a JSON file directly inside the repository docs directory.' }
    $docsItem = Get-Item -LiteralPath $docsPath
    if (($docsItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'The report directory must not be a link or junction.' }
    if (Test-Path -LiteralPath $reportPath) {
        $existing = Get-Item -LiteralPath $reportPath
        if ($existing.PSIsContainer -or ($existing.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'The report target must be a regular file.' }
    }
}

$manifestFile = Read-StableJson $manifestPath
$nativeFile = Read-StableJson $nativePath
$manifest = $manifestFile.Data
$native = $nativeFile.Data
if ($manifest.geometryAuthority -ne 'minecraft-world' -or $native.authority -ne 'minecraft-world') {
    throw 'Both the world manifest and native blueprint must declare minecraft-world authority.'
}
if ($nativeFile.Written -lt $manifestFile.Written.AddSeconds(-2)) {
    throw 'Native blueprint predates the world manifest by more than two seconds; wait for the native export.'
}
if ($null -eq $manifest.PSObject.Properties['cells'] -or $null -eq $native.PSObject.Properties['objects']) { throw 'Missing cells or native objects array.' }
$cells = @($manifest.cells)
$nativeObjects = @($native.objects)
if ($cells.Count -eq 0 -or [int]$manifest.cellCount -ne $cells.Count) { throw 'World manifest cell count is empty or inconsistent.' }

$buckets = @{}
for ($index = 0; $index -lt $nativeObjects.Count; $index++) {
    $object = $nativeObjects[$index]
    $idValue = Read-FiniteNumber $object 'id'
    if ($idValue -le 0 -or $idValue -ne [Math]::Truncate($idValue)) { throw 'Invalid native object ID.' }
    $key = Get-PositionKey ([int]$idValue) (Read-FiniteNumber $object 'x') (Read-FiniteNumber $object 'y')
    if (-not $buckets.ContainsKey($key)) { $buckets[$key] = [Collections.Generic.List[int]]::new() }
    $buckets[$key].Add($index)
}
$consumed = [bool[]]::new($nativeObjects.Count)
$missing = 0
$dimensionMismatches = 0
$matched = 0
$dimensionChecked = 0
$nativeBoundsExamples = [Collections.Generic.List[object]]::new()
$examples = [Collections.Generic.List[object]]::new()
$counts = [ordered]@{ solid = 0; spike = 0; special = 0 }
foreach ($cell in $cells) {
    if ($null -eq $cell.compiled) { throw 'A manifest cell has no compiled object.' }
    $expected = $cell.compiled
    $idValue = Read-FiniteNumber $expected 'id'
    if ($idValue -le 0 -or $idValue -ne [Math]::Truncate($idValue)) { throw 'Invalid compiled cell ID.' }
    $id = [int]$idValue
    $x = Read-FiniteNumber $expected 'x'
    $y = Read-FiniteNumber $expected 'y'
    if ($id -eq 1) { $counts.solid++ } elseif ($id -eq 8) { $counts.spike++ } else { $counts.special++ }
    $best = -1
    $firstPositionMatch = -1
    $visited = [Collections.Generic.HashSet[int]]::new()
    foreach ($dx in @(-0.01, 0.0, 0.01)) {
        foreach ($dy in @(-0.01, 0.0, 0.01)) {
            $key = Get-PositionKey $id ($x + $dx) ($y + $dy)
            if (-not $buckets.ContainsKey($key)) { continue }
            foreach ($candidate in $buckets[$key]) {
                if ($consumed[$candidate] -or -not $visited.Add($candidate)) { continue }
                $actual = $nativeObjects[$candidate]
                if ([Math]::Abs([double]$actual.x - $x) -gt $tolerance -or [Math]::Abs([double]$actual.y - $y) -gt $tolerance) { continue }
                if ($firstPositionMatch -lt 0) { $firstPositionMatch = $candidate }
                if (Test-CellDimensions $expected $actual) { $best = $candidate; break }
            }
            if ($best -ge 0) { break }
        }
        if ($best -ge 0) { break }
    }
    if ($best -lt 0) { $best = $firstPositionMatch }
    if ($best -lt 0) {
        $missing++
        if ($examples.Count -lt 20) { $examples.Add([pscustomobject]@{ issue = 'missing-id-or-center'; cell = $cell.pos; id = $id; x = $x; y = $y }) }
        continue
    }
    $consumed[$best] = $true
    $matched++
    $scale = if ($null -ne $expected.PSObject.Properties['scale']) { Read-FiniteNumber $expected 'scale' } else { 1.0 }
    if (($id -eq 1 -and [Math]::Abs([Math]::Abs($scale) - 1.0) -le $tolerance) -or
        ($id -eq 8 -and $null -ne $expected.PSObject.Properties['w'] -and $null -ne $expected.PSObject.Properties['h'])) { $dimensionChecked++ }
    if ($id -eq 8 -and $nativeBoundsExamples.Count -lt 5) {
        $nativeBoundsExamples.Add([pscustomobject]@{ id = $id; x = $x; y = $y; w = $nativeObjects[$best].w; h = $nativeObjects[$best].h; expectedBoundsProvided = ($null -ne $expected.PSObject.Properties['w'] -and $null -ne $expected.PSObject.Properties['h']) })
    }
    if (-not (Test-CellDimensions $expected $nativeObjects[$best])) {
        $dimensionMismatches++
        if ($examples.Count -lt 20) { $examples.Add([pscustomobject]@{ issue = 'native-dimensions'; cell = $cell.pos; id = $id; x = $x; y = $y; nativeWidth = $nativeObjects[$best].w; nativeHeight = $nativeObjects[$best].h }) }
    }
}
# Check that neither producer rewrote its file during the comparison.
if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash -ne $manifestFile.Hash -or
    (Get-FileHash -LiteralPath $nativePath -Algorithm SHA256).Hash -ne $nativeFile.Hash) { throw 'Exports changed during verification; retry after both producers finish.' }
$summary = [ordered]@{
    checkedAt = [DateTime]::UtcNow.ToString('o')
    authority = 'minecraft-world'
    passed = ($missing -eq 0 -and $dimensionMismatches -eq 0)
    scope = $manifest.scope
    authoringRegion = $manifest.authoringRegion
    cells = $cells.Count
    counts = $counts
    unsupportedCells = $manifest.unsupportedCells
    nativeTotal = $nativeObjects.Count
    matchedCells = $matched
    missingCells = $missing
    dimensionCheckedCells = $dimensionChecked
    dimensionMismatches = $dimensionMismatches
    unmatchedNativeObjects = $nativeObjects.Count - $matched
    positionToleranceGDUnits = $tolerance
    manifestWrittenUtc = $manifestFile.Written.ToString('o')
    nativeWrittenUtc = $nativeFile.Written.ToString('o')
    manifestSHA256 = $manifestFile.Hash
    nativeSHA256 = $nativeFile.Hash
    mismatchExamples = @($examples.ToArray())
    nativeSpikeBoundsExamples = @($nativeBoundsExamples.ToArray())
    limitation = 'Checks compiled-cell IDs, centers and unit full-solid dimensions. Spike sizes are checked only when the manifest provides expected native bounds; native forgiving hitboxes are preserved. Native continuation can add unmatched objects. Does not verify unchanged XO geometry, normal completion, invisible planes, or full trigger/effect behavior.'
}
$json = $summary | ConvertTo-Json -Depth 10
if ($reportPath) { [IO.File]::WriteAllText($reportPath, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false)) }
$json
if (-not $summary.passed) { throw "Authority verification failed: $missing missing cells; $dimensionMismatches dimension mismatches." }
