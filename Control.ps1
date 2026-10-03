param([Parameter(Mandatory=$true)][string]$Action,[string]$PayloadJson='{}')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'outputs/Control-Bridge.ps1') -Action $Action -PayloadJson $PayloadJson
