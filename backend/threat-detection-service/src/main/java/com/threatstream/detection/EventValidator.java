package com.threatstream.detection;
import org.springframework.stereotype.Component;
@Component class EventValidator {
  void validate(AuthEvent e) {
    if (e == null || e.eventId() == null || e.timestamp() == null || e.sourceIp() == null || e.sourceIp().isBlank() || e.username() == null || e.username().isBlank() || e.schemaVersion() != 1 || !"AUTH_LOGIN".equals(e.eventType()) || !("SUCCESS".equals(e.outcome()) || "FAILURE".equals(e.outcome()))) throw new IllegalArgumentException("invalid authentication event");
  }
}
