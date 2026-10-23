package com.threatstream.alert;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record AlertResponse(
    UUID alertId,
    String ruleId,
    String threatType,
    String severity,
    String sourceIp,
    String description,
    Map<String, Object> evidence,
    Instant firstSeen,
    Instant lastSeen,
    Instant detectedAt,
    String status,
    Instant createdAt,
    Instant updatedAt) {

  public static AlertResponse from(AlertDocument d) {
    return new AlertResponse(
        d.getAlertId(),
        d.getRuleId(),
        d.getThreatType(),
        d.getSeverity(),
        d.getSourceIp(),
        d.getDescription(),
        d.getEvidence() != null ? d.getEvidence() : Collections.emptyMap(),
        d.getFirstSeen(),
        d.getLastSeen(),
        d.getDetectedAt(),
        d.getStatus(),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }
}

record PageResponse(
    List<AlertResponse> items, int page, int size, long totalElements, int totalPages) {}

record StatisticsResponse(
    long totalAlerts,
    long openAlerts,
    long acknowledgedAlerts,
    long resolvedAlerts,
    Map<String, Long> bySeverity,
    Map<String, Long> byThreatType) {}
