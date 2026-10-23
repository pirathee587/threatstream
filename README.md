# ThreatStream

Phase 1 foundation for a real-time security monitoring and threat detection system. It provides service skeletons, local Kafka/PostgreSQL infrastructure, a React shell, and a Python dataset-processor placeholder. Detection and authentication features are deliberately out of scope.

## Quick start

Prerequisites: Java 21/Maven 3.9+, Node 22+/npm 10+, Python 3.12+, and Docker Desktop (Windows/macOS) or Docker Engine with Compose (Linux).

1. Copy `.env.example` to `.env` (`Copy-Item .env.example .env` in PowerShell; `cp .env.example .env` on Linux/macOS), then set a local-only `POSTGRES_PASSWORD`.
2. Start infrastructure: `docker compose up -d kafka postgres kafka-init`.
3. Inspect it with `docker compose ps` and `docker compose logs kafka-init`.
4. Run the frontend: `cd frontend && npm install && npm run dev`.
5. Verify Java: `cd backend && mvn verify`.
6. Follow [dataset processor setup](dataset-processor/README.md).

Run service containers after infrastructure is ready with `docker compose --profile apps up --build`. Health endpoints are `/actuator/health` on ports 8081–8084. Stop with `docker compose down`; use `-v` only to intentionally erase local Kafka/database data.

## Phase 2 demo login

Set `DEMO_LOGIN_PASSWORD` in `.env` before starting the `apps` profile. Visit the frontend's `/demo-login` route or POST to `http://localhost:8084/api/demo/auth/login`:

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"username":"demo-user","password":"your-local-demo-password"}' \
  http://localhost:8084/api/demo/auth/login
```

Success returns `200`, invalid credentials return `401`, malformed requests return `400`, and unavailable Kafka returns `503`; every attempt is synchronously published before its response. The event contains `eventId`, `eventType` (`AUTH_LOGIN`), `schemaVersion` (`1`), UTC `timestamp`, backend-observed `sourceIp`, normalized `username`, `outcome`, optional safe `failureReason`, and `serviceName`. Passwords are never included. Forwarded IP headers are intentionally ignored unless a trusted-proxy deployment is explicitly added.

With Kafka running, inspect events from inside the broker network:

```bash
docker compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic security.auth.events --from-beginning
```

## Phase 3 authentication detection

The detection service consumes `security.auth.events` with group `threat-detection-service-v1`, validates schema version 1 events, and publishes sanitized JSON alerts to `security.alerts`. Development thresholds are configurable through `.env`: brute force is 10 failures/IP in 60 seconds (HIGH), password spraying is 10 distinct usernames/IP in 120 seconds (HIGH), and failures followed by success is 5 failures/IP in 5 minutes (MEDIUM). These are demo values, not universal security policy.

The consumer uses record acknowledgements, bounded retries, then routes malformed/repeatedly failing records to `security.events.dlt`. Alert-publish failure is retried through the same consumer path before an offset is acknowledged. Event-ID deduplication and all rule windows are in memory with bounded retention: restarts or multiple service instances can lead to missed or duplicated detections, and no exactly-once or durable state is claimed.

Inspect alerts with:

```bash
docker compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic security.alerts --from-beginning
```

## Phase 4 alert API

The Alert Service consumes `security.alerts` as `alert-service-v1`, validates schema version 1 events, persists `alert_id` idempotently, and routes repeatedly failing records to `security.events.dlt`. `V2__create_alerts.sql` adds PostgreSQL JSONB evidence plus query indexes. APIs: `GET /api/alerts` (bounded `page`/`size`, filters, date range and stable sorting), `GET /api/alerts/{alertId}`, `GET /api/alerts/statistics`, `PATCH /api/alerts/{alertId}/acknowledge`, and `PATCH /api/alerts/{alertId}/resolve`. Swagger UI is available in development at `/swagger-ui/index.html`.

Persistence is at-least-once: the unique `alert_id` constraint safely handles duplicate Kafka delivery, but database and Kafka transactions are not atomically coordinated.

## Phase 5 network dataset replay

The Python CIC-IDS2017 replay processor publishes label-free `NETWORK_FLOW` events to `security.network.events`. See the [dataset processor README](dataset-processor/README.md) for installation, dry-run validation, small sample replay, full replay, data location, and schema rules. It never publishes CIC ground-truth labels.

## Phase 6 network detection

The detection service also consumes label-free network flows and emits compatible alerts for port scanning (`NETWORK_PORT_SCAN`), high single-source traffic (`NETWORK_DOS`), and multi-source target traffic (`NETWORK_DDOS`). Defaults are demonstration heuristics: 20 distinct ports/source-target/60s, 10MB/s or 10k packets/s source-target/60s, and 20MB/s across five sources/target/60s. Cooldowns suppress duplicate alerts. Use the `NETWORK_*`, `PORT_SCAN_*`, `DOS_*`, and `DDOS_*` variables in `.env.example` to tune them. These alerts indicate possible activity, not confirmed attacks; state is bounded, in memory, and lost on restart or not shared across replicas.

## Phase 7 security dashboard

A polished, responsive security operations center (SOC) dashboard built with React 19, TypeScript, Recharts, and Tailwind CSS, connected directly to the Alert Service REST API.

### Routes & Features
- `/` — **Security Overview**: Live metrics (Total Alerts, Open, High + Critical, Acknowledged, Resolved), Recharts visualizations for severity breakdown and threat type distribution, recent alerts table with quick investigation links, auto-polling with race-condition protection, and an explicit telemetry notice regarding the absence of historical timeseries trend endpoints in the current backend.
- `/alerts` — **Alert Investigation List**: Server-side pagination with selectable page sizes (10, 20, 50), stable sorting by allowed fields (`detectedAt`, `severity`, `status`, `threatType`, `createdAt`) in `ASC` or `DESC` order, multi-field filters for severity, status, threat type substring, source IP, and ISO date range (`from` and `to`), plus filter reset.
- `/alerts/:alertId` — **Alert Detail & Action Center**: Deep alert metadata view, safe evidence JSON inspector with copy-to-clipboard, status transition controls (`Acknowledge` and `Resolve`) with duplicate-submission protection, and terminal state enforcement (resolved alerts are non-reopenable per Alert Service business rules).
- `/demo-login` — Preserved Phase 2 authentication event simulation view.

### Configuration
Set the following variables in `frontend/.env` or root `.env`:
```bash
# Alert Service REST API endpoint
VITE_ALERT_API_BASE_URL=http://localhost:8083

# Telemetry polling interval in milliseconds (default 30000, minimum 5000)
VITE_ALERT_POLL_INTERVAL_MS=30000

# Backend CORS allowance
CORS_ALLOWED_ORIGIN=http://localhost:5173
```

### Running & Testing
```bash
cd frontend
npm install
npm run dev          # Start local Vite dev server at http://localhost:5173
npm test             # Run Vitest test suite with mocked API responses
npm run build        # Typecheck and build production bundle (tsc -b && vite build)
npm run lint         # Run ESLint validation
npm run format:check # Validate code formatting with Prettier
```

## Local topics and security

`kafka-init` explicitly creates `security.auth.events`, `security.network.events`, `security.alerts`, and `security.events.dlt`, each with three partitions and replication factor one. Kafka has no host port binding. Configuration is read from environment variables documented in `.env.example`; `.env`, datasets, and secrets are ignored by Git.

CIC-IDS2017 belongs in `dataset-processor/data/CIC-IDS2017/`, which is never committed.
