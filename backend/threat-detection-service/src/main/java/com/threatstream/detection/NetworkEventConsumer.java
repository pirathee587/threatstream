package com.threatstream.detection;

import com.threatstream.common.CorrelationIdContext;
import com.threatstream.common.KafkaCorrelationHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class NetworkEventConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(NetworkEventConsumer.class);
  private final NetworkDetectionEngine engine;

  NetworkEventConsumer(NetworkDetectionEngine engine) {
    this.engine = engine;
  }

  void consume(NetworkEvent event) {
    consume(
        new ConsumerRecord<>(
            "security.network.events",
            0,
            0L,
            event != null && event.eventId() != null ? event.eventId().toString() : null,
            event));
  }

  @KafkaListener(
      topics = "${network-detection.topic}",
      containerFactory = "networkKafkaListenerContainerFactory")
  void consume(ConsumerRecord<String, NetworkEvent> record) {
    try {
      KafkaCorrelationHelper.extractAndSetCorrelationId(record.headers());
      NetworkEvent event = record.value();
      if (event == null) {
        LOG.warn(
            "Encountered null/unparseable network payload at topic {} partition {} offset {}",
            record.topic(),
            record.partition(),
            record.offset());
        return;
      }
      engine.process(event);
      LOG.info(
          "Successfully processed network event {} [topic={} partition={} offset={}]",
          event.eventId(),
          record.topic(),
          record.partition(),
          record.offset());
    } catch (IllegalArgumentException e) {
      LOG.warn("Rejected invalid network event [errorCategory=VALIDATION_ERROR]: {}", e.getMessage());
      throw e;
    } catch (RuntimeException e) {
      LOG.warn(
          "Network event processing failed for {} [errorCategory=PROCESSING_ERROR]",
          record.value() == null ? "unknown" : record.value().eventId(),
          e);
      throw e;
    } finally {
      CorrelationIdContext.clear();
    }
  }
}
