$Ports = @(
    @{ Name = 'api-gateway'; Port = 8080 },
    @{ Name = 'api-passenger'; Port = 8081 },
    @{ Name = 'service-passenger-user'; Port = 8083 },
    @{ Name = 'service-price'; Port = 8084 },
    @{ Name = 'service-map'; Port = 8085 },
    @{ Name = 'service-driver-user'; Port = 8086 },
    @{ Name = 'api-boss'; Port = 8087 },
    @{ Name = 'api-driver'; Port = 8088 },
    @{ Name = 'service-order'; Port = 8089 },
    @{ Name = 'service-sse-push'; Port = 9000 },
    @{ Name = 'service-pay'; Port = 9001 },
    @{ Name = 'Redis'; Port = 6379 },
    @{ Name = 'Nacos'; Port = 8848 }
)

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

$Ports | ForEach-Object {
    [PSCustomObject]@{
        Name = $_.Name
        Port = $_.Port
        Listening = Test-Port $_.Port
    }
} | Format-Table -AutoSize
