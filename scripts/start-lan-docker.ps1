$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env.lan'
$exampleFile = Join-Path $projectRoot '.env.lan.example'
$dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'

if (-not (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI was not found. Install Docker Desktop for Windows, then run lan-docker-start.bat again.'
}
if (-not (Test-Path -LiteralPath $dockerDesktop)) {
    throw 'Docker Desktop is not installed or its installation is damaged. Reinstall Docker Desktop for Windows, then run lan-docker-start.bat again.'
}

if (-not (Test-Path -LiteralPath $environmentFile)) {
    Copy-Item -LiteralPath $exampleFile -Destination $environmentFile
    throw 'Created .env.lan. Replace its database password, then run lan-docker-start.bat again.'
}

Push-Location $projectRoot
try {
    # Run detached so closing the launcher window does not stop the LAN service.
    & docker.exe compose --env-file .env.lan -f compose.lan.yml up --build --detach
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose exited with code $LASTEXITCODE"
    }
    & docker.exe compose --env-file .env.lan -f compose.lan.yml ps
} finally {
    Pop-Location
}
