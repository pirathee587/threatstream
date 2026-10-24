package com.threatstream.alert;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

  private final String configuredApiKey;
  private final boolean requireKey;

  public ApiKeyAuthenticationFilter(String configuredApiKey, boolean isProduction) {
    this.configuredApiKey = configuredApiKey != null ? configuredApiKey.trim() : "";
    this.requireKey = !this.configuredApiKey.isEmpty() || isProduction;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    if (path.startsWith("/actuator/")) {
      filterChain.doFilter(request, response);
      return;
    }

    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    String candidateKey = resolveApiKey(request);

    if (requireKey) {
      if (candidateKey == null || candidateKey.isBlank()) {
        sendUnauthorized(response, "API key required.");
        return;
      }

      if (!constantTimeEquals(candidateKey, configuredApiKey)) {
        LOG.warn("Rejected request to {} with invalid API key", path);
        sendUnauthorized(response, "Invalid API key.");
        return;
      }

      var auth =
          new UsernamePasswordAuthenticationToken(
              "api-client", null, List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
      SecurityContextHolder.getContext().setAuthentication(auth);
    } else {
      // In dev mode with no key configured, assign ROLE_ANALYST
      var auth =
          new UsernamePasswordAuthenticationToken(
              "dev-client", null, List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    filterChain.doFilter(request, response);
  }

  private String resolveApiKey(HttpServletRequest request) {
    String header = request.getHeader("X-API-Key");
    if (header != null && !header.isBlank()) {
      return header.trim();
    }
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return authHeader.substring(7).trim();
    }
    return null;
  }

  private boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
    byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(aBytes, bBytes);
  }

  private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
  }
}
