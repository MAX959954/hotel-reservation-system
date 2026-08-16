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

Write-Host "Tunnels and dev server stopped. Docker containers are still running." -ForegroundColor Yellow
