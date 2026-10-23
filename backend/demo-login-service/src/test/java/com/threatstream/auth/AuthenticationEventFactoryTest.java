package com.threatstream.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthenticationEventFactoryTest {
  @Test void creates_a_sanitized_versioned_success_event() throws Exception {
    var factory = new AuthenticationEventFactory(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var event = factory.create("alice", "127.0.0.1", true);
    var json = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(event.outcome()).isEqualTo("SUCCESS");
    assertThat(event.failureReason()).isNull();
    assertThat(json).doesNotContain("password");
  }

  @Test void every_attempt_gets_a_new_id_and_failures_are_safe() {
    var factory = new AuthenticationEventFactory(Clock.systemUTC());
    var first = factory.create("alice", "127.0.0.1", false);
    var second = factory.create("alice", "127.0.0.1", false);
    assertThat(first.eventId()).isNotEqualTo(second.eventId());
    assertThat(first.failureReason()).isEqualTo("INVALID_CREDENTIALS");
  }
}
