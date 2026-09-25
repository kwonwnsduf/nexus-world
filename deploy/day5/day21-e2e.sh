#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://127.0.0.1}"
output_file="${2:-$(mktemp)}"

curl --fail --silent --show-error "$base_url/api/health" >/dev/null
curl --fail --silent --show-error "$base_url/api/platform/status" \
  | python3 -c 'import json,sys; value=json.load(sys.stdin); assert value["status"] == "UP"; assert value["downstream"]["service"] == "ai-service"'

ai_container="$(docker ps --filter 'name=nexus-world-ai-service-1' --format '{{.ID}}' | head -n 1)"
if [[ -z "$ai_container" ]]; then
  echo "[FAIL] AI service container is not running" >&2
  exit 1
fi
docker exec "$ai_container" python -c \
  'import os, psycopg; value=os.environ.get("RAG_DATABASE_URL"); assert value, "RAG_DATABASE_URL is missing"; connection=psycopg.connect(value); connection.execute("SELECT 1"); connection.close()'
echo "[OK] AI retrieval repository → PostgreSQL connectivity"

curl --fail --silent --show-error --request POST \
  --header 'Content-Type: application/json' \
  --data '{"query":"중국 반도체 공급이 50% 감소하면?"}' \
  --output "$output_file" \
  "$base_url/api/demo/supply-chain"

python3 - "$output_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    value = json.load(stream)

assert value["contractVersion"] == "v2"
assert value["status"] == "COMPLETED", value.get("dataQuality")
assert value["worldVersionId"]
assert value["target"]["entityType"] == "COUNTRY"
assert value["shock"]["basisType"] == "USER_ASSUMPTION"
assert value["worldManifest"]["retrievalIndexStatus"] == "READY"
assert value["worldManifest"]["retrievalDocumentCount"] == value["worldManifest"]["expectedRetrievalDocumentCount"]
assert value["provenance"]["baselineValueCount"] > 0
assert value["provenance"]["relationshipParameterCount"] > 0
assert value["graph"]["evidenceCount"] > 0
assert len(value["branches"]) == 3
assert len({branch["name"] for branch in value["branches"]}) == 3
assert all(branch["invariantsPassed"] for branch in value["branches"])
assert all(branch["resultHash"] for branch in value["branches"])
print(f'[OK] grounded Day 21 scenario {value["scenarioId"]} used World Version {value["worldVersionId"]}')
PY
