# ThreatStream Phase 12 — Observability, Reliability & Fault Recovery Audit

**Date:** 2026-10-23  
**Auditor:** Senior Backend Engineer & Distributed Systems Reliability Architect (SRE)  
**Status:** Audit Completed  
**Baseline Verification:** All 6 Maven modules build cleanly and pass all unit/integration tests; all 18 React/Vite frontend tests pass.

---

## 1. Executive Summary

ThreatStream provides end-to-end event collection, threat detection, and alert management for security operations. As the system scales into production, observability, distributed tracing, consumer fault recovery, and persistent store resiliency must be elevated to enterprise-grade standards.

This audit evaluates the current architecture against operational resilience, distributed failure modes, log hygiene, and recovery patterns.

---

## 2. Baseline Architecture & Operational State

| Component | Target Port | Status / Verification | Observability / Failure Deficiencies Identified |
|---|---|---|---|
| **Demo Login Service** | `8084` | Verified passing tests | Missing correlation ID propagation in HTTP filters and Kafka producer headers; no structured JSON log encoder. |
| **Event Collector Service** | `8081` | Verified passing tests | Missing Kafka record headers for correlation tracking; default Actuator health without custom Kafka readiness indicator. |
| **Threat Detection Service** | `8082` | Verified passing tests | `JsonDeserializer` without `ErrorHandlingDeserializer` causes consumer stall on malformed records (poison pills); MDC correlation ID not extracted from Kafka headers. |
| **Alert Service** | `8083` | Verified passing tests | Duplicate key handling is idempotent, but lacks backoff for transient MongoDB Atlas disconnects; `DefaultErrorHandler` retries `IllegalArgumentException` unnecessarily. |
| **Kafka Broker** | `9094` (KRaft) | Verified healthy | Docker compose lacks graceful stop timeouts and container health checks for application microservices. |
| **MongoDB Atlas** | Cloud (`ap-south-1`) | Verified replica set | Socket timeouts and connection pool sizing defaults rely on driver defaults rather than explicit bounded SLA bounds. |
| **React Frontend** | `5173` | 18/18 tests passing | Axios client lacks structured classification between authentication failure (401/403) and backend service outage (502/503/504). |

---

## 3. Detailed Reliability & Observability Gap Analysis

### 3.1 Task 12.1 — Structured Logging & Injection Protection (CWE-117)
- **Current State:** Log statements use basic SLF4J parameterized strings (`LOG.warn("Alert persistence failed for {}", ...)`). In `backend/common`, `LogSanitizer` and `CorrelationIdContext` were drafted but are not yet wired into HTTP filters or Kafka interceptors across the services.
- **Vulnerabilities / Gaps:** Untrusted fields (e.g. usernames, IPs, payload fields) can contain CRLF characters leading to log injection. Standard logging outputs plain text rather than machine-parseable structured format with uniform MDC fields (`serviceName`, `correlationId`, `traceId`, `eventId`, `outcome`).

### 3.2 Task 12.2 — Distributed Event Tracing Across Asynchronous Hops
- **Current State:** `AuthenticationEvent` has an `eventId` (UUID), but downstream `SecurityAlert` generates a separate `alertId`. There is no end-to-end correlation ID propagating through HTTP headers (`X-Correlation-ID`) into Kafka record headers (`X-Correlation-ID`), through detection evaluation, and into persisted alerts.
- **Gaps:** Operators cannot trace an incident from initial login failure or API ingestion, through Kafka topics `security.auth.events` and `security.alerts`, into MongoDB `alerts` collection.

### 3.3 Task 12.3 — Health Checks & Readiness Probes
- **Current State:** `management.endpoints.web.exposure.include: health,info` is exposed, but Spring Boot Actuator probes (`health/liveness`, `health/readiness`) are not explicitly configured with `show-details: when-authorized` or `never`.
- **Gaps:** Default health endpoints can expose internal MongoDB connection string or Kafka cluster metadata if not properly secured. A transient network partition must not trigger immediate container crash-loops (liveness failure vs readiness failure).

