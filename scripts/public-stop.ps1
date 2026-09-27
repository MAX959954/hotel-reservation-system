<#
.SYNOPSIS
  Takes Folio offline: turns off Tailscale Funnel and stops the production stack
  started by public-start.ps1. The database is kept.

.PARAMETER DeleteData
  Also delete the database, uploads and Redis data (docker compose down -v).
  The demo hotels are recreated on the next start; registered users are lost.
#>
param([switch]$DeleteData)

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$compose = @('-f', 'deploy/docker-compose.prod.yml', '-f', 'deploy/docker-compose.home.yml', '--env-file', 'deploy/.env')

# 1. Stop publishing first, so nobody hits a half-stopped site.
$tailscale = (Get-Command tailscale -ErrorAction SilentlyContinue).Source
if (-not $tailscale) {
    $candidate = Join-Path $env:ProgramFiles 'Tailscale\tailscale.exe'
    if (Test-Path $candidate) { $tailscale = $candidate }
}
if ($tailscale) {
    Write-Host "==> Turning off Tailscale Funnel..." -ForegroundColor Cyan
    & $tailscale funnel --https=443 off
    if ($LASTEXITCODE -ne 0) { Write-Host "    (Funnel was not on - nothing to turn off.)" }
} else {
    Write-Host "Tailscale not found - skipping Funnel." -ForegroundColor Yellow
}

# 2. Stop the containers.
Push-Location $root
try {
    if ($DeleteData) {
        Write-Host "==> Stopping the stack and DELETING its data..." -ForegroundColor Cyan
        docker compose @compose down -v
    } else {
        Write-Host "==> Stopping the stack (data is kept)..." -ForegroundColor Cyan
        docker compose @compose down
    }
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose down failed (exit code $LASTEXITCODE). Is Docker Desktop running?"
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Folio is offline. Start it again with:  scripts\public-start.ps1" -ForegroundColor Green
