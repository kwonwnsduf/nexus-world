[CmdletBinding()]
param(
    [string]$TerraformDirectory = ".\infra\aws\day5",
    [string]$ReleaseId = ""
)

$ErrorActionPreference = "Stop"
$env:TF_CLI_CONFIG_FILE = "NUL"

if (-not $ReleaseId) {
    $gitSha = (& git rev-parse --short=12 HEAD).Trim()
    $timestamp = [DateTime]::UtcNow.ToString("yyyyMMddHHmmss")
    $ReleaseId = "$timestamp-$gitSha"
}
if ($ReleaseId -notmatch '^[A-Za-z0-9._-]+$') {
    throw "ReleaseId may contain only letters, numbers, dots, underscores, and hyphens."
}

$terraformChdir = "-chdir=$TerraformDirectory"
$terraformOutputs = (& terraform $terraformChdir output -json) | ConvertFrom-Json
$instanceId = [string]$terraformOutputs.instance_id.value
$bucket = [string]$terraformOutputs.artifact_bucket.value
$region = [string]$terraformOutputs.aws_region.value
$domain = [string]$terraformOutputs.domain_name.value
$tlsEmail = [string]$terraformOutputs.tls_email.value
if ($LASTEXITCODE -ne 0) {
    throw "Terraform outputs are unavailable. Run terraform apply first."
}

$tempDirectory = Join-Path ([IO.Path]::GetTempPath()) "nexus-world-day5-$([Guid]::NewGuid())"
$archive = Join-Path $tempDirectory "nexus-world-$ReleaseId.tar.gz"
New-Item -ItemType Directory -Path $tempDirectory | Out-Null

try {
    & tar.exe `
        --exclude=.git `
        --exclude=node_modules `
        --exclude=.next `
        --exclude=.gradle `
        --exclude=build `
        --exclude=.terraform `
        --exclude=terraform.tfstate `
        --exclude=terraform.tfstate.backup `
        --exclude=.env `
        --exclude=.env.* `
        -czf $archive .
    if ($LASTEXITCODE -ne 0) { throw "Failed to create deployment archive." }

    $artifactKey = "releases/$ReleaseId.tar.gz"
    & aws s3 cp $archive "s3://$bucket/$artifactKey" --region $region --sse AES256 --only-show-errors
    if ($LASTEXITCODE -ne 0) { throw "Failed to upload deployment archive." }

    $remoteCommand = "mkdir -p /tmp/nexus-world-bootstrap && aws s3 cp 's3://$bucket/$artifactKey' '/tmp/nexus-world-bootstrap/release.tar.gz' --region '$region' --only-show-errors && tar -xzf /tmp/nexus-world-bootstrap/release.tar.gz -C /tmp/nexus-world-bootstrap && bash /tmp/nexus-world-bootstrap/deploy/day5/remote-deploy.sh '$bucket' '$artifactKey' '$ReleaseId' '$domain' '$tlsEmail'"
    $parametersFile = Join-Path $tempDirectory "ssm-parameters.json"
    $parametersJson = @{ commands = @($remoteCommand) } | ConvertTo-Json -Depth 4
    [IO.File]::WriteAllText($parametersFile, $parametersJson, [Text.UTF8Encoding]::new($false))

    $commandId = (& aws ssm send-command `
        --region $region `
        --instance-ids $instanceId `
        --document-name AWS-RunShellScript `
        --comment "Deploy NEXUS WORLD $ReleaseId" `
        --parameters "file://$parametersFile" `
        --query "Command.CommandId" `
        --output text `
        --no-cli-pager).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Failed to send deployment command." }

    Write-Host "Deployment command: $commandId"
    & aws ssm wait command-executed --region $region --command-id $commandId --instance-id $instanceId
    & aws ssm get-command-invocation --region $region --command-id $commandId --instance-id $instanceId --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw "Deployment failed. Inspect the command output above." }
}
finally {
    Remove-Item -LiteralPath $tempDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
