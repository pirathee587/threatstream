package com.threatstream.alert;
import java.time.Instant; import java.util.Map; import java.util.UUID;
public record AlertEvent(UUID alertId,String eventType,int schemaVersion,String ruleId,String threatType,String severity,String sourceIp,String description,Map<String,Object> evidence,Instant firstSeen,Instant lastSeen,Instant detectedAt,String status) { }
