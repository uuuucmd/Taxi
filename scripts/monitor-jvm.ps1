param(
    [int]$IntervalSeconds = 5,
    [int]$Count = 0,
    [string]$CsvPath = '',
    [switch]$Clear,
    [int[]]$Ports = @(8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 9000, 9001)
)

$ErrorActionPreference = 'Stop'

if ($IntervalSeconds -lt 1) {
    throw 'IntervalSeconds must be at least 1.'
}

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')

$NameByPort = @{
    8080 = 'api-gateway'
    8081 = 'api-passenger'
    8082 = 'service-verificationCode'
    8083 = 'service-passenger-user'
    8084 = 'service-price'
    8085 = 'service-map'
    8086 = 'service-driver-user'
    8087 = 'api-boss'
    8088 = 'api-driver'
    8089 = 'service-order'
    9000 = 'service-sse-push'
    9001 = 'service-pay'
}

function Resolve-Jstat {
    $command = Get-Command jstat -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\jstat.exe'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }

    $candidate = Join-Path $ProjectRoot '.tools\jdk-17.0.20.1+1\bin\jstat.exe'
    if (Test-Path -LiteralPath $candidate) { return $candidate }

    throw 'jstat not found. Add the JDK bin directory to PATH or set JAVA_HOME.'
}

function Read-GcSample {
    param(
        [string]$Jstat,
        [int]$ProcessId
    )

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Jstat -gc $ProcessId 1 1 2>$null
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) { return $null }
    $lines = @($output | ForEach-Object { $_.ToString() })

    $header = $lines | Where-Object { $_ -match '^\s*S0C\s' } | Select-Object -First 1
    $values = $lines | Where-Object { $_ -match '^\s*\d' } | Select-Object -Last 1
    if (-not $header -or -not $values) { return $null }

    $names = $header.Trim() -split '\s+'
    $numbers = $values.Trim() -split '\s+'
    if ($names.Count -ne $numbers.Count) { return $null }

    $sample = @{}
    for ($i = 0; $i -lt $names.Count; $i++) {
        $sample[$names[$i]] = [double]$numbers[$i]
    }
    return $sample
}

$Jstat = Resolve-Jstat
$CsvEnabled = -not [string]::IsNullOrWhiteSpace($CsvPath)
if ($CsvEnabled) {
    $csvDirectory = Split-Path -Parent $CsvPath
    if ($csvDirectory) { New-Item -ItemType Directory -Force -Path $csvDirectory | Out-Null }
}

$previousCpu = @{}
$round = 0

while ($true) {
    $round++
    $timestamp = Get-Date
    if ($Clear) { Clear-Host }
    Write-Host "JVM monitor  $($timestamp.ToString('yyyy-MM-dd HH:mm:ss'))  interval=${IntervalSeconds}s  (Ctrl+C to stop)"

    $rows = foreach ($port in $Ports) {
        $name = if ($NameByPort.ContainsKey($port)) { $NameByPort[$port] } else { "port-$port" }
        $connection = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $connection) {
            [PSCustomObject]@{
                Service = $name
                Port = $port
                Pid = '-'
                Status = 'DOWN'
                Heap = '-'
                Old = '-'
                YGC = '-'
                YGCT = '-'
                FGC = '-'
                FGCT = '-'
                CGC = '-'
                CGCT = '-'
                GC = '-'
                Cpu = '-'
            }
            continue
        }

        $processId = [int]$connection.OwningProcess
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        $cpuPercent = '-'
        if ($process) {
            $cpuSeconds = $process.CPU
            if ($previousCpu.ContainsKey($processId)) {
                $delta = $cpuSeconds - $previousCpu[$processId]
                $cpuPercent = '{0:N1}' -f (($delta / $IntervalSeconds) * 100)
            }
            $previousCpu[$processId] = $cpuSeconds
        }

        $sample = Read-GcSample -Jstat $Jstat -ProcessId $processId
        if (-not $sample) {
            [PSCustomObject]@{
                Service = $name
                Port = $port
                Pid = $processId
                Status = 'NO-DATA'
                Heap = '-'
                Old = '-'
                YGC = '-'
                YGCT = '-'
                FGC = '-'
                FGCT = '-'
                CGC = '-'
                CGCT = '-'
                GC = '-'
                Cpu = $cpuPercent
            }
            continue
        }

        $heapUsedKb = $sample['S0U'] + $sample['S1U'] + $sample['EU'] + $sample['OU']
        $heapCommittedKb = $sample['S0C'] + $sample['S1C'] + $sample['EC'] + $sample['OC']
        $oldPercent = if ($sample['OC'] -gt 0) { ($sample['OU'] / $sample['OC']) * 100 } else { 0 }

        [PSCustomObject]@{
            Service = $name
            Port = $port
            Pid = $processId
            Status = 'OK'
            Heap = '{0:N0}/{1:N0}M' -f ($heapUsedKb / 1024), ($heapCommittedKb / 1024)
            Old = '{0:N1}%' -f $oldPercent
            YGC = '{0:N0}' -f $sample['YGC']
            YGCT = '{0:N2}s' -f $sample['YGCT']
            FGC = '{0:N0}' -f $sample['FGC']
            FGCT = '{0:N2}s' -f $sample['FGCT']
            CGC = '{0:N0}' -f $sample['CGC']
            CGCT = '{0:N2}s' -f $sample['CGCT']
            GC = '{0:N2}s' -f $sample['GCT']
            Cpu = $cpuPercent
        }
    }

    $format = '{0,-25} {1,5} {2,6} {3,-7} {4,-11} {5,6} {6,5} {7,7} {8,5} {9,7} {10,7} {11,6}'
    Write-Host ($format -f 'Service', 'Port', 'Pid', 'Status', 'Heap', 'Old', 'YGC', 'YGCT', 'FGC', 'FGCT', 'GCT', 'CPU%')
    foreach ($row in $rows) {
        Write-Host ($format -f $row.Service, $row.Port, $row.Pid, $row.Status, $row.Heap, $row.Old, $row.YGC, $row.YGCT, $row.FGC, $row.FGCT, $row.GC, $row.Cpu)
    }

    if ($CsvEnabled) {
        $rows | Select-Object @{n = 'Time'; e = { $timestamp.ToString('yyyy-MM-dd HH:mm:ss') } }, * |
            Export-Csv -LiteralPath $CsvPath -Append -NoTypeInformation -Encoding UTF8
        Write-Host "Appended to $CsvPath"
    }

    if ($rows.Status -contains 'NO-DATA') {
        Write-Host 'NO-DATA means the JVM has no jvmstat file. Restart that service once; see logs for details.' -ForegroundColor Yellow
    }

    if ($Count -gt 0 -and $round -ge $Count) { break }
    Start-Sleep -Seconds $IntervalSeconds
}