### 3.4 Task 12.4 — Kafka Consumer Reliability, Poison Pills & DLT Handling
- **Current State:** `KafkaErrorConfiguration` configures `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` and `FixedBackOff(1000L, 3)`.
- **Gaps:**
  1. **Poison Pill Vulnerability:** The Kafka consumer uses `JsonDeserializer` directly. If a publisher sends non-JSON bytes or an incompatible schema, `SerializationException` is thrown before listener dispatch. Without `ErrorHandlingDeserializer`, the consumer partition can stall in an infinite crash-and-poll loop.
  2. **Non-Retryable Exceptions:** Business validation errors (`IllegalArgumentException`) are retried 3 times pointlessly before going to DLT. Only transient network or IO errors should be retried.
  3. **DLT Metadata:** The Dead Letter Topic `security.events.dlt` needs standard headers (`kafka_dlt-original-topic`, `kafka_dlt-exception-message`, `kafka_dlt-exception-stacktrace`) for inspection and replayability.

### 3.5 Task 12.5 — Idempotence & Deduplication
- **Current State:** `EventDeduplicator` in `threat-detection-service` uses an in-memory `LinkedHashMap` with retention cutoff. `AlertService` checks `repo.findByAlertId()` and catches `DuplicateKeyException`.
- **Gaps:** Need dedicated unit and integration tests verifying duplicate message arrival under Kafka consumer rebalance, redelivery, and simultaneous multi-threaded inserts.

### 3.6 Task 12.6 — MongoDB Resilience & Atlas Outage Recovery
- **Current State:** `AlertService.persist` calls `mongoTemplate.insert(doc)`.
- **Gaps:** If MongoDB Atlas experiences a brief network flap or failover, the write fails immediately. The Kafka listener error handler catches the exception and retries 3 times with 1-second backoff. If Atlas does not recover within 3 seconds, the record is routed to DLT rather than lost. We must ensure DLT preservation is guaranteed and documented, and verify reconnection upon restore.

### 3.7 Task 12.7 — Metrics & Operational Monitoring
- **Current State:** `micrometer-registry-prometheus` is included in POM files, but Prometheus endpoints are not exposed in `application.yml` management configurations, and custom operational metrics (events consumed, alerts triggered, DLT routing events) are not registered with `MeterRegistry`.
- **Gaps:** Operators cannot monitor Kafka consumer lag, rule execution counts, or DLT rates in Prometheus or Grafana.

### 3.8 Task 12.8 — Graceful Shutdown
- **Current State:** Spring Boot `server.shutdown: graceful` is not configured, and Kafka listener container shutdown timeouts are not defined.
- **Gaps:** Container SIGTERM can immediately kill threads midway through Kafka offset commit or MongoDB document write, leading to duplicate processing or unacknowledged records upon restart.

### 3.9 Task 12.9 — Frontend Operational Feedback
- **Current State:** Frontend uses Axios to fetch alerts and metrics.
- **Gaps:** When backend services or Kafka/Mongo are recovering, the frontend needs clear, non-leaking user notifications (e.g. "Service temporarily unavailable, retrying...") rather than raw JavaScript exceptions or unhandled promise rejections.

---

## 4. Implementation Plan

1. **Phase 12.1 & 12.2:** Common Logging & Distributed Tracing (Correlation ID Filter, Kafka Header Interceptors, Structured Logger).
2. **Phase 12.3:** Health Check & Readiness Configuration across all 4 microservices.
3. **Phase 12.4 & 12.5:** Kafka Consumer Hardening (`ErrorHandlingDeserializer`, Non-retryable categorization, DLT routing, and idempotence testing).
4. **Phase 12.6:** MongoDB Resilient Connection & Outage Recovery.
5. **Phase 12.7:** Operational Metrics instrumentation (`MeterRegistry` counters/timers).
6. **Phase 12.8:** Graceful Shutdown configuration in Spring Boot and Docker.
7. **Phase 12.9:** Frontend Error Handling & Resilience.
8. **Phase 12.10 & Section 13:** Automated Reliability Test Suite (Tests A-F) & Documentation.
