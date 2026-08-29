param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDirectory = Join-Path $projectRoot 'frontend'
$backendDirectory = Join-Path $projectRoot 'backend'

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command exited with code $LASTEXITCODE"
    }
}

Write-Host 'Building LAN frontend...'
Push-Location $frontendDirectory
try {
    Invoke-Checked npm.cmd ci
    if (-not $SkipTests) {
        Invoke-Checked npm.cmd test
        Invoke-Checked npm.cmd run lint
    }
    Invoke-Checked npm.cmd run build
} finally {
    Pop-Location
}

Write-Host 'Packaging Spring Boot with the frontend on the same port...'
Push-Location $backendDirectory
try {
    if ($SkipTests) {
        Invoke-Checked mvn.cmd --batch-mode --no-transfer-progress '-DskipTests' package
    } else {
        Invoke-Checked mvn.cmd --batch-mode --no-transfer-progress package
    }
} finally {
    Pop-Location
}

$jar = Get-ChildItem -LiteralPath (Join-Path $backendDirectory 'target') -Filter 'xidao-poker-*.jar' |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) {
    throw 'LAN package was not created'
}

Write-Host "LAN package ready: $($jar.FullName)"
