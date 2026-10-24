# ThreatStream Security Configuration Guide

This guide describes the required environment variables, secrets management practices, configuration examples, and operational procedures for securing ThreatStream across development, staging, and production environments.

> [!CAUTION]
> **Never commit actual passwords, private keys, database connection strings, or API keys to version control.** Always use `.env` (which is excluded in `.gitignore` and `.dockerignore`) or a cloud secrets manager (e.g. AWS Secrets Manager, HashiCorp Vault).

---

## 1. Environment Variable Reference

### 1.1 Alert Service
| Variable | Required | Default (Dev) | Description |
|---|---|---|---|
| `MONGODB_URI` | **Yes** | None | MongoDB Atlas connection string (`mongodb+srv://...`). Must enforce TLS. |
| `MONGODB_DATABASE` | No | `threatstream` | Target MongoDB database name. |
| `ALERT_SERVICE_API_KEY` | **Yes (Prod)** | Empty (Dev) | Shared secret key required to access `/api/alerts/**`. Enforced in production profile. |
| `SERVER_PORT` | No | `8083` | HTTP port for the service. |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker endpoints (`kafka:9092` in Compose). |
| `CORS_ALLOWED_ORIGIN` | No | `http://localhost:5173` | Allowed browser origin for CORS preflight. |

### 1.2 Demo Login Service
| Variable | Required | Default (Dev) | Description |
|---|---|---|---|
| `DEMO_AUTH_ENABLED` | No | `true` | Set to `false` in production to disable the demo login endpoint. |
| `DEMO_LOGIN_USERNAME` | No | `demo-user` | Username accepted by the demo login endpoint. |
| `DEMO_LOGIN_PASSWORD` | **Yes** | None | Password for the demo endpoint. Fails startup in prod if empty. |
| `DEMO_RATE_LIMIT_PER_MINUTE`| No | `60` | Maximum login attempts permitted per client IP per minute. |
| `SERVER_PORT` | No | `8084` | HTTP port for the service. |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker endpoints. |

### 1.3 Threat Detection Service
| Variable | Required | Default (Dev) | Description |
|---|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker endpoints. |
| `BRUTE_FORCE_THRESHOLD` | No | `10` | Failed attempts required within window to trigger brute-force alert. |
| `BRUTE_FORCE_WINDOW` | No | `PT60S` | ISO-8601 duration window for brute-force tracking. |
| `DETECTION_DEDUP_RETENTION` | No | `PT10M` | Duration for retaining processed event IDs in deduplication cache. |
| `NETWORK_MAX_TRACKED_KEYS` | No | `10000` | Maximum distinct IP keys retained in memory before eviction. |

### 1.4 Frontend Client
| Variable | Required | Default (Dev) | Description |
|---|---|---|---|
| `VITE_ALERT_API_BASE_URL` | No | `http://localhost:8083` | Base URL for Alert Service REST API. |
| `VITE_API_BASE_URL` | No | `http://localhost:8084` | Base URL for Demo Login Service. |
| `VITE_API_KEY` | No | Empty | Optional API key included in `X-API-Key` requests to Alert Service. |

> [!WARNING]
> **Vite client variables:** Any variable prefixed with `VITE_` is baked directly into client-side JavaScript bundles. Never store database credentials, server private keys, or admin super-secrets in `VITE_` variables.

---

## 2. Safe Configuration Templates

