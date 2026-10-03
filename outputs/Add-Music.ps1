#Requires -Version 7.0
<#
.SYNOPSIS
Copies a local audio track into this workspace and selects it for native GD music.
.EXAMPLE
.\Add-Music.ps1
.EXAMPLE
.\Add-Music.ps1 -Path 'C:\Music\track.mp3' -Offset 2.5 -Volume 0.8 -Restart
.EXAMPLE
.\Add-Music.ps1 -Disable
.EXAMPLE
.\Add-Music.ps1 -Path 'C:\Music\track.ogg' -PrepareOnly
#>
param(
    [string]$Path,
    [ValidateRange(0.0,86400.0)][double]$Offset=0,
    [ValidateRange(0.0,1.0)][double]$Volume=1,
    [switch]$Disable,
    [switch]$Restart,
    [switch]$PrepareOnly
)
$ErrorActionPreference='Stop'
if([double]::IsNaN($Offset) -or [double]::IsInfinity($Offset) -or [double]::IsNaN($Volume) -or [double]::IsInfinity($Volume)){throw 'Offset and Volume must be finite numbers.'}
if($PrepareOnly -and $Restart){throw 'PrepareOnly cannot be combined with Restart.'}
if($Disable -and $Path){throw 'Disable does not take a Path.'}
$musicRoot=Join-Path $PSScriptRoot 'music'
$controlScript=Join-Path $PSScriptRoot 'Control-Bridge.ps1'
if(-not(Test-Path -LiteralPath $controlScript -PathType Leaf)){throw 'Control-Bridge.ps1 is missing beside this script.'}
$selected=$null
$source=$null
$sha256=$null
if(-not $Disable){
    if(-not $Path){
        if(-not ('BridgeMusicPicker' -as [type])){
            Add-Type -TypeDefinition @'
using System;
using System.Threading;
using System.Windows.Forms;
public static class BridgeMusicPicker {
    public static string Choose(string initialDirectory) {
        string selected = null;
        Exception failure = null;
        var thread = new Thread(() => {
            try {
                using (var dialog = new OpenFileDialog()) {
                    dialog.Title = "Choose music for the Minecraft GD bridge";
                    dialog.Filter = "Audio tracks (*.mp3;*.ogg;*.wav)|*.mp3;*.ogg;*.wav";
                    dialog.CheckFileExists = true;
                    dialog.Multiselect = false;
                    if (System.IO.Directory.Exists(initialDirectory)) dialog.InitialDirectory = initialDirectory;
                    if (dialog.ShowDialog() == DialogResult.OK) selected = dialog.FileName;
                }
            } catch (Exception error) { failure = error; }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure != null) throw failure;
        return selected;
    }
}
'@ -ReferencedAssemblies System.Windows.Forms,System.Threading.Thread,System.Threading,System.ComponentModel.Primitives
        }
        $Path=[BridgeMusicPicker]::Choose($musicRoot)
        if(-not $Path){Write-Host 'Music selection cancelled.';return}
    }
    $source=(Resolve-Path -LiteralPath $Path).ProviderPath
    $audio=Get-Item -LiteralPath $source
    if($audio.PSIsContainer){throw 'Path must identify an audio file.'}
    $extension=$audio.Extension.ToLowerInvariant()
    if($extension -notin @('.mp3','.ogg','.wav')){throw 'Choose an MP3, OGG or WAV file.'}
    if($audio.Length -lt 1 -or $audio.Length -gt 256MB){throw 'The audio file must be between 1 byte and 256 MiB.'}
    $sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    $stem=[regex]::Replace($audio.BaseName,'[^\p{L}\p{Nd}_-]','_')
    if($stem.Length -gt 80){$stem=$stem.Substring(0,80)}
    if(-not $stem){$stem='track'}
    New-Item -ItemType Directory -Path $musicRoot -Force | Out-Null
    $selected=Join-Path $musicRoot ($stem+'-'+$sha256.Substring(0,12).ToLowerInvariant()+$extension)
    if(Test-Path -LiteralPath $selected){
        if((Get-FileHash -LiteralPath $selected -Algorithm SHA256).Hash -ne $sha256){throw 'The destination name already exists with different content.'}
    }else{
        Copy-Item -LiteralPath $source -Destination $selected
        if((Get-FileHash -LiteralPath $selected -Algorithm SHA256).Hash -ne $sha256){throw 'The copied audio file failed its checksum verification.'}
    }
    $selected=(Resolve-Path -LiteralPath $selected).ProviderPath
}
New-Item -ItemType Directory -Path $musicRoot -Force | Out-Null
$configuration=[ordered]@{enabled=(-not [bool]$Disable);path=if($selected){$selected}else{''};offset=$Offset;volume=$Volume}
$configPath=Join-Path $musicRoot 'selected-music.json'
$temporary=Join-Path $musicRoot ('selected-music-'+[guid]::NewGuid().ToString('N')+'.tmp')
try{
    [IO.File]::WriteAllText($temporary,($configuration | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporary,$configPath,$true)
}finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary -Force}}
$acknowledgement=$null
$restartAcknowledgement=$null
if(-not $PrepareOnly){
    $acknowledgement=& $controlScript -Action music-config -PayloadFile $configPath
    if($Restart){$restartAcknowledgement=& $controlScript -Action restart}
}
[pscustomobject]@{
    enabled=$configuration.enabled
    source=$source
    audio=$selected
    sha256=$sha256
    configuration=$configPath
    offsetSeconds=$Offset
    volume=$Volume
    preparedOnly=[bool]$PrepareOnly
    restartRequested=[bool]$Restart
    acknowledgement=$acknowledgement
    restartAcknowledgement=$restartAcknowledgement
    note=if($PrepareOnly){'Prepared in the workspace. No bridge command was sent.'}else{'Music selection was relayed. It applies at the next native music start/restart; GD controls pause and synchronization. Native music requires GD bridge 0.3.3.'}
} | ConvertTo-Json -Depth 12
