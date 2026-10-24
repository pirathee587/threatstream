package com.threatstream.detection;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
class SuspiciousLoginRule implements DetectionRule {
  private final DetectionProperties p;
  private final AlertFactory f;
  private final Map<String, Deque<Instant>> failures = new HashMap<>();

  SuspiciousLoginRule(DetectionProperties p, AlertFactory f) {
    this.p = p;
    this.f = f;
  }

  public synchronized Optional<SecurityAlert> evaluate(AuthEvent e) {
    String identity = e.sourceIp() + "\u0000" + e.username();
    if (!failures.containsKey(identity) && failures.size() >= p.maxTrackedSources()) {
      Iterator<String> keys = failures.keySet().iterator();
      if (keys.hasNext()) keys.remove();
    }

    Deque<Instant> q = failures.computeIfAbsent(identity, x -> new ArrayDeque<>());
    Instant cut = e.timestamp().minus(p.suspiciousLogin().window());
    while (!q.isEmpty() && q.peek().isBefore(cut)) {
      q.remove();
    }
    if ("FAILURE".equals(e.outcome())) {
      EventTimeWindow.add(q, e.timestamp());
      return Optional.empty();
    }
    if ("SUCCESS".equals(e.outcome()) && q.size() >= p.suspiciousLogin().threshold()) {
      var a =
          Optional.of(
              f.create(
                  "AUTH_FAILURES_THEN_SUCCESS",
                  "SUSPICIOUS_LOGIN_SEQUENCE",
                  "MEDIUM",
                  e,
                  q.size(),
                  q.peek()));
      q.clear();
      return a;
    }
    return Optional.empty();
  }

  @Scheduled(fixedDelayString = "${detection.cleanup-interval:PT1M}")
  synchronized void expireStaleState() {
    Instant cutoff = Instant.now().minus(p.stateExpiration());
    failures.entrySet().removeIf(entry -> entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff));
  }
}
