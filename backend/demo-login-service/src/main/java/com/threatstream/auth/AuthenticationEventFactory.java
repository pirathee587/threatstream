package com.threatstream.auth;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AuthenticationEventFactory {
  private final Clock clock;

  AuthenticationEventFactory() { this(Clock.systemUTC()); }
  AuthenticationEventFactory(Clock clock) { this.clock = clock; }

  AuthenticationEvent create(String username, String sourceIp, boolean successful) {
    return new AuthenticationEvent(UUID.randomUUID(), "AUTH_LOGIN", 1, clock.instant(), sourceIp,
        username, successful ? "SUCCESS" : "FAILURE",
        successful ? null : "INVALID_CREDENTIALS", "demo-login-service");
  }
}
