#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: remote-rollback.sh <release-id>" >&2
  exit 2
fi

release_id="$1"
release_dir="/opt/nexus-world/releases/$release_id"

if [[ ! -f "$release_dir/deploy/day5/compose.prod.yaml" || ! -f "$release_dir/.env" ]]; then
  echo "release not found: $release_id" >&2
  exit 1
fi

cd "$release_dir"
docker compose --env-file .env -f deploy/day5/compose.prod.yaml up -d --no-build --remove-orphans

for attempt in $(seq 1 24); do
  if curl --fail --silent http://127.0.0.1:3000/api/health >/dev/null && \
     curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null; then
    ln -sfn "$release_dir" /opt/nexus-world/current
    echo "rolled back to release $release_id"
    exit 0
  fi
  sleep 5
done

docker compose --env-file .env -f deploy/day5/compose.prod.yaml logs --tail=100
exit 1
