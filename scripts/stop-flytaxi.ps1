$ErrorActionPreference = 'Continue'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$PidRoot = Join-Path $ProjectRoot '.run'

if (-not (Test-Path $PidRoot)) {
    Write-Host "No pid directory found: $PidRoot"
    exit 0
}

Get-ChildItem $PidRoot -Filter '*.pid' | ForEach-Object {
    $name = $_.BaseName
    $pidValue = Get-Content $_.FullName -Raw
    $pidValue = $pidValue.Trim()

    if (-not $pidValue) { return }

    $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    if ($process) {
        Write-Host "Stopping $name, pid $pidValue."
        Stop-Process -Id $pidValue -Force
    } else {
        Write-Host "$name is not running, pid $pidValue not found."
    }

    Remove-Item $_.FullName -Force
}

$AppPorts = 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 9000, 9001
foreach ($port in $AppPorts) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($connection in $connections) {
        $process = Get-Process -Id $connection.OwningProcess -ErrorAction SilentlyContinue
        if ($process) {
            Write-Host "Stopping process on port $port, pid $($process.Id), name $($process.ProcessName)."
            Stop-Process -Id $process.Id -Force
        }
    }
}
