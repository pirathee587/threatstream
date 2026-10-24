# ThreatStream Observability Guide

## 1. Overview

ThreatStream employs an asynchronous, event-driven microservices architecture:
```
[React Frontend / Demo Client]
         │  (HTTP + X-Correlation-ID)
         ▼
[Demo Login Service :8084]
         │  (Kafka Producer + Header: X-Correlation-ID)
         ▼
[Kafka Topic: security.auth.events]
         │  (Kafka Consumer + ErrorHandlingDeserializer)
         ▼
[Threat Detection Service :8082]
         │  (Kafka Producer + Header: X-Correlation-ID)
         ▼
[Kafka Topic: security.alerts]
         │  (Kafka Consumer + Idempotent Persistence)
         ▼
[Alert Service :8083] ──► [MongoDB Atlas Database]
         ▲
         │  (HTTP GET/PATCH + X-API-Key + X-Correlation-ID)
[SecOps UI Dashboard :5173]
```

This guide details how to monitor, trace, diagnose, and audit ThreatStream components across staging and production environments.

---

## 2. Centralized Structured Logging

### 2.1 Format and Pattern
All backend microservices format console and container logs with an ISO-8601 UTC timestamp, bounded thread name, MDC correlation ID, bounded logger class, and the sanitized log message:

```
%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p [%15.15t] [%X{correlationId:-none}] %-40.40logger{39} : %m%n
```

Example Log Entry:
```
2026-10-23T21:34:30.496+05:30  INFO [           main] [6c84ec00-c5b6-40d7-a03f-fbf804a3e3c4] c.t.auth.DemoLoginSecurityTest           : Starting DemoLoginSecurityTest using Java 21
```

### 2.2 Standard Fields
| Field | Source | Purpose |
|---|---|---|
| `timestamp` | JVM / System Clock | ISO-8601 millisecond-precision event timestamp. |
| `level` | Logback (`INFO`, `WARN`, `ERROR`, `DEBUG`) | Severity of the log record. |
| `correlationId` | SLF4J MDC (`CorrelationIdContext`) | End-to-end trace identifier tracking an event or HTTP request across hops. |
| `logger` | Package/Class | Originating service subsystem. |
| `errorCategory` | Exception Handler | Categorization (`VALIDATION_ERROR`, `PERSISTENCE_ERROR`, `PROCESSING_ERROR`). |

### 2.3 Log Injection Prevention (CWE-117)
All untrusted strings (usernames, input payloads, external headers) pass through `LogSanitizer.sanitize()`:
- Strips CR (`\r`), LF (`\n`), tab (`\t`), and null bytes (`\u0000`).
- Truncates oversized input to 256 characters with `...[truncated]`.
- Excludes credentials, plaintext passwords, MongoDB connection secrets, and bearer tokens.

---

## 3. Distributed Event Tracing

### 3.1 Trace Context Lifecycle
1. **HTTP Ingestion:** `CorrelationIdFilter` checks for an incoming `X-Correlation-ID` header. If absent, it generates a standard UUID v4 and binds it to `CorrelationIdContext` (MDC).
2. **Kafka Publication:** `KafkaCorrelationHelper.injectCorrelationId(record.headers())` copies the active correlation ID into Kafka record headers as UTF-8 bytes (`X-Correlation-ID`).
3. **Kafka Consumption:** Consumers (`AuthEventConsumer`, `NetworkEventConsumer`, `AlertConsumer`) invoke `KafkaCorrelationHelper.extractAndSetCorrelationId(record.headers())` to establish context before processing and clear MDC in `finally`.
4. **Alert Persistence:** `AlertFactory` embeds the correlation ID into the alert `evidence` map under the `"correlationId"` key, making the original request traceable directly from MongoDB query results.

### 3.2 Correlating an Incident
To track an end-to-end incident across container logs:
```bash
# Grep all service logs for a specific correlation ID
docker compose logs | grep "6c84ec00-c5b6-40d7-a03f-fbf804a3e3c4"
```

---

## 4. Health Checks and Readiness Probes

### 4.1 Endpoints
Each backend service exposes operational probes under `/actuator`:

| Service | Port | Liveness Probe | Readiness Probe |
|---|---|---|---|
| **Demo Login Service** | `8084` | `http://localhost:8084/actuator/health/liveness` | `http://localhost:8084/actuator/health/readiness` |
| **Threat Detection Service** | `8082` | `http://localhost:8082/actuator/health/liveness` | `http://localhost:8082/actuator/health/readiness` |
| **Alert Service** | `8083` | `http://localhost:8083/actuator/health/liveness` | `http://localhost:8083/actuator/health/readiness` |
| **Event Collector Service** | `8081` | `http://localhost:8081/actuator/health/liveness` | `http://localhost:8081/actuator/health/readiness` |

### 4.2 Probing Policies
- **Liveness:** Reflects internal process integrity. If liveness fails, the container is deadlocked or out of memory and should be restarted.
- **Readiness:** Reflects operational capacity to accept traffic. If an external service (e.g. MongoDB or Kafka broker) is temporarily unreachable, readiness marks `OUT_OF_SERVICE` or `DOWN`, removing the pod from routing without triggering catastrophic restart loops.
- **Information Protection:** Health responses configured with `show-details: when-authorized` protect sensitive infrastructure details (hostnames, cluster topology, credentials) from unauthenticated discovery.

---

## 5. Metrics and Operational Monitoring

### 5.1 Prometheus Metrics Endpoint
All microservices include `micrometer-registry-prometheus` exposing metrics at:
```
GET /actuator/prometheus
```

### 5.2 Key Metrics to Monitor
| Metric | Type | Purpose |
|---|---|---|
| `http.server.requests` | Timer | Request rate, latency distribution, and HTTP status codes (2xx, 4xx, 5xx). |
| `jvm.memory.used` / `jvm.memory.max` | Gauge | Heap and non-heap memory utilization. |
| `jvm.gc.pause` | Timer | Garbage collection duration and frequency. |
| `spring.kafka.listener` | Counter/Timer | Kafka consumer processing rate and latency per record. |
| `mongodb.driver.pool.size` | Gauge | MongoDB connection pool utilization in Alert Service. |

### 5.3 Low-Cardinality Metric Labels
Metric tags strictly use bounded enumerations:
- `outcome`: `SUCCESS`, `FAILURE`
- `threatType`: `BRUTE_FORCE`, `PASSWORD_SPRAYING`, `PORT_SCAN`, `DOS`, `DDOS`
- `severity`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `status`: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`

*No user-supplied inputs, IP addresses, or UUIDs are used as metric tags.*

---

## 6. Docker Container Health & Graceful Shutdown

All application containers in `docker-compose.yml` are configured with:
1. **Container Healthchecks:** Automatically probe `/actuator/health/liveness` every 15 seconds.
2. **Graceful Stop Window:** `stop_grace_period: 20s` allows Spring Boot's 15-second graceful shutdown phase (`server.shutdown: graceful`) to finish active Kafka offset commits and DB transactions cleanly before container termination.
