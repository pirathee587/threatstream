package com.threatstream.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class LoginControllerTest {
  @Test void ignores_spoofed_forwarded_headers() {
    LoginService service = mock(LoginService.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("10.0.0.8");
    when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9");
    when(service.login(any(), any())).thenReturn(false);
    var response = new LoginController(service).login(new LoginRequest("alice", "not-a-secret"), request);
    assertThat(response.getStatusCode().value()).isEqualTo(401);
    verify(service).login(new LoginRequest("alice", "not-a-secret"), "10.0.0.8");
  }
}
