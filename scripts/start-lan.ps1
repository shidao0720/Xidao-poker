param(
    [ValidateRange(1, 65535)][int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$targetDirectory = Join-Path $projectRoot 'backend\target'
$jar = $null
if (Test-Path -LiteralPath $targetDirectory) {
    $jar = Get-ChildItem -LiteralPath $targetDirectory -Filter 'xidao-poker-*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

if ($null -eq $jar) {
    Write-Host 'No LAN package found; building it first...'
    & (Join-Path $PSScriptRoot 'build-lan.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw 'LAN build failed'
    }
    $jar = Get-ChildItem -LiteralPath $targetDirectory -Filter 'xidao-poker-*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

Write-Host 'Starting Xidao Poker LAN server.'
Write-Host 'Keep this window open. Press Ctrl+C to stop the server.'
& java.exe -jar $jar.FullName "--server.port=$Port" '--server.address=0.0.0.0'
if ($LASTEXITCODE -ne 0) {
    throw "LAN server exited with code $LASTEXITCODE"
}
