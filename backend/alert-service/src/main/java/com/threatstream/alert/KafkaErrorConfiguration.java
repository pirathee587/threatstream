package com.threatstream.alert;

import jakarta.validation.ValidationException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaErrorConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaErrorConfiguration.class);

  @Bean
  DefaultErrorHandler kafkaErrorHandler(
      KafkaTemplate<Object, Object> kafka, AlertProperties properties) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            kafka,
            (record, exception) -> {
              LOG.warn(
                  "Routing unrecoverable or permanently invalid alert event from topic {} partition {} offset {} to DLT {} [reason={}]",
                  record.topic(),
                  record.partition(),
                  record.offset(),
                  properties.dltTopic(),
                  exception.getMessage());
              return new TopicPartition(properties.dltTopic(), Math.max(0, record.partition()));
            });

    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            recoverer, new FixedBackOff(1000L, Math.max(1, properties.retryAttempts())));

    // Fast-fail non-retryable issues directly to DLT without spinning pointless retries
    errorHandler.addNotRetryableExceptions(
        IllegalArgumentException.class,
        DeserializationException.class,
        SerializationException.class,
        ValidationException.class);

    errorHandler.setRetryListeners(
        (record, exception, deliveryAttempt) ->
            LOG.warn(
                "Transient alert processing/persistence failure [attempt={}/{} topic={} partition={} offset={}]: {}",
                deliveryAttempt,
                properties.retryAttempts(),
                record.topic(),
                record.partition(),
                record.offset(),
                exception.getMessage()));

    return errorHandler;
  }
}
