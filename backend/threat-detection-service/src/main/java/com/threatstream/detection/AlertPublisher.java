package com.threatstream.detection;

import com.threatstream.common.KafkaCorrelationHelper;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class AlertPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(AlertPublisher.class);
  private final KafkaTemplate<String, SecurityAlert> kafka;
  private final DetectionProperties properties;

  AlertPublisher(KafkaTemplate<String, SecurityAlert> kafka, DetectionProperties properties) {
    this.kafka = kafka;
    this.properties = properties;
  }

  void publish(SecurityAlert alert) {
    try {
      ProducerRecord<String, SecurityAlert> record =
          new ProducerRecord<>(properties.alertTopic(), alert.alertId().toString(), alert);
      KafkaCorrelationHelper.injectCorrelationId(record.headers());
      kafka.send(record).get(15, TimeUnit.SECONDS);
      LOG.info(
          "Published security alert {} [ruleId={} threatType={} severity={}] to topic {}",
          alert.alertId(),
          alert.ruleId(),
          alert.threatType(),
          alert.severity(),
          properties.alertTopic());
    } catch (Exception e) {
      LOG.error("Failed to publish security alert {} to Kafka", alert.alertId(), e);
      throw new AlertPublishException();
    }
  }
}
