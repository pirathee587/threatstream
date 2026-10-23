package com.threatstream.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(EventPublishingException.class)
  ResponseEntity<LoginResponse> kafkaUnavailable() { return ResponseEntity.status(503).body(new LoginResponse("Authentication event service is unavailable. Please try again.")); }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<LoginResponse> invalidRequest() { return ResponseEntity.badRequest().body(new LoginResponse("Username and password are required.")); }
}
