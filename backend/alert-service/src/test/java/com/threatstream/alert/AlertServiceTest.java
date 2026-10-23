package com.threatstream.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class AlertServiceTest {

  private AlertRepository repo;
  private AlertValidator validator;
  private MongoTemplate mongoTemplate;
  private AlertService service;

  @BeforeEach
  void setUp() {
    repo = mock(AlertRepository.class);
    validator = mock(AlertValidator.class);
    mongoTemplate = mock(MongoTemplate.class);
    service = new AlertService(repo, validator, mongoTemplate);
  }

  private AlertEvent createEvent(UUID alertId) {
    Instant now = Instant.now();
    return new AlertEvent(
        alertId,
        "SECURITY_ALERT",
        1,
        "RULE-1",
        "PORT_SCAN",
        "HIGH",
        "10.0.0.1",
        "Test port scan",
        Map.of("ports", 20),
        now,
        now,
        now,
        "OPEN");
  }

  @Test
  void persistsValidAlertSuccessfully() {
    UUID alertId = UUID.randomUUID();
    AlertEvent event = createEvent(alertId);

    when(repo.findByAlertId(alertId)).thenReturn(Optional.empty());

    service.persist(event);

    verify(validator).validate(event);
    verify(mongoTemplate).insert(any(AlertDocument.class));
  }

  @Test
  void ignoresDuplicateAlertIdGracefully() {
    UUID alertId = UUID.randomUUID();
    AlertEvent event = createEvent(alertId);

    AlertDocument existing = new AlertDocument();
    existing.setAlertId(alertId);
    existing.setThreatType(event.threatType());
    existing.setRuleId(event.ruleId());
    existing.setSourceIp(event.sourceIp());
    existing.setSeverity(event.severity());

    when(repo.findByAlertId(alertId)).thenReturn(Optional.of(existing));

    service.persist(event);

    verify(validator).validate(event);
    verify(mongoTemplate, never()).insert(any(AlertDocument.class));
  }

  @Test
  void handlesDuplicateKeyExceptionOnConcurrentInsert() {
    UUID alertId = UUID.randomUUID();
    AlertEvent event = createEvent(alertId);

    AlertDocument existing = new AlertDocument();
    existing.setAlertId(alertId);
    existing.setThreatType(event.threatType());
    existing.setRuleId(event.ruleId());
    existing.setSourceIp(event.sourceIp());
    existing.setSeverity(event.severity());

    when(repo.findByAlertId(alertId))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));
    doThrow(new DuplicateKeyException("E11000 duplicate key"))
        .when(mongoTemplate)
        .insert(any(AlertDocument.class));

    service.persist(event);

    verify(mongoTemplate).insert(any(AlertDocument.class));
  }

  @Test
  void retrievesAlertByIdSuccessfully() {
    UUID alertId = UUID.randomUUID();
    AlertDocument doc = new AlertDocument();
    doc.setAlertId(alertId);
    doc.setThreatType("BRUTE_FORCE");

    when(repo.findByAlertId(alertId)).thenReturn(Optional.of(doc));

    AlertResponse response = service.get(alertId);
    assertThat(response.alertId()).isEqualTo(alertId);
    assertThat(response.threatType()).isEqualTo("BRUTE_FORCE");
  }

  @Test
  void throwsExceptionWhenAlertMissing() {
    UUID alertId = UUID.randomUUID();
    when(repo.findByAlertId(alertId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(alertId))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("alert not found");
  }

  @Test
  void acknowledgesAlertWhenOpen() {
    UUID alertId = UUID.randomUUID();
    AlertDocument updated = new AlertDocument();
    updated.setAlertId(alertId);
    updated.setStatus("ACKNOWLEDGED");

    when(mongoTemplate.findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AlertDocument.class)))
        .thenReturn(updated);

    AlertResponse response = service.transition(alertId, "ACKNOWLEDGED");
    assertThat(response.status()).isEqualTo("ACKNOWLEDGED");
  }

  @Test
  void resolvesAlertSuccessfully() {
    UUID alertId = UUID.randomUUID();
    AlertDocument updated = new AlertDocument();
    updated.setAlertId(alertId);
    updated.setStatus("RESOLVED");

    when(mongoTemplate.findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AlertDocument.class)))
        .thenReturn(updated);

    AlertResponse response = service.transition(alertId, "RESOLVED");
    assertThat(response.status()).isEqualTo("RESOLVED");
  }

  @Test
  void preventsReopeningResolvedAlert() {
    UUID alertId = UUID.randomUUID();
    AlertDocument resolvedDoc = new AlertDocument();
    resolvedDoc.setAlertId(alertId);
    resolvedDoc.setStatus("RESOLVED");

    // findAndModify fails because status == RESOLVED
    when(mongoTemplate.findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AlertDocument.class)))
        .thenReturn(null);
    when(repo.findByAlertId(alertId)).thenReturn(Optional.of(resolvedDoc));

    assertThatThrownBy(() -> service.transition(alertId, "ACKNOWLEDGED"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("resolved alerts cannot be reopened");
  }

  @Test
  void listsAlertsWithPaginationAndFilters() {
    AlertDocument doc = new AlertDocument();
    doc.setAlertId(UUID.randomUUID());
    doc.setSeverity("HIGH");

    when(mongoTemplate.count(any(Query.class), eq(AlertDocument.class))).thenReturn(15L);
    when(mongoTemplate.find(any(Query.class), eq(AlertDocument.class))).thenReturn(List.of(doc));

    PageResponse page =
        service.list(
            0,
            10,
            "detectedAt",
            Sort.Direction.DESC,
            "HIGH",
            "OPEN",
            "PORT_SCAN",
            "10.0.0.1",
            null,
            null);

    assertThat(page.page()).isEqualTo(0);
    assertThat(page.size()).isEqualTo(10);
    assertThat(page.totalElements()).isEqualTo(15L);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.items()).hasSize(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void aggregatesStatisticsCorrectly() {
    when(mongoTemplate.count(any(Query.class), eq(AlertDocument.class))).thenReturn(50L);
    when(repo.countByStatus("OPEN")).thenReturn(20L);
    when(repo.countByStatus("ACKNOWLEDGED")).thenReturn(10L);
    when(repo.countByStatus("RESOLVED")).thenReturn(20L);

    AggregationResults<Map> severityResults = mock(AggregationResults.class);
    when(severityResults.getMappedResults())
        .thenReturn(List.of(Map.of("_id", "HIGH", "count", 15), Map.of("_id", "CRITICAL", "count", 5)));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("alerts"), eq(Map.class)))
        .thenReturn(severityResults);

    StatisticsResponse stats = service.stats();

    assertThat(stats.totalAlerts()).isEqualTo(50L);
    assertThat(stats.openAlerts()).isEqualTo(20L);
    assertThat(stats.acknowledgedAlerts()).isEqualTo(10L);
    assertThat(stats.resolvedAlerts()).isEqualTo(20L);
    assertThat(stats.bySeverity()).containsEntry("HIGH", 15L);
    assertThat(stats.bySeverity()).containsEntry("CRITICAL", 5L);
  }
}
