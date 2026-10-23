package com.threatstream.alert;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
public class MongoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(MongoConfiguration.class);

  @Value("${spring.data.mongodb.uri:}")
  private String mongoUri;

  @Value("${spring.data.mongodb.database:threatstream}")
  private String databaseName;

  private final MongoTemplate mongoTemplate;
  private final MongoMappingContext mongoMappingContext;

  public MongoConfiguration(MongoTemplate mongoTemplate, MongoMappingContext mongoMappingContext) {
    this.mongoTemplate = mongoTemplate;
    this.mongoMappingContext = mongoMappingContext;
  }

  @PostConstruct
  public void validateConfiguration() {
    if (mongoUri == null || mongoUri.isBlank()) {
      LOG.error(
          "MongoDB configuration missing: MONGODB_URI environment variable must be set. "
              + "Silently falling back to an in-memory database is prohibited.");
      throw new IllegalStateException(
          "Required MongoDB configuration is missing: please set MONGODB_URI in the environment.");
    }

    String sanitizedUri = mongoUri.replaceAll("://[^@]+@", "://<masked-credentials>@");
    LOG.info("Configured MongoDB Atlas connection: {} (database: {})", sanitizedUri, databaseName);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeIndexes() {
    try {
      IndexOperations indexOps = mongoTemplate.indexOps(AlertDocument.class);
      IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);
      resolver.resolveIndexFor(AlertDocument.class).forEach(indexOps::ensureIndex);
      LOG.info("Deterministic MongoDB indexes verified for collection 'alerts'");
    } catch (Exception e) {
      LOG.error("Failed to initialize MongoDB indexes for 'alerts' collection", e);
      throw new IllegalStateException("Failed to verify/create MongoDB indexes on startup", e);
    }
  }
}
