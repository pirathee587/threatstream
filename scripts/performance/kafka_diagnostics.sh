#!/usr/bin/env bash
# Read-only Kafka and container diagnostics for a local Compose environment.
set -euo pipefail

if [[ "${1:-}" != "--allow-local-compose" ]]; then
  echo "Pass --allow-local-compose after confirming Docker Compose is local." >&2
  exit 64
fi

docker compose ps
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe \
  --topic security.auth.events --topic security.alerts --topic security.events.dlt
for group in threat-detection-service-v1 alert-service-v1; do
  docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 --describe --group "$group" || true
done
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'
