<#
.SYNOPSIS
  Stops everything scripts\start-tunnels.ps1 started: both cloudflared tunnels and
  the frontend dev server. Docker containers are left running (stop those yourself
  with `docker compose stop` in Hotel-system if you want them down too).
#>

$ErrorActionPreference = 'SilentlyContinue'

$logDir  = Join-Path $env:TEMP 'folio-tunnels'
$pidFile = Join-Path $logDir 'pids.json'

if (Test-Path $pidFile) {
    $ids = Get-Content $pidFile | ConvertFrom-Json
    foreach ($p in @($ids.backendTunnelPid, $ids.frontendTunnelPid)) {
        if ($p) { Stop-Process -Id $p -Force }
    }
    # frontendDevPid is cmd.exe, which spawned npm.cmd, which spawned the actual
    # node/vite process — Stop-Process only kills cmd.exe itself and leaves vite
    # running in the background, so this needs taskkill's /T (tree) instead.
    if ($ids.frontendDevPid) {
        taskkill /PID $ids.frontendDevPid /T /F 2>$null
    }
    Remove-Item $pidFile
}

# Catch-all in case a PID from the file already recycled to something else. Only
# cloudflared, not node — killing every node.exe on the machine would also take out
# any unrelated Node process you happen to have running.
Get-Process cloudflared | Stop-Process -Force

# start-tunnels.ps1 wrote the (now dead) tunnel URLs into both .env files. Put them back
# to local values, otherwise the next plain local run (docker compose up / npm run dev)
# talks to a tunnel that no longer exists and gets blocked by CORS.
$root = Split-Path -Parent $PSScriptRoot
function Reset-EnvValue {
    param([string]$EnvPath, [string]$Key, [string]$Value)
    if (-not (Test-Path $EnvPath)) { return }
    $content = Get-Content $EnvPath
    if ($content -match "^$Key=") {
        $content = $content -replace "^$Key=.*", "$Key=$Value"
        Set-Content -Path $EnvPath -Value $content
    }
}
# Empty = docker-compose.yml's default localhost allow-list.
Reset-EnvValue -EnvPath (Join-Path $root 'Hotel-system\.env') -Key 'CORS_ALLOWED_ORIGINS' -Value ''
Reset-EnvValue -EnvPath (Join-Path $root 'frontend\.env') -Key 'VITE_API_BASE_URL' -Value 'http://localhost:8081'

# Recreate the API container so it picks up the local CORS list again.
Push-Location $root
docker compose up -d app 2>$null | Out-Null
Pop-Location

Write-Host "Tunnels and dev server stopped; .env files reset to local URLs. Docker containers are still running." -ForegroundColor Yellow
