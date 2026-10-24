# Phase 13 performance audit

**Audit date:** 2026-10-24  
**Scope:** source review and local, in-process measurement only. No deployment or cloud resource was changed.

## Current pipeline and configuration

`demo-login-service` publishes authenticated login outcomes to `security.auth.events`; `threat-detection-service` validates and deduplicates them, evaluates rules, and publishes alerts; `alert-service` persists the alert to MongoDB and exposes the REST API. Topic bootstrap creates **three partitions** for every listed topic (`infrastructure/kafka/topics.sh`), not one partition.

| Area | Current behaviour | Performance/correctness implication |
| --- | --- | --- |
| Producers | Login producer uses `acks=all` and idempotence; alert producer uses `acks=all`. | Favors durability; synchronous broker/Atlas latency must be measured in an integrated run. |
| Consumers | Both listeners use `ack-mode=record`; listener concurrency is not configured, so Spring Kafka uses one consumer thread per listener. | At most one partition is actively consumed by each service group. Increasing it must be paired with ordering and rule-state tests. |
| Deduplication | A synchronized `LinkedHashMap` retains IDs for 10 minutes and is capped at 100,000 entries. | Memory is bounded, but one monitor serializes calls. Evicting oldest entries under sustained load intentionally weakens duplicate suppression for evicted IDs. |
| Detection rules | Brute-force and password-spraying rules synchronize their state maps; each tracks up to 10,000 source IPs. | State makes unordered concurrent processing risky. Password spraying builds a distinct-username stream for each event. |
| MongoDB | `alertId` has a deterministic unique index; date, severity/date, status/date, severity, status, threat type, and source-IP indexes are declared. UUIDs use standard representation. | Insert is protected against duplicate keys. Index utility requires `explain("executionStats")` against representative data; no Atlas plan was available during this audit. |
| Alert list | A count query plus a paged, sorted query are issued per request. | Offset pagination becomes progressively more expensive at deep pages. |
| Statistics | Four counts plus two aggregations are executed per request. | Six Atlas round trips are the clearest suspected API latency bottleneck, but no change is justified until measured. |
| Frontend | Axios has a 10-second timeout and request correlation ID. Vite splits React, charts, and icons into vendor chunks. | There is no polling loop in the API client; component-level request behaviour still needs browser profiling under real data. |
| Observability | Health, metrics, and Prometheus endpoints are exposed; correlation IDs are logs/headers rather than metric labels. | Safe bounded labels are preserved. Container CPU/memory and Kafka group lag can be sampled with the supplied diagnostic script. |

## Risks to test before tuning

1. A listener concurrency increase can reorder events for a source IP across partitions; rule state and threshold crossings must remain correct.
2. `RECORD` acknowledgement reduces replay scope but adds per-record commit overhead. Batch acknowledgement is not an automatic improvement because durability and duplicate behavior change.
3. `AlertService.stats()` is network-round-trip heavy. A `$facet` aggregation is a candidate only after a measured baseline and query-plan review.
4. MongoDB Atlas latency and remote connection saturation cannot be inferred from mocked or in-process tests.
5. Demo-login rate limiting intentionally constrains HTTP ingress; it must not be disabled outside a dedicated local run.

## Measurement protocol

Use [PERFORMANCE_TESTING_GUIDE.md](PERFORMANCE_TESTING_GUIDE.md). Start at two clients, capture diagnostics before/after, and stop on rising errors, lag, or resource pressure. Results from unit microbenchmarks must be labelled as such; pipeline and Atlas results require the local services and a dedicated benchmark database.
