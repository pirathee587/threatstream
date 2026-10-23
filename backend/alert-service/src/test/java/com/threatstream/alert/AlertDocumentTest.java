package com.threatstream.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertDocumentTest {

  @Test
  void mapsFieldsCorrectlyToAlertResponse() {
    UUID alertId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-23T10:00:00Z");

    AlertDocument doc = new AlertDocument();
    doc.setId("mongo-obj-id-12345");
    doc.setAlertId(alertId);
    doc.setRuleId("RULE-PORT-SCAN");
    doc.setThreatType("PORT_SCAN");
    doc.setSeverity("HIGH");
    doc.setSourceIp("192.168.1.100");
    doc.setDescription("Port scan detected");
    doc.setEvidence(Map.of("portsCount", 25, "protocol", "TCP"));
    doc.setFirstSeen(now.minusSeconds(60));
    doc.setLastSeen(now);
    doc.setDetectedAt(now);
    doc.setStatus("OPEN");
    doc.setCreatedAt(now);
    doc.setUpdatedAt(now);

    AlertResponse response = AlertResponse.from(doc);

    assertThat(response.alertId()).isEqualTo(alertId);
    assertThat(response.ruleId()).isEqualTo("RULE-PORT-SCAN");
    assertThat(response.threatType()).isEqualTo("PORT_SCAN");
    assertThat(response.severity()).isEqualTo("HIGH");
    assertThat(response.sourceIp()).isEqualTo("192.168.1.100");
    assertThat(response.description()).isEqualTo("Port scan detected");
    assertThat(response.evidence()).containsEntry("portsCount", 25);
    assertThat(response.evidence()).containsEntry("protocol", "TCP");
    assertThat(response.detectedAt()).isEqualTo(now);
    assertThat(response.status()).isEqualTo("OPEN");
  }

  @Test
  void handlesNullEvidenceSafely() {
    AlertDocument doc = new AlertDocument();
    doc.setAlertId(UUID.randomUUID());
    doc.setEvidence(null);

    AlertResponse response = AlertResponse.from(doc);
    assertThat(response.evidence()).isNotNull().isEmpty();
  }
}
