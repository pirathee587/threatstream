package com.threatstream.detection;
import java.time.Instant;
import java.util.UUID;
public record AuthEvent(UUID eventId, String eventType, int schemaVersion, Instant timestamp, String sourceIp, String username, String outcome, String failureReason, String serviceName) { }
