$ErrorActionPreference = "Stop"

function Test-Command {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$VersionArguments,
        [Parameter(Mandatory = $true)][bool]$Required
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        $kind = if ($Required) { "ERROR" } else { "INFO" }
        Write-Host "[$kind] $Name was not found."
        return -not $Required
    }

    $versionOutput = @(cmd.exe /d /c "$Name $VersionArguments 2>&1")
    $versionExitCode = $LASTEXITCODE
    if ($versionExitCode -ne 0) {
        $kind = if ($Required) { "ERROR" } else { "INFO" }
        Write-Host "[$kind] $Name is present but not executable."
        return -not $Required
    }
    Write-Host "[OK] $Name - $($versionOutput[0])"
    return $true
}

$checks = @(
    (Test-Command -Name "git" -VersionArguments "--version" -Required $true),
    (Test-Command -Name "java" -VersionArguments "-version" -Required $true),
    (Test-Command -Name "node" -VersionArguments "--version" -Required $true),
    (Test-Command -Name "corepack" -VersionArguments "--version" -Required $true),
    (Test-Command -Name "docker" -VersionArguments "--version" -Required $true),
    (Test-Command -Name "python" -VersionArguments "--version" -Required $false)
)

$javaVersionOutput = @(cmd.exe /d /c "java -version 2>&1")
$javaVersion = $javaVersionOutput[0]
if ($javaVersion -notmatch 'version "17\.') {
    Write-Host "[ERROR] Java 17 is required; detected: $javaVersion"
    $checks += $false
}

cmd.exe /d /c "docker info >NUL 2>&1"
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Docker daemon is reachable."
} else {
    Write-Host "[ERROR] Docker daemon is not reachable. Start Docker Desktop with Linux containers."
    $checks += $false
}

if ($checks -contains $false) {
    exit 1
}

Write-Host "Environment is ready. Python is optional because AI checks run in Docker."
