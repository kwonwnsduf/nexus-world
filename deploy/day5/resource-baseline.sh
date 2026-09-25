#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: resource-baseline.sh <release-id> <output-file>" >&2
  exit 2
fi

release_id="$1"
output_file="$2"
stats_file="$(mktemp)"
trap 'rm -f "$stats_file"' EXIT
docker stats --no-stream --format '{{json .}}' >"$stats_file"

python3 - "$release_id" "$stats_file" "$output_file" <<'PY'
import datetime
import json
import os
import shutil
import sys

release_id, stats_path, output_path = sys.argv[1:]
with open(stats_path, encoding="utf-8") as stream:
    containers = [json.loads(line) for line in stream if line.strip()]

def meminfo():
    values = {}
    with open("/proc/meminfo", encoding="ascii") as stream:
        for line in stream:
            key, raw = line.split(":", 1)
            values[key] = int(raw.strip().split()[0])
    return values

memory = meminfo()
disk = shutil.disk_usage("/")
load_1m, load_5m, load_15m = os.getloadavg()
payload = {
    "schemaVersion": "day21-resource-baseline-v1",
    "recordedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "releaseId": release_id,
    "host": {
        "cpuCount": os.cpu_count(),
        "loadAverage": {"oneMinute": load_1m, "fiveMinutes": load_5m, "fifteenMinutes": load_15m},
        "memoryBytes": {
            "total": memory["MemTotal"] * 1024,
            "available": memory["MemAvailable"] * 1024,
        },
        "rootDiskBytes": {"total": disk.total, "used": disk.used, "free": disk.free},
    },
    "containers": containers,
}
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, "w", encoding="utf-8") as stream:
    json.dump(payload, stream, indent=2, sort_keys=True)
    stream.write("\n")
print(json.dumps(payload, indent=2, sort_keys=True))
PY
