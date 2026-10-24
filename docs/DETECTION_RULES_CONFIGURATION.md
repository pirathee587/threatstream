# Detection rules configuration

All properties are under `detection` in the threat-detection service configuration. Defaults preserve the pre-Phase-14 thresholds: brute force is 10 failures in 60 seconds; password spraying is 10 distinct failed usernames in 120 seconds; the failures-then-success sequence is 5 failures in 5 minutes.

| Property | Default | Validation / effect |
| --- | --- | --- |
| `brute-force.threshold`, `password-spraying.threshold`, `suspicious-login.threshold` | 10, 10, 5 | 1..100,000; alert threshold |
| `*.window` | PT60S, PT120S, PT5M | positive duration; event-time evaluation window |
| `max-tracked-sources` | 10,000 | 1..100,000; bounded in-memory source keys |
| `max-tracked-usernames` | 1,000 | 1..10,000; per-source distinct-user cap for spraying |
| `state-expiration`, `cleanup-interval` | PT10M, PT1M | positive configured retention controls; current rule cleanup happens during evaluation |
| `risk.*-weight` | 60, 70, 45 | 0..100 policy points per rule |
| `risk.medium-max`, `risk.high-max`, `risk.maximum` | 49, 74, 100 | ordered boundaries, each within 0..100 |

Invalid ranges are rejected by constructor and Bean Validation at startup. Detection cannot be disabled with a zero threshold or zero window.
