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
    Integer riskScore,
    List<String> ruleIds,
    String evidenceSummary,
    Instant firstSeen,
    Instant lastSeen,
    Instant detectedAt,
    String status,
    Instant createdAt,
    Instant updatedAt) {

  AlertResponse(
      UUID alertId, String ruleId, String threatType, String severity, String sourceIp,
      String description, Map<String, Object> evidence, Instant firstSeen, Instant lastSeen,
      Instant detectedAt, String status, Instant createdAt, Instant updatedAt) {
    this(alertId, ruleId, threatType, severity, sourceIp, description, evidence, null,
        Collections.emptyList(), null, firstSeen, lastSeen, detectedAt, status, createdAt, updatedAt);
  }

  public static AlertResponse from(AlertDocument d) {
    return new AlertResponse(
        d.getAlertId(),
        d.getRuleId(),
        d.getThreatType(),
        d.getSeverity(),
        d.getSourceIp(),
        d.getDescription(),
        d.getEvidence() != null ? d.getEvidence() : Collections.emptyMap(),
        d.getRiskScore(),
        d.getRuleIds(),
        d.getEvidenceSummary(),
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
