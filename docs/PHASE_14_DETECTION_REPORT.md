# Phase 14 detection report

**Status:** Partially completed — source implementation and focused unit checks completed; Mockito-dependent and integrated checks remain environment-blocked.

## Delivered

Detection properties now validate thresholds, positive windows, bounded source/user capacities and ordered risk boundaries. Brute force remains 10 failures/60 seconds; password spraying is corrected to 10 distinct **failed** usernames/120 seconds; failures-then-success remains 5 failures/5 minutes. Rules use source-IP-local, bounded state and scheduled stale-state removal.

The scoring engine assigns 60 points to brute force, 70 to password spraying, and 45 to failures-then-success, clamps to 0–100, and maps 0–49 to `MEDIUM`, 50–74 to `HIGH`, and 75–100 to `CRITICAL`. Scores are per alert, rather than summed across rules, to avoid double-counting. `riskScore`, `ruleIds`, and `evidenceSummary` are additive Kafka/MongoDB/REST fields; old documents deserialize with absent values and the UI displays a legacy fallback.

## Compatibility and limitations

Kafka topic names, alert IDs, UUID handling, alert status transitions, and REST response fields are preserved. No MongoDB index was added because no score/rule filter was added without query-plan evidence. Risk is trusted-backend calculated and is not a calibrated probability. Local in-memory state is not shared across process instances or Kafka partitions; event-time ordering is only reliable within a partition.

## Verification

`mvn -q -pl threat-detection-service -am -Dtest=DetectionRulesTest,RiskScorerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed.

`mvn -q -pl alert-service -am -DskipTests compile` passed. The wider backend test run compiled Phase 14 code but failed 7 existing Mockito-based tests because the restricted JVM cannot self-attach the Byte Buddy agent. `npm` is unavailable in this environment, so frontend test/lint/build commands could not be run. Integrated Kafka, REST, and Atlas measurements remain blocked exactly as described in the Phase 13 report.
