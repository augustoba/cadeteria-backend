package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.AdminUsuarioDtos.CambiarMiPasswordRequest;
import com.cadeteria.backend.service.AdminUsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que cada usuario del panel hace sobre su propia cuenta. Va aparte de
 * {@code /api/admin/usuarios} porque esa ruta exige el permiso "usuarios" y esto es para
 * cualquiera que haya entrado al panel (cae en {@code /api/admin/**}, rol ADMIN).
 */
@RestController
@RequestMapping("/api/admin/mi-cuenta")
public class MiCuentaAdminController {

    private final AdminUsuarioService service;

    public MiCuentaAdminController(AdminUsuarioService service) {
        this.service = service;
    }

    @PostMapping("/password")
    public ResponseEntity<Void> cambiarPassword(Authentication auth, @Valid @RequestBody CambiarMiPasswordRequest req) {
        service.cambiarMiPassword(auth.getName(), req.actual(), req.nueva());
        return ResponseEntity.noContent().build();
    }
}
