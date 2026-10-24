package com.threatstream.alert;

import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
class AlertService {

  private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);

  private final AlertRepository repo;
  private final AlertValidator validator;
  private final MongoTemplate mongoTemplate;

  AlertService(AlertRepository r, AlertValidator v, MongoTemplate m) {
    this.repo = r;
    this.validator = v;
    this.mongoTemplate = m;
  }

  void persist(AlertEvent x) {
    validator.validate(x);
    Optional<AlertDocument> existing = repo.findByAlertId(x.alertId());
    if (existing.isPresent()) {
      verifyConsistentDuplicate(existing.get(), x);
      return;
    }

    AlertDocument doc = new AlertDocument();
    doc.setAlertId(x.alertId());
    doc.setEventType(x.eventType());
    doc.setSchemaVersion(x.schemaVersion());
    doc.setRuleId(x.ruleId());
    doc.setThreatType(x.threatType());
    doc.setSeverity(x.severity());
    doc.setSourceIp(x.sourceIp());
    doc.setDescription(x.description());
    doc.setEvidence(x.evidence() == null ? Collections.emptyMap() : x.evidence());
    doc.setRiskScore(x.riskScore());
    doc.setRuleIds(x.ruleIds());
    doc.setEvidenceSummary(x.evidenceSummary());
    doc.setFirstSeen(x.firstSeen());
    doc.setLastSeen(x.lastSeen());
    doc.setDetectedAt(x.detectedAt());
    doc.setStatus(x.status() == null ? "OPEN" : x.status());
    Instant now = Instant.now();
    doc.setCreatedAt(now);
    doc.setUpdatedAt(now);

    try {
      mongoTemplate.insert(doc);
    } catch (DuplicateKeyException duplicate) {
      Optional<AlertDocument> reloaded = repo.findByAlertId(x.alertId());
      if (reloaded.isPresent()) {
        verifyConsistentDuplicate(reloaded.get(), x);
        return;
      }
      throw duplicate;
    }
  }

  private void verifyConsistentDuplicate(AlertDocument existing, AlertEvent incoming) {
    boolean matches =
        Objects.equals(existing.getThreatType(), incoming.threatType())
            && Objects.equals(existing.getRuleId(), incoming.ruleId())
            && Objects.equals(existing.getSourceIp(), incoming.sourceIp())
            && Objects.equals(existing.getSeverity(), incoming.severity());

    if (!matches) {
      LOG.warn(
          "Encountered conflicting payload for existing alert ID {}: "
              + "existing threatType={}, incoming threatType={}, "
              + "existing ruleId={}, incoming ruleId={}. "
              + "Preserving original stored alert without overwrite.",
          incoming.alertId(),
          existing.getThreatType(),
          incoming.threatType(),
          existing.getRuleId(),
          incoming.ruleId());
    }
  }

  PageResponse list(
      int page,
      int size,
      String sortBy,
      Sort.Direction direction,
      String severity,
      String status,
      String threat,
      String ip,
      Instant from,
      Instant to) {

    List<Criteria> conditions = new ArrayList<>();
    if (severity != null) {
      conditions.add(Criteria.where("severity").is(severity));
    }
    if (status != null) {
      conditions.add(Criteria.where("status").is(status));
    }
    if (threat != null) {
      conditions.add(Criteria.where("threatType").is(threat));
    }
    if (ip != null) {
      conditions.add(Criteria.where("sourceIp").is(ip));
    }
    if (from != null && to != null) {
      conditions.add(Criteria.where("detectedAt").gte(from).lte(to));
    } else if (from != null) {
      conditions.add(Criteria.where("detectedAt").gte(from));
    } else if (to != null) {
      conditions.add(Criteria.where("detectedAt").lte(to));
    }

    Query countQuery = new Query();
    if (!conditions.isEmpty()) {
      countQuery.addCriteria(new Criteria().andOperator(conditions.toArray(new Criteria[0])));
    }

    long totalElements = mongoTemplate.count(countQuery, AlertDocument.class);

    int boundedSize = Math.min(size, 100);
    PageRequest pageRequest =
        PageRequest.of(
            page,
            boundedSize,
            Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "alertId")));

    Query query = new Query();
    if (!conditions.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(conditions.toArray(new Criteria[0])));
    }
    query.with(pageRequest);

    List<AlertDocument> docs = mongoTemplate.find(query, AlertDocument.class);
    int totalPages =
        boundedSize == 0 ? 1 : (int) Math.ceil((double) totalElements / boundedSize);

    return new PageResponse(
        docs.stream().map(AlertResponse::from).toList(),
        page,
        boundedSize,
        totalElements,
        totalPages);
  }

  AlertResponse get(UUID id) {
    return repo.findByAlertId(id)
        .map(AlertResponse::from)
        .orElseThrow(() -> new NoSuchElementException("alert not found"));
  }

  AlertResponse transition(UUID id, String target) {
    Instant now = Instant.now();

    if ("RESOLVED".equals(target)) {
      Query query = new Query(Criteria.where("alertId").is(id));
      Update update = new Update().set("status", "RESOLVED").set("updatedAt", now);
      AlertDocument updated =
          mongoTemplate.findAndModify(
              query, update, FindAndModifyOptions.options().returnNew(true), AlertDocument.class);
      if (updated == null) {
        throw new NoSuchElementException("alert not found");
      }
      return AlertResponse.from(updated);
    }

    Query conditionalQuery =
        new Query(Criteria.where("alertId").is(id).and("status").ne("RESOLVED"));
    Update update = new Update().set("status", target).set("updatedAt", now);
    AlertDocument updated =
        mongoTemplate.findAndModify(
            conditionalQuery,
            update,
            FindAndModifyOptions.options().returnNew(true),
            AlertDocument.class);

    if (updated != null) {
      return AlertResponse.from(updated);
    }

    // Determine why update returned null
    AlertDocument existing =
        repo.findByAlertId(id)
            .orElseThrow(() -> new NoSuchElementException("alert not found"));

    if ("RESOLVED".equals(existing.getStatus())) {
      throw new IllegalStateException("resolved alerts cannot be reopened");
    }

    return AlertResponse.from(existing);
  }

  StatisticsResponse stats() {
    long totalAlerts = mongoTemplate.count(new Query(), AlertDocument.class);
    long openAlerts = repo.countByStatus("OPEN");
    long acknowledgedAlerts = repo.countByStatus("ACKNOWLEDGED");
    long resolvedAlerts = repo.countByStatus("RESOLVED");

    Aggregation severityAgg =
        Aggregation.newAggregation(Aggregation.group("severity").count().as("count"));
    @SuppressWarnings("rawtypes")
    AggregationResults<Map> severityRes =
        mongoTemplate.aggregate(severityAgg, "alerts", Map.class);
    Map<String, Long> bySeverity = new TreeMap<>();
    for (@SuppressWarnings("rawtypes") Map doc : severityRes.getMappedResults()) {
      Object idVal = doc.get("_id");
      Object countVal = doc.get("count");
      if (idVal != null && countVal instanceof Number num) {
        bySeverity.put(idVal.toString(), num.longValue());
      }
    }

    Aggregation threatAgg =
        Aggregation.newAggregation(Aggregation.group("threatType").count().as("count"));
    @SuppressWarnings("rawtypes")
    AggregationResults<Map> threatRes =
        mongoTemplate.aggregate(threatAgg, "alerts", Map.class);
    Map<String, Long> byThreatType = new TreeMap<>();
    for (@SuppressWarnings("rawtypes") Map doc : threatRes.getMappedResults()) {
      Object idVal = doc.get("_id");
      Object countVal = doc.get("count");
      if (idVal != null && countVal instanceof Number num) {
        byThreatType.put(idVal.toString(), num.longValue());
      }
    }

    return new StatisticsResponse(
        totalAlerts, openAlerts, acknowledgedAlerts, resolvedAlerts, bySeverity, byThreatType);
  }
}
