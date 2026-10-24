package com.threatstream.detection;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
class NetworkKafkaConfiguration {

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, NetworkEvent> networkKafkaListenerContainerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrap,
      @Value("${spring.kafka.consumer.group-id}") String group,
      DefaultErrorHandler errors) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, group);
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    var jsonDeserializer = new JsonDeserializer<>(NetworkEvent.class, false);
    jsonDeserializer.addTrustedPackages("com.threatstream.detection");

    var keyDeserializer = new ErrorHandlingDeserializer<>(new StringDeserializer());
    var valueDeserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);

    var factory = new ConcurrentKafkaListenerContainerFactory<String, NetworkEvent>();
    factory.setConsumerFactory(
        new DefaultKafkaConsumerFactory<>(properties, keyDeserializer, valueDeserializer));
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.getContainerProperties().setShutdownTimeout(10000L);
    factory.setCommonErrorHandler(errors);
    return factory;
  }
}
