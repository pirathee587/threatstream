package com.threatstream.detection;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class EventValidator {
  private static final Instant MIN_TIMESTAMP = Instant.parse("2020-01-01T00:00:00Z");
  private static final Instant MAX_FUTURE_OFFSET = Instant.parse("2035-01-01T00:00:00Z");

  void validate(AuthEvent e) {
    if (e == null
        || e.eventId() == null
        || e.timestamp() == null
        || e.timestamp().isBefore(MIN_TIMESTAMP)
        || e.timestamp().isAfter(MAX_FUTURE_OFFSET)
        || e.sourceIp() == null
        || e.sourceIp().isBlank()
        || e.sourceIp().length() > 64
        || e.username() == null
        || e.username().isBlank()
        || e.username().length() > 256
        || e.schemaVersion() != 1
        || !"AUTH_LOGIN".equals(e.eventType())
        || !("SUCCESS".equals(e.outcome()) || "FAILURE".equals(e.outcome()))) {
      throw new IllegalArgumentException("invalid authentication event");
    }
  }
}
