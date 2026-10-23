package com.threatstream.auth;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component("demoAuthenticationEventPublisher")
class AuthenticationEventPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationEventPublisher.class);
  private final KafkaTemplate<String, AuthenticationEvent> kafkaTemplate;
  private final String topic;

  AuthenticationEventPublisher(KafkaTemplate<String, AuthenticationEvent> kafkaTemplate,
      @Value("${demo.auth.topic}") String topic) { this.kafkaTemplate = kafkaTemplate; this.topic = topic; }

  void publish(AuthenticationEvent event) {
    try {
      kafkaTemplate.send(topic, event.eventId().toString(), event).get(15, TimeUnit.SECONDS);
      LOGGER.info("Published authentication event {} to {}", event.eventId(), topic);
    } catch (Exception exception) {
      LOGGER.warn("Unable to publish authentication event {}", event.eventId(), exception);
      throw new EventPublishingException();
    }
  }
}
