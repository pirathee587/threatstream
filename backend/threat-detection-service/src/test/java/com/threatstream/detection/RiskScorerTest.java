package com.threatstream.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RiskScorerTest {
  private final DetectionProperties properties = new DetectionProperties("a", "b", "d", 3,
      Duration.ofMinutes(10), new DetectionProperties.RuleConfig(3, Duration.ofMinutes(1)),
      new DetectionProperties.RuleConfig(3, Duration.ofMinutes(2)), new DetectionProperties.RuleConfig(2, Duration.ofMinutes(5)),
      10, 5, Duration.ofMinutes(10), Duration.ofMinutes(1), new DetectionProperties.RiskConfig(50, 75, 25, 49, 74, 100));
  @Test void maps_rule_scores_at_severity_boundaries() {
    RiskScorer scorer = new RiskScorer(properties);
    assertThat(scorer.score("AUTH_BRUTE_FORCE")).isEqualTo(50);
    assertThat(scorer.score("AUTH_PASSWORD_SPRAYING")).isEqualTo(75);
    assertThat(scorer.score("AUTH_FAILURES_THEN_SUCCESS")).isEqualTo(25);
    assertThat(scorer.score("UNKNOWN")).isZero();
    assertThat(scorer.severity(0)).isEqualTo("MEDIUM");
    assertThat(scorer.severity(24)).isEqualTo("MEDIUM");
    assertThat(scorer.severity(25)).isEqualTo("MEDIUM");
    assertThat(scorer.severity(49)).isEqualTo("MEDIUM");
    assertThat(scorer.severity(50)).isEqualTo("HIGH");
    assertThat(scorer.severity(74)).isEqualTo("HIGH");
    assertThat(scorer.severity(75)).isEqualTo("CRITICAL");
    assertThat(scorer.severity(100)).isEqualTo("CRITICAL");
  }
  @Test void rejects_invalid_rule_windows_and_risk_boundaries() {
    assertThatThrownBy(() -> new DetectionProperties.RuleConfig(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DetectionProperties.RiskConfig(1, 1, 1, 75, 50, 100)).isInstanceOf(IllegalArgumentException.class);
  }
}
