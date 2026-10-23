package com.threatstream.alert;
import org.springframework.stereotype.Component;
@Component class AlertValidator { void validate(AlertEvent e){if(e==null||e.alertId()==null||e.schemaVersion()!=1||!"SECURITY_ALERT".equals(e.eventType())||e.ruleId()==null||e.threatType()==null||e.sourceIp()==null||e.description()==null||e.firstSeen()==null||e.lastSeen()==null||e.detectedAt()==null||!("MEDIUM".equals(e.severity())||"HIGH".equals(e.severity())||"CRITICAL".equals(e.severity())))throw new IllegalArgumentException("invalid alert event");} }
