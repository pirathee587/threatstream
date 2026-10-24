package com.threatstream.detection;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class EventDeduplicator {

  private static final int MAX_CAPACITY = 100_000;
  private final LinkedHashMap<UUID, Instant> seen = new LinkedHashMap<>();
  private final Clock clock;
  private final DetectionProperties props;

  @Autowired
  EventDeduplicator(DetectionProperties props) {
    this(props, Clock.systemUTC());
  }

  EventDeduplicator(DetectionProperties props, Clock clock) {
    this.props = props;
    this.clock = clock;
  }

  synchronized boolean first(UUID id) {
    if (id == null) {
      return false;
    }
    Instant cutoff = clock.instant().minus(props.dedupRetention());
    Iterator<Instant> it = seen.values().iterator();
    while (it.hasNext() && it.next().isBefore(cutoff)) {
      it.remove();
    }
    if (seen.size() >= MAX_CAPACITY) {
      Iterator<UUID> keyIt = seen.keySet().iterator();
      if (keyIt.hasNext()) {
        keyIt.next();
        keyIt.remove();
      }
    }
    return seen.putIfAbsent(id, clock.instant()) == null;
  }
}
