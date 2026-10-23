package com.threatstream.detection;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration; import java.time.Instant; import java.util.UUID; import org.junit.jupiter.api.Test;
class DetectionRulesTest {
 private final DetectionProperties p=new DetectionProperties("in","out","dlt",3,Duration.ofMinutes(10),new DetectionProperties.RuleConfig(3,Duration.ofMinutes(1)),new DetectionProperties.RuleConfig(3,Duration.ofMinutes(2)),new DetectionProperties.RuleConfig(2,Duration.ofMinutes(5)));
 private AuthEvent event(String ip,String user,String outcome,int second){return new AuthEvent(UUID.randomUUID(),"AUTH_LOGIN",1,Instant.parse("2026-01-01T00:00:00Z").plusSeconds(second),ip,user,outcome,null,"demo-login-service");}
 @Test void brute_force_threshold_and_ips_are_independent(){var r=new BruteForceRule(p,new AlertFactory());assertThat(r.evaluate(event("1.1.1.1","a","FAILURE",1))).isEmpty();assertThat(r.evaluate(event("2.2.2.2","a","FAILURE",2))).isEmpty();r.evaluate(event("1.1.1.1","a","FAILURE",3));assertThat(r.evaluate(event("1.1.1.1","a","FAILURE",4))).isPresent();}
 @Test void spraying_counts_distinct_users(){var r=new PasswordSprayingRule(p,new AlertFactory());r.evaluate(event("1.1.1.1","a","FAILURE",1));r.evaluate(event("1.1.1.1","a","FAILURE",2));r.evaluate(event("1.1.1.1","b","FAILURE",3));assertThat(r.evaluate(event("1.1.1.1","c","FAILURE",4))).isPresent();}
 @Test void failures_followed_by_success_are_suspicious(){var r=new SuspiciousLoginRule(p,new AlertFactory());r.evaluate(event("1.1.1.1","a","FAILURE",1));r.evaluate(event("1.1.1.1","a","FAILURE",2));assertThat(r.evaluate(event("1.1.1.1","a","SUCCESS",3))).isPresent();}
}
