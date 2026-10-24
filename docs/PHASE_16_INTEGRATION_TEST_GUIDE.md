# Phase 16 local integration test guide

This workflow is local only. It uses the existing Kafka/KRaft configuration and an isolated MongoDB database; it does not touch Atlas.

## Unit tests

```bash
cd backend
mvn test
```

The `integration-tests` Maven profile is deliberately separate. It runs `*IT` tests through Failsafe and requires an explicit local URI; it never reads `MONGODB_URI` or `.env`.

## Start the isolated environment

On a host supported by MongoDB (the current host's Linux 7.0.0 kernel is not):

```bash
docker compose -f docker-compose.yml -f docker-compose.integration.yml \
  --profile apps --profile integration up -d --build
docker compose -f docker-compose.yml -f docker-compose.integration.yml ps
curl -fsS http://localhost:8082/actuator/health/liveness
curl -fsS http://localhost:8083/actuator/health/liveness
curl -fsS http://localhost:8084/actuator/health/liveness
```

Then run the database integration tests against that exact isolated target:

```bash
cd backend
mvn verify -Pintegration-tests -pl alert-service -am \
  -Dit.mongo.uri=mongodb://localhost:27018/threatstream_integration \
  -Dit.mongo.database=threatstream_integration
```

`mongo-test` is intentionally internal-only, has its own `mongo-integration-data` volume, and uses `threatstream_integration`. Do not point `MONGODB_URI` at Atlas for this workflow.

## Verify the pipeline

Use a synthetic failed-login sequence with a local test credential, then poll `GET /api/alerts` for an alert with the expected rule/risk fields. Verify duplicate event IDs create one persisted alert, then test acknowledge/resolve using its returned UUID. Capture Kafka/DLT and group diagnostics before and after:

```bash
./scripts/performance/kafka_diagnostics.sh --allow-local-compose
python3 scripts/performance/rest_load_test.py --allow-local-target --endpoint list --requests 10 --concurrency 2
```

For malformed-message/DLT validation, publish only synthetic malformed input to the local topic, then verify the consumer remains live and the record appears on `security.events.dlt`. Record the actual offsets, error category, and absence of credentials in diagnostics.

## Frontend

The committed lockfile uses npm:

```bash
cd frontend
npm ci
npm test
npm run format:check
npm run lint
npm run build
```

## Stop

This stops local containers but retains the isolated test volume:

```bash
docker compose -f docker-compose.yml -f docker-compose.integration.yml \
  --profile apps --profile integration down
```

Use `down -v` only when intentionally deleting the local integration volume.
