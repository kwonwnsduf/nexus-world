[CmdletBinding()]
param(
    [string]$TerraformDirectory = ".\infra\aws\day5",
    [switch]$ConfigureOptionalAiServices
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

if ($ConfigureOptionalAiServices) {
    $openAiApiKey = Get-PlainText (Read-Host "OpenAI API key (blank to omit)" -AsSecureString)
    $unComtradeApiKey = Get-PlainText (Read-Host "UN Comtrade API key (blank to omit)" -AsSecureString)
    $neo4jUri = Read-Host "Neo4j Aura URI (blank to omit)"
    if ($openAiApiKey) {
        Set-SecureParameter "$prefix/openai-api-key" $openAiApiKey $region
    }
    if ($unComtradeApiKey) {
        Set-SecureParameter "$prefix/un-comtrade-api-key" $unComtradeApiKey $region
    }
    if ($neo4jUri) {
        $neo4jUsername = Read-Host "Neo4j username"
        $neo4jPassword = Get-PlainText (Read-Host "Neo4j password" -AsSecureString)
        $neo4jDatabase = Read-Host "Neo4j database (default: neo4j)"
        if (-not $neo4jDatabase) { $neo4jDatabase = "neo4j" }
        Set-SecureParameter "$prefix/neo4j-uri" $neo4jUri $region
        Set-SecureParameter "$prefix/neo4j-username" $neo4jUsername $region
        Set-SecureParameter "$prefix/neo4j-password" $neo4jPassword $region
        Set-SecureParameter "$prefix/neo4j-database" $neo4jDatabase $region
    }
}

Write-Host "Runtime secrets stored under $prefix. Values were not written to Terraform state."
