package com.cadeteria.backend.controller;

import com.cadeteria.backend.model.CalleUnion;
import com.cadeteria.backend.service.UnionCallesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Calles que la base propia tiene con dos nombres (2026-10-03). Bajo {@code /api/admin/configuracion},
 * así que exige el permiso "configuracion" (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/configuracion/calles")
public class CallesAdminController {

    private final UnionCallesService service;

    public CallesAdminController(UnionCallesService service) {
        this.service = service;
    }

    /** Qué pares se unirían, sin tocar nada. */
    @GetMapping("/duplicadas")
    public List<UnionCallesService.Union> duplicadas() {
        return service.duplicadas(false);
    }

    @PostMapping("/unir-duplicadas")
    public List<UnionCallesService.Union> unirDuplicadas() {
        return service.duplicadas(true);
    }

    @GetMapping("/uniones")
    public List<CalleUnion> uniones() {
        return service.historial();
    }
}
