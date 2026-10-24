package com.threatstream.common;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/**
 * Utility for propagating distributed correlation IDs across Kafka message headers.
 */
public final class KafkaCorrelationHelper {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

  private KafkaCorrelationHelper() {}

  /**
   * Injects the current correlation ID from MDC (or generates a new one) into Kafka Headers.
   */
  public static void injectCorrelationId(Headers headers) {
    if (headers == null) {
      return;
    }
    String correlationId = CorrelationIdContext.getOrGenerate();
    headers.remove(CORRELATION_ID_HEADER);
    headers.add(CORRELATION_ID_HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Extracts correlation ID from Kafka Headers, setting it into MDC.
   * If not present, generates and establishes a new correlation ID.
   */
  public static String extractAndSetCorrelationId(Headers headers) {
    String correlationId = null;
    if (headers != null) {
      Header header = headers.lastHeader(CORRELATION_ID_HEADER);
      if (header != null && header.value() != null) {
        correlationId = new String(header.value(), StandardCharsets.UTF_8);
      }
    }
    if (correlationId != null && !correlationId.isBlank()) {
      CorrelationIdContext.set(correlationId);
    } else {
      correlationId = CorrelationIdContext.getOrGenerate();
    }
    return correlationId;
  }
}
