# Phase 15 validation plan

## Baseline

The source implements three auth rules: brute force (10 failed events/source/60s), password spraying (10 distinct failed usernames/source/120s), and failures followed by success (five failures/source-and-username/5m). Event IDs are deduplicated in a bounded in-memory map for 10 minutes. Alerts are armed per source during an active brute-force/spraying window, which suppresses repeat alerts from one burst; this differs from duplicate-event prevention and MongoDB's deterministic-`alertId` duplicate persistence protection.

Risk policy is deterministic: 60, 70 and 45 respectively, with Medium 0–49, High 50–74, Critical 75–100. REST/MongoDB/frontend fields are additive and legacy values are nullable.

## Scenarios

Isolated tests cover threshold/window boundaries, source isolation, distinct username behavior, a late event, source-and-username sequence correlation, event validation, state capacity eviction, and concurrent deduplication. `RiskScorerTest` covers mapping and invalid rule/risk configuration. The test suite uses only synthetic identities and timestamps.

## Environment and blockers

Kafka-to-MongoDB-to-REST validation needs local Compose services, a dedicated test database, and consumer-lag tooling. It must not be inferred from unit tests. The restricted JVM cannot self-attach Mockito's Byte Buddy agent, blocking existing Mockito tests. `npm` is absent, so frontend tests/builds require a compatible installed package manager before execution.
