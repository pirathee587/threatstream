package com.threatstream.detection;

import com.threatstream.common.CorrelationIdContext;
import com.threatstream.common.KafkaCorrelationHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class AuthEventConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(AuthEventConsumer.class);
  private final DetectionEngine engine;

  AuthEventConsumer(DetectionEngine engine) {
    this.engine = engine;
  }

  void consume(AuthEvent event) {
    consume(
        new ConsumerRecord<>(
            "security.auth.events",
            0,
            0L,
            event != null && event.eventId() != null ? event.eventId().toString() : null,
            event));
  }

  @KafkaListener(topics = "${detection.auth-topic}")
  void consume(ConsumerRecord<String, AuthEvent> record) {
    try {
      KafkaCorrelationHelper.extractAndSetCorrelationId(record.headers());
      AuthEvent event = record.value();
      if (event == null) {
        LOG.warn(
            "Encountered null/unparseable auth payload at topic {} partition {} offset {}",
            record.topic(),
            record.partition(),
            record.offset());
        return;
      }
      engine.process(event);
      LOG.info(
          "Successfully processed auth event {} [topic={} partition={} offset={}]",
          event.eventId(),
          record.topic(),
          record.partition(),
          record.offset());
    } catch (IllegalArgumentException e) {
      LOG.warn("Rejected invalid authentication event [errorCategory=VALIDATION_ERROR]: {}", e.getMessage());
      throw e;
    } catch (RuntimeException e) {
      LOG.warn(
          "Authentication event processing failed for {} [errorCategory=PROCESSING_ERROR]",
          record.value() == null ? "unknown" : record.value().eventId(),
          e);
      throw e;
    } finally {
      CorrelationIdContext.clear();
    }
  }
}
