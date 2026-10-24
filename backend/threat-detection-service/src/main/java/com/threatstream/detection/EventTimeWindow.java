package com.threatstream.detection;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/** Maintains event-time ordering so bounded windows remain correct after a late record. */
final class EventTimeWindow {
  private EventTimeWindow() {}

  static void add(Deque<Instant> values, Instant timestamp) {
    if (values.isEmpty() || !timestamp.isBefore(values.peekLast())) {
      values.addLast(timestamp);
      return;
    }
    Deque<Instant> ordered = new ArrayDeque<>();
    boolean inserted = false;
    for (Instant value : values) {
      if (!inserted && timestamp.isBefore(value)) {
        ordered.addLast(timestamp);
        inserted = true;
      }
      ordered.addLast(value);
    }
    if (!inserted) ordered.addLast(timestamp);
    values.clear();
    values.addAll(ordered);
  }

  static void addEvent(Deque<AuthEvent> values, AuthEvent event) {
    if (values.isEmpty() || !event.timestamp().isBefore(values.peekLast().timestamp())) {
      values.addLast(event);
      return;
    }
    Deque<AuthEvent> ordered = new ArrayDeque<>();
    boolean inserted = false;
    for (AuthEvent value : values) {
      if (!inserted && event.timestamp().isBefore(value.timestamp())) {
        ordered.addLast(event);
        inserted = true;
      }
      ordered.addLast(value);
    }
    if (!inserted) ordered.addLast(event);
    values.clear();
    values.addAll(ordered);
  }
}
