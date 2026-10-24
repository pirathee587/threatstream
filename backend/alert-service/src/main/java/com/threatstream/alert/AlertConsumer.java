package com.threatstream.alert;

import com.threatstream.common.CorrelationIdContext;
import com.threatstream.common.KafkaCorrelationHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class AlertConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(AlertConsumer.class);
  private final AlertService service;

  AlertConsumer(AlertService service) {
    this.service = service;
  }

  void consume(AlertEvent event) {
    consume(
        new ConsumerRecord<>(
            "security.alerts",
            0,
            0L,
            event != null && event.alertId() != null ? event.alertId().toString() : null,
            event));
  }

  @KafkaListener(topics = "${alert.topic}")
  void consume(ConsumerRecord<String, AlertEvent> record) {
    try {
      KafkaCorrelationHelper.extractAndSetCorrelationId(record.headers());
      AlertEvent event = record.value();
      if (event == null) {
        LOG.warn(
            "Encountered null/unparseable alert event payload at topic {} partition {} offset {}",
            record.topic(),
            record.partition(),
            record.offset());
        return;
      }
      service.persist(event);
      LOG.info(
          "Successfully consumed and persisted alert {} [ruleId={} threatType={} severity={}]",
          event.alertId(),
          event.ruleId(),
          event.threatType(),
          event.severity());
    } catch (IllegalArgumentException e) {
      LOG.warn("Rejected invalid security alert [errorCategory=VALIDATION_ERROR]: {}", e.getMessage());
      throw e;
    } catch (RuntimeException e) {
      LOG.warn(
          "Alert persistence failed for {} [errorCategory=PERSISTENCE_ERROR]",
          record.value() == null ? "unknown" : record.value().alertId(),
          e);
      throw e;
    } finally {
      CorrelationIdContext.clear();
    }
  }
}
