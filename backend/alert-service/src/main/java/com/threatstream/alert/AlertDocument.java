package com.threatstream.alert;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "alerts")
@CompoundIndexes({
  @CompoundIndex(name = "idx_alerts_detected_at_alert_id", def = "{'detectedAt': -1, 'alertId': 1}"),
  @CompoundIndex(name = "idx_alerts_severity_detected_at", def = "{'severity': 1, 'detectedAt': -1}"),
  @CompoundIndex(name = "idx_alerts_status_detected_at", def = "{'status': 1, 'detectedAt': -1}")
})
public class AlertDocument {

  @Id
  private String id;

  @Indexed(unique = true, name = "idx_alerts_alert_id_unique")
  @Field("alertId")
  private UUID alertId;

  @Field("eventType")
  private String eventType;

  @Field("schemaVersion")
  private int schemaVersion;

  @Field("ruleId")
  private String ruleId;

  @Indexed(name = "idx_alerts_threat_type")
  @Field("threatType")
  private String threatType;

  @Indexed(name = "idx_alerts_severity")
  @Field("severity")
  private String severity;

  @Indexed(name = "idx_alerts_source_ip")
  @Field("sourceIp")
  private String sourceIp;

  @Field("description")
  private String description;

  @Field("evidence")
  private Map<String, Object> evidence;

  @Field("firstSeen")
  private Instant firstSeen;

  @Field("lastSeen")
  private Instant lastSeen;

  @Indexed(name = "idx_alerts_detected_at")
  @Field("detectedAt")
  private Instant detectedAt;

  @Indexed(name = "idx_alerts_status")
  @Field("status")
  private String status;

  @CreatedDate
  @Field("createdAt")
  private Instant createdAt;

  @LastModifiedDate
  @Field("updatedAt")
  private Instant updatedAt;

  public AlertDocument() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public UUID getAlertId() {
    return alertId;
  }

  public void setAlertId(UUID alertId) {
    this.alertId = alertId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getRuleId() {
    return ruleId;
  }

  public void setRuleId(String ruleId) {
    this.ruleId = ruleId;
  }

  public String getThreatType() {
    return threatType;
  }

  public void setThreatType(String threatType) {
    this.threatType = threatType;
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public String getSourceIp() {
    return sourceIp;
  }

  public void setSourceIp(String sourceIp) {
    this.sourceIp = sourceIp;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Map<String, Object> getEvidence() {
    return evidence != null ? evidence : Collections.emptyMap();
  }

  public void setEvidence(Map<String, Object> evidence) {
    this.evidence = evidence;
  }

  public Instant getFirstSeen() {
    return firstSeen;
  }

  public void setFirstSeen(Instant firstSeen) {
    this.firstSeen = firstSeen;
  }

  public Instant getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(Instant lastSeen) {
    this.lastSeen = lastSeen;
  }

  public Instant getDetectedAt() {
    return detectedAt;
  }

  public void setDetectedAt(Instant detectedAt) {
    this.detectedAt = detectedAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