### Local Development Template (`.env`)
```bash
# PostgreSQL
POSTGRES_DB=threatstream
POSTGRES_USER=threatstream_app
POSTGRES_PASSWORD=local-dev-password-only
POSTGRES_PORT=5432

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9094
KAFKA_TOPIC_AUTH_EVENTS=security.auth.events
KAFKA_TOPIC_NETWORK_EVENTS=security.network.events
KAFKA_TOPIC_ALERTS=security.alerts
KAFKA_TOPIC_DLT=security.events.dlt

# Demo Login
DEMO_AUTH_ENABLED=true
DEMO_LOGIN_USERNAME=demo-user
DEMO_LOGIN_PASSWORD=local-demo-password
DEMO_RATE_LIMIT_PER_MINUTE=60

# API Security (optional in dev, required in prod)
ALERT_SERVICE_API_KEY=dev-local-api-key-optional
CORS_ALLOWED_ORIGIN=http://localhost:5173

# MongoDB Atlas (Replace with your Atlas cluster details)
MONGODB_URI=mongodb+srv://<atlas-user>:<atlas-password>@<cluster>.mongodb.net
MONGODB_DATABASE=threatstream

# Frontend
VITE_ALERT_API_BASE_URL=http://localhost:8083
VITE_API_BASE_URL=http://localhost:8084
VITE_ALERT_POLL_INTERVAL_MS=30000
VITE_API_KEY=dev-local-api-key-optional
```

### Production Profile Guidance
When deploying to staging or production:
1. Set `SPRING_PROFILES_ACTIVE=production` on all Spring Boot microservices.
2. Ensure `ALERT_SERVICE_API_KEY` is populated with a high-entropy cryptographically random string (e.g. `openssl rand -hex 32`).
3. Set `DEMO_AUTH_ENABLED=false` to eliminate the demonstration login endpoint in production.
4. Restrict `CORS_ALLOWED_ORIGIN` to your verified production domain (e.g. `https://threatstream.example.com`).

---

## 3. Secret Rotation Procedures

### 3.1 Rotating MongoDB Atlas Credentials
1. **Create New Database User:** In MongoDB Atlas Console -> Database Access, add a new user with `readWrite` permissions on the `threatstream` database. Do not delete the old user yet.
2. **Update Application Secrets:** Update the `MONGODB_URI` environment variable in your container orchestrator (e.g., Kubernetes Secrets, AWS ECS Task Definition).
3. **Rolling Restart:** Trigger a rolling restart of `alert-service`. Observe startup logs to verify connectivity:
   ```
   Configured MongoDB Atlas connection: mongodb+srv://<masked-credentials>@... (database: threatstream)
   Deterministic MongoDB indexes verified for collection 'alerts'
   ```
4. **Decommission Old User:** Verify the service is operating normally, then remove the retired user in Atlas Console.

### 3.2 Rotating Alert Service API Keys
1. **Generate New Key:**
   ```bash
   openssl rand -hex 32
   ```
2. **Deploy Dual-Acceptance or Coordinated Update:**
   - Update `ALERT_SERVICE_API_KEY` in `alert-service` environment.
   - Update `VITE_API_KEY` in frontend build environment and trigger frontend redeploy.
3. **Verify:** Check that requests from the frontend or SOC analysts include the new key and receive HTTP 200 responses.

---

## 4. Manual Infrastructure Hardening Checklist

### MongoDB Atlas Checklist
- [ ] Network Access: Whitelist only the static outbound NAT gateway IPs of the application cluster. Never leave `0.0.0.0/0` active in production.
- [ ] Database Role: Ensure database user has minimum necessary roles (`readWrite` on `threatstream.alerts`). Avoid `atlasAdmin` or cluster-wide roles.
- [ ] TLS Version: Verify Atlas cluster requires TLS 1.2 or higher.
- [ ] Auditing: Enable MongoDB Atlas database auditing for audit trail compliance.

### Apache Kafka Production Checklist
- [ ] Listener Encryption: Configure SSL/TLS encryption (`listeners=SSL://...`) for all client and inter-broker communication.
- [ ] Authentication: Enable SASL/SCRAM-SHA-512 authentication.
- [ ] Access Control Lists (ACLs):
  - `demo-login-service`: `WRITE` to `security.auth.events` only.
  - `threat-detection-service`: `READ` from `security.auth.events` and `security.network.events`; `WRITE` to `security.alerts` and `security.events.dlt`.
  - `alert-service`: `READ` from `security.alerts`; `WRITE` to `security.events.dlt`.
- [ ] Network Isolation: Ensure Kafka broker ports (9092, 9094) are not reachable from the public internet.
