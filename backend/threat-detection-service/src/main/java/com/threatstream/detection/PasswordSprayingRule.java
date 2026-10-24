package com.threatstream.detection;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
class PasswordSprayingRule implements DetectionRule {
  private final DetectionProperties p;
  private final AlertFactory f;
  private final Map<String, Deque<AuthEvent>> state = new HashMap<>();
  private final Set<String> armed = new HashSet<>();

  PasswordSprayingRule(DetectionProperties p, AlertFactory f) {
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

    Deque<AuthEvent> q = state.computeIfAbsent(e.sourceIp(), x -> new ArrayDeque<>());
    Instant cut = e.timestamp().minus(p.passwordSpraying().window());
    while (!q.isEmpty() && q.peek().timestamp().isBefore(cut)) {
      q.remove();
    }
    if (q.isEmpty()) {
      armed.remove(e.sourceIp());
    }
    boolean knownUser = q.stream().anyMatch(existing -> existing.username().equals(e.username()));
    if (!knownUser && q.stream().map(AuthEvent::username).distinct().count() >= p.maxTrackedUsernames()) {
      return Optional.empty();
    }
    EventTimeWindow.addEvent(q, e);

    int users = (int) q.stream().map(AuthEvent::username).distinct().count();
    if (users >= p.passwordSpraying().threshold() && armed.add(e.sourceIp())) {
      return Optional.of(
          f.create("AUTH_PASSWORD_SPRAYING", "PASSWORD_SPRAYING", "HIGH", e, users, q.peek().timestamp()));
    }
    return Optional.empty();
  }

  @Scheduled(fixedDelayString = "${detection.cleanup-interval:PT1M}")
  synchronized void expireStaleState() {
    Instant cutoff = Instant.now().minus(p.stateExpiration());
    state.entrySet().removeIf(entry -> entry.getValue().isEmpty() || entry.getValue().peekLast().timestamp().isBefore(cutoff));
    armed.retainAll(state.keySet());
  }
}
