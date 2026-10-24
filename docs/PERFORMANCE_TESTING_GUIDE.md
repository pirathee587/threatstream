# Performance testing guide

All commands in this guide are for a local Docker Compose environment. The scripts reject non-local HTTP targets and cap a run at 1,000 requests and 32 clients. Do not point them at shared Atlas data or a public endpoint.

## Preconditions

Start the local stack with credentials in `.env`:

```bash
docker compose --profile apps up -d --build
curl -fsS http://localhost:8082/actuator/health
curl -fsS http://localhost:8083/actuator/health
curl -fsS http://localhost:8084/actuator/health
```

Run the read-only Kafka, consumer-lag, CPU, and memory snapshot before and after each workload:

```bash
./scripts/performance/kafka_diagnostics.sh --allow-local-compose
```

## Detection microbenchmark and correctness cases

```bash
cd backend
mvn -q -pl threat-detection-service -am test -Dtest=DetectionEngineBenchmarkTest,DetectionRulesTest -Dsurefire.failIfNoSpecifiedTests=false
```

This covers unique and duplicate IDs, brute-force threshold boundaries, independent identities, and an eight-thread bounded workload. It is an in-process CPU benchmark; it does not represent Kafka or Atlas latency.

## REST workload

Begin at two clients, then use 4 and 8 only if CPU, memory, error rate, and consumer lag remain stable.

```bash
python3 scripts/performance/rest_load_test.py --allow-local-target --endpoint list --requests 100 --concurrency 2
python3 scripts/performance/rest_load_test.py --allow-local-target --endpoint statistics --requests 100 --concurrency 2
```

The demo-login workload deliberately publishes failed authentication events. It therefore changes the local pipeline and should be run only against an isolated local database. Set `DEMO_RATE_LIMIT_PER_MINUTE` high enough for the bounded run; an HTTP 429 is a valid protective result, not a performance failure.

```bash
python3 scripts/performance/rest_load_test.py --allow-local-target --endpoint login --requests 50 --concurrency 2
```

State transitions require a known alert ID and are intentionally opt-in:

```bash
python3 scripts/performance/rest_load_test.py --allow-local-target --endpoint acknowledge --alert-id '<local-alert-uuid>' --requests 10 --concurrency 2
python3 scripts/performance/rest_load_test.py --allow-local-target --endpoint resolve --alert-id '<local-alert-uuid>' --requests 10 --concurrency 2
```

Capture Prometheus metrics at `/actuator/prometheus` for each service. Record `jvm_memory_*`, `jvm_gc_*`, `http_server_requests_*`, Kafka consumer metrics, and retry/DLT metrics when present. Never add correlation IDs, IPs, or UUIDs as metric labels.

## MongoDB Atlas audit

Use a dedicated benchmark database, not `threatstream`. Before changing indexes, collect `getIndexes()` and `explain("executionStats")` for representative list and statistics queries. Keep `idx_alerts_alert_id_unique` and standard UUID representation. Delete only records with a benchmark-specific marker in that dedicated database.

## Frontend and regression checks

```bash
cd frontend
npm run test
npm run lint
npm run format:check
npm run build
```

Record the generated asset sizes from Vite. The configured vendor chunks make bundle changes observable without changing UI request behavior.
