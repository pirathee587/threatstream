package com.threatstream.detection;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
class BruteForceRule implements DetectionRule {
  private final DetectionProperties p;
  private final AlertFactory f;
  private final Map<String, Deque<Instant>> state = new HashMap<>();
  private final Set<String> armed = new HashSet<>();

  BruteForceRule(DetectionProperties p, AlertFactory f) {
    this.p = p;
    this.f = f;
  }

  public synchronized Optional<SecurityAlert> evaluate(AuthEvent e) {
    if (!"FAILURE".equals(e.outcome())) {
      return Optional.empty();
    }

    if (!state.containsKey(e.sourceIp()) && state.size() >= p.maxTrackedSources()) {
      Iterator<String> keys = state.keySet().iterator();
      if (keys.hasNext()) { armed.remove(keys.next()); keys.remove(); }
    }

    Deque<Instant> q = state.computeIfAbsent(e.sourceIp(), x -> new ArrayDeque<>());
    Instant cut = e.timestamp().minus(p.bruteForce().window());
    while (!q.isEmpty() && q.peek().isBefore(cut)) {
      q.remove();
    }
    if (q.isEmpty()) {
      armed.remove(e.sourceIp());
    }
    EventTimeWindow.add(q, e.timestamp());

    if (q.size() >= p.bruteForce().threshold() && armed.add(e.sourceIp())) {
      return Optional.of(
          f.create("AUTH_BRUTE_FORCE", "BRUTE_FORCE", "HIGH", e, q.size(), q.peek()));
    }
    return Optional.empty();
  }

  @Scheduled(fixedDelayString = "${detection.cleanup-interval:PT1M}")
  synchronized void expireStaleState() {
    Instant cutoff = Instant.now().minus(p.stateExpiration());
    state.entrySet().removeIf(entry -> entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff));
    armed.retainAll(state.keySet());
  }
}
