#Requires -Version 7.0
<#
.SYNOPSIS
Creates original, asset-free native GD source fixtures; never loads a game or writes runtime controls.
.NOTES
Generated IDs are local test identities, not downloaded online levels. Runtime playback,
activation and AUTO completion must be verified separately. Do not use voxel import as
proof that fractional/half-scale native geometry has been reproduced exactly.
#>
[CmdletBinding()]
param([ValidateSet('all','A','B','C')][string]$Fixture = 'all')
$ErrorActionPreference = 'Stop'
$fixturesRoot = Join-Path $PSScriptRoot 'fixtures'
New-Item -ItemType Directory -Path $fixturesRoot -Force | Out-Null
$culture = [Globalization.CultureInfo]::InvariantCulture
# Hand-authored cube/normal-speed header, standard ground, normal gravity, no dual/platformer mode.
# Header field meanings are documented by gd.py's own header serializer; no XO records are copied.
$header = 'kA2,0,kA3,0,kA4,0,kA6,1,kA7,1,kA8,0,kA10,0,kA11,0,kA22,0,kS1,70,kS2,105,kS3,145,kS4,90,kS5,100,kS6,110,kS7,255,kS8,255,kS9,255,kS10,230,kS11,230,kS12,230'
function New-FixtureObject([int]$Id, [double]$X, [double]$Y, [string]$Role,
        [double]$Scale = 1, [double]$Rotation = 0, [bool]$FlipX = $false, [bool]$FlipY = $false) {
    if ($X -lt 0 -or $Scale -le 0) { throw 'Fixture positions must have nonnegative X and positive scale.' }
    [pscustomobject][ordered]@{ id=$Id; x=$X; y=$Y; editorY=$Y-90; scale=$Scale; rotation=$Rotation; flipX=$FlipX; flipY=$FlipY; role=$Role }
}
function ConvertTo-NativeRecord($Object) {
    $parts = [Collections.Generic.List[string]]::new()
    foreach ($pair in @(@('1',$Object.id), @('2',$Object.x), @('3',$Object.editorY), @('6',$Object.rotation), @('32',$Object.scale))) {
        $parts.Add([string]$pair[0]); $parts.Add(([double]$pair[1]).ToString('0.########', $culture))
    }
    if ($Object.flipX) { $parts.Add('4'); $parts.Add('1') }
    if ($Object.flipY) { $parts.Add('5'); $parts.Add('1') }
    return [string]::Join(',', $parts) + ';'
}
function Compress-NativeLevel([string]$Raw) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Raw)
    $buffer = [IO.MemoryStream]::new()
    $gzip = [IO.Compression.GZipStream]::new($buffer, [IO.Compression.CompressionLevel]::Optimal, $true)
    try { $gzip.Write($bytes,0,$bytes.Length) } finally { $gzip.Dispose() }
    $compressed = $buffer.ToArray(); $buffer.Dispose()
    $encoded = [Convert]::ToBase64String($compressed).Replace('+','-').Replace('/','_')
    # Validate actual generated gzip bytes, rather than relying on their prefix.
    $readBuffer = [IO.MemoryStream]::new($compressed, $false)
    $readGzip = [IO.Compression.GZipStream]::new($readBuffer, [IO.Compression.CompressionMode]::Decompress)
    $reader = [IO.StreamReader]::new($readGzip, [Text.Encoding]::UTF8)
    try { $decoded = $reader.ReadToEnd() } finally { $reader.Dispose(); $readBuffer.Dispose() }
    if ($decoded -cne $Raw) { throw 'Fixture gzip roundtrip failed.' }
    return $encoded
}
$definitions = @(
    [pscustomobject]@{
        key='A'; id=900000101; slug='fixture-a-blocks-spikes'; name='Bridge fixture A - blocks and spikes'
        purpose='Full cubes and single/double/triple standard spikes, separated by long clear sections. Yellow pads are candidate automatic jump launchers; AUTO is unverified.'
        autoCandidate=$true
        objects=@(
            (New-FixtureObject 35 345 90 'candidate automatic jump pad before full block'),
            (New-FixtureObject 1 405 105 'full block on native floor'),
            (New-FixtureObject 35 645 90 'candidate automatic jump pad before single spike'),
            (New-FixtureObject 8 705 105 'single spike'),
            (New-FixtureObject 35 945 90 'candidate automatic jump pad before double spikes'),
            (New-FixtureObject 8 1005 105 'double spike 1'),
            (New-FixtureObject 8 1035 105 'double spike 2'),
            (New-FixtureObject 35 1305 90 'candidate automatic jump pad before triple spikes'),
            (New-FixtureObject 8 1365 105 'triple spike 1'),
            (New-FixtureObject 8 1395 105 'triple spike 2'),
            (New-FixtureObject 8 1425 105 'triple spike 3'),
            (New-FixtureObject 1 1725 195 'isolated elevated full block')
        )
    },
    [pscustomobject]@{
        key='B'; id=900000102; slug='fixture-b-fractional-transforms'; name='Bridge fixture B - fractional transforms'
        purpose='Separated elevated samples for exact centers, half/quarter scale, 45/90-degree rotation and flips. Two disjoint quarter-scale blocks share one editor cell and must remain separate pieces. Ordinary route stays clear; this is a geometry inspection fixture.'
        autoCandidate=$false
        objects=@(
            (New-FixtureObject 1 345 165 'unit block control'),
            (New-FixtureObject 1 525.25 165.75 'half-scale fractional block' 0.5),
            (New-FixtureObject 1 524.25 225 'same editor cell pair A; distinct quarter-scale piece' 0.25),
            (New-FixtureObject 1 535.25 225 'same editor cell pair B; 11 units from A, 3.5-unit visual gap' 0.25),
            (New-FixtureObject 1 705.25 285.75 'unit block rotated 45 degrees' 1 45),
            (New-FixtureObject 1 705.5 180.25 'half-scale fractional block rotated 90' 0.5 90),
            (New-FixtureObject 8 885.25 165.75 'half-scale fractional spike' 0.5),
            (New-FixtureObject 8 1065.5 180.25 'half-scale spike rotated 90' 0.5 90),
            (New-FixtureObject 8 1245.25 180.75 'horizontal flip and 90-degree rotation' 1 90 $true),
            (New-FixtureObject 8 1425.5 180.25 'vertical flip' 1 0 $false $true),
            (New-FixtureObject 8 1605.25 180.75 'both flips and 90-degree rotation' 0.5 90 $true $true)
        )
    },
    [pscustomobject]@{
        key='C'; id=900000103; slug='fixture-c-specials'; name='Bridge fixture C - separate native specials'
        purpose='Yellow jump pad, yellow orb, ship portal and cube return portal in separate clear areas. Orb activation needs native input; no automatic completion is claimed.'
        autoCandidate=$false
        objects=@(
            (New-FixtureObject 35 345 90 'yellow jump pad'),
            (New-FixtureObject 36 705 165 'yellow jump orb; native press required'),
            (New-FixtureObject 13 1065 135 'ship mode portal'),
            (New-FixtureObject 12 1485 135 'cube return portal'),
            (New-FixtureObject 1 1845 195 'isolated elevated unit block reference')
        )
    }
)
$evidence = [ordered]@{
    localNativeSerializer='outputs/bridge/gd-world-authority-next/src/main.cpp: objectRecord keys 1/2/3/6/32; native Y = serialized editor Y + 90'
    localNativeLoader='outputs/bridge/gd-world-authority-next/src/main.cpp: LevelLoader.beginData decompresses levelString and creates a temporary native PlayLayer without saving'
    objectSerialization='https://github.com/nekitdev/gd.py/blob/master/gd/api/objects.py'
    nativeObjectIDs='https://github.com/nekitdev/gd.py/blob/master/gd/enums.py'
    headerSerialization='https://github.com/nekitdev/gd.py/blob/master/gd/api/header.py'
    note='gd.py sources establish the fixture format and conventional IDs; actual behavior in the installed GD build remains a separate runtime test.'
}
$written = [Collections.Generic.List[object]]::new()
foreach ($definition in $definitions) {
    if ($Fixture -ne 'all' -and $Fixture -ne $definition.key) { continue }
    $raw = $header + ';' + (($definition.objects | ForEach-Object { ConvertTo-NativeRecord $_ }) -join '')
    $levelString = Compress-NativeLevel $raw
    $counts = [ordered]@{ solid=0; spike=0; pad=0; orb=0; portal=0 }
    foreach ($object in $definition.objects) {
        switch ([int]$object.id) { 1 {$counts.solid++} 8 {$counts.spike++} 35 {$counts.pad++} 36 {$counts.orb++} {$_ -in @(12,13)} {$counts.portal++} }
    }
    $source = [ordered]@{
        fixture=$true; fixtureVersion=1; provenance='Original hand-authored bridge test geometry, not a downloaded level'
        license='CC0-1.0 for authored fixture geometry and metadata; no game assets included'
        id=$definition.id; levelId=$definition.id; name=$definition.name; creator='MinecraftGDBridge fixtures'
        songId=0; audioTrack=0; downloadMusic=$false
        levelString=$levelString; rawLevelString=$raw
        fixtureMetadata=[ordered]@{
            purpose=$definition.purpose; sourceObjectCount=$definition.objects.Count; counts=$counts
            coordinates='x/y are native world GD units; serialized key 3 is y - 90; 30 units = one Minecraft block'
            nativeImplicitFloorY=90; floorVerifiedInThisFixture=$false
            autoCandidate=$definition.autoCandidate; autoVerified=$false; nativePlaybackVerified=$false
            exactMinecraftGeometryVerified=$false; completionVerified=$false; compressionRoundtripVerified=$true
            samples=$definition.objects; sourceEvidence=$evidence
            sameCellPieceTest=if($definition.key -eq 'B') {
                [ordered]@{
                    centersGD=@(@(524.25,225),@(535.25,225)); scale=0.25
                    visualSideGDUnits=7.5; centerSeparationGDUnits=11; unrotatedVisualGapGDUnits=3.5
                    ownerCell=@(17,71,0); ownershipFormula='Java Math.round(x/30 - 0.5), Math.round(64 + y/30 - 0.5), z=0'
                    required='Preserve both distinct source pieces within the same editor cell; do not collapse them into a single full block.'
                    nativeBoundsVerified=$false; exactMinecraftPiecePreservationVerified=$false
                }
            } else { $null }
            verification='Load native GD source first, capture fresh blueprint/framebuffer evidence, compare every center/scale/rotation and actual collision bounds, then test exact Minecraft reproduction. Current voxel import may snap fractional/half-scale samples and is not proof of exact conversion.'
        }
    }
    $filePath = Join-Path $fixturesRoot ($definition.slug + '.json')
    [IO.File]::WriteAllText($filePath, ($source | ConvertTo-Json -Depth 12) + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
    $written.Add([pscustomobject]@{fixture=$definition.key; id=$definition.id; path=$filePath; objects=$definition.objects.Count; sha256=(Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash; nativePlaybackVerified=$false})
}
$written | ConvertTo-Json -Depth 4
