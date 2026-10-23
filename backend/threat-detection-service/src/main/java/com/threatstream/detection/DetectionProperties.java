package com.threatstream.detection;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("detection")
public record DetectionProperties(String authTopic, String alertTopic, String dltTopic, int retryAttempts, Duration dedupRetention, RuleConfig bruteForce, RuleConfig passwordSpraying, RuleConfig suspiciousLogin) {
  public record RuleConfig(int threshold, Duration window) { }
}
