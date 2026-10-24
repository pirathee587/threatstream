package com.threatstream.alert;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlertController.class)
@Import({ApiExceptionHandler.class, SecurityConfiguration.class})
@TestPropertySource(properties = {
    "app.security.api-key=test-secret-api-key-12345",
    "app.cors.allowed-origin=http://localhost:5173"
})
class AlertServiceSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AlertService service;

  private AlertResponse dummyResponse(UUID alertId) {
    Instant now = Instant.now();
    return new AlertResponse(
        alertId, "RULE-1", "PORT_SCAN", "HIGH", "10.0.0.1", "Desc",
        Map.of(), now, now, now, "OPEN", now, now);
  }

  @Test
  void rejectsRequestWithoutApiKeyWhenConfigured() throws Exception {
    mockMvc
        .perform(get("/api/alerts"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(jsonPath("$.message").value("API key required."));
  }

  @Test
  void rejectsRequestWithInvalidApiKey() throws Exception {
    mockMvc
        .perform(get("/api/alerts").header("X-API-Key", "wrong-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(jsonPath("$.message").value("Invalid API key."));
  }

  @Test
  void allowsRequestWithValidXApiKeyHeader() throws Exception {
    UUID alertId = UUID.randomUUID();
    when(service.list(anyInt(), anyInt(), anyString(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageResponse(List.of(dummyResponse(alertId)), 0, 20, 1L, 1));

    mockMvc
        .perform(get("/api/alerts").header("X-API-Key", "test-secret-api-key-12345"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void allowsRequestWithValidAuthorizationBearerHeader() throws Exception {
    UUID alertId = UUID.randomUUID();
    when(service.list(anyInt(), anyInt(), anyString(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageResponse(List.of(dummyResponse(alertId)), 0, 20, 1L, 1));

    mockMvc
        .perform(get("/api/alerts").header("Authorization", "Bearer test-secret-api-key-12345"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void rejectsExcessivePaginationSizeWithBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/alerts")
            .header("X-API-Key", "test-secret-api-key-12345")
            .param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid alert request."));
  }

  @Test
  void rejectsMalformedUuidInPathWithBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/alerts/not-a-valid-uuid")
            .header("X-API-Key", "test-secret-api-key-12345"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid alert request."));
  }

  @Test
  void allowsPreflightCorsOptionsRequestWithoutAuth() throws Exception {
    mockMvc
        .perform(options("/api/alerts")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk());
  }
}
