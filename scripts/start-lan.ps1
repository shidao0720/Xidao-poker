param(
    [ValidateRange(1, 65535)][int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$targetDirectory = Join-Path $projectRoot 'backend\target'
function Test-SpringBootJar {
    param([Parameter(Mandatory = $true)][System.IO.FileInfo]$File)

    $temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('xidao-manifest-' + [guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $temporaryDirectory -Force | Out-Null
        Push-Location $temporaryDirectory
        & jar.exe xf $File.FullName META-INF/MANIFEST.MF 2>$null
        $manifestPath = Join-Path $temporaryDirectory 'META-INF\MANIFEST.MF'
        if (-not (Test-Path -LiteralPath $manifestPath)) {
            return $false
        }
        $content = Get-Content -LiteralPath $manifestPath -Raw
        return $content -match '(?m)^Main-Class:\s*org\.springframework\.boot\.loader\.' -and
            $content -match '(?m)^Start-Class:\s*[^\r\n]+'
    } catch {
        return $false
    } finally {
        Pop-Location -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Find-SpringBootJar {
    if (-not (Test-Path -LiteralPath $targetDirectory)) {
        return $null
    }
    foreach ($candidate in (Get-ChildItem -LiteralPath $targetDirectory -Filter 'xidao-poker-*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending)) {
        if (Test-SpringBootJar -File $candidate) {
            return $candidate
        }
    }
    return $null
}

$jar = Find-SpringBootJar
if ($null -eq $jar) {
    Write-Host 'No executable Spring Boot package found; building it first...'
    & (Join-Path $PSScriptRoot 'build-lan.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw 'LAN build failed'
    }
    $jar = Find-SpringBootJar
}

if ($null -eq $jar) {
    throw 'No executable Spring Boot JAR was produced. Check the Maven spring-boot:repackage step and ensure no old JAR is locked by another process.'
}

Write-Host 'Starting Xidao Poker LAN server.'
Write-Host 'Keep this window open. Press Ctrl+C to stop the server.'
& java.exe -jar $jar.FullName "--server.port=$Port" '--server.address=0.0.0.0'
if ($LASTEXITCODE -ne 0) {
    throw "LAN server exited with code $LASTEXITCODE"
}
