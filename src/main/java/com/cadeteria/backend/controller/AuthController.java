package com.cadeteria.backend.controller;

import com.cadeteria.backend.config.JwtService;
import com.cadeteria.backend.dto.LoginRequest;
import com.cadeteria.backend.dto.TokenResponse;
import com.cadeteria.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login/admin")
    public ResponseEntity<TokenResponse> loginAdmin(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String ip = http.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = http.getRemoteAddr();
        JwtService.TokenData data = authService.loginAdmin(req.username(), req.password(), ip);
        return ResponseEntity.ok(TokenResponse.bearer(data.token(), data.tipo(), data.expiresAt()));
    }

    @PostMapping("/login/cadete")
    public ResponseEntity<TokenResponse> loginCadete(@Valid @RequestBody LoginRequest req) {
        JwtService.TokenData data = authService.loginCadete(req.username(), req.password());
        return ResponseEntity.ok(TokenResponse.bearer(data.token(), data.tipo(), data.expiresAt()));
    }
}
