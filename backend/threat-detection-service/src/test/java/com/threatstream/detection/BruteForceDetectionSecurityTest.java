package com.threatstream.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BruteForceDetectionSecurityTest {

  private DetectionProperties props;
  private AlertFactory alertFactory;
  private BruteForceRule rule;
  private EventValidator validator;
  private EventDeduplicator deduplicator;

  @BeforeEach
  void setUp() {
    props =
        new DetectionProperties(
            "security.auth.events",
            "security.alerts",
            "security.events.dlt",
            3,
            Duration.ofMinutes(10),
            new DetectionProperties.RuleConfig(10, Duration.ofMinutes(1)),
            new DetectionProperties.RuleConfig(10, Duration.ofMinutes(2)),
            new DetectionProperties.RuleConfig(5, Duration.ofMinutes(5)));
    alertFactory = new AlertFactory();
    rule = new BruteForceRule(props, alertFactory);
    validator = new EventValidator();
    deduplicator = new EventDeduplicator(props);
  }

  private AuthEvent createEvent(String ip, String username, String outcome, Instant timestamp) {
    return new AuthEvent(
        UUID.randomUUID(),
        "AUTH_LOGIN",
        1,
        timestamp,
        ip,
        username,
        outcome,
        null,
        "demo-login-service");
  }

  @Test
  void generatesAlertAtExactlyTenFailedAttempts() {
    Instant base = Instant.parse("2026-10-23T10:00:00Z");

    // 1 to 9 failed attempts -> no alert
    for (int i = 1; i <= 9; i++) {
      Optional<SecurityAlert> alert =
          rule.evaluate(createEvent("192.168.1.50", "admin", "FAILURE", base.plusSeconds(i)));
      assertThat(alert).as("Attempt " + i + " should not trigger brute-force alert").isEmpty();
    }

    // Exactly 10th failed attempt -> triggers alert
    Optional<SecurityAlert> alert10 =
        rule.evaluate(createEvent("192.168.1.50", "admin", "FAILURE", base.plusSeconds(10)));
    assertThat(alert10).as("Attempt 10 must trigger brute-force alert").isPresent();
    assertThat(alert10.get().threatType()).isEqualTo("BRUTE_FORCE");
    assertThat(alert10.get().severity()).isEqualTo("HIGH");
    assertThat(alert10.get().sourceIp()).isEqualTo("192.168.1.50");

    // 11th failed attempt within same window -> armed suppression prevents duplicate alert
    Optional<SecurityAlert> alert11 =
        rule.evaluate(createEvent("192.168.1.50", "admin", "FAILURE", base.plusSeconds(11)));
    assertThat(alert11).as("Attempt 11 should be suppressed to prevent alert flood").isEmpty();
  }

  @Test
  void mixedSuccessDoesNotTriggerBruteForce() {
    Instant base = Instant.parse("2026-10-23T10:00:00Z");

    for (int i = 1; i <= 5; i++) {
      rule.evaluate(createEvent("192.168.1.51", "admin", "FAILURE", base.plusSeconds(i)));
    }
    // Successful login
    Optional<SecurityAlert> successAlert =
        rule.evaluate(createEvent("192.168.1.51", "admin", "SUCCESS", base.plusSeconds(6)));
    assertThat(successAlert).isEmpty();

    // 4 more failures (total 9 failures)
    for (int i = 7; i <= 10; i++) {
      Optional<SecurityAlert> alert =
          rule.evaluate(createEvent("192.168.1.51", "admin", "FAILURE", base.plusSeconds(i)));
      assertThat(alert).isEmpty();
    }

    // 10th failure triggers
    Optional<SecurityAlert> finalAlert =
        rule.evaluate(createEvent("192.168.1.51", "admin", "FAILURE", base.plusSeconds(11)));
    assertThat(finalAlert).isPresent();
  }

  @Test
  void deduplicatorFiltersDuplicateEventDelivery() {
    UUID eventId = UUID.randomUUID();
    assertThat(deduplicator.first(eventId)).isTrue();
    assertThat(deduplicator.first(eventId)).isFalse();
    assertThat(deduplicator.first(null)).isFalse();
  }

  @Test
  void generatesDeterministicAlertIdForIdenticalIncidentAttributes() {
    Instant firstSeen = Instant.parse("2026-10-23T10:00:00Z");
    UUID id1 = AlertFactory.generateAlertId("AUTH_BRUTE_FORCE", "BRUTE_FORCE", "192.168.1.99", firstSeen);
    UUID id2 = AlertFactory.generateAlertId("AUTH_BRUTE_FORCE", "BRUTE_FORCE", "192.168.1.99", firstSeen);

    assertThat(id1).isEqualTo(id2);

    UUID differentIp = AlertFactory.generateAlertId("AUTH_BRUTE_FORCE", "BRUTE_FORCE", "192.168.1.100", firstSeen);
    assertThat(differentIp).isNotEqualTo(id1);
  }

  @Test
  void rejectsMalformedEventsInValidator() {
    Instant now = Instant.parse("2026-10-23T10:00:00Z");

    // Missing eventId
    assertThatThrownBy(
            () -> validator.validate(new AuthEvent(null, "AUTH_LOGIN", 1, now, "1.1.1.1", "u", "FAILURE", null, "s")))
        .isInstanceOf(IllegalArgumentException.class);

    // Timestamp far in the past
    assertThatThrownBy(
            () ->
                validator.validate(
                    new AuthEvent(
                        UUID.randomUUID(),
                        "AUTH_LOGIN",
                        1,
                        Instant.parse("2010-01-01T00:00:00Z"),
                        "1.1.1.1",
                        "u",
                        "FAILURE",
                        null,
                        "s")))
        .isInstanceOf(IllegalArgumentException.class);

    // Invalid schema version
    assertThatThrownBy(
            () ->
                validator.validate(
                    new AuthEvent(
                        UUID.randomUUID(), "AUTH_LOGIN", 2, now, "1.1.1.1", "u", "FAILURE", null, "s")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
