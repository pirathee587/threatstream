package com.threatstream.detection;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
class AlertFactory {
  private final Clock clock;
  private final RiskScorer scorer;

  AlertFactory() {
    this(Clock.systemUTC(), null);
  }

  AlertFactory(Clock clock) { this(clock, null); }

  @Autowired
  AlertFactory(RiskScorer scorer) {
    this(Clock.systemUTC(), scorer);
  }

  private AlertFactory(Clock clock, RiskScorer scorer) {
    this.clock = clock;
    this.scorer = scorer;
  }

  SecurityAlert create(
      String rule, String type, String severity, AuthEvent e, int count, Instant first) {
    return create(
        rule,
        type,
        severity,
        e.sourceIp(),
        e.timestamp(),
        first,
        Map.of(
            "attemptCount",
            count,
            "windowSeconds",
            Math.max(0, e.timestamp().getEpochSecond() - first.getEpochSecond())));
  }

  SecurityAlert create(
      String rule,
      String type,
      String severity,
      String ip,
      Instant last,
      Instant first,
      Map<String, Object> evidence) {
    UUID deterministicId = generateAlertId(rule, type, ip, first);
    Map<String, Object> enhancedEvidence = new java.util.HashMap<>(evidence != null ? evidence : Map.of());
    String correlationId = com.threatstream.common.CorrelationIdContext.get();
    if (correlationId != null && !correlationId.isBlank()) {
      enhancedEvidence.put("correlationId", correlationId);
    }
    int riskScore = scorer == null ? fallbackScore(rule) : scorer.score(rule);
    String riskSeverity = scorer == null ? severity : scorer.severity(riskScore);
    enhancedEvidence.put("riskScore", riskScore);
    enhancedEvidence.put("scoringRationale", scorer == null ? "Configured rule policy score." : scorer.rationale(rule, riskScore));
    return new SecurityAlert(
        deterministicId,
        "SECURITY_ALERT",
        1,
        rule,
        type,
        riskSeverity,
        ip,
        "Possible " + type.toLowerCase().replace('_', ' ') + " activity detected.",
        enhancedEvidence,
        first,
        last,
        clock.instant(),
        "OPEN", riskScore, java.util.List.of(rule), "Matched " + rule + " with " + enhancedEvidence.getOrDefault("attemptCount", 0) + " relevant observations.");
  }

  private static int fallbackScore(String rule) { return "AUTH_PASSWORD_SPRAYING".equals(rule) ? 70 : "AUTH_BRUTE_FORCE".equals(rule) ? 60 : 45; }

  static UUID generateAlertId(String rule, String type, String ip, Instant first) {
    long epochMilli = first != null ? first.toEpochMilli() : 0L;
    String key =
        (rule != null ? rule : "")
            + ":"
            + (type != null ? type : "")
            + ":"
            + (ip != null ? ip : "")
            + ":"
            + epochMilli;
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
  }
}
