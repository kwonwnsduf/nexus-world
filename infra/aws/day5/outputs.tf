output "instance_id" {
  description = "EC2 instance managed through Systems Manager."
  value       = aws_instance.app.id
}

output "public_ip" {
  description = "Public IPv4 address."
  value       = aws_instance.app.public_ip
}

output "artifact_bucket" {
  description = "Private bucket for release archives and database backups."
  value       = aws_s3_bucket.artifacts.id
}

output "parameter_prefix" {
  description = "SSM path where runtime secrets must be stored."
  value       = var.parameter_prefix
}

output "aws_region" {
  value = var.aws_region
}

output "domain_name" {
  value = var.domain_name
}

output "tls_email" {
  value     = var.tls_email
  sensitive = true
}

output "demo_url" {
  description = "Demo URL. HTTPS is configured during deployment when domain_name and tls_email are supplied."
  value       = var.domain_name != "" ? "https://${var.domain_name}" : "http://${aws_instance.app.public_ip}"
}
