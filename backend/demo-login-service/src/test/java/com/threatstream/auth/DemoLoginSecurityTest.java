package com.threatstream.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "demo.auth.rate-limit-per-minute=5",
    "demo.auth.enabled=true",
    "demo.auth.username=demo-user",
    "demo.auth.password=super-secret-demo-pass"
})
class DemoLoginSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthenticationEventPublisher eventPublisher;

  @Test
  void enforcesRateLimitingAfterLimitExceeded() throws Exception {
    String payload = "{\"username\":\"demo-user\",\"password\":\"super-secret-demo-pass\"}";

    // First 5 requests within limit should succeed or return valid status
    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(
              post("/api/demo/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(payload)
                  .with(request -> {
                    request.setRemoteAddr("192.168.10.50");
                    return request;
                  }))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("Demo login successful."));
    }

    // 6th request from same IP exceeds rate limit -> 429 Too Many Requests
    mockMvc
        .perform(
            post("/api/demo/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(request -> {
                  request.setRemoteAddr("192.168.10.50");
                  return request;
                }))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.message").value("Rate limit exceeded. Too many login attempts."));
  }

  @Test
  void rejectsInvalidCredentialsWith401() throws Exception {
    String wrongPayload = "{\"username\":\"demo-user\",\"password\":\"wrong-password\"}";

    mockMvc
        .perform(
            post("/api/demo/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongPayload)
                .with(request -> {
                  request.setRemoteAddr("192.168.10.60");
                  return request;
                }))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid credentials."));
  }

  @Test
  void rejectsMalformedRequestBodyWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/demo/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\"}")
                .with(request -> {
                  request.setRemoteAddr("192.168.10.70");
                  return request;
                }))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Username and password are required."));
  }
}
