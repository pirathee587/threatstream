package com.threatstream.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticationEvent(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant timestamp,
    String sourceIp,
    String username,
    String outcome,
    String failureReason,
    String serviceName) { }
