# ThreatStream Failure Recovery Runbook

**Target Audience:** Site Reliability Engineers (SRE), Security Operations (SecOps), Platform Engineers  
**Classification:** Operational Guide  
**Version:** 1.0 (Phase 12)

---

## 1. Incident Severity Definitions

| Severity | Definition | Target Resolution |
|---|---|---|
| **SEV-1** | Kafka broker unavailable, MongoDB Atlas cluster unreachable, or Alert API completely down. | < 30 minutes |
| **SEV-2** | Increased DLT routing rate, degraded detection throughput, or consumer rebalancing delays. | < 2 hours |
| **SEV-3** | Transient network hiccups resolved by automated bounded retries; individual duplicate key logs. | < 24 hours |

---

## 2. Scenario Runbooks

### 2.1 Runbook A — Kafka Broker Outage or Disconnect

**Symptoms:**
- Log warnings: `org.apache.kafka.clients.NetworkClient : Node -1 disconnected.` or `BrokerNotAvailableException`.
- Actuator readiness probe (`/actuator/health/readiness`) transitions to `DOWN` or `OUT_OF_SERVICE`.
- Demo login fails with HTTP 500 (`EventPublishingException`).

**Automated Protection:**
- Spring Kafka consumers back off exponentially or via configured fixed intervals (`1000ms`, 3 attempts).
- Once the broker recovers, the consumers automatically reconnect and resume consuming from the last committed offset.

**Recovery Steps:**
1. Check Kafka container health:
   ```bash
   docker compose ps kafka
   docker compose logs --tail=100 kafka
   ```
2. If Kafka is down, restart the broker:
   ```bash
   docker compose restart kafka
   ```
3. Verify topic readiness:
   ```bash
   docker exec -it threatstream-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
   ```
4. Check service logs to confirm automatic reconnection:
   ```bash
   docker compose logs -f threat-detection-service alert-service
   ```

---

### 2.2 Runbook B — MongoDB Atlas Outage or Failover

**Symptoms:**
- Log warnings: `Alert persistence failed for <alertId> [errorCategory=PERSISTENCE_ERROR]`.
- Alert Service readiness probe returns `DOWN`.
- REST queries against `/api/alerts` return HTTP 503 or timeout.

**Automated Protection:**
- Alert Service consumers retry transient persistence errors up to 3 times (1-second backoff).
- If Atlas remains unavailable beyond configured retry bounds, the event is routed safely to `security.events.dlt` to avoid consumer thread blockage or unbounded backpressure.
- No alerts are discarded silently.

**Recovery Steps:**
1. Check Atlas cluster status via MongoDB Cloud Console or AWS health dashboard (`ap-south-1`).
2. Verify DNS resolution and TLS connectivity from the host:
   ```bash
   nslookup cluster0.threatstream.mongodb.net
   ```
3. Once Atlas connectivity is restored, verify Alert Service logs:
   ```bash
   docker compose logs --tail=50 alert-service
   ```
4. Query `/actuator/health` to confirm MongoDB status is `UP`.
5. Replay any alerts preserved in `security.events.dlt` (see Runbook C).

---

### 2.3 Runbook C — Poison Pill Messages & Dead Letter Topic (DLT) Inspection

**Symptoms:**
- Events routed to `security.events.dlt`.
- Log entries: `Routing unrecoverable or permanently invalid event to DLT`.

**Automated Protection:**
- `ErrorHandlingDeserializer` intercepts corrupted or malformed payloads, preventing consumer crash loops.
- `KafkaErrorConfiguration` routes non-retryable errors (`IllegalArgumentException`, `DeserializationException`, `SerializationException`) immediately to `security.events.dlt` without spinning pointless retries.
- Standard headers are appended:
  - `kafka_dlt-original-topic`
  - `kafka_dlt-original-partition`
  - `kafka_dlt-original-offset`
  - `kafka_dlt-exception-message`
  - `kafka_dlt-exception-stacktrace`

**Inspection Steps:**
1. Read messages from the DLT topic:
   ```bash
   docker exec -it threatstream-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 \
     --topic security.events.dlt \
     --from-beginning \
     --property print.headers=true \
     --property print.key=true
   ```
2. Diagnose root cause:
   - If payload is malicious/corrupted: Document the incident; no replay needed.
   - If payload was valid but rejected due to temporary database downtime: Re-publish to the original topic once dependency is healthy.

**Replay Steps (Selective Replay):**
1. Extract the valid payload from the DLT output.
2. Publish back to the originating topic:
   ```bash
   docker exec -i threatstream-kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
     --bootstrap-server localhost:9092 \
     --topic security.alerts
   ```

---

### 2.4 Runbook D — Duplicate Delivery & Idempotent Verification

**Symptoms:**
- Duplicate message IDs detected during consumer group rebalancing or broker network retransmits.

**Automated Protection:**
1. **Threat Detection Service:** `EventDeduplicator` tracks seen UUIDs in an in-memory sliding window (retention configurable, default 10 minutes). Repeated event IDs are dropped immediately before evaluating detection rules.
2. **Alert Service:** `AlertService.persist()` first checks `findByAlertId()`. If existing, it executes `verifyConsistentDuplicate()` and returns without duplicate insert.
3. **MongoDB Constraint:** `AlertDocument` enforces `@Indexed(unique = true)` on `alertId`. If concurrent workers race to insert identical alerts, `DuplicateKeyException` is caught and safely resolved without throwing an unhandled error.

**Validation Steps:**
1. Query MongoDB for duplicate alert IDs:
   ```bash
   # Count documents grouped by alertId
   mongosh "$MONGODB_URI" --eval 'db.alerts.aggregate([{"$group": {"_id": "$alertId", "count": {"$sum": 1}}}, {"$match": {"count": {"$gt": 1}}}])'
   ```
2. The output must return 0 documents matching `count > 1`.

---

### 2.5 Runbook E — Graceful Shutdown & Recovery Verification

**Procedure:**
1. When stopping services, send standard SIGTERM:
   ```bash
   docker compose stop threat-detection-service alert-service
   ```
2. Container waits up to 20 seconds (`stop_grace_period`), allowing Spring Boot's 15-second shutdown window to:
   - Stop accepting new records.
   - Complete processing for in-flight events.
   - Commit consumer offsets to Kafka.
3. Upon restart, the consumer resumes at the exact committed offset with zero message loss and zero duplicated processing:
   ```bash
   docker compose start threat-detection-service alert-service
   ```
