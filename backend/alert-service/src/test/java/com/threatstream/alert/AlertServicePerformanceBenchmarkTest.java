package com.threatstream.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class AlertServicePerformanceBenchmarkTest {

  private AlertRepository repository;
  private AlertValidator validator;
  private MongoTemplate mongoTemplate;
  private AlertService alertService;

  @BeforeEach
  void setUp() {
    repository = mock(AlertRepository.class);
    validator = new AlertValidator();
    mongoTemplate = mock(MongoTemplate.class);
    alertService = new AlertService(repository, validator, mongoTemplate);
  }

  private AlertEvent createAlertEvent(UUID id, String severity, String status) {
    Instant now = Instant.now();
    return new AlertEvent(
        id,
        "SECURITY_ALERT",
        1,
        "AUTH_BRUTE_FORCE",
        "BRUTE_FORCE",
        severity,
        "192.168.1.1",
        "Benchmark alert",
        Map.of("attemptCount", 10),
        now.minusSeconds(60),
        now,
        now,
        status);
  }

  @Test
  void benchmarkAlertPersistenceThroughput() {
    int warmUpIterations = 2_000;
    int iterations = 10_000;
    when(repository.findByAlertId(any(UUID.class))).thenReturn(Optional.empty());

    // Warm-up
    for (int i = 0; i < warmUpIterations; i++) {
      alertService.persist(createAlertEvent(UUID.randomUUID(), "HIGH", "OPEN"));
    }

    List<AlertEvent> events = new ArrayList<>(iterations);
    for (int i = 0; i < iterations; i++) {
      events.add(createAlertEvent(UUID.randomUUID(), "HIGH", "OPEN"));
    }

    long start = System.nanoTime();
    for (AlertEvent event : events) {
      alertService.persist(event);
    }
    long elapsedNanos = System.nanoTime() - start;
    double opsPerSec = (iterations * 1_000_000_000.0) / elapsedNanos;

    System.out.printf(
        "[BENCHMARK] AlertService.persist Validation & Mapping: %,d alerts in %.2f ms (%,.0f ops/sec)%n",
        iterations, elapsedNanos / 1_000_000.0, opsPerSec);

    verify(mongoTemplate, times(iterations + warmUpIterations)).insert(any(AlertDocument.class));
    assertThat(opsPerSec).isGreaterThan(30_000);
  }

  @Test
  void benchmarkPaginatedListQueryMapping() {
    int iterations = 1_000;
    List<AlertDocument> simulatedBatch = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      AlertDocument doc = new AlertDocument();
      doc.setAlertId(UUID.randomUUID());
      doc.setThreatType("BRUTE_FORCE");
      doc.setSeverity("HIGH");
      doc.setStatus("OPEN");
      doc.setSourceIp("10.0.0.1");
      doc.setDetectedAt(Instant.now());
      simulatedBatch.add(doc);
    }

    when(mongoTemplate.count(any(Query.class), eq(AlertDocument.class))).thenReturn(10_000L);
    when(mongoTemplate.find(any(Query.class), eq(AlertDocument.class))).thenReturn(simulatedBatch);

    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      PageResponse page =
          alertService.list(
              i % 20, 50, "detectedAt", Sort.Direction.DESC, "HIGH", "OPEN", null, null, null, null);
      assertThat(page.items()).hasSize(50);
    }
    long elapsedNanos = System.nanoTime() - start;
    double opsPerSec = (iterations * 1_000_000_000.0) / elapsedNanos;

    System.out.printf(
        "[BENCHMARK] AlertService.list Query & Serialization Mapping: %,d pages in %.2f ms (%,.0f queries/sec)%n",
        iterations, elapsedNanos / 1_000_000.0, opsPerSec);

    assertThat(opsPerSec).isGreaterThan(5_000);
  }
}
