#!/usr/bin/env sh
set -eu

check() {
  name="$1"
  url="$2"
  attempt=1
  while [ "$attempt" -le 30 ]; do
    if curl --fail --silent --show-error "$url" | grep -q '"status":"UP"'; then
      echo "[OK] $name $url"
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  echo "[FAIL] $name $url" >&2
  return 1
}

check web "http://127.0.0.1:${WEB_PORT:-3000}/api/health"
check core-api "http://127.0.0.1:${CORE_API_PORT:-8080}/actuator/health"
core_health="$(curl --fail --silent --show-error "http://127.0.0.1:${CORE_API_PORT:-8080}/actuator/health")"
echo "$core_health" | grep -q '"db":{"status":"UP"'
echo "[OK] core-api PostgreSQL connectivity"
check ai-service "http://127.0.0.1:${AI_SERVICE_PORT:-8000}/health"
check ai-contract "http://127.0.0.1:${AI_SERVICE_PORT:-8000}/api/v1/platform/capabilities"

core_contract="$(curl --fail --silent --show-error "http://127.0.0.1:${CORE_API_PORT:-8080}/api/v1/platform/status")"
echo "$core_contract" | grep -q '"service":"core-api"'
echo "$core_contract" | grep -q '"service":"ai-service"'
echo "[OK] core-to-ai-contract"

web_contract="$(curl --fail --silent --show-error "http://127.0.0.1:${WEB_PORT:-3000}/api/platform/status")"
echo "$web_contract" | grep -q '"service":"core-api"'
echo "$web_contract" | grep -q '"service":"ai-service"'
echo "[OK] web-to-core-to-ai-contract"

docker compose exec -T ai-service python -c \
  'import os, psycopg; value=os.environ.get("RAG_DATABASE_URL"); assert value; connection=psycopg.connect(value); connection.execute("SELECT 1"); connection.close()'
echo "[OK] AI retrieval repository to PostgreSQL connectivity"

login_response="$(curl --fail --silent --show-error -H 'Content-Type: application/json' \
  -d "{\"username\":\"${BOOTSTRAP_ADMIN_USERNAME:-admin}\",\"password\":\"${BOOTSTRAP_ADMIN_PASSWORD:-nexus-world-local-admin}\"}" \
  "http://127.0.0.1:${CORE_API_PORT:-8080}/api/v1/auth/login")"
access_token="$(echo "$login_response" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
refresh_token="$(echo "$login_response" | sed -n 's/.*"refreshToken":"\([^"]*\)".*/\1/p')"
test -n "$access_token"
test -n "$refresh_token"
me_response="$(curl --fail --silent --show-error -H "Authorization: Bearer $access_token" \
  "http://127.0.0.1:${CORE_API_PORT:-8080}/api/v1/auth/me")"
echo "$me_response" | grep -q '"ADMIN"'
echo "[OK] local JWT login and protected identity"

rotated_response="$(curl --fail --silent --show-error -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$refresh_token\"}" \
  "http://127.0.0.1:${CORE_API_PORT:-8080}/api/v1/auth/refresh")"
rotated_access_token="$(echo "$rotated_response" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
rotated_refresh_token="$(echo "$rotated_response" | sed -n 's/.*"refreshToken":"\([^"]*\)".*/\1/p')"
test -n "$rotated_access_token"
test -n "$rotated_refresh_token"
test "$rotated_refresh_token" != "$refresh_token"
curl --fail --silent --show-error -o /dev/null -H "Authorization: Bearer $rotated_access_token" \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$rotated_refresh_token\"}" \
  "http://127.0.0.1:${CORE_API_PORT:-8080}/api/v1/auth/logout"
revoked_status="$(curl --silent -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $rotated_access_token" \
  "http://127.0.0.1:${CORE_API_PORT:-8080}/api/v1/auth/me")"
test "$revoked_status" = "401"
echo "[OK] refresh rotation, logout, and access-token blacklist"

if [ "${RUN_GROUNDED_E2E:-false}" = "true" ]; then
  demo_response="$(mktemp)"
  trap 'rm -f "$demo_response"' EXIT
  curl --fail --silent --show-error --request POST --header 'Content-Type: application/json' \
    --data '{"query":"중국 반도체 공급이 50% 감소하면?"}' --output "$demo_response" \
    "http://127.0.0.1:${WEB_PORT:-3000}/api/demo/supply-chain"
  python3 - "$demo_response" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as stream:
    value = json.load(stream)
assert value["contractVersion"] == "v2"
assert value["worldVersionId"] and value["target"]["entityId"]
if value["status"] == "COMPLETED":
    assert len(value["branches"]) == 3
    assert all(branch["invariantsPassed"] for branch in value["branches"])
else:
    assert value["status"] == "INSUFFICIENT_DATA" and not value["branches"]
PY
  echo "[OK] grounded ingestion-world-GraphRAG-simulation contract"
else
  echo "[SKIP] grounded external-data E2E (set RUN_GROUNDED_E2E=true after ingestion)"
fi

echo "All NEXUS WORLD health, contract, persistence, and authentication checks passed."
