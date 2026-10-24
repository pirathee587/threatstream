package com.threatstream.alert;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlertController.class)
@Import({ApiExceptionHandler.class, SecurityConfiguration.class})
class AlertControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AlertService service;

  private AlertResponse createResponse(UUID alertId, String status) {
    Instant now = Instant.now();
    return new AlertResponse(
        alertId,
        "RULE-1",
        "PORT_SCAN",
        "HIGH",
        "10.0.0.1",
        "Test description",
        Map.of("ports", 15),
        now,
        now,
        now,
        status,
        now,
        now);
  }

  @Test
  void listsAlertsSuccessfully() throws Exception {
    UUID alertId = UUID.randomUUID();
    AlertResponse response = createResponse(alertId, "OPEN");
    PageResponse page = new PageResponse(List.of(response), 0, 20, 1L, 1);

    when(service.list(anyInt(), anyInt(), anyString(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(page);

    mockMvc
        .perform(get("/api/alerts").param("page", "0").param("size", "20").param("severity", "HIGH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].alertId").value(alertId.toString()))
        .andExpect(jsonPath("$.items[0].severity").value("HIGH"));
  }

  @Test
  void rejectsInvalidQueryParams() throws Exception {
    mockMvc
        .perform(get("/api/alerts").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid alert request."));

    mockMvc
        .perform(get("/api/alerts").param("sortBy", "unauthorizedField"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid alert request."));

    mockMvc
        .perform(get("/api/alerts").param("severity", "INVALID_SEVERITY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid alert request."));
  }

  @Test
  void retrievesSingleAlert() throws Exception {
    UUID alertId = UUID.randomUUID();
    AlertResponse response = createResponse(alertId, "OPEN");

    when(service.get(alertId)).thenReturn(response);

    mockMvc
        .perform(get("/api/alerts/{alertId}", alertId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alertId").value(alertId.toString()))
        .andExpect(jsonPath("$.threatType").value("PORT_SCAN"));
  }

  @Test
  void returns404WhenAlertNotFound() throws Exception {
    UUID alertId = UUID.randomUUID();
    when(service.get(alertId)).thenThrow(new NoSuchElementException("alert not found"));

    mockMvc
        .perform(get("/api/alerts/{alertId}", alertId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Alert not found."));
  }

  @Test
  void retrievesStatistics() throws Exception {
    StatisticsResponse stats =
        new StatisticsResponse(
            100L, 30L, 20L, 50L, Map.of("HIGH", 40L, "MEDIUM", 60L), Map.of("PORT_SCAN", 70L));

    when(service.stats()).thenReturn(stats);

    mockMvc
        .perform(get("/api/alerts/statistics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAlerts").value(100))
        .andExpect(jsonPath("$.openAlerts").value(30))
        .andExpect(jsonPath("$.bySeverity.HIGH").value(40));
  }

  @Test
  void acknowledgesAlert() throws Exception {
    UUID alertId = UUID.randomUUID();
    AlertResponse response = createResponse(alertId, "ACKNOWLEDGED");

    when(service.transition(alertId, "ACKNOWLEDGED")).thenReturn(response);

    mockMvc
        .perform(patch("/api/alerts/{alertId}/acknowledge", alertId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
  }

  @Test
  void resolvesAlert() throws Exception {
    UUID alertId = UUID.randomUUID();
    AlertResponse response = createResponse(alertId, "RESOLVED");

    when(service.transition(alertId, "RESOLVED")).thenReturn(response);

    mockMvc
        .perform(patch("/api/alerts/{alertId}/resolve", alertId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
  }

  @Test
  void rejectsReopeningResolvedAlertWithBadRequest() throws Exception {
    UUID alertId = UUID.randomUUID();
    when(service.transition(alertId, "ACKNOWLEDGED"))
        .thenThrow(new IllegalStateException("resolved alerts cannot be reopened"));

    mockMvc
        .perform(patch("/api/alerts/{alertId}/acknowledge", alertId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid alert request."));
  }
}
