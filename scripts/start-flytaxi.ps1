param(
    [switch]$Build
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$LogRoot = Join-Path $ProjectRoot 'logs'
$PidRoot = Join-Path $ProjectRoot '.run'

New-Item -ItemType Directory -Force -Path $LogRoot, $PidRoot | Out-Null

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) { return }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*(?:#|$)') { continue }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') { continue }
        $name = $matches[1]
        $value = $matches[2].Trim()
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

Import-DotEnv (Join-Path $ProjectRoot '.env')

$JavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if (-not $JavaHome) { $JavaHome = Join-Path $ProjectRoot '.tools\jdk-17.0.20.1+1' }

$MavenHome = [Environment]::GetEnvironmentVariable('MAVEN_HOME', 'User')
if (-not $MavenHome) { $MavenHome = 'E:\maven\apache-maven-3.10.0' }

$env:JAVA_HOME = $JavaHome
$env:MAVEN_HOME = $MavenHome
$env:M2_HOME = $MavenHome
$env:Path = "$JavaHome\bin;$MavenHome\bin;$env:Path"
$env:DB_USERNAME = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { '123456' }
$env:REDIS_HOST = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { 'localhost' }
$env:REDIS_PORT = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { '6379' }
$env:REDIS_DATABASE = if ($env:REDIS_DATABASE) { $env:REDIS_DATABASE } else { '0' }
$env:NACOS_SERVER_ADDR = if ($env:NACOS_SERVER_ADDR) { $env:NACOS_SERVER_ADDR } else { 'localhost:8848' }
$env:NACOS_USERNAME = if ($env:NACOS_USERNAME) { $env:NACOS_USERNAME } else { 'nacos' }
$env:NACOS_PASSWORD = if ($null -ne $env:NACOS_PASSWORD) { $env:NACOS_PASSWORD } else { '' }
$env:NACOS_DISCOVERY_IP = if ($env:NACOS_DISCOVERY_IP) { $env:NACOS_DISCOVERY_IP } else { '127.0.0.1' }
$env:AMAP_KEY = if ($env:AMAP_KEY) { $env:AMAP_KEY } else { '' }
$env:BOSS_ADMIN_USERNAME = if ($env:BOSS_ADMIN_USERNAME) { $env:BOSS_ADMIN_USERNAME } else { 'admin' }
$env:BOSS_ADMIN_PASSWORD = if ($env:BOSS_ADMIN_PASSWORD) { $env:BOSS_ADMIN_PASSWORD } else { 'admin123' }
$env:VERIFICATION_CODE_DEV_MODE = if ($null -ne $env:VERIFICATION_CODE_DEV_MODE) { $env:VERIFICATION_CODE_DEV_MODE } else { 'true' }
$env:DEBUG = 'false'

function Test-Port {
    param([int]$Port)
    $client = New-Object Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(1000)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-Port {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 5
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return (Test-Port $Port)
}

if (-not (Test-Port 6379)) {
    docker start flytaxi-redis 2>$null | Out-Null
    $redisReady = Wait-Port -Port 6379 -TimeoutSeconds 5

    if (-not $redisReady) {
        $dockerDesktop = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'
        if (Test-Path -LiteralPath $dockerDesktop) {
            Write-Host 'Starting Docker Desktop for Redis...'
            Start-Process -FilePath $dockerDesktop -WindowStyle Hidden | Out-Null
            Wait-Port -Port 6379 -TimeoutSeconds 75 | Out-Null
            docker start flytaxi-redis 2>$null | Out-Null
            $redisReady = Wait-Port -Port 6379 -TimeoutSeconds 10
        }
    }

    if (-not $redisReady) {
        throw 'Redis is unavailable. Start Docker Desktop and the flytaxi-redis container.'
    }
}

if (-not (Test-Port 8848)) {
    Start-Process -FilePath 'E:\nacos\nacos\bin\startup.cmd' -ArgumentList '-m standalone' -WorkingDirectory 'E:\nacos\nacos\bin' -WindowStyle Hidden | Out-Null
    Start-Sleep -Seconds 10
}

if ($Build) {
    Push-Location $ProjectRoot
    try {
        mvn clean install -DskipTests
    } finally {
        Pop-Location
    }
}

Push-Location $ProjectRoot
try {
    $mavenRepository = (mvn '-q' 'help:evaluate' '-Dexpression=settings.localRepository' '-DforceStdout' 2>$null).Trim()
    if (-not $mavenRepository) { $mavenRepository = Join-Path $env:USERPROFILE '.m2\repository' }

    $sharedArtifacts = @(
        (Join-Path $mavenRepository 'com\flytaxi\internal-common\1.0-SNAPSHOT\internal-common-1.0-SNAPSHOT.jar'),
        (Join-Path $mavenRepository 'com\flytaxi\security-support-core\1.0-SNAPSHOT\security-support-core-1.0-SNAPSHOT.jar'),
        (Join-Path $mavenRepository 'com\flytaxi\security-support-session\1.0-SNAPSHOT\security-support-session-1.0-SNAPSHOT.jar')
    )

    if ($sharedArtifacts.Where({ -not (Test-Path -LiteralPath $_) }).Count -gt 0) {
        mvn -q -pl internal-common,security-support-core,security-support-session -am install -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Failed to install shared Maven modules.' }
    } else {
        Write-Host 'Shared Maven modules already installed; skipping rebuild.'
    }
} finally {
    Pop-Location
}

$Services = @(
    @{ Name = 'service-passenger-user'; Module = 'service-passenger-user'; Port = 8083; Args = '' },
    @{ Name = 'service-driver-user'; Module = 'service-driver-user'; Port = 8086; Args = '' },
    @{ Name = 'service-price'; Module = 'service-price'; Port = 8084; Args = '' },
    @{ Name = 'service-map'; Module = 'service-map'; Port = 8085; Args = '' },
    @{ Name = 'service-order'; Module = 'service-order'; Port = 8089; Args = ''; Profile = '8089' },
    @{ Name = 'service-pay'; Module = 'service-pay'; Port = 9001; Args = '' },
    @{ Name = 'service-sse-push'; Module = 'service-sse-push'; Port = 9000; Args = '' },
    @{ Name = 'api-passenger'; Module = 'api-passenger'; Port = 8081; Args = '' },
    @{ Name = 'api-driver'; Module = 'api-driver'; Port = 8088; Args = '' },
    @{ Name = 'api-boss'; Module = 'api-boss'; Port = 8087; Args = '' },
    @{ Name = 'api-gateway'; Module = 'api-gateway'; Port = 8080; Args = '' }
)

foreach ($service in $Services) {
    if (Test-Port $service.Port) {
        Write-Host "$($service.Name) already listens on port $($service.Port)."
        continue
    }

    $logFile = Join-Path $LogRoot "$($service.Name).log"
    $errFile = Join-Path $LogRoot "$($service.Name).err.log"
    $pidFile = Join-Path $PidRoot "$($service.Name).pid"
    $module = $service.Module
    $runArgs = $service.Args
    $profile = if ($service.ContainsKey('Profile')) { $service.Profile } else { '' }

    $command = @"
`$env:JAVA_HOME = '$JavaHome'
`$env:MAVEN_HOME = '$MavenHome'
`$env:M2_HOME = '$MavenHome'
`$env:Path = '$JavaHome\bin;$MavenHome\bin;' + `$env:Path
`$env:DB_USERNAME = '$($env:DB_USERNAME)'
`$env:DB_PASSWORD = '$($env:DB_PASSWORD)'
`$env:REDIS_HOST = '$($env:REDIS_HOST)'
`$env:REDIS_PORT = '$($env:REDIS_PORT)'
`$env:REDIS_DATABASE = '$($env:REDIS_DATABASE)'
`$env:NACOS_SERVER_ADDR = '$($env:NACOS_SERVER_ADDR)'
`$env:NACOS_USERNAME = '$($env:NACOS_USERNAME)'
`$env:NACOS_PASSWORD = '$($env:NACOS_PASSWORD)'
`$env:NACOS_DISCOVERY_IP = '$($env:NACOS_DISCOVERY_IP)'
`$env:AMAP_KEY = '$($env:AMAP_KEY)'
`$env:BOSS_ADMIN_USERNAME = '$($env:BOSS_ADMIN_USERNAME)'
`$env:BOSS_ADMIN_PASSWORD = '$($env:BOSS_ADMIN_PASSWORD)'
`$env:VERIFICATION_CODE_DEV_MODE = '$($env:VERIFICATION_CODE_DEV_MODE)'
`$env:DEBUG = 'false'
`$env:SPRING_PROFILES_ACTIVE = '$profile'
Set-Location '$ProjectRoot'
mvn -pl $module spring-boot:run $runArgs
"@

    $process = Start-Process -FilePath 'powershell' `
        -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $command `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError $errFile `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -Path $pidFile -Value $process.Id
    Write-Host "Started $($service.Name) on port $($service.Port), pid $($process.Id)."
    Start-Sleep -Seconds 3
    if (-not (Wait-Port -Port $service.Port -TimeoutSeconds 90)) {
        throw 'Service failed to start.'
    }
}

Write-Host "Logs: $LogRoot"
Write-Host "Run scripts/status-flytaxi.ps1 to check ports."
