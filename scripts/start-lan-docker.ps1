$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env.lan'
$exampleFile = Join-Path $projectRoot '.env.lan.example'

if (-not (Test-Path -LiteralPath $environmentFile)) {
    Copy-Item -LiteralPath $exampleFile -Destination $environmentFile
    throw 'Created .env.lan. Replace its database password, then run lan-docker-start.bat again.'
}

Push-Location $projectRoot
try {
    & docker.exe compose --env-file .env.lan -f compose.lan.yml up --build
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose exited with code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
