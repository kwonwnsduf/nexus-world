#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: remote-deploy.sh <artifact-bucket> <artifact-key> <release-id> [domain] [tls-email]" >&2
  exit 2
fi

bucket="$1"
artifact_key="$2"
release_id="$3"
domain="${4:-}"
tls_email="${5:-}"
release_dir="/opt/nexus-world/releases/$release_id"
archive="/tmp/nexus-world-$release_id.tar.gz"

source /etc/nexus-world-day5

parameter() {
  aws ssm get-parameter \
    --region "$AWS_REGION" \
    --name "$PARAMETER_PREFIX/$1" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text
}

aws s3 cp "s3://$bucket/$artifact_key" "$archive" --region "$AWS_REGION" --only-show-errors
install -d -m 0750 "$release_dir"
tar -xzf "$archive" -C "$release_dir"

umask 077
cat >"$release_dir/.env" <<EOF
IMAGE_TAG=$release_id
POSTGRES_DB=nexusworld
POSTGRES_USER=nexusworld
POSTGRES_PASSWORD=$(parameter postgres-password)
JWT_SECRET_BASE64=$(parameter jwt-secret-base64)
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=$(parameter bootstrap-admin-username)
BOOTSTRAP_ADMIN_PASSWORD=$(parameter bootstrap-admin-password)
EOF

cd "$release_dir"
docker compose --env-file .env -f deploy/day5/compose.prod.yaml build --pull
docker compose --env-file .env -f deploy/day5/compose.prod.yaml up -d --remove-orphans

for attempt in $(seq 1 36); do
  if curl --fail --silent --show-error http://127.0.0.1:3000/api/health >/dev/null && \
     curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health >/dev/null; then
    break
  fi
  if [[ "$attempt" == "36" ]]; then
    docker compose --env-file .env -f deploy/day5/compose.prod.yaml ps
    docker compose --env-file .env -f deploy/day5/compose.prod.yaml logs --tail=100
    exit 1
  fi
  sleep 5
done

ln -sfn "$release_dir" /opt/nexus-world/current
install -m 0755 deploy/day5/backup-postgres.sh /usr/local/sbin/nexus-world-backup
install -m 0755 deploy/day5/restore-postgres.sh /usr/local/sbin/nexus-world-restore
install -m 0644 deploy/day5/systemd/nexus-world-backup.service /etc/systemd/system/nexus-world-backup.service
install -m 0644 deploy/day5/systemd/nexus-world-backup.timer /etc/systemd/system/nexus-world-backup.timer
systemctl daemon-reload
systemctl enable --now nexus-world-backup.timer

if [[ -n "$domain" ]]; then
  if [[ -z "$tls_email" ]]; then
    echo "tls-email is required when domain is set" >&2
    exit 1
  fi
  sed -i "s/server_name _;/server_name $domain;/" /etc/nginx/nginx.conf
  nginx -t
  systemctl reload nginx
  certbot --nginx --non-interactive --agree-tos --redirect --email "$tls_email" -d "$domain"
fi

curl --fail --silent --show-error http://127.0.0.1/api/health >/dev/null
rm -f "$archive"
echo "release $release_id deployed successfully"
