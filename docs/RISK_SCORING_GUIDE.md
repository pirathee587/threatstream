# Risk scoring guide

ThreatStream calculates a deterministic 0–100 policy score in the detection service. It is not a probability of compromise.

| Rule | Default score |
| --- | ---: |
| `AUTH_BRUTE_FORCE` | 60 |
| `AUTH_PASSWORD_SPRAYING` | 70 |
| `AUTH_FAILURES_THEN_SUCCESS` | 45 |

Scores are clamped to `risk.maximum`. Scores 0–49 map to `MEDIUM`, 50–74 to `HIGH`, and 75–100 to `CRITICAL`, preserving the pre-existing accepted severity values. Alert messages include `riskScore`, `ruleIds`, `evidenceSummary`, and a concise scoring rationale. These are additive and legacy alerts remain readable with absent risk fields.

Rules emit independently; the score is for the individual alert and does not add scores from coincident rules, preventing double-counting of the same event evidence.
