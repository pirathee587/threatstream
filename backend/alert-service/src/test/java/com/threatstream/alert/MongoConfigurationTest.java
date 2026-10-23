package com.threatstream.alert;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.util.ReflectionTestUtils;

class MongoConfigurationTest {

  @Test
  void throwsExceptionWhenMongoUriIsMissing() {
    MongoTemplate template = mock(MongoTemplate.class);
    MongoMappingContext context = mock(MongoMappingContext.class);

    MongoConfiguration config = new MongoConfiguration(template, context);
    ReflectionTestUtils.setField(config, "mongoUri", "");

    assertThatThrownBy(config::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MONGODB_URI");
  }

  @Test
  void throwsExceptionWhenMongoUriIsNull() {
    MongoTemplate template = mock(MongoTemplate.class);
    MongoMappingContext context = mock(MongoMappingContext.class);

    MongoConfiguration config = new MongoConfiguration(template, context);
    ReflectionTestUtils.setField(config, "mongoUri", null);

    assertThatThrownBy(config::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MONGODB_URI");
  }
}
