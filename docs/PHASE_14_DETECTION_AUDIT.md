# Phase 14 detection audit

## Baseline inspected

The audit reviewed `BruteForceRule`, `PasswordSprayingRule`, `SuspiciousLoginRule`, `DetectionEngine`, `EventDeduplicator`, event/alert records, Alert Service persistence/controllers, frontend alert pages, and Phase 12/13 reports.

Brute force previously emitted one `HIGH` alert after 10 failed events from one source IP in 60 seconds. Password spraying emitted one `HIGH` alert after 10 distinct usernames from a source IP in 120 seconds, but included successful events. The existing sequence rule emits after five failures followed by a success in five minutes. Deduplication uses a bounded (100,000) in-memory event-ID map with a 10-minute clock-time retention. All rules are synchronized; Kafka has three partitions, so only records sharing a Kafka key/partition have ordering guarantees.

## Gaps and compatible changes

Hard-coded 10,000-source limits could clear all rule state; thresholds were configurable but lacked validation; alerts had no score/evidence fields; and rule state expired only when a later event arrived. Phase 14 adds validated properties, bounded eviction, scheduled expiry, failure-only spraying evidence, a deterministic score, and additive alert fields. Existing topics, event IDs, alert lifecycle and severity values are unchanged.

The rules use source IP as their stable identity. Event-time windows accept out-of-order events only to the extent events remain in the current deque; cross-partition or multi-instance correlation is not guaranteed. A distributed state store would be required for that guarantee.

## Required verification

Rule thresholds/windows, duplicate filtering, concurrent evaluation, capacity/expiry, score boundaries, legacy documents and REST/frontend display require coverage. Existing Mockito-dependent tests are blocked in this restricted JVM; Phase 13 also records integrated Kafka/Atlas/HTTP measurements as unavailable because services and a dedicated benchmark database are absent.
