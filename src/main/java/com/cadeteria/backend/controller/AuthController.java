package com.cadeteria.backend.controller;

import com.cadeteria.backend.config.JwtService;
import com.cadeteria.backend.dto.LoginRequest;
import com.cadeteria.backend.dto.RecuperarPasswordDtos.ConfirmarRequest;
import com.cadeteria.backend.dto.RecuperarPasswordDtos.SolicitarRequest;
import com.cadeteria.backend.dto.TokenResponse;
import com.cadeteria.backend.service.AuthService;
import com.cadeteria.backend.service.PasswordRecoveryService;
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
    private final PasswordRecoveryService passwordRecoveryService;

    public AuthController(AuthService authService, PasswordRecoveryService passwordRecoveryService) {
        this.authService = authService;
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping("/login/admin")
    public ResponseEntity<TokenResponse> loginAdmin(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        // La IP real ya viene resuelta por Tomcat desde el proxy de confianza (ver RateLimitFilter).
        String ip = http.getRemoteAddr();
        JwtService.TokenData data = authService.loginAdmin(req.username(), req.password(), ip);
        return ResponseEntity.ok(TokenResponse.bearer(data.token(), data.tipo(), data.expiresAt()));
    }

    @PostMapping("/login/cadete")
    public ResponseEntity<TokenResponse> loginCadete(@Valid @RequestBody LoginRequest req) {
        JwtService.TokenData data = authService.loginCadete(req.username(), req.password(), req.versionApp());
        return ResponseEntity.ok(TokenResponse.bearer(data.token(), data.tipo(), data.expiresAt()));
    }

    /** "Olvidé mi contraseña" (app de cadetes, sin sesión) — siempre 200, exista o no el usuario, para no filtrar qué DNIs están de alta. */
    @PostMapping("/recuperar-password")
    public ResponseEntity<Void> recuperarPassword(@Valid @RequestBody SolicitarRequest req) {
        passwordRecoveryService.solicitar(req.username());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/recuperar-password/confirmar")
    public ResponseEntity<Void> confirmarRecuperarPassword(@Valid @RequestBody ConfirmarRequest req) {
        passwordRecoveryService.confirmar(req.username(), req.codigo(), req.nuevaPassword());
        return ResponseEntity.ok().build();
    }
}
