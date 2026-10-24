package com.threatstream.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class MongoAtlasConnectivityIT {

  private static final Logger LOG = LoggerFactory.getLogger(MongoAtlasConnectivityIT.class);

  private String mongoUri;
  private String databaseName;
  private MongoClient mongoClient;
  private MongoTemplate mongoTemplate;
  private MongoMappingContext mappingContext;
  private final UUID testAlertId = UUID.randomUUID();

  private String resolveMongoUri() {
    String sysProp = System.getProperty("it.mongo.uri");
    if (sysProp != null && !sysProp.isBlank()) {
      return sysProp.trim();
    }
    String env = System.getenv("IT_MONGODB_URI");
    return env == null || env.isBlank() ? null : env.trim();
  }

  @BeforeEach
  void setUp() {
    mongoUri = resolveMongoUri();
    org.junit.jupiter.api.Assumptions.assumeTrue(
        mongoUri != null && !mongoUri.isBlank(),
        "Set it.mongo.uri or IT_MONGODB_URI to an isolated integration database");

    databaseName = System.getProperty("it.mongo.database", System.getenv("IT_MONGODB_DATABASE"));
    if (databaseName == null || databaseName.isBlank()) {
      databaseName = "threatstream_integration";
    }
    org.junit.jupiter.api.Assumptions.assumeTrue(databaseName.startsWith("threatstream_integration"),
        "integration test database must use the threatstream_integration prefix");

    String masked = mongoUri.replaceAll("://[^@]+@", "://<masked-credentials>@");
    LOG.info("Connecting to isolated MongoDB integration database at: {} (db: {})", masked, databaseName);

    MongoClientSettings settings = MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(mongoUri))
        .uuidRepresentation(UuidRepresentation.STANDARD)
        .build();

    mongoClient = MongoClients.create(settings);
    MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);

    mappingContext = new MongoMappingContext();
    MongoCustomConversions conversions = new MongoCustomConversions(Collections.emptyList());
    mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
    mappingContext.afterPropertiesSet();

    MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(factory), mappingContext);
    converter.setCustomConversions(conversions);
    converter.setCodecRegistryProvider(factory);
    converter.setTypeMapper(new DefaultMongoTypeMapper(null));
    converter.afterPropertiesSet();

    mongoTemplate = new MongoTemplate(factory, converter);
  }

  @AfterEach
  void tearDown() {
    if (mongoTemplate != null && testAlertId != null) {
      try {
        mongoTemplate.remove(
            org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("alertId").is(testAlertId)),
            AlertDocument.class);
      } catch (Exception e) {
        LOG.warn("Failed to clean up test alert: {}", e.getMessage());
      }
    }
    if (mongoClient != null) {
      mongoClient.close();
    }
  }

  @Test
  void testAtlasPingCommand() {
    MongoDatabase db = mongoClient.getDatabase(databaseName);
    Document pingResult = db.runCommand(new Document("ping", 1));
    LOG.info("Ping result: {}", pingResult.toJson());
    assertThat(pingResult.get("ok")).isNotNull();
    Number ok = (Number) pingResult.get("ok");
    assertThat(ok.intValue()).isEqualTo(1);
  }

  @Test
  void testIndexInitializationAndUniqueConstraint() {
    IndexOperations indexOps = mongoTemplate.indexOps(AlertDocument.class);
    IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
    resolver.resolveIndexFor(AlertDocument.class).forEach(indexOps::ensureIndex);

    List<IndexInfo> indexes = indexOps.getIndexInfo();
    LOG.info("Configured indexes on alerts: {}", indexes.stream().map(IndexInfo::getName).toList());

    boolean hasAlertIdUniqueIndex = indexes.stream()
        .anyMatch(idx -> idx.isUnique() && idx.getIndexFields().stream().anyMatch(f -> "alertId".equals(f.getKey())));

    assertThat(hasAlertIdUniqueIndex)
        .withFailMessage("Expected unique index on 'alertId'")
        .isTrue();
  }

  @Test
  void testAlertLifecycleInMongoAtlas() {
    Instant now = Instant.now();
    AlertDocument doc = new AlertDocument();
    doc.setAlertId(testAlertId);
    doc.setEventType("SECURITY_ALERT");
    doc.setSchemaVersion(1);
    doc.setRuleId("RULE-RUNTIME-TEST");
    doc.setThreatType("BRUTE_FORCE");
    doc.setSeverity("HIGH");
    doc.setSourceIp("192.168.1.100");
    doc.setDescription("Runtime test alert verification");
    doc.setEvidence(Map.of("testKey", "testVal", "environment", "phase10"));
    doc.setFirstSeen(now);
    doc.setLastSeen(now);
    doc.setDetectedAt(now);
    doc.setStatus("OPEN");
    doc.setCreatedAt(now);
    doc.setUpdatedAt(now);

    // 1. Insert
    AlertDocument saved = mongoTemplate.insert(doc);
    assertThat(saved).isNotNull();
    assertThat(saved.getId()).isNotBlank();
    assertThat(saved.getAlertId()).isEqualTo(testAlertId);

    // 2. Query
    AlertDocument retrieved = mongoTemplate.findOne(
        org.springframework.data.mongodb.core.query.Query.query(
            org.springframework.data.mongodb.core.query.Criteria.where("alertId").is(testAlertId)),
        AlertDocument.class);

    assertThat(retrieved).isNotNull();
    assertThat(retrieved.getStatus()).isEqualTo("OPEN");
    assertThat(retrieved.getSourceIp()).isEqualTo("192.168.1.100");

    // 3. Status transition to ACKNOWLEDGED
    retrieved.setStatus("ACKNOWLEDGED");
    retrieved.setUpdatedAt(Instant.now());
    mongoTemplate.save(retrieved);

    AlertDocument acked = mongoTemplate.findOne(
        org.springframework.data.mongodb.core.query.Query.query(
            org.springframework.data.mongodb.core.query.Criteria.where("alertId").is(testAlertId)),
        AlertDocument.class);

    assertThat(acked).isNotNull();
    assertThat(acked.getStatus()).isEqualTo("ACKNOWLEDGED");

    // 4. Status transition to RESOLVED
    acked.setStatus("RESOLVED");
    acked.setUpdatedAt(Instant.now());
    mongoTemplate.save(acked);

    AlertDocument resolved = mongoTemplate.findOne(
        org.springframework.data.mongodb.core.query.Query.query(
            org.springframework.data.mongodb.core.query.Criteria.where("alertId").is(testAlertId)),
        AlertDocument.class);

    assertThat(resolved).isNotNull();
    assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
  }
}
