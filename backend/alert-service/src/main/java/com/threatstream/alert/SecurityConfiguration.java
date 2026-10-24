package com.threatstream.alert;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityConfiguration.class);

  private final String allowedOrigin;
  private final String apiKey;
  private final boolean isProduction;

  public SecurityConfiguration(
      @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin,
      @Value("${app.security.api-key:}") String apiKey,
      Environment environment) {
    this.allowedOrigin = allowedOrigin;
    this.apiKey = apiKey != null ? apiKey.trim() : "";
    this.isProduction = List.of(environment.getActiveProfiles()).contains("production");

    if (isProduction && this.apiKey.isEmpty()) {
      throw new IllegalStateException(
          "Production security violation: ALERT_SERVICE_API_KEY / SECURITY_API_KEY must be configured in production profile.");
    }
    if (this.apiKey.isEmpty()) {
      LOG.warn(
          "SECURITY WARNING: Alert Service is running without API key authentication. Set ALERT_SERVICE_API_KEY for production deployments.");
    }
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .contentTypeOptions(Customizer.withDefaults()))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, authException) -> {
                          response.setStatus(401);
                          response.setContentType("application/json");
                          response.setCharacterEncoding("UTF-8");
                          response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required.\"}");
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          response.setStatus(403);
                          response.setContentType("application/json");
                          response.setCharacterEncoding("UTF-8");
                          response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied.\"}");
                        }))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/alerts/**")
                    .hasRole("ANALYST")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new com.threatstream.common.CorrelationIdFilter(),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new ApiKeyAuthenticationFilter(apiKey, isProduction),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
    return username -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "Stateless API key authentication only");
    };
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(allowedOrigin));
    configuration.setAllowedMethods(List.of("GET", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-API-Key", "X-Correlation-ID"));
    configuration.setExposedHeaders(List.of("X-Correlation-ID"));
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
