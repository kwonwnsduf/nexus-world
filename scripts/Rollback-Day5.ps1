[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$ReleaseId,
    [string]$TerraformDirectory = ".\infra\aws\day5"
)

$ErrorActionPreference = "Stop"
$env:TF_CLI_CONFIG_FILE = "NUL"
$terraformChdir = "-chdir=$TerraformDirectory"
$terraformOutputs = (& terraform $terraformChdir output -json) | ConvertFrom-Json
$instanceId = [string]$terraformOutputs.instance_id.value
$region = [string]$terraformOutputs.aws_region.value
$command = "bash /opt/nexus-world/releases/$ReleaseId/deploy/day5/remote-rollback.sh '$ReleaseId'"
$parametersFile = Join-Path ([IO.Path]::GetTempPath()) "nexus-world-rollback-$([Guid]::NewGuid()).json"
$parametersJson = @{ commands = @($command) } | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText($parametersFile, $parametersJson, [Text.UTF8Encoding]::new($false))

try {
    $commandId = (& aws ssm send-command `
        --region $region `
        --instance-ids $instanceId `
        --document-name AWS-RunShellScript `
        --comment "Rollback NEXUS WORLD to $ReleaseId" `
        --parameters "file://$parametersFile" `
        --query "Command.CommandId" `
        --output text `
        --no-cli-pager).Trim()

    & aws ssm wait command-executed --region $region --command-id $commandId --instance-id $instanceId
    & aws ssm get-command-invocation --region $region --command-id $commandId --instance-id $instanceId --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw "Rollback failed." }
}
finally {
    Remove-Item -LiteralPath $parametersFile -Force -ErrorAction SilentlyContinue
}
