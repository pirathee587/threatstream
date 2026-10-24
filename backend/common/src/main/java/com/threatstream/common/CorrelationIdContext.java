package com.threatstream.common;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Manages distributed correlation IDs across HTTP requests and Kafka event messaging.
 */
public final class CorrelationIdContext {

  public static final String MDC_KEY = "correlationId";
  public static final String HTTP_HEADER = "X-Correlation-ID";
  public static final String KAFKA_HEADER = "X-Correlation-ID";

  private CorrelationIdContext() {}

  public static String get() {
    return MDC.get(MDC_KEY);
  }

  public static String getOrGenerate() {
    String current = get();
    if (current == null || current.isBlank()) {
      current = UUID.randomUUID().toString();
      set(current);
    }
    return current;
  }

  public static void set(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      MDC.put(MDC_KEY, UUID.randomUUID().toString());
      return;
    }
    // Sanitize to alphanumeric and hyphens, bounded length
    String sanitized = LogSanitizer.sanitize(correlationId.trim());
    if (sanitized.length() > 64) {
      sanitized = sanitized.substring(0, 64);
    }
    MDC.put(MDC_KEY, sanitized);
  }

  public static void clear() {
    MDC.remove(MDC_KEY);
  }
}
