<#
.SYNOPSIS
  Starts Folio on this PC in production mode and publishes it on the internet through
  Tailscale Funnel (https://<machine>.<tailnet>.ts.net). Stop it with public-stop.ps1.

.DESCRIPTION
  1. starts Docker Desktop if it is not running;
  2. builds and starts the production stack (deploy/docker-compose.prod.yml +
     deploy/docker-compose.home.yml, settings from deploy/.env);
  3. waits until the API is healthy;
  4. turns on Tailscale Funnel for 127.0.0.1:8088.
  Can be run from any folder. Guide: deploy/README.md, "Host it from your own PC".

.PARAMETER NoBuild
  Skip rebuilding the images (faster start when the code has not changed).
#>
param([switch]$NoBuild)

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root 'deploy\.env'
$compose = @('-f', 'deploy/docker-compose.prod.yml', '-f', 'deploy/docker-compose.home.yml', '--env-file', 'deploy/.env')

function Test-DockerReady {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    try {
        docker info 2>$null 1>$null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Wait-ForDocker {
    param([int]$TimeoutSeconds = 180)
    if (Test-DockerReady) { return }

    Write-Host "==> Docker Desktop is not running - starting it..." -ForegroundColor Cyan
    $candidates = @(
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Docker\Docker\Docker Desktop.exe')
    )
    $dockerCli = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCli) {
        $installDir = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $dockerCli.Source))
        $candidates += (Join-Path $installDir 'Docker Desktop.exe')
    }
    $exe = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $exe) {
        throw "Docker Desktop.exe not found. Start Docker Desktop manually and run this script again."
    }
    Start-Process $exe
    for ($i = 0; $i -lt $TimeoutSeconds; $i += 3) {
        Start-Sleep -Seconds 3
        if (Test-DockerReady) {
            Write-Host "    Docker is ready." -ForegroundColor Green
            return
        }
    }
    throw "Docker Desktop did not start within ${TimeoutSeconds}s."
}

function Get-Tailscale {
    $cmd = Get-Command tailscale -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $exe = Join-Path $env:ProgramFiles 'Tailscale\tailscale.exe'
    if (Test-Path $exe) { return $exe }
    throw "Tailscale not found. Install it (winget install tailscale.tailscale), sign in, and run this script again."
}

function Wait-ForApp {
    param([int]$TimeoutSeconds = 240)
    Write-Host "==> Waiting for the API to become healthy (up to ${TimeoutSeconds}s)..." -ForegroundColor Cyan
    for ($i = 0; $i -lt $TimeoutSeconds; $i += 3) {
        try {
            $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8088/actuator/health' -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) {
                Write-Host "    API is up." -ForegroundColor Green
                return
            }
        } catch { }
        Start-Sleep -Seconds 3
    }
    Write-Host "The API did not become healthy. Last log lines:" -ForegroundColor Red
    Push-Location $root
    docker compose @compose logs app --tail 60
    Pop-Location
    throw "The API failed to start - see the log above."
}

# ---- Preflight ----
if (-not (Test-Path $envFile)) {
    throw "deploy\.env not found. Copy deploy\.env.example to deploy\.env and fill it in (deploy/README.md)."
}
$domainLine = Select-String -Path $envFile -Pattern '^DOMAIN=(.+)$' | Select-Object -First 1
if (-not $domainLine) { throw "DOMAIN is not set in deploy\.env." }
$domain = $domainLine.Matches[0].Groups[1].Value.Trim()
if ($domain -eq 'localhost') {
    throw "DOMAIN in deploy\.env is 'localhost'. Set it to your Funnel address, e.g. folio.tailf75b74.ts.net."
}
$tailscale = Get-Tailscale

Wait-ForDocker

# ---- Start the stack ----
Push-Location $root
try {
    if ($NoBuild) {
        Write-Host "==> Starting the stack (no rebuild)..." -ForegroundColor Cyan
        docker compose @compose up -d
    } else {
        Write-Host "==> Building and starting the stack (the first build takes several minutes)..." -ForegroundColor Cyan
        docker compose @compose up -d --build
    }
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed (exit code $LASTEXITCODE) - see the errors above." }
} finally {
    Pop-Location
}
Wait-ForApp

# ---- Publish ----
Write-Host "==> Turning on Tailscale Funnel..." -ForegroundColor Cyan
& $tailscale funnel --bg 8088
if ($LASTEXITCODE -ne 0) {
    throw "tailscale funnel failed. If it printed a link, open it, approve Funnel and run this script again."
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host " Folio is online:  https://$domain" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host " Stop it with:  scripts\public-stop.ps1"
