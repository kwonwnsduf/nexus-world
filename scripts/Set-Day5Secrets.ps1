[CmdletBinding()]
param(
    [string]$TerraformDirectory = ".\infra\aws\day5"
)

$ErrorActionPreference = "Stop"
$env:TF_CLI_CONFIG_FILE = "NUL"

function Get-PlainText([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Set-SecureParameter([string]$Name, [string]$Value, [string]$Region) {
    & aws ssm put-parameter `
        --region $Region `
        --name $Name `
        --type SecureString `
        --value $Value `
        --overwrite `
        --no-cli-pager | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to write SSM parameter $Name"
    }
}

$terraformChdir = "-chdir=$TerraformDirectory"
$terraformOutputs = (& terraform $terraformChdir output -json) | ConvertFrom-Json
$region = [string]$terraformOutputs.aws_region.value
$prefix = [string]$terraformOutputs.parameter_prefix.value
if ($LASTEXITCODE -ne 0) {
    throw "Terraform outputs are unavailable. Run terraform apply first."
}

$postgresPassword = Get-PlainText (Read-Host "PostgreSQL password" -AsSecureString)
$adminUsername = Read-Host "Bootstrap administrator username"
$adminPassword = Get-PlainText (Read-Host "Bootstrap administrator password" -AsSecureString)

$jwtBytes = [byte[]]::new(48)
$randomNumberGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $randomNumberGenerator.GetBytes($jwtBytes)
}
finally {
    $randomNumberGenerator.Dispose()
}
$jwtSecret = [Convert]::ToBase64String($jwtBytes)

Set-SecureParameter "$prefix/postgres-password" $postgresPassword $region
Set-SecureParameter "$prefix/jwt-secret-base64" $jwtSecret $region
Set-SecureParameter "$prefix/bootstrap-admin-username" $adminUsername $region
Set-SecureParameter "$prefix/bootstrap-admin-password" $adminPassword $region

Write-Host "Runtime secrets stored under $prefix. Values were not written to Terraform state."
