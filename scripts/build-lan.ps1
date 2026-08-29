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
        Invoke-Checked mvn.cmd --batch-mode --no-transfer-progress '-DskipTests' clean package
    } else {
        Invoke-Checked mvn.cmd --batch-mode --no-transfer-progress clean package
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

$temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('xidao-manifest-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $temporaryDirectory -Force | Out-Null
    Push-Location $temporaryDirectory
    & jar.exe xf $jar.FullName META-INF/MANIFEST.MF 2>$null
    $manifestPath = Join-Path $temporaryDirectory 'META-INF\MANIFEST.MF'
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        throw 'The generated JAR has no manifest'
    }
    $manifestContent = Get-Content -LiteralPath $manifestPath -Raw
    if ($manifestContent -notmatch '(?m)^Main-Class:\s*org\.springframework\.boot\.loader\.' -or
        $manifestContent -notmatch '(?m)^Start-Class:\s*[^\r\n]+') {
        throw 'The generated JAR is not an executable Spring Boot JAR (missing Main-Class or Start-Class)'
    }
} finally {
    Pop-Location -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "LAN package ready: $($jar.FullName)"
