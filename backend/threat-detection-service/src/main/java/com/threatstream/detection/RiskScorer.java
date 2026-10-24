package com.threatstream.detection;

import org.springframework.stereotype.Component;

/** Deterministic policy score, not a probability of compromise. */
@Component
class RiskScorer {
  private final DetectionProperties properties;
  RiskScorer(DetectionProperties properties) { this.properties = properties; }
  int score(String ruleId) {
    var risk = properties.risk();
    int score = switch (ruleId) { case "AUTH_BRUTE_FORCE" -> risk.bruteForceWeight(); case "AUTH_PASSWORD_SPRAYING" -> risk.passwordSprayingWeight(); case "AUTH_FAILURES_THEN_SUCCESS" -> risk.suspiciousLoginWeight(); default -> 0; };
    return Math.max(0, Math.min(risk.maximum(), score));
  }
  String severity(int score) { var risk = properties.risk(); return score <= risk.mediumMax() ? "MEDIUM" : score <= risk.highMax() ? "HIGH" : "CRITICAL"; }
  String rationale(String ruleId, int score) { return "Rule " + ruleId + " contributed " + score + " of " + properties.risk().maximum() + " policy points."; }
}
