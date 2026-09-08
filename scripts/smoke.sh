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

echo "All NEXUS WORLD health and Day 2 contract checks passed."
