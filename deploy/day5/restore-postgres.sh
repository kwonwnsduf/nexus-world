#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: nexus-world-restore <s3-backup-key>" >&2
  exit 2
fi

source /etc/nexus-world-day5
release_dir="$(readlink -f /opt/nexus-world/current)"
backup_key="$1"
backup_file="/tmp/nexus-world-restore.sql.gz"

if [[ "$backup_key" != backups/*.sql.gz ]]; then
  echo "backup key must match backups/*.sql.gz" >&2
  exit 2
fi

aws s3 cp "s3://$ARTIFACT_BUCKET/$backup_key" "$backup_file" \
  --region "$AWS_REGION" \
  --only-show-errors

cd "$release_dir"
docker compose --env-file .env -f deploy/day5/compose.prod.yaml stop core-api
docker compose --env-file .env -f deploy/day5/compose.prod.yaml exec -T postgres \
  psql -U nexusworld -d postgres -v ON_ERROR_STOP=1 \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'nexusworld' AND pid <> pg_backend_pid();" \
  -c "DROP DATABASE IF EXISTS nexusworld;" \
  -c "CREATE DATABASE nexusworld OWNER nexusworld;"
gunzip -c "$backup_file" | docker compose --env-file .env -f deploy/day5/compose.prod.yaml exec -T postgres \
  psql -U nexusworld -d nexusworld -v ON_ERROR_STOP=1
docker compose --env-file .env -f deploy/day5/compose.prod.yaml start core-api
rm -f "$backup_file"
echo "database restored from $backup_key"
