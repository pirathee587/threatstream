package com.threatstream.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/auth")
class LoginController {
  private final LoginService loginService;
  LoginController(LoginService loginService) { this.loginService = loginService; }
  @PostMapping("/login")
  ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    boolean successful = loginService.login(request, servletRequest.getRemoteAddr());
    return successful ? ResponseEntity.ok(new LoginResponse("Demo login successful."))
        : ResponseEntity.status(401).body(new LoginResponse("Invalid credentials."));
  }
}
