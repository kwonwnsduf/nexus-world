$ErrorActionPreference = "Stop"

$webPort = if ($env:WEB_PORT) { $env:WEB_PORT } else { "3000" }
$corePort = if ($env:CORE_API_PORT) { $env:CORE_API_PORT } else { "8080" }
$aiPort = if ($env:AI_SERVICE_PORT) { $env:AI_SERVICE_PORT } else { "8000" }
$attemptLimit = if ($env:SMOKE_ATTEMPTS) { [int]$env:SMOKE_ATTEMPTS } else { 30 }
$delaySeconds = if ($env:SMOKE_DELAY_SECONDS) { [int]$env:SMOKE_DELAY_SECONDS } else { 2 }

$checks = @(
    @{ Name = "web"; Url = "http://127.0.0.1:$webPort/api/health"; Service = "web" },
    @{ Name = "core-api"; Url = "http://127.0.0.1:$corePort/actuator/health"; Service = $null },
    @{ Name = "ai-service"; Url = "http://127.0.0.1:$aiPort/health"; Service = "ai-service" }
)

foreach ($check in $checks) {
    $lastError = $null
    for ($attempt = 1; $attempt -le $attemptLimit; $attempt++) {
        try {
            $response = Invoke-RestMethod -Uri $check.Url -TimeoutSec 3
            if ($response.status -ne "UP") {
                throw "status was '$($response.status)'"
            }
            if ($check.Service -and $response.service -ne $check.Service) {
                throw "service was '$($response.service)'"
            }
            Write-Host "[OK] $($check.Name) $($check.Url)"
            $lastError = $null
            break
        } catch {
            $lastError = $_.Exception.Message
            if ($attempt -lt $attemptLimit) {
                Start-Sleep -Seconds $delaySeconds
            }
        }
    }
    if ($lastError) {
        Write-Error "[FAIL] $($check.Name) at $($check.Url): $lastError"
        exit 1
    }
}

$contractChecks = @(
    @{ Name = "ai-contract"; Url = "http://127.0.0.1:$aiPort/api/v1/platform/capabilities"; Downstream = $false },
    @{ Name = "core-to-ai-contract"; Url = "http://127.0.0.1:$corePort/api/v1/platform/status"; Downstream = $true },
    @{ Name = "web-to-core-to-ai-contract"; Url = "http://127.0.0.1:$webPort/api/platform/status"; Downstream = $true }
)

foreach ($check in $contractChecks) {
    $response = Invoke-RestMethod -Uri $check.Url -TimeoutSec 5
    if ($response.status -ne "UP") {
        throw "[FAIL] $($check.Name): status was '$($response.status)'"
    }
    if ($check.Downstream -and $response.downstream.service -ne "ai-service") {
        throw "[FAIL] $($check.Name): downstream service was '$($response.downstream.service)'"
    }
    Write-Host "[OK] $($check.Name) $($check.Url)"
}

Write-Host "All NEXUS WORLD health and Day 2 contract checks passed."
