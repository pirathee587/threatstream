package com.threatstream.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DetectionAccuracyTest {
  private static final Instant BASE = Instant.parse("2026-10-24T12:00:00Z");
  private DetectionProperties properties(int threshold, int sources) {
    return new DetectionProperties("in", "out", "dlt", 3, Duration.ofMinutes(10),
        new DetectionProperties.RuleConfig(threshold, Duration.ofSeconds(60)),
        new DetectionProperties.RuleConfig(threshold, Duration.ofSeconds(120)),
        new DetectionProperties.RuleConfig(threshold, Duration.ofMinutes(5)), sources, 10,
        Duration.ofMinutes(1), Duration.ofSeconds(1), new DetectionProperties.RiskConfig(60, 70, 45, 49, 74, 100));
  }
  private AuthEvent event(String ip, String user, String outcome, int second) {
    return new AuthEvent(UUID.randomUUID(), "AUTH_LOGIN", 1, BASE.plusSeconds(second), ip, user, outcome, null, "synthetic-test");
  }

  @Test void bruteForceBoundaryWindowAndSourceIsolation() {
    BruteForceRule rule = new BruteForceRule(properties(10, 10), new AlertFactory());
    for (int i = 0; i < 9; i++) assertThat(rule.evaluate(event("10.0.0.1", "a", "FAILURE", i))).isEmpty();
    assertThat(rule.evaluate(event("10.0.0.2", "a", "FAILURE", 9))).isEmpty();
    assertThat(rule.evaluate(event("10.0.0.1", "a", "FAILURE", 10))).isPresent();
    BruteForceRule expired = new BruteForceRule(properties(2, 10), new AlertFactory());
    expired.evaluate(event("10.0.0.3", "a", "FAILURE", 0));
    assertThat(expired.evaluate(event("10.0.0.3", "a", "FAILURE", 61))).isEmpty();
  }

  @Test void sprayingCountsOnlyDistinctFailuresAndHandlesLateEvents() {
    PasswordSprayingRule rule = new PasswordSprayingRule(properties(10, 10), new AlertFactory());
    for (int i = 0; i < 20; i++) assertThat(rule.evaluate(event("10.0.1.1", "same", "FAILURE", i))).isEmpty();
    for (int i = 0; i < 8; i++) assertThat(rule.evaluate(event("10.0.1.1", "u" + i, "FAILURE", 30 + i))).isEmpty();
    assertThat(rule.evaluate(event("10.0.1.1", "success", "SUCCESS", 40))).isEmpty();
    assertThat(rule.evaluate(event("10.0.1.1", "u8", "FAILURE", 20))).isPresent();
  }

  @Test void sequenceUsesSourceAndUsernameAndDoesNotCrossAccounts() {
    SuspiciousLoginRule rule = new SuspiciousLoginRule(properties(5, 10), new AlertFactory());
    for (int i = 0; i < 5; i++) rule.evaluate(event("10.0.2.1", "alice", "FAILURE", i));
    assertThat(rule.evaluate(event("10.0.2.1", "bob", "SUCCESS", 6))).isEmpty();
    assertThat(rule.evaluate(event("10.0.2.1", "alice", "SUCCESS", 6))).isPresent();
  }

  @Test void dedupExpiresAndIsThreadSafe() throws Exception {
    Instant now = Instant.parse("2026-10-24T13:00:00Z");
    EventDeduplicator dedup = new EventDeduplicator(properties(2, 10), Clock.fixed(now, ZoneOffset.UTC));
    UUID id = UUID.randomUUID();
    assertThat(dedup.first(id)).isTrue();
    assertThat(dedup.first(id)).isFalse();
    try (var pool = Executors.newFixedThreadPool(8)) {
      List<Callable<Boolean>> jobs = java.util.stream.IntStream.range(0, 20).mapToObj(i -> (Callable<Boolean>) () -> dedup.first(UUID.randomUUID())).toList();
      assertThat(pool.invokeAll(jobs).stream().map(f -> { try { return f.get(); } catch (Exception e) { throw new AssertionError(e); } })).allMatch(Boolean::booleanValue);
    }
  }

  @Test void validatesAdversarialFieldsAndCapacityEvictsRatherThanClearingAllState() {
    EventValidator validator = new EventValidator();
    assertThatThrownBy(() -> validator.validate(new AuthEvent(null, "AUTH_LOGIN", 1, BASE, "ip", "u", "FAILURE", null, "s"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> validator.validate(new AuthEvent(UUID.randomUUID(), "AUTH_LOGIN", 1, BASE, "ip\n", "u".repeat(300), "UNKNOWN", null, "s"))).isInstanceOf(IllegalArgumentException.class);
    BruteForceRule rule = new BruteForceRule(properties(2, 1), new AlertFactory());
    rule.evaluate(event("10.0.3.1", "a", "FAILURE", 1));
    rule.evaluate(event("10.0.3.2", "a", "FAILURE", 2));
    assertThat(rule.evaluate(event("10.0.3.1", "a", "FAILURE", 3))).isEmpty();
  }
}
