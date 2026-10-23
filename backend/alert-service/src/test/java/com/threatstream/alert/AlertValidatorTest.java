package com.threatstream.alert;
import static org.assertj.core.api.Assertions.*; import java.time.Instant; import java.util.*; import org.junit.jupiter.api.Test;
class AlertValidatorTest { private AlertEvent event(){return new AlertEvent(UUID.randomUUID(),"SECURITY_ALERT",1,"AUTH_BRUTE_FORCE","BRUTE_FORCE","HIGH","127.0.0.1","Possible activity",Map.of("attemptCount",10),Instant.now(),Instant.now(),Instant.now(),"OPEN");}
 @Test void accepts_supported_sanitized_alert(){assertThatCode(()->new AlertValidator().validate(event())).doesNotThrowAnyException();}
 @Test void rejects_unsupported_schema(){var e=event();assertThatThrownBy(()->new AlertValidator().validate(new AlertEvent(e.alertId(),e.eventType(),2,e.ruleId(),e.threatType(),e.severity(),e.sourceIp(),e.description(),e.evidence(),e.firstSeen(),e.lastSeen(),e.detectedAt(),e.status()))).isInstanceOf(IllegalArgumentException.class);}
}
