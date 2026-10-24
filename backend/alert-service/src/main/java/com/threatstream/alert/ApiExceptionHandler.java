package com.threatstream.alert;

import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  record ErrorResponse(String message) {}

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ErrorResponse> missing() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Alert not found."));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    IllegalStateException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentNotValidException.class
  })
  ResponseEntity<ErrorResponse> invalid() {
    return ResponseEntity.badRequest().body(new ErrorResponse("Invalid alert request."));
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ErrorResponse> unauthorized() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Authentication required."));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> forbidden() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Access denied."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> internalError(Exception e) {
    LOG.error("Unhandled server exception in alert-service", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("An unexpected server error occurred."));
  }
}

