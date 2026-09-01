$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env.lan'
$exampleFile = Join-Path $projectRoot '.env.lan.example'
$dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'

function Get-LanAddressCandidates {
    $candidates = foreach ($networkInterface in [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
        if ($networkInterface.OperationalStatus -ne [System.Net.NetworkInformation.OperationalStatus]::Up) { continue }
        if ($networkInterface.NetworkInterfaceType -eq [System.Net.NetworkInformation.NetworkInterfaceType]::Loopback) { continue }

        $description = ($networkInterface.Name + ' ' + $networkInterface.Description).ToLowerInvariant()
        $priority = if ($description -match 'wi-fi|wifi|wireless|wlan|ethernet|以太网|无线') { 0 }
            elseif ($description -match 'docker|vmware|virtualbox|hyper-v|vpn|tunnel|vethernet|wsl') { 2 }
            else { 1 }

        foreach ($addressInfo in $networkInterface.GetIPProperties().UnicastAddresses) {
            $address = $addressInfo.Address
            if ($address.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) { continue }
            $bytes = $address.GetAddressBytes()
            $private = $bytes[0] -eq 10 -or
                ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
                ($bytes[0] -eq 192 -and $bytes[1] -eq 168)
            if ($private) {
                [pscustomobject]@{
                    Priority = $priority
                    Interface = $networkInterface.Name
                    Address = $address.ToString()
                }
            }
        }
    }
    return @($candidates | Sort-Object Priority, Interface, Address -Unique)
}

function Get-ConfiguredHttpPort {
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^\s*POKER_HTTP_PORT\s*=\s*(\d+)\s*$') {
            return [int]$Matches[1]
        }
    }
    return 8080
}

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
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose status check exited with code $LASTEXITCODE"
    }

    $httpPort = Get-ConfiguredHttpPort
    Write-Host ''
    Write-Host "Local browser: http://localhost:$httpPort" -ForegroundColor Cyan
    $addresses = Get-LanAddressCandidates
    if ($addresses.Count -eq 0) {
        Write-Warning 'No private LAN IPv4 address was found. Check Wi-Fi/Ethernet connection and firewall.'
    } else {
        Write-Host 'Other devices on the same LAN can use:' -ForegroundColor Cyan
        foreach ($candidate in $addresses) {
            Write-Host ("  http://{0}:{1}  ({2})" -f $candidate.Address, $httpPort, $candidate.Interface)
        }
    }
} finally {
    Pop-Location
}
