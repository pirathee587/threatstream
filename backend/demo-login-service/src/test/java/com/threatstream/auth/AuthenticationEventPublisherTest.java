package com.threatstream.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class AuthenticationEventPublisherTest {
  @SuppressWarnings("unchecked")
  @Test void propagates_kafka_publish_failure() {
    KafkaTemplate<String, AuthenticationEvent> template = mock(KafkaTemplate.class);
    when(template.send(any(String.class), any(String.class), any(AuthenticationEvent.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));
    var publisher = new AuthenticationEventPublisher(template, "security.auth.events");
    var event = new AuthenticationEvent(UUID.randomUUID(), "AUTH_LOGIN", 1, Instant.now(), "127.0.0.1", "alice", "SUCCESS", null, "demo-login-service");
    assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(EventPublishingException.class);
  }
}
