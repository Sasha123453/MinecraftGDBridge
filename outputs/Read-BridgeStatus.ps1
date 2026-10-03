param([int]$Seconds = 5)
$ErrorActionPreference = 'Continue'
$mcStatusPath = Join-Path $PSScriptRoot 'launcher\data\instances\gdbridge\.minecraft\config\gdbridge\status.json'
$gdStatusPath = Join-Path $env:LOCALAPPDATA 'GeometryDash\geode\mods\experiment.minecraft_bridge\sender-status.json'
function Read-StatusFile([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        try {
            $file = Get-Item -LiteralPath $Path
            [PSCustomObject]@{ModifiedUtc=$file.LastWriteTimeUtc;FileAgeSeconds=[math]::Round(([datetime]::UtcNow-$file.LastWriteTimeUtc).TotalSeconds,2);State=(Get-Content -LiteralPath $Path -Raw -ErrorAction Stop | ConvertFrom-Json)}
        } catch { [PSCustomObject]@{Error=$_.Exception.Message} }
    }
}
$secondsToObserve = [math]::Max(1,[math]::Min(60,$Seconds))
$before = Read-StatusFile $mcStatusPath
Start-Sleep -Seconds $secondsToObserve
$after = Read-StatusFile $mcStatusPath
$workspaceRoot = Split-Path $PSScriptRoot -Parent
$bridgeJavaPath = Join-Path $workspaceRoot 'work\toolchains\jdk-17.0.20.1+1\bin\javaw.exe'
$bridgeJavaConsolePath = Join-Path $workspaceRoot 'work\toolchains\jdk-17.0.20.1+1\bin\java.exe'
$expectedJavaPaths = @($bridgeJavaPath,$bridgeJavaConsolePath,'P:\work\toolchains\jdk-17.0.20.1+1\bin\java.exe','P:\work\toolchains\jdk-17.0.20.1+1\bin\javaw.exe')
$javaProcesses = @(Get-Process -Name javaw,java -ErrorAction SilentlyContinue | Where-Object { $_.Path -in $expectedJavaPaths } | Select-Object Id,ProcessName,Path,MainWindowTitle)
$gdProcesses = @(Get-Process -Name GeometryDash -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq 'D:\SteamLibrary\steamapps\common\Geometry Dash\GeometryDash.exe' } | Select-Object Id,ProcessName,Path,MainWindowTitle)
$liveFramesObserved = $false
$playerPositionChanged = $false
if ($before -and $after -and $before.State -and $after.State) {
    $liveFramesObserved = $after.State.receivedFrames -gt $before.State.receivedFrames -and $after.FileAgeSeconds -lt 3 -and $after.State.staleAgeMs -lt 1000
    $playerPositionChanged = $after.State.x -ne $before.State.x -or $after.State.y -ne $before.State.y
}
[PSCustomObject]@{
    JavaProcesses=$javaProcesses
    GeometryDashProcesses=$gdProcesses
    ObservationSeconds=$secondsToObserve
    LiveFramesObserved=$liveFramesObserved
    PlayerPositionChanged=$playerPositionChanged
    MinecraftBefore=$before
    MinecraftAfter=$after
    GeometryDashSender=(Read-StatusFile $gdStatusPath)
} | ConvertTo-Json -Depth 8
