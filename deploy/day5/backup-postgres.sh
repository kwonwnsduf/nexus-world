#!/usr/bin/env bash
set -euo pipefail

source /etc/nexus-world-day5
release_dir="$(readlink -f /opt/nexus-world/current)"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="/tmp/nexus-world-$timestamp.sql.gz"

cd "$release_dir"
docker compose --env-file .env -f deploy/day5/compose.prod.yaml exec -T postgres \
  pg_dump -U nexusworld -d nexusworld | gzip -9 >"$backup_file"

aws s3 cp "$backup_file" "s3://$ARTIFACT_BUCKET/backups/$timestamp.sql.gz" \
  --region "$AWS_REGION" \
  --sse AES256 \
  --only-show-errors
rm -f "$backup_file"
echo "database backup uploaded: backups/$timestamp.sql.gz"
