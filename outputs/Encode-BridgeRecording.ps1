#Requires -Version 7.0
param(
    [Parameter(Mandatory=$true)][string]$RecordingDirectory,
    [string]$FfmpegPath,
    [string]$OutputPath,
    [ValidateRange(1,120)][int]$WaitSeconds=60,
    [switch]$Force
)
$ErrorActionPreference='Stop'
$recordingRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'bridge/recordings')).ProviderPath
$recording=(Resolve-Path -LiteralPath $RecordingDirectory).ProviderPath
$rootPrefix=$recordingRoot.TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar
if(-not $recording.StartsWith($rootPrefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Choose an owned recording directory under outputs/bridge/recordings.'}
if(-not(Test-Path -LiteralPath $recording -PathType Container)){throw 'RecordingDirectory must be a directory.'}
$recordPrefix=$recording.TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar
$metadataPath=Join-Path $recording 'recording.json'
if(-not $OutputPath){$OutputPath=Join-Path $recording 'recording.mp4'}
$OutputPath=[IO.Path]::GetFullPath($OutputPath)
if(-not $OutputPath.StartsWith($recordPrefix,[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetExtension($OutputPath) -ne '.mp4'){throw 'OutputPath must be an MP4 in this owned recording directory.'}
if((Test-Path -LiteralPath $OutputPath) -and -not $Force){throw 'Video exists; choose another OutputPath or use -Force.'}
function Test-CompletePng([string]$File){
    if(-not(Test-Path -LiteralPath $File -PathType Leaf)){return $false}
    $stream=$null
    try{
        $stream=[IO.File]::Open($File,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
        if($stream.Length -lt 36){return $false}
        $header=New-Object byte[] 8
        if($stream.Read($header,0,8) -ne 8 -or [BitConverter]::ToString($header) -ne '89-50-4E-47-0D-0A-1A-0A'){return $false}
        $null=$stream.Seek(-12,[IO.SeekOrigin]::End);$tail=New-Object byte[] 12
        return $stream.Read($tail,0,12) -eq 12 -and [BitConverter]::ToString($tail) -eq '00-00-00-00-49-45-4E-44-AE-42-60-82'
    }catch{return $false}finally{if($stream){$stream.Dispose()}}
}
$timer=[Diagnostics.Stopwatch]::StartNew();$metadata=$null;$frames=@()
while($timer.Elapsed.TotalSeconds -lt $WaitSeconds){
    try{$candidate=Get-Content -Raw -LiteralPath $metadataPath | ConvertFrom-Json}catch{$candidate=$null}
    if($candidate){
        if($candidate.state -eq 'failed'){throw "Native recording failed: $($candidate.error)"}
        if($candidate.state -eq 'capture-complete'){
            $frames=@($candidate.frames | Sort-Object index)
            if($frames.Count -lt 2){throw 'Recording needs at least two captured frames.'}
            $complete=$true
            foreach($frame in $frames){
                $frameFile=[IO.Path]::GetFullPath([string]$frame.path)
                if(-not $frameFile.StartsWith($recordPrefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Frame path escapes the owned recording directory.'}
                if(-not(Test-CompletePng $frameFile)){$complete=$false;break}
            }
            if($complete){$metadata=$candidate;break}
        }
    }
    Start-Sleep -Milliseconds 100
}
if(-not $metadata){throw "Recording or complete PNGs unavailable within ${WaitSeconds}s."}
if(-not $FfmpegPath){
    $ffmpegCommand=Get-Command ffmpeg -ErrorAction SilentlyContinue
    if($ffmpegCommand){$FfmpegPath=$ffmpegCommand.Source}else{
        $videoTools=Join-Path (Split-Path $PSScriptRoot -Parent) 'work/video-tools'
        $bundledFfmpeg=Join-Path $videoTools 'imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
        if(Test-Path -LiteralPath $bundledFfmpeg -PathType Leaf){$FfmpegPath=$bundledFfmpeg}else{
            $candidate=Get-ChildItem -LiteralPath $videoTools -Recurse -File -Filter '*ffmpeg*.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
            if($candidate){$FfmpegPath=$candidate.FullName}
        }
    }
}
if(-not $FfmpegPath -or -not(Test-Path -LiteralPath $FfmpegPath -PathType Leaf)){throw 'ffmpeg unavailable; supply -FfmpegPath or install it under work/video-tools.'}
$invariant=[Globalization.CultureInfo]::InvariantCulture
$concat=[Collections.Generic.List[string]]::new();$concat.Add('ffconcat version 1.0')
for($i=0;$i -lt $frames.Count;$i++){
    $seconds=[double]$frames[$i].timeSeconds
    $next=if($i+1 -lt $frames.Count){[double]$frames[$i+1].timeSeconds}else{[double]$metadata.durationSeconds}
    if(-not [double]::IsFinite($seconds) -or -not [double]::IsFinite($next) -or $next -le $seconds){throw 'Frame timeline must be finite and strictly increasing.'}
    $duration=$next-$seconds
    $safePath=([IO.Path]::GetFullPath([string]$frames[$i].path)).Replace('\','/').Replace("'","'\''")
    $concat.Add("file '$safePath'");$concat.Add('duration '+$duration.ToString('0.000000000',$invariant))
}
$lastPath=([IO.Path]::GetFullPath([string]$frames[-1].path)).Replace('\','/').Replace("'","'\''")
$concat.Add("file '$lastPath'")
$concatPath=Join-Path $recording 'frames.ffconcat'
[IO.File]::WriteAllLines($concatPath,$concat,[Text.UTF8Encoding]::new($false))
$videoDuration=[double]$metadata.durationSeconds-[double]$frames[0].timeSeconds
$arguments=@('-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',$concatPath,'-fps_mode','vfr','-t',$videoDuration.ToString('0.000000000',$invariant),'-vf','pad=ceil(iw/2)*2:ceil(ih/2)*2','-c:v','libx264','-preset','fast','-crf','18','-pix_fmt','yuv420p','-movflags','+faststart',$OutputPath)
& $FfmpegPath @arguments
if($LASTEXITCODE -ne 0 -or -not(Test-Path -LiteralPath $OutputPath -PathType Leaf)){throw 'ffmpeg video encoding failed.'}
$report=[ordered]@{video=$OutputPath;frames=$frames.Count;timelineMode='VFR from native frame timestamps';durationSeconds=$videoDuration;sourceMetadata=$metadataPath;desktopCapture=$false;sha256=(Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash}
$reportPath=Join-Path $recording 'encoding.json'
[IO.File]::WriteAllText($reportPath,($report | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$report | ConvertTo-Json
