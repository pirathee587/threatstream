package com.threatstream.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private final int maxRequestsPerMinute;
  private final Map<String, Deque<Instant>> requestCounts = new ConcurrentHashMap<>();

  public RateLimitingFilter(
      @Value("${demo.auth.rate-limit-per-minute:60}") int maxRequestsPerMinute) {
    this.maxRequestsPerMinute = maxRequestsPerMinute;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!"/api/demo/auth/login".equals(request.getRequestURI())
        || !"POST".equalsIgnoreCase(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    String clientIp = request.getRemoteAddr();
    Instant now = Instant.now();
    Instant windowStart = now.minusSeconds(60);

    AtomicBoolean permitted = new AtomicBoolean(false);
    requestCounts.compute(
        clientIp != null ? clientIp : "unknown",
        (key, timestamps) -> {
          if (timestamps == null) {
            timestamps = new ArrayDeque<>();
          }
          while (!timestamps.isEmpty() && timestamps.peek().isBefore(windowStart)) {
            timestamps.poll();
          }

          if (timestamps.size() < maxRequestsPerMinute) {
            timestamps.add(now);
            permitted.set(true);
          } else {
            permitted.set(false);
          }
          return timestamps;
        });

    if (requestCounts.size() > 5000) {
      requestCounts.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    if (!permitted.get()) {
      response.setStatus(429);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter()
          .write("{\"message\":\"Rate limit exceeded. Too many login attempts.\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }
}
