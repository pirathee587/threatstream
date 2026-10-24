package com.threatstream.detection;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SecurityAlert(UUID alertId, String eventType, int schemaVersion, String ruleId, String threatType, String severity, String sourceIp, String description, Map<String, Object> evidence, Instant firstSeen, Instant lastSeen, Instant detectedAt, String status, Integer riskScore, java.util.List<String> ruleIds, String evidenceSummary) { }
