package com.threatstream.detection;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component class AlertFactory { private final Clock clock=Clock.systemUTC(); SecurityAlert create(String rule, String type, String severity, AuthEvent e, int count, Instant first) { return create(rule,type,severity,e.sourceIp(),e.timestamp(),first,Map.of("attemptCount",count,"windowSeconds",Math.max(0,e.timestamp().getEpochSecond()-first.getEpochSecond()))); } SecurityAlert create(String rule,String type,String severity,String ip,Instant last,Instant first,Map<String,Object> evidence){return new SecurityAlert(UUID.randomUUID(),"SECURITY_ALERT",1,rule,type,severity,ip,"Possible "+type.toLowerCase().replace('_',' ')+" activity detected.",evidence,first,last,clock.instant(),"OPEN");} }
