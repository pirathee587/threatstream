package com.threatstream.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class CorsConfiguration implements WebMvcConfigurer {
  private final String origin;

  CorsConfiguration(@Value("${app.cors.allowed-origin:http://localhost:5173}") String origin) {
    this.origin = origin;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(origin)
        .allowedMethods("GET", "PATCH", "OPTIONS")
        .allowedHeaders("Content-Type", "Authorization", "X-API-Key")
        .maxAge(3600);
  }
}

