# Phase 13 performance report

**Status:** baseline source audit and repeatable local harness complete; integrated Kafka/Atlas and REST measurements blocked in this workspace because the local Compose application services were not running and no dedicated Atlas benchmark database was configured.

## Measured results

The following command was run on 2026-10-24 with OpenJDK 21:

```bash
cd backend
mvn -q -pl threat-detection-service,alert-service -am test \
  -Dtest=DetectionEngineBenchmarkTest,AlertServicePerformanceBenchmarkTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| Benchmark | Result | Interpretation |
| --- | ---: | --- |
| Brute-force single-thread evaluation | 175,076 events/s (12,000 events) | In-process rule evaluation only. |
| Brute-force eight-thread evaluation | 483,359 events/s (40,000 events) | Shared synchronized state limits scaling; this is not Kafka throughput. |
| Unique ID deduplication | 1,367,960 ops/s (50,000 IDs) | In-process, includes map maintenance. |
| Duplicate ID rejection | 5,766,272 ops/s (50,000 IDs) | In-process, after a warmed deduplication map. |

The alert-service benchmark did **not** execute. Mockito's inline mock maker could not attach an agent in this restricted JVM (`Could not self-attach to current VM`). This is an environment/test-runner limitation, not a product performance result. It must be rerun on a JVM that permits the Mockito agent before recording alert mapping numbers.

No HTTP, end-to-end, consumer-lag, retry/DLT, container, MongoDB execution-plan, or Atlas latency values are claimed. `localhost:8082` and `localhost:8083` were unreachable at audit time.

## Changes made in this phase

| Observed need | Change | Before/after claim | Correctness risk and verification |
| --- | --- | --- | --- |
| No safe repeatable HTTP workload | Added `scripts/performance/rest_load_test.py`. It is dependency-free, local-target-only, and bounded to 1,000 requests/32 clients. | No performance improvement claimed. | Mutating transitions require an explicit local alert ID; test reports status/error rate and latency percentiles. |
| No standard local operational snapshot | Added `scripts/performance/kafka_diagnostics.sh`. | No performance improvement claimed. | Read-only topic/group/stat collection; requires explicit local Compose acknowledgement. |
| Audit assumptions were not fully aligned with source | Corrected the audit to reflect three Kafka partitions and current bounded deduplication. | No performance improvement claimed. | Source reviewed against configuration and topic bootstrap script. |

No runtime concurrency, acknowledgement, schema, index, alert lifecycle, or detection-policy change was made because the required integrated baseline is unavailable.

## Remaining work / blocked measurements

1. Start the local Compose `apps` profile with a dedicated benchmark MongoDB database and run the guide at 2, 4, and 8 clients.
2. Capture consumer lag for both consumer groups during the demo-login workload, plus retry and DLT metrics/log counts.
3. Run `explain("executionStats")` for alert list filters and statistics aggregation with increasing dedicated data volumes. Retain the unique `alertId` index and standard UUID representation.
4. Re-run the alert-service benchmark with a JVM that permits the Mockito agent (or a non-mocked integration fixture).
5. Record frontend build sizes and browser render/request profiles after the local API workload is available.

These are deliberately recorded as blocked rather than estimated. No deployment infrastructure was introduced.
