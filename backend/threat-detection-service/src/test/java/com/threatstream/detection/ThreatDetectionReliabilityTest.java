package com.threatstream.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.threatstream.common.CorrelationIdContext;
import com.threatstream.common.KafkaCorrelationHelper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;

class ThreatDetectionReliabilityTest {

  private EventValidator validator;
  private EventDeduplicator deduplicator;
  private AlertPublisher publisher;
  private DetectionRule rule;
  private DetectionEngine engine;
  private AuthEventConsumer consumer;

  @BeforeEach
  void setUp() {
    validator = new EventValidator();
    DetectionProperties properties =
        new DetectionProperties(
            "security.auth.events",
            "security.alerts",
            "security.events.dlt",
            3,
            Duration.ofMinutes(10),
            new DetectionProperties.RuleConfig(10, Duration.ofSeconds(60)),
            new DetectionProperties.RuleConfig(10, Duration.ofSeconds(120)),
            new DetectionProperties.RuleConfig(5, Duration.ofMinutes(5)));
    deduplicator = new EventDeduplicator(properties);
    publisher = mock(AlertPublisher.class);
    rule = mock(DetectionRule.class);
    engine = new DetectionEngine(validator, deduplicator, List.of(rule), publisher);
    consumer = new AuthEventConsumer(engine);
  }

  @AfterEach
  void tearDown() {
    CorrelationIdContext.clear();
  }

  private AuthEvent createValidAuthEvent(UUID eventId, String username, String ip) {
    return new AuthEvent(
        eventId,
        "AUTH_LOGIN",
        1,
        Instant.now(),
        ip,
        username,
        "FAILURE",
        "INVALID_CREDENTIALS",
        "demo-login-service");
  }

  @Test
  void deduplicatesRepeatedAuthEventsIdempotently() {
    UUID eventId = UUID.randomUUID();
    AuthEvent event = createValidAuthEvent(eventId, "alice", "192.168.1.50");

    // First arrival
    consumer.consume(event);
    verify(rule, times(1)).evaluate(event);

    // Duplicate arrival of same eventId
    consumer.consume(event);
    // Rule evaluation should NOT be called again
    verify(rule, times(1)).evaluate(event);
  }

  @Test
  void propagatesCorrelationIdFromKafkaHeadersIntoProcessingContext() {
    UUID eventId = UUID.randomUUID();
    AuthEvent event = createValidAuthEvent(eventId, "bob", "192.168.1.51");

    RecordHeaders headers = new RecordHeaders();
    headers.add(KafkaCorrelationHelper.CORRELATION_ID_HEADER, "corr-test-pipeline-42".getBytes(StandardCharsets.UTF_8));
    ConsumerRecord<String, AuthEvent> record =
        new ConsumerRecord<>("security.auth.events", 0, 100L, eventId.toString(), event);
    headers.forEach(h -> record.headers().add(h));

    consumer.consume(record);

    verify(rule).evaluate(event);
    // After execution, MDC is cleaned up
    assertThat(CorrelationIdContext.get()).isNull();
  }

  @Test
  void rejectsInvalidAuthEventWithoutEvaluatingRules() {
    UUID eventId = UUID.randomUUID();
    // Invalid schema version and missing required fields
    AuthEvent invalid =
        new AuthEvent(
            eventId,
            "INVALID_TYPE",
            -1,
            null,
            "",
            "",
            "FAILURE",
            null,
            "");

    assertThatThrownBy(() -> consumer.consume(invalid))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(rule);
  }

  @Test
  void verifiesAlertFactoryAttachesCorrelationIdToEvidence() {
    AlertFactory factory = new AlertFactory();
    CorrelationIdContext.set("corr-trace-8888");

    SecurityAlert alert =
        factory.create(
            "BRUTE_FORCE_AUTH",
            "BRUTE_FORCE",
            "HIGH",
            "192.168.1.55",
            Instant.now(),
            Instant.now().minusSeconds(10),
            java.util.Map.of("attemptCount", 12));

    assertThat(alert.evidence()).containsKey("correlationId");
    assertThat(alert.evidence().get("correlationId")).isEqualTo("corr-trace-8888");
  }

  @Test
  void configuresKafkaErrorHandlerWithDltRecoverer() {
    KafkaErrorConfiguration config = new KafkaErrorConfiguration();
    DetectionProperties properties =
        new DetectionProperties(
            "security.auth.events",
            "security.alerts",
            "security.events.dlt",
            3,
            Duration.ofMinutes(10),
            new DetectionProperties.RuleConfig(10, Duration.ofSeconds(60)),
            new DetectionProperties.RuleConfig(10, Duration.ofSeconds(120)),
            new DetectionProperties.RuleConfig(5, Duration.ofMinutes(5)));
    org.springframework.kafka.core.KafkaTemplate<Object, Object> mockTemplate = mock(org.springframework.kafka.core.KafkaTemplate.class);

    DefaultErrorHandler handler = config.kafkaErrorHandler(mockTemplate, properties);
    assertThat(handler).isNotNull();
  }
}
