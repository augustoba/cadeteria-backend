package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.RolDtos.PermisoResponse;
import com.cadeteria.backend.dto.RolDtos.RolRequest;
import com.cadeteria.backend.dto.RolDtos.RolResponse;
import com.cadeteria.backend.service.RolService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Roles con permisos configurables (mejora pedida por el dueño 2026-09-16). Listar
 * (GET) lo puede hacer cualquier admin logueado — hace falta, por ejemplo, para llenar
 * el selector de rol al crear un usuario en "Usuarios" (permiso "usuarios", distinto de
 * "roles"). Crear/editar/borrar un rol sí exige el permiso "roles" — ver SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/roles")
public class RolController {

    private final RolService service;

    public RolController(RolService service) {
        this.service = service;
    }

    @GetMapping
    public List<RolResponse> listar() {
        return service.listar();
    }

    @GetMapping("/permisos")
    public List<PermisoResponse> permisos() {
        return service.catalogoPermisos();
    }

    @PostMapping
    public RolResponse crear(@Valid @RequestBody RolRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    public RolResponse actualizar(@PathVariable String id, @Valid @RequestBody RolRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable String id) {
        service.eliminar(id);
    }
}
