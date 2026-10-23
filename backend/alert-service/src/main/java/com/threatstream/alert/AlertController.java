package com.threatstream.alert;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
class AlertController {

  private static final Set<String> SORTS =
      Set.of("detectedAt", "severity", "status", "threatType", "createdAt");

  private final AlertService service;

  AlertController(AlertService s) {
    service = s;
  }

  @GetMapping
  @Operation(summary = "List alerts with bounded pagination and filters")
  PageResponse list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "detectedAt") String sortBy,
      @RequestParam(defaultValue = "DESC") Sort.Direction direction,
      @RequestParam(required = false) String severity,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String threatType,
      @RequestParam(required = false) String sourceIp,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {

    if (page < 0
        || size < 1
        || !SORTS.contains(sortBy)
        || (severity != null && !Set.of("MEDIUM", "HIGH", "CRITICAL").contains(severity))
        || (status != null && !Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED").contains(status))
        || (from != null && to != null && from.isAfter(to))) {
      throw new IllegalArgumentException("invalid query");
    }

    return service.list(
        page, size, sortBy, direction, severity, status, threatType, sourceIp, from, to);
  }

  @GetMapping("/{alertId}")
  AlertResponse get(@PathVariable UUID alertId) {
    return service.get(alertId);
  }

  @GetMapping("/statistics")
  StatisticsResponse stats() {
    return service.stats();
  }

  @PatchMapping("/{alertId}/acknowledge")
  AlertResponse acknowledge(@PathVariable UUID alertId) {
    return service.transition(alertId, "ACKNOWLEDGED");
  }

  @PatchMapping("/{alertId}/resolve")
  AlertResponse resolve(@PathVariable UUID alertId) {
    return service.transition(alertId, "RESOLVED");
  }
}
