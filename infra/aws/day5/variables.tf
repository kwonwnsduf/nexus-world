variable "aws_region" {
  description = "AWS region for the Day 5 deployment."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "Name used for AWS resources."
  type        = string
  default     = "nexus-world"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "demo"
}

variable "instance_type" {
  description = "EC2 instance type. Increase this before splitting services."
  type        = string
  default     = "t3.medium"
}

variable "root_volume_size_gib" {
  description = "Encrypted gp3 root volume size in GiB."
  type        = number
  default     = 30
}

variable "allowed_http_cidrs" {
  description = "CIDRs allowed to reach Nginx."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "parameter_prefix" {
  description = "SSM Parameter Store prefix containing runtime secrets."
  type        = string
  default     = "/nexus-world/day5"

  validation {
    condition     = startswith(var.parameter_prefix, "/")
    error_message = "parameter_prefix must start with /."
  }
}

variable "domain_name" {
  description = "Optional DNS name. Leave empty for HTTP on the EC2 public address."
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Optional Route 53 hosted zone ID. When set with domain_name, an A record is created."
  type        = string
  default     = ""
}

variable "tls_email" {
  description = "Optional Let's Encrypt notification email. Required by the deploy script when domain_name is set."
  type        = string
  default     = ""
}

variable "docker_compose_version" {
  description = "Pinned Docker Compose plugin version installed by cloud-init."
  type        = string
  default     = "v2.35.1"
}
