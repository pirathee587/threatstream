package com.threatstream.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet filter that extracts or generates an X-Correlation-ID header,
 * establishes it in MDC for structured logging, and attaches it to the response.
 */
public class CorrelationIdFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
      String headerValue = httpRequest.getHeader(CorrelationIdContext.HTTP_HEADER);
      if (headerValue != null && !headerValue.isBlank()) {
        CorrelationIdContext.set(headerValue);
      } else {
        CorrelationIdContext.getOrGenerate();
      }
      httpResponse.setHeader(CorrelationIdContext.HTTP_HEADER, CorrelationIdContext.get());
    }
    try {
      chain.doFilter(request, response);
    } finally {
      CorrelationIdContext.clear();
    }
  }
}
