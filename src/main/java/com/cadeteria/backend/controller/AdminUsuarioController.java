package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.AdminUsuarioDtos.AdminUsuarioResponse;
import com.cadeteria.backend.dto.AdminUsuarioDtos.CambiarRolRequest;
import com.cadeteria.backend.dto.AdminUsuarioDtos.CrearAdminRequest;
import com.cadeteria.backend.dto.AdminUsuarioDtos.CrearAdminResponse;
import com.cadeteria.backend.dto.AdminUsuarioDtos.ResetearPasswordResponse;
import com.cadeteria.backend.service.AdminUsuarioService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gestión de usuarios del panel (DUENO/OPERADOR). Ya queda solo para DUENO a nivel de
 * ruta en {@code SecurityConfig} (hasRole("ADMIN_DUENO")).
 */
@RestController
@RequestMapping("/api/admin/usuarios")
public class AdminUsuarioController {

    private final AdminUsuarioService service;

    public AdminUsuarioController(AdminUsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUsuarioResponse> listar() {
        return service.listar();
    }

    @PostMapping
    public CrearAdminResponse crear(@Valid @RequestBody CrearAdminRequest req) {
        return service.crear(req);
    }

    @PatchMapping("/{id}/rol")
    public AdminUsuarioResponse cambiarRol(@PathVariable String id, @Valid @RequestBody CambiarRolRequest req) {
        return service.cambiarRol(id, req.rol());
    }

    @PatchMapping("/{id}/habilitado")
    public AdminUsuarioResponse habilitar(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return service.habilitar(id, Boolean.TRUE.equals(body.get("enabled")));
    }

    @PostMapping("/{id}/resetear-password")
    public ResetearPasswordResponse resetearPassword(@PathVariable String id) {
        return service.resetearPassword(id);
    }
}
