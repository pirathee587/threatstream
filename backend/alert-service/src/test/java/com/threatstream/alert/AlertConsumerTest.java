package com.threatstream.alert;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertConsumerTest {

  @Test
  void delegatesPersistenceToAlertService() {
    AlertService service = mock(AlertService.class);
    AlertConsumer consumer = new AlertConsumer(service);

    AlertEvent event =
        new AlertEvent(
            UUID.randomUUID(),
            "SECURITY_ALERT",
            1,
            "RULE-1",
            "PORT_SCAN",
            "HIGH",
            "10.0.0.1",
            "Desc",
            Map.of(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            "OPEN");

    consumer.consume(event);
    verify(service).persist(event);
  }

  @Test
  void propagatesPersistenceFailureForKafkaRetry() {
    AlertService service = mock(AlertService.class);
    AlertConsumer consumer = new AlertConsumer(service);

    AlertEvent event =
        new AlertEvent(
            UUID.randomUUID(),
            "SECURITY_ALERT",
            1,
            "RULE-1",
            "PORT_SCAN",
            "HIGH",
            "10.0.0.1",
            "Desc",
            Map.of(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            "OPEN");

    doThrow(new RuntimeException("MongoDB unavailable")).when(service).persist(event);

    assertThatThrownBy(() -> consumer.consume(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("MongoDB unavailable");
  }

  @Test
  void propagatesValidationFailureForKafkaDltRouting() {
    AlertService service = mock(AlertService.class);
    AlertConsumer consumer = new AlertConsumer(service);

    AlertEvent event =
        new AlertEvent(
            UUID.randomUUID(),
            "INVALID_TYPE",
            1,
            "RULE-1",
            "PORT_SCAN",
            "HIGH",
            "10.0.0.1",
            "Desc",
            Map.of(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            "OPEN");

    doThrow(new IllegalArgumentException("invalid alert event")).when(service).persist(event);

    assertThatThrownBy(() -> consumer.consume(event))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid alert event");
  }
}
