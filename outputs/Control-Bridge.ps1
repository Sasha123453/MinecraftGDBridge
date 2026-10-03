param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('build-mode','export','restart','jump','gd-pause','shutdown-gd','quit','resume-world','load-level','load-level-data','set-time','camera','screenshot','shaders','open-world','set-demo','import-blueprint','scenery','move-marker','music-config','diagnostic-noclip','record','visual-style')]
    [string]$Action,
    [ValidateLength(1,96)][string]$Nonce=([guid]::NewGuid().ToString('N')),
    [string]$PayloadJson='{}',
    [string]$PayloadFile,
    [ValidateRange(1,10)][int]$WaitSeconds=10
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Runtime-Paths.ps1')
$mcProfilePath=Join-Path (Get-BridgeRuntimeOutputs) 'launcher\data\instances\gdbridge\.minecraft'
$controlDir=Join-Path $mcProfilePath 'config\gdbridge'
if($PayloadFile -and $PSBoundParameters.ContainsKey('PayloadJson')){throw 'Choose PayloadJson or PayloadFile, not both.'}
$json=if($PayloadFile){Get-Content -Raw -LiteralPath $PayloadFile}else{$PayloadJson}
try{$payload=$json | ConvertFrom-Json}catch{throw "Invalid JSON payload: $($_.Exception.Message)"}
if($payload -isnot [pscustomobject]){throw 'The payload must be a JSON object.'}
$request=[ordered]@{}
foreach($property in $payload.PSObject.Properties){$request[$property.Name]=$property.Value}
$request['nonce']=$Nonce;$request['action']=$Action
if(-not(Test-Path -LiteralPath $mcProfilePath -PathType Container)){throw 'The isolated Minecraft profile does not exist.'}
New-Item -ItemType Directory -Path $controlDir -Force | Out-Null
$control=Join-Path $controlDir 'control.json'
$temporary=Join-Path $controlDir ('control-'+[guid]::NewGuid().ToString('N')+'.tmp')
try{
    [IO.File]::WriteAllText($temporary,($request | ConvertTo-Json -Depth 30 -Compress),[Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporary,$control,$true)
}finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary -Force}}
$timer=[Diagnostics.Stopwatch]::StartNew()
$statusFile=Join-Path $controlDir 'control-status.json'
$ack=$null
while($timer.Elapsed.TotalSeconds -lt $WaitSeconds){
    $candidate=$null
    if(Test-Path -LiteralPath $statusFile){try{$candidate=Get-Content -Raw -LiteralPath $statusFile | ConvertFrom-Json}catch{}}
    if($candidate -and [string]$candidate.nonce -ceq $Nonce){$ack=$candidate;break}
    Start-Sleep -Milliseconds 100
}
if(-not $ack){throw "Minecraft did not acknowledge '$Action' nonce=$Nonce within ${WaitSeconds}s. It must be running the control-enabled bridge (0.3)."}
if($ack.PSObject.Properties['error']){throw "Minecraft rejected '$Action': $($ack.error)"}
if([string]$ack.result -match '^GD disconnected|^Control error'){throw "Minecraft could not apply '$Action': $($ack.result)"}
$screenshot=$null
if($Action -eq 'screenshot'){
    $filename='bridge-'+[regex]::Replace($Nonce,'[^A-Za-z0-9_-]','_')+'.png'
    $screenshot=Join-Path $mcProfilePath ('screenshots\'+$filename)
    $complete=$false
    while($timer.Elapsed.TotalSeconds -lt $WaitSeconds){
        if(Test-Path -LiteralPath $screenshot -PathType Leaf){
            $stream=$null
            try{
                $stream=[IO.File]::Open($screenshot,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
                if($stream.Length -ge 24){
                    $null=$stream.Seek(-12,[IO.SeekOrigin]::End);$tail=New-Object byte[] 12
                    if($stream.Read($tail,0,12) -eq 12){$complete=([BitConverter]::ToString($tail) -eq '00-00-00-00-49-45-4E-44-AE-42-60-82')}
                }
            }catch{}finally{if($stream){$stream.Dispose()}}
        }
        if($complete){break}
        Start-Sleep -Milliseconds 100
    }
    if(-not $complete){throw "Screenshot acknowledged, but the complete native PNG was not written within ${WaitSeconds}s: $screenshot"}
}
[pscustomobject]@{nonce=$Nonce;action=$Action;acknowledgement=$ack;screenshot=$screenshot;elapsedMs=$timer.ElapsedMilliseconds;note='Acknowledgement confirms the API request; queued world/export operations may finish afterward.'} | ConvertTo-Json -Depth 15
