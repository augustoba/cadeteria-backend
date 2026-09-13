package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.SeguridadDtos.AccesoLogResponse;
import com.cadeteria.backend.dto.SeguridadDtos.CerrarSesionesResponse;
import com.cadeteria.backend.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Registro de accesos (ronda 5, punto 50) y botón de emergencia (ronda 6, punto 64). */
@RestController
@RequestMapping("/api/admin/seguridad")
public class SeguridadAdminController {

    private final AuthService authService;

    public SeguridadAdminController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/accesos")
    public List<AccesoLogResponse> accesos(@RequestParam(defaultValue = "200") int cantidad) {
        return authService.ultimosAccesos(cantidad).stream().map(AccesoLogResponse::from).toList();
    }

    /** Invalida de una todos los tokens ya emitidos (admins y cadetes) — para cuando se sospecha de un usuario/contraseña filtrado. */
    @PostMapping("/cerrar-sesiones")
    public CerrarSesionesResponse cerrarSesiones() {
        return new CerrarSesionesResponse(authService.cerrarTodasLasSesiones());
    }
}
