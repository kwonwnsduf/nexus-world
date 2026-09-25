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

optional_parameter() {
  aws ssm get-parameter \
    --region "$AWS_REGION" \
    --name "$PARAMETER_PREFIX/$1" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text 2>/dev/null || true
}

openai_api_key="$(optional_parameter openai-api-key)"
un_comtrade_api_key="$(optional_parameter un-comtrade-api-key)"
neo4j_uri="$(optional_parameter neo4j-uri)"
neo4j_username="$(optional_parameter neo4j-username)"
neo4j_password="$(optional_parameter neo4j-password)"
neo4j_database="$(optional_parameter neo4j-database)"
neo4j_enabled=false
if [[ -n "$neo4j_uri" && -n "$neo4j_password" ]]; then
  neo4j_enabled=true
fi
if [[ -z "$openai_api_key" || -z "$un_comtrade_api_key" ]]; then
  echo "openai-api-key and un-comtrade-api-key SSM parameters are required for Day 21" >&2
  exit 1
fi

admin_username="$(parameter bootstrap-admin-username)"
admin_password="$(parameter bootstrap-admin-password)"

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
BOOTSTRAP_ADMIN_USERNAME=$admin_username
BOOTSTRAP_ADMIN_PASSWORD=$admin_password
DEMO_MODE_ENABLED=true
DEMO_USERNAME=$(parameter bootstrap-admin-username)
DEMO_PASSWORD=$(parameter bootstrap-admin-password)
OPENAI_API_KEY=$openai_api_key
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
OPENAI_EMBEDDING_TIMEOUT_SECONDS=30
UN_COMTRADE_API_KEY=$un_comtrade_api_key
NEO4J_ENABLED=$neo4j_enabled
NEO4J_URI=${neo4j_uri:-bolt://neo4j:7687}
NEO4J_USERNAME=${neo4j_username:-neo4j}
NEO4J_PASSWORD=$neo4j_password
NEO4J_DATABASE=${neo4j_database:-neo4j}
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
BASE_URL=http://127.0.0.1:8080 \
ADMIN_USERNAME="$admin_username" \
ADMIN_PASSWORD="$admin_password" \
bash deploy/day5/bootstrap-day21-world.sh
DEMO_RESPONSE="$(mktemp)"
BASELINE_FILE="/opt/nexus-world/shared/baselines/$release_id.json"
trap 'rm -f "$archive" "$DEMO_RESPONSE"' EXIT
bash deploy/day5/day21-e2e.sh http://127.0.0.1 "$DEMO_RESPONSE"
install -d -m 0750 /opt/nexus-world/shared/baselines
bash deploy/day5/resource-baseline.sh "$release_id" "$BASELINE_FILE"
aws s3 cp "$BASELINE_FILE" "s3://$bucket/baselines/$release_id.json" \
  --region "$AWS_REGION" --sse AES256 --only-show-errors
rm -f "$archive"
echo "release $release_id deployed successfully; resource baseline: s3://$bucket/baselines/$release_id.json"
