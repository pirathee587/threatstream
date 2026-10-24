package com.threatstream.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DetectionEngineBenchmarkTest {

  private DetectionProperties properties;
  private EventDeduplicator deduplicator;
  private AlertFactory alertFactory;
  private BruteForceRule bruteForceRule;
  private PasswordSprayingRule passwordSprayingRule;

  @BeforeEach
  void setUp() {
    properties =
        new DetectionProperties(
            "security.auth.events",
            "security.alerts",
            "security.events.dlt",
            3,
            Duration.ofMinutes(10),
            new DetectionProperties.RuleConfig(10, Duration.ofSeconds(60)),
            new DetectionProperties.RuleConfig(10, Duration.ofSeconds(120)),
            new DetectionProperties.RuleConfig(5, Duration.ofMinutes(5)));

    deduplicator = new EventDeduplicator(properties);
    alertFactory = new AlertFactory();
    bruteForceRule = new BruteForceRule(properties, alertFactory);
    passwordSprayingRule = new PasswordSprayingRule(properties, alertFactory);
  }

  private AuthEvent createAuthEvent(UUID id, String ip, String user, String outcome, Instant ts) {
    return new AuthEvent(
        id,
        "AUTH_LOGIN",
        1,
        ts,
        ip,
        user,
        outcome,
        "FAILURE".equals(outcome) ? "INVALID_CREDENTIALS" : null,
        "demo-login-service");
  }

  @Test
  void benchmarkEventDeduplicationThroughputAndMemory() {
    int warmUpIterations = 5_000;
    int testIterations = 50_000;

    // Warm-up
    for (int i = 0; i < warmUpIterations; i++) {
      deduplicator.first(UUID.randomUUID());
    }

    // Benchmark unique events
    long start = System.nanoTime();
    for (int i = 0; i < testIterations; i++) {
      boolean first = deduplicator.first(UUID.randomUUID());
      assertThat(first).isTrue();
    }
    long elapsedNanos = System.nanoTime() - start;
    double opsPerSec = (testIterations * 1_000_000_000.0) / elapsedNanos;

    System.out.printf(
        "[BENCHMARK] EventDeduplicator Unique Ingestion: %,d ops in %.2f ms (%,.0f ops/sec)%n",
        testIterations, elapsedNanos / 1_000_000.0, opsPerSec);

    assertThat(opsPerSec).isGreaterThan(10_000);

    // Benchmark duplicate suppression
    UUID fixedId = UUID.randomUUID();
    deduplicator.first(fixedId);

    start = System.nanoTime();
    for (int i = 0; i < testIterations; i++) {
      boolean first = deduplicator.first(fixedId);
      assertThat(first).isFalse();
    }
    elapsedNanos = System.nanoTime() - start;
    double dupOpsPerSec = (testIterations * 1_000_000_000.0) / elapsedNanos;

    System.out.printf(
        "[BENCHMARK] EventDeduplicator Duplicate Rejection: %,d ops in %.2f ms (%,.0f ops/sec)%n",
        testIterations, elapsedNanos / 1_000_000.0, dupOpsPerSec);

    assertThat(dupOpsPerSec).isGreaterThan(50_000);
  }

  @Test
  void benchmarkBruteForceRuleThroughputAndBoundaryConditions() {
    int numIps = 1_000;
    int attemptsPerIp = 12; // 10 is threshold, 11-12 should be suppressed
    AtomicInteger totalAlerts = new AtomicInteger(0);

    Instant now = Instant.now();
    long start = System.nanoTime();

    for (int i = 0; i < numIps; i++) {
      String ip = "10.0." + (i / 250) + "." + (i % 250);
      for (int a = 0; a < attemptsPerIp; a++) {
        AuthEvent event =
            createAuthEvent(UUID.randomUUID(), ip, "user_" + (i % 50), "FAILURE", now.plusSeconds(a));
        Optional<SecurityAlert> alert = bruteForceRule.evaluate(event);
        if (alert.isPresent()) {
          totalAlerts.incrementAndGet();
        }
      }
    }

    long elapsedNanos = System.nanoTime() - start;
    int totalEvents = numIps * attemptsPerIp;
    double opsPerSec = (totalEvents * 1_000_000_000.0) / elapsedNanos;

    System.out.printf(
        "[BENCHMARK] BruteForceRule Evaluation: %,d events across %,d IPs in %.2f ms (%,.0f ops/sec)%n",
        totalEvents, numIps, elapsedNanos / 1_000_000.0, opsPerSec);

    // Exactly 1 alert per IP (at threshold 10), subsequent 11 and 12 are suppressed
    assertThat(totalAlerts.get()).isEqualTo(numIps);
    assertThat(opsPerSec).isGreaterThan(25_000);
  }

  @Test
  void benchmarkConcurrentDetectionEngineEvaluation() throws Exception {
    int threadCount = 8;
    int eventsPerThread = 5_000;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch doneGate = new CountDownLatch(threadCount);
    AtomicInteger alertsCreated = new AtomicInteger(0);

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      executor.submit(
          () -> {
            try {
              startGate.await();
              Instant now = Instant.now();
              for (int i = 0; i < eventsPerThread; i++) {
                String ip = "192.168." + threadId + "." + (i % 100);
                AuthEvent event =
                    createAuthEvent(UUID.randomUUID(), ip, "user_" + i, "FAILURE", now.plusMillis(i));
                if (bruteForceRule.evaluate(event).isPresent()) {
                  alertsCreated.incrementAndGet();
                }
              }
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              doneGate.countDown();
            }
          });
    }

    long start = System.nanoTime();
    startGate.countDown();
    boolean completed = doneGate.await(30, TimeUnit.SECONDS);
    long elapsedNanos = System.nanoTime() - start;
    executor.shutdown();

    assertThat(completed).isTrue();
    int totalEvents = threadCount * eventsPerThread;
    double throughput = (totalEvents * 1_000_000_000.0) / elapsedNanos;

    System.out.printf(
        "[BENCHMARK] Concurrent BruteForceRule (%d threads): %,d events in %.2f ms (%,.0f ops/sec)%n",
        threadCount, totalEvents, elapsedNanos / 1_000_000.0, throughput);

    assertThat(throughput).isGreaterThan(20_000);
    assertThat(alertsCreated.get()).isPositive();
  }
}
