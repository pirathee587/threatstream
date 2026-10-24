package com.threatstream.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class LoginService {
  private final String configuredUsername;
  private final String configuredPassword;
  private final boolean demoEnabled;
  private final AuthenticationEventFactory eventFactory;
  private final AuthenticationEventPublisher publisher;

  LoginService(
      @Value("${demo.auth.username:demo-user}") String configuredUsername,
      @Value("${demo.auth.password:}") String configuredPassword,
      @Value("${demo.auth.enabled:true}") boolean demoEnabled,
      AuthenticationEventFactory eventFactory,
      AuthenticationEventPublisher publisher) {
    this.configuredUsername = configuredUsername;
    this.configuredPassword = configuredPassword != null ? configuredPassword : "";
    this.demoEnabled = demoEnabled;
    this.eventFactory = eventFactory;
    this.publisher = publisher;
  }

  boolean isDemoEnabled() {
    return demoEnabled;
  }

  boolean login(LoginRequest request, String sourceIp) {
    String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
    boolean valid = username.equals(configuredUsername.trim().toLowerCase(java.util.Locale.ROOT))
        && !configuredPassword.isBlank()
        && MessageDigest.isEqual(request.password().getBytes(StandardCharsets.UTF_8), configuredPassword.getBytes(StandardCharsets.UTF_8));
    publisher.publish(eventFactory.create(username, sourceIp, valid));
    return valid;
  }
}
