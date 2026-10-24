package com.threatstream.detection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("detection")
public record DetectionProperties(
    @NotBlank String authTopic, @NotBlank String alertTopic, @NotBlank String dltTopic,
    @Min(0) @Max(20) int retryAttempts, @NotNull Duration dedupRetention,
    @Valid @NotNull RuleConfig bruteForce, @Valid @NotNull RuleConfig passwordSpraying,
    @Valid @NotNull RuleConfig suspiciousLogin, @Min(1) @Max(100_000) int maxTrackedSources,
    @Min(1) @Max(10_000) int maxTrackedUsernames, @NotNull Duration stateExpiration,
    @NotNull Duration cleanupInterval, @Valid @NotNull RiskConfig risk) {
  public DetectionProperties(String authTopic, String alertTopic, String dltTopic, int retryAttempts,
      Duration dedupRetention, RuleConfig bruteForce, RuleConfig passwordSpraying, RuleConfig suspiciousLogin) {
    this(authTopic, alertTopic, dltTopic, retryAttempts, dedupRetention, bruteForce, passwordSpraying,
        suspiciousLogin, 10_000, 1_000, Duration.ofMinutes(10), Duration.ofMinutes(1),
        new RiskConfig(60, 70, 45, 49, 74, 100));
  }
  public record RuleConfig(@Min(1) @Max(100_000) int threshold, @NotNull Duration window) {
    public RuleConfig { if (window.isNegative() || window.isZero()) throw new IllegalArgumentException("rule window must be positive"); }
  }
  public record RiskConfig(@Min(0) @Max(100) int bruteForceWeight, @Min(0) @Max(100) int passwordSprayingWeight,
      @Min(0) @Max(100) int suspiciousLoginWeight, @Min(0) @Max(100) int mediumMax,
      @Min(0) @Max(100) int highMax, @Min(1) @Max(100) int maximum) {
    public RiskConfig { if (mediumMax >= highMax || highMax >= maximum) throw new IllegalArgumentException("risk boundaries must be ordered and below maximum"); }
  }
}
