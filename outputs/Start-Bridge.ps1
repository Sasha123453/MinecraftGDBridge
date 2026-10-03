$ErrorActionPreference = 'Stop'
$steamCandidates = @()
try {
    $steamSetting = Get-ItemProperty -LiteralPath 'HKCU:\Software\Valve\Steam' -ErrorAction Stop
    if ($steamSetting.SteamExe) { $steamCandidates += $steamSetting.SteamExe }
} catch {}
$steamCandidates += @('C:\Program Files (x86)\Steam\steam.exe', 'C:\Program Files\Steam\steam.exe', 'D:\Steam\steam.exe')
$steamExe = $steamCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $steamExe) { throw 'Steam.exe not found. Install Steam, or add its path to steamCandidates in this file.' }
$gdMod = 'D:\SteamLibrary\steamapps\common\Geometry Dash\geode\mods\experiment.minecraft_bridge.geode'
if (-not (Test-Path -LiteralPath $gdMod)) { Write-Warning 'Geometry Dash bridge mod has not been found at the installed game location. See README.txt.' }
$bridgeListening = $false
try { $bridgeListening = @([System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -eq 18471 -and $_.Address.ToString() -eq '127.0.0.1' }).Count -gt 0 } catch {}
if (-not $bridgeListening) { & (Join-Path $PSScriptRoot 'launcher\Start-Minecraft.ps1') }
if (-not (Get-Process -Name GeometryDash -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath $steamExe -ArgumentList @('-applaunch', '322170') -WindowStyle Hidden
}
Write-Host 'Minecraft profile requested; Geometry Dash requested through Steam. In GD use MC Bridge POC, then play normally.'
Write-Host 'Read-BridgeStatus.ps1 reports the received live state without controlling the games.'
