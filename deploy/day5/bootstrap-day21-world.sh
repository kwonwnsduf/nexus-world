#!/usr/bin/env bash
set -euo pipefail

: "${BASE_URL:=http://127.0.0.1:8080}"
: "${ADMIN_USERNAME:?ADMIN_USERNAME is required}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is required}"

python3 - <<'PY'
import json
import os
import urllib.request


def post(path, value, token=None):
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(
        os.environ["BASE_URL"] + path,
        data=json.dumps(value).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        return json.load(response)


login = post(
    "/api/v1/auth/login",
    {"username": os.environ["ADMIN_USERNAME"], "password": os.environ["ADMIN_PASSWORD"]},
)
token = login.get("accessToken")
if not isinstance(token, str) or not token:
    raise RuntimeError("bootstrap login did not return an access token")

result = post(
    "/api/v1/admin/ingestions/UN_COMTRADE",
    {
        "parameters": {
            "reporterCode": "156",
            "partnerCode": "410",
            "period": "2023",
            "flowCode": "X",
            "cmdCode": "8542",
            "maxRecords": "100",
        }
    },
    token,
)
if result.get("status") != "SUCCEEDED" or result.get("pagesFetched", 0) < 1:
    raise RuntimeError(f"grounded bootstrap ingestion failed: {result.get('status')}")
print(
    "[OK] production UN Comtrade ingestion "
    f"run={result.get('runId')} normalized={result.get('normalizedRecords')}"
)
PY
