$bridgeLocalSettings=Join-Path (Split-Path -Parent $PSScriptRoot) '.local.json'
$script:bridgeRuntimeRoot=if(Test-Path -LiteralPath $bridgeLocalSettings){
    [IO.Path]::GetFullPath((Get-Content -Raw -LiteralPath $bridgeLocalSettings | ConvertFrom-Json).runtimeRoot)
}else{[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))}
function Get-BridgeRuntimeRoot { $script:bridgeRuntimeRoot }
function Get-BridgeRuntimeOutputs { Join-Path (Get-BridgeRuntimeRoot) 'outputs' }
