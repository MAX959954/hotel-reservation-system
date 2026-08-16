<#
.SYNOPSIS
  Brings up the full local stack (Postgres/Redis/backend in Docker + the Vite dev
  server) and exposes both the backend and frontend to the internet via free
  Cloudflare quick tunnels, wiring the generated URLs into each side's .env
  automatically — the manual "run 4 terminals, copy-paste 2 URLs back and forth"
  dance this replaces.

.NOTES
  Quick tunnels (no Cloudflare account) hand out a random *.trycloudflare.com
  address every time they start — there is no way around re-wiring the URLs on
  every run, that part isn't a workaround-able limitation, just what this script
  automates instead of doing by hand.
#>

$ErrorActionPreference = 'Stop'

# Start-Process resolves the exe via CreateProcess, which doesn't always see the same
# PATH an interactive shell does right after a winget install — the full path sidesteps
# that entirely. Adjust if cloudflared lives somewhere else on your machine.
$cloudflared  = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'
$root         = Split-Path -Parent $PSScriptRoot
$backendDir   = Join-Path $root 'Hotel-system'
$frontendDir  = Join-Path $root 'frontend'
$logDir       = Join-Path $env:TEMP 'folio-tunnels'
$pidFile      = Join-Path $logDir 'pids.json'

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# Kill anything left over from a previous run — quick-tunnel URLs are single-use
# per process, so a stale cloudflared/npm from last time just gets in the way.
if (Test-Path $pidFile) {
    $old = Get-Content $pidFile | ConvertFrom-Json
    foreach ($p in @($old.backendTunnelPid, $old.frontendTunnelPid, $old.frontendDevPid)) {
        if ($p) { Stop-Process -Id $p -Force -ErrorAction SilentlyContinue }
    }
}
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

function Wait-ForTunnelUrl {
    # cloudflared logs to stderr, not stdout — Start-Process won't let both redirect to
    # the same file, so the two streams land in separate files and both get searched.
    param([string]$OutPath, [string]$ErrPath, [int]$TimeoutSeconds = 30)
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        foreach ($path in @($ErrPath, $OutPath)) {
            if (Test-Path $path) {
                $match = Select-String -Path $path -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue |
                         Select-Object -First 1
                if ($match) { return $match.Matches[0].Value }
            }
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for a trycloudflare.com URL in $ErrPath / $OutPath"
}

function Set-EnvValue {
    param([string]$EnvPath, [string]$Key, [string]$Value)
    $content = Get-Content $EnvPath
    $pattern = "^$Key="
    if ($content -match $pattern) {
        $content = $content -replace ($pattern + '.*'), "$Key=$Value"
    } else {
        $content += "$Key=$Value"
    }
    Set-Content -Path $EnvPath -Value $content
}

Write-Host "==> Starting Postgres, Redis, backend (Docker)..." -ForegroundColor Cyan
Push-Location $backendDir
docker compose up -d postgres redis app
Pop-Location

Write-Host "==> Starting backend tunnel..." -ForegroundColor Cyan
$backendOutLog = Join-Path $logDir 'backend.out.log'
$backendErrLog = Join-Path $logDir 'backend.err.log'
Remove-Item $backendOutLog, $backendErrLog -ErrorAction SilentlyContinue
$backendTunnel = Start-Process $cloudflared -ArgumentList 'tunnel --url http://localhost:8081' `
    -RedirectStandardOutput $backendOutLog -RedirectStandardError $backendErrLog -WindowStyle Hidden -PassThru
$backendUrl = Wait-ForTunnelUrl -OutPath $backendOutLog -ErrPath $backendErrLog
Write-Host "    Backend URL: $backendUrl" -ForegroundColor Green

Write-Host "==> Writing VITE_API_BASE_URL into frontend/.env..." -ForegroundColor Cyan
Set-EnvValue -EnvPath (Join-Path $frontendDir '.env') -Key 'VITE_API_BASE_URL' -Value $backendUrl

Write-Host "==> Starting frontend dev server..." -ForegroundColor Cyan
$frontendDevOutLog = Join-Path $logDir 'frontend-dev.out.log'
$frontendDevErrLog = Join-Path $logDir 'frontend-dev.err.log'
Remove-Item $frontendDevOutLog, $frontendDevErrLog -ErrorAction SilentlyContinue
# npm.cmd is a shell script, not a real Win32 executable — Start-Process needs cmd.exe
# as the actual process host to run it, same reason `&` call-operator tricks exist for
# invoking it directly from PowerShell.
$frontendDev = Start-Process cmd.exe -ArgumentList '/c', 'npm run dev' -WorkingDirectory $frontendDir `
    -RedirectStandardOutput $frontendDevOutLog -RedirectStandardError $frontendDevErrLog -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 4

Write-Host "==> Starting frontend tunnel..." -ForegroundColor Cyan
$frontendOutLog = Join-Path $logDir 'frontend.out.log'
$frontendErrLog = Join-Path $logDir 'frontend.err.log'
Remove-Item $frontendOutLog, $frontendErrLog -ErrorAction SilentlyContinue
$frontendTunnel = Start-Process $cloudflared -ArgumentList 'tunnel --url http://localhost:5173' `
    -RedirectStandardOutput $frontendOutLog -RedirectStandardError $frontendErrLog -WindowStyle Hidden -PassThru
$frontendUrl = Wait-ForTunnelUrl -OutPath $frontendOutLog -ErrPath $frontendErrLog
Write-Host "    Frontend URL: $frontendUrl" -ForegroundColor Green

Write-Host "==> Writing CORS_ALLOWED_ORIGINS into Hotel-system/.env and recreating the app container..." -ForegroundColor Cyan
Set-EnvValue -EnvPath (Join-Path $backendDir '.env') -Key 'CORS_ALLOWED_ORIGINS' -Value $frontendUrl
Push-Location $backendDir
docker compose up -d app
Pop-Location

@{
    backendTunnelPid  = $backendTunnel.Id
    frontendTunnelPid = $frontendTunnel.Id
    frontendDevPid    = $frontendDev.Id
} | ConvertTo-Json | Set-Content $pidFile

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host " Frontend:  $frontendUrl" -ForegroundColor Yellow
Write-Host " Backend:   $backendUrl" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host " Run scripts\stop-tunnels.ps1 to shut everything back down."
