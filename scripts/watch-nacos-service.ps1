param(
    [int]$DurationSeconds = 180,
    [int]$PollMilliseconds = 500,
    [string]$ServiceName = 'service-passenger-user',
    [string]$NacosAddress = 'http://localhost:8848',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'

if ($DurationSeconds -lt 1 -or $PollMilliseconds -lt 100) {
    throw 'DurationSeconds must be positive and PollMilliseconds must be at least 100.'
}

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
if (-not $OutputPath) {
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputPath = Join-Path $ProjectRoot "logs\nacos-service-instance-watch-$timestamp.jsonl"
}

$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$uri = "$($NacosAddress.TrimEnd('/'))/nacos/v1/ns/instance/list?serviceName=$([uri]::EscapeDataString($ServiceName))&groupName=DEFAULT_GROUP&namespaceId=public&healthyOnly=false"
$stopAt = (Get-Date).AddSeconds($DurationSeconds)

Write-Host "Recording $ServiceName instances for $DurationSeconds seconds."
Write-Host "Output: $OutputPath"

while ((Get-Date) -lt $stopAt) {
    $record = [ordered]@{
        observedAt = (Get-Date).ToString('o')
        serviceName = $ServiceName
        hosts = @()
        error = $null
    }

    try {
        $response = Invoke-RestMethod -Uri $uri -TimeoutSec 3
        $record.hosts = @($response.hosts | ForEach-Object {
            [ordered]@{
                ip = $_.ip
                port = $_.port
                healthy = $_.healthy
                enabled = $_.enabled
                weight = $_.weight
            }
        })
    } catch {
        $record.error = $_.Exception.Message
    }

    ($record | ConvertTo-Json -Depth 6 -Compress) | Add-Content -LiteralPath $OutputPath -Encoding UTF8
    Start-Sleep -Milliseconds $PollMilliseconds
}

Write-Host 'Instance watch complete.'
