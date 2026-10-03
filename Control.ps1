param([Parameter(Mandatory=$true)][string]$Action,[string]$PayloadJson='{}')
$ErrorActionPreference='Stop'
$localSettings=Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '.local.json') | ConvertFrom-Json
& (Join-Path $localSettings.runtimeRoot 'outputs/Control-Bridge.ps1') -Action $Action -PayloadJson $PayloadJson
