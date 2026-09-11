# Day 5: Terraform single-EC2 deployment

Status: Complete (2026-09-10)

## Completion evidence

- Terraform applied successfully in `ap-northeast-2`; a follow-up plan reported `No changes`.
- Public demo URL: `http://43.203.246.60`
- Nginx returned Web health `UP` and the Web → Core → AI platform contract returned Core and AI `UP`.
- Administrator login and the protected current-user endpoint succeeded through the public Nginx route.
- Release `20260910012425-341bb7e80683` was deployed and then successfully rolled back to `20260910011658-341bb7e80683` using release-tagged images.
- PostgreSQL backup `backups/20260910T012342Z.sql.gz` was uploaded to the private encrypted S3 bucket and restored successfully.
- The daily backup systemd timer is enabled and active.

The public IP is an EC2-assigned address and can change if the instance is replaced. Use `terraform output demo_url` as the authoritative endpoint. HTTPS remains pending a domain name; no TLS certificate can be issued safely for a bare EC2 IPv4 address.

## Outcome

Day 5 provides a reproducible, low-cost AWS demo deployment without committing the project to its final hosting topology.

Terraform creates:

- one dedicated VPC with one public subnet and no NAT Gateway;
- one encrypted Amazon Linux 2023 EC2 instance;
- a Security Group exposing only HTTP and HTTPS;
- an EC2 role for Systems Manager, the deployment bucket, backups, and the configured SSM parameter path;
- a private, encrypted, versioned S3 bucket with 30-day release and backup retention;
- an optional Route 53 record.

The instance runs host Nginx in front of Docker Compose. Web binds to `127.0.0.1:3000`, Core API to `127.0.0.1:8080`, and AI/PostgreSQL remain on the Docker network. No SSH ingress is created.

This is not a highly available production topology. The EC2 instance and its root volume are a single point of failure. Daily PostgreSQL dumps are therefore stored outside the instance and restore must be tested.

## Prerequisites

- Terraform 1.6 or newer
- AWS CLI authenticated to the target account
- permission to manage EC2, VPC, IAM, S3, SSM, and optional Route 53 resources
- a domain in Route 53 when HTTPS is required

## Provision

```powershell
Copy-Item .\infra\aws\day5\terraform.tfvars.example .\infra\aws\day5\terraform.tfvars
terraform -chdir=.\infra\aws\day5 init
terraform -chdir=.\infra\aws\day5 plan -out=day5.tfplan
terraform -chdir=.\infra\aws\day5 apply day5.tfplan
```

For HTTPS, set `domain_name`, `route53_zone_id`, and `tls_email` in the ignored `terraform.tfvars`. Without a domain, the deployment is reachable over the output HTTP URL.

The initial cloud-init run installs Docker, Docker Compose, Nginx, and Certbot. Wait until the instance appears as managed in Systems Manager before the first deployment.

## Store runtime secrets

```powershell
.\scripts\Set-Day5Secrets.ps1
```

The script prompts for PostgreSQL and administrator credentials, creates a random 48-byte JWT secret, and writes SecureString parameters below the Terraform output `parameter_prefix`. Secret values are not Terraform inputs and are not written to Terraform state.

## Deploy

```powershell
.\scripts\Deploy-Day5.ps1
terraform -chdir=.\infra\aws\day5 output demo_url
```

The deployment script archives the working tree, uploads it to the private bucket, and invokes the remote deployment through Systems Manager. The remote operation builds release-tagged images, starts Compose, checks Web and Core health, configures the backup timer, and obtains a Let's Encrypt certificate when a domain is configured.

To list server-side releases:

```powershell
$instance = terraform -chdir=.\infra\aws\day5 output -raw instance_id
$region = terraform -chdir=.\infra\aws\day5 output -raw aws_region
aws ssm send-command --region $region --instance-ids $instance --document-name AWS-RunShellScript --parameters 'commands=["ls -1 /opt/nexus-world/releases"]'
```

## Roll back

```powershell
.\scripts\Rollback-Day5.ps1 -ReleaseId 20260910120000-abc123def456
```

Rollback reuses the images tagged for that release and does not roll back the PostgreSQL schema or data. A release containing an incompatible database migration needs a separately reviewed database recovery procedure.

## Backup and restore

The systemd timer runs `/usr/local/sbin/nexus-world-backup` daily. Run it manually through a Systems Manager session or command to verify the first backup.

List backups:

```powershell
$bucket = terraform -chdir=.\infra\aws\day5 output -raw artifact_bucket
$region = terraform -chdir=.\infra\aws\day5 output -raw aws_region
aws s3 ls "s3://$bucket/backups/" --region $region
```

Restore is intentionally an explicit, destructive operation:

```text
sudo nexus-world-restore backups/20260910T031500Z.sql.gz
```

It stops Core API, recreates the `nexusworld` database, imports the dump, and starts Core API. Test it only against data that may safely be replaced.

## Expansion gates

- Move PostgreSQL to RDS when data durability, point-in-time recovery, memory pressure, or recovery objectives require it.
- Add a load balancer and multiple app instances when availability or zero-downtime deployments require them.
- Add private subnets and NAT or VPC endpoints when workloads must not have public addresses and controlled outbound access is required.
- Choose ECS or k3s when services or workers need independent scaling or multiple-node scheduling.
