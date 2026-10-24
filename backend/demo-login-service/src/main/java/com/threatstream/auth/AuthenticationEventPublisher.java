package com.threatstream.auth;

import com.threatstream.common.KafkaCorrelationHelper;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
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

  AuthenticationEventPublisher(
      KafkaTemplate<String, AuthenticationEvent> kafkaTemplate,
      @Value("${demo.auth.topic}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  void publish(AuthenticationEvent event) {
    try {
      ProducerRecord<String, AuthenticationEvent> record =
          new ProducerRecord<>(topic, event.eventId().toString(), event);
      KafkaCorrelationHelper.injectCorrelationId(record.headers());
      kafkaTemplate.send(record).get(15, TimeUnit.SECONDS);
      LOGGER.info("Published authentication event {} to {} [outcome={}]", event.eventId(), topic, event.outcome());
    } catch (Exception exception) {
      LOGGER.warn("Unable to publish authentication event {}", event.eventId(), exception);
      throw new EventPublishingException();
    }
  }
}
