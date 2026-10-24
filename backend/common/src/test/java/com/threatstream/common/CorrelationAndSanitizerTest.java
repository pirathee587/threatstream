package com.threatstream.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CorrelationAndSanitizerTest {

  @AfterEach
  void tearDown() {
    CorrelationIdContext.clear();
  }

  @Test
  void sanitizesCrlfAndControlCharacters() {
    String malicious = "admin\r\n[CRITICAL] Fake log injected\t\u0000";
    String sanitized = LogSanitizer.sanitize(malicious);
    assertThat(sanitized).doesNotContain("\r");
    assertThat(sanitized).doesNotContain("\n");
    assertThat(sanitized).doesNotContain("\t");
    assertThat(sanitized).doesNotContain("\u0000");
  }

  @Test
  void handlesNullAndLongInputsInSanitizer() {
    assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
    String longInput = "a".repeat(300);
    String sanitized = LogSanitizer.sanitize(longInput);
    assertThat(sanitized).hasSizeLessThan(300);
    assertThat(sanitized).endsWith("...[truncated]");
  }

  @Test
  void managesMdcCorrelationIdLifecycle() {
    assertThat(CorrelationIdContext.get()).isNull();
    String generated = CorrelationIdContext.getOrGenerate();
    assertThat(generated).isNotBlank();
    assertThat(CorrelationIdContext.get()).isEqualTo(generated);

    CorrelationIdContext.set("custom-trace-12345");
    assertThat(CorrelationIdContext.get()).isEqualTo("custom-trace-12345");

    CorrelationIdContext.clear();
    assertThat(CorrelationIdContext.get()).isNull();
  }

  @Test
  void injectsAndExtractsKafkaCorrelationHeaders() {
    CorrelationIdContext.set("trace-abc-987");
    RecordHeaders headers = new RecordHeaders();

    KafkaCorrelationHelper.injectCorrelationId(headers);
    assertThat(headers.lastHeader(KafkaCorrelationHelper.CORRELATION_ID_HEADER)).isNotNull();

    CorrelationIdContext.clear();
    assertThat(CorrelationIdContext.get()).isNull();

    String extracted = KafkaCorrelationHelper.extractAndSetCorrelationId(headers);
    assertThat(extracted).isEqualTo("trace-abc-987");
    assertThat(CorrelationIdContext.get()).isEqualTo("trace-abc-987");
  }
}
