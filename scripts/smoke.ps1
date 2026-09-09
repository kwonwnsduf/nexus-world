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

$coreHealth = Invoke-RestMethod -Uri "http://127.0.0.1:$corePort/actuator/health" -TimeoutSec 5
if ($coreHealth.components.db.status -ne "UP") {
    throw "[FAIL] core-api database health was '$($coreHealth.components.db.status)'"
}
Write-Host "[OK] core-api PostgreSQL connectivity"

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

$loginBody = @{
    username = if ($env:BOOTSTRAP_ADMIN_USERNAME) { $env:BOOTSTRAP_ADMIN_USERNAME } else { "admin" }
    password = if ($env:BOOTSTRAP_ADMIN_PASSWORD) { $env:BOOTSTRAP_ADMIN_PASSWORD } else { "nexus-world-local-admin" }
} | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$corePort/api/v1/auth/login" `
    -ContentType "application/json" -Body $loginBody -TimeoutSec 5
if ($login.tokenType -ne "Bearer" -or -not $login.accessToken) {
    throw "[FAIL] local JWT login did not return a bearer token"
}
$me = Invoke-RestMethod -Uri "http://127.0.0.1:$corePort/api/v1/auth/me" `
    -Headers @{ Authorization = "Bearer $($login.accessToken)" } -TimeoutSec 5
if ($me.username -ne ($loginBody | ConvertFrom-Json).username -or $me.roles -notcontains "ADMIN") {
    throw "[FAIL] authenticated identity or ADMIN role did not match"
}
Write-Host "[OK] local JWT login and protected identity"

$refreshBody = @{ refreshToken = $login.refreshToken } | ConvertTo-Json
$rotated = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$corePort/api/v1/auth/refresh" `
    -ContentType "application/json" -Body $refreshBody -TimeoutSec 5
if (-not $rotated.accessToken -or -not $rotated.refreshToken -or $rotated.refreshToken -eq $login.refreshToken) {
    throw "[FAIL] refresh token rotation did not return a new token pair"
}
$logoutBody = @{ refreshToken = $rotated.refreshToken } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$corePort/api/v1/auth/logout" `
    -Headers @{ Authorization = "Bearer $($rotated.accessToken)" } `
    -ContentType "application/json" -Body $logoutBody -TimeoutSec 5
$revokedStatus = 200
try {
    Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$corePort/api/v1/auth/me" `
        -Headers @{ Authorization = "Bearer $($rotated.accessToken)" } -TimeoutSec 5 | Out-Null
} catch {
    $revokedStatus = [int]$_.Exception.Response.StatusCode
}
if ($revokedStatus -ne 401) {
    throw "[FAIL] logged-out access token returned HTTP $revokedStatus instead of 401"
}
Write-Host "[OK] refresh rotation, logout, and access-token blacklist"

Write-Host "All NEXUS WORLD health, contract, persistence, and Day 4 authentication checks passed."
