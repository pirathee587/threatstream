package com.threatstream.alert;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("alert") public record AlertProperties(String topic,String dltTopic,int retryAttempts) { }
