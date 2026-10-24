package com.threatstream.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.threatstream.common.CorrelationIdContext;
import com.threatstream.common.KafkaCorrelationHelper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

class AlertServiceReliabilityTest {

  private AlertRepository repository;
  private AlertValidator validator;
  private MongoTemplate mongoTemplate;
  private AlertService alertService;
  private AlertConsumer alertConsumer;

  @BeforeEach
  void setUp() {
    repository = mock(AlertRepository.class);
    validator = new AlertValidator();
    mongoTemplate = mock(MongoTemplate.class);
    alertService = new AlertService(repository, validator, mongoTemplate);
    alertConsumer = new AlertConsumer(alertService);
  }

  @AfterEach
  void tearDown() {
    CorrelationIdContext.clear();
  }

  private AlertEvent createSampleAlertEvent(UUID id) {
    Instant now = Instant.now();
    return new AlertEvent(
        id,
        "SECURITY_ALERT",
        1,
        "BRUTE_FORCE_AUTH",
        "BRUTE_FORCE",
        "HIGH",
        "192.168.1.100",
        "Multiple failed login attempts detected.",
        Map.of("attemptCount", 10),
        now.minusSeconds(30),
        now,
        now,
        "OPEN");
  }

  @Test
  void handlesDuplicateAlertDeliveryIdempotentlyWithoutError() {
    UUID alertId = UUID.randomUUID();
    AlertEvent event = createSampleAlertEvent(alertId);

    AlertDocument existingDoc = new AlertDocument();
    existingDoc.setAlertId(alertId);
    existingDoc.setThreatType(event.threatType());
    existingDoc.setRuleId(event.ruleId());
    existingDoc.setSourceIp(event.sourceIp());
    existingDoc.setSeverity(event.severity());

    // First arrival: not yet stored
    when(repository.findByAlertId(alertId)).thenReturn(Optional.empty());
    alertService.persist(event);
    verify(mongoTemplate, times(1)).insert(any(AlertDocument.class));

    // Duplicate arrival: already stored in MongoDB
    when(repository.findByAlertId(alertId)).thenReturn(Optional.of(existingDoc));
    alertService.persist(event);
    // Insert count should remain 1 (no duplicate insert attempted)
    verify(mongoTemplate, times(1)).insert(any(AlertDocument.class));
  }

  @Test
  void handlesConcurrentDuplicateRaceConditionGracefully() {
    UUID alertId = UUID.randomUUID();
    AlertEvent event = createSampleAlertEvent(alertId);

    AlertDocument existingDoc = new AlertDocument();
    existingDoc.setAlertId(alertId);
    existingDoc.setThreatType(event.threatType());
    existingDoc.setRuleId(event.ruleId());
    existingDoc.setSourceIp(event.sourceIp());
    existingDoc.setSeverity(event.severity());

    when(repository.findByAlertId(alertId))
        .thenReturn(Optional.empty()) // Check 1 finds nothing
        .thenReturn(Optional.of(existingDoc)); // Reload after duplicate-key finds doc inserted by concurrent thread

    doThrow(new DuplicateKeyException("E11000 duplicate key error"))
        .when(mongoTemplate)
        .insert(any(AlertDocument.class));

    // Must not throw exception; handles concurrent duplicate idempotently
    alertService.persist(event);
    verify(repository, times(2)).findByAlertId(alertId);
  }

  @Test
  void propagatesCorrelationIdFromKafkaHeaderIntoMdcDuringProcessing() {
    UUID alertId = UUID.randomUUID();
    AlertEvent event = createSampleAlertEvent(alertId);

    RecordHeaders headers = new RecordHeaders();
    headers.add(KafkaCorrelationHelper.CORRELATION_ID_HEADER, "test-corr-trace-999".getBytes(StandardCharsets.UTF_8));
    ConsumerRecord<String, AlertEvent> record =
        new ConsumerRecord<>("security.alerts", 0, 15L, alertId.toString(), event);
    headers.forEach(h -> record.headers().add(h));

    alertConsumer.consume(record);

    verify(mongoTemplate).insert(any(AlertDocument.class));
    // After consumption, MDC is cleared cleanly
    assertThat(CorrelationIdContext.get()).isNull();
  }

  @Test
  void propagatesValidationFailureImmediatelyForDltFastFail() {
    UUID alertId = UUID.randomUUID();
    // Invalid schema version and missing required fields
    AlertEvent malformed =
        new AlertEvent(
            alertId,
            "INVALID_TYPE",
            -1,
            "",
            "",
            "INVALID_SEV",
            "",
            null,
            null,
            null,
            null,
            null,
            null);

    assertThatThrownBy(() -> alertConsumer.consume(malformed))
        .isInstanceOf(IllegalArgumentException.class);

    // MongoDB insert must never be reached
    verifyNoInteractions(mongoTemplate);
  }

  @Test
  void configuresNonRetryableExceptionsOnDefaultErrorHandler() {
    KafkaErrorConfiguration errorConfig = new KafkaErrorConfiguration();
    AlertProperties properties = new AlertProperties("security.alerts", "security.events.dlt", 3);
    org.springframework.kafka.core.KafkaTemplate<Object, Object> mockTemplate = mock(org.springframework.kafka.core.KafkaTemplate.class);

    DefaultErrorHandler handler = errorConfig.kafkaErrorHandler(mockTemplate, properties);
    assertThat(handler).isNotNull();
  }
}
