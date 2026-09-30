package com.cadeteria.backend.controller;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.service.CelularCadeteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Celular vinculado del cadete, en su ficha (2026-09-29). Verlo es de cualquier admin; "Habilitar nuevo
 * celular" exige el permiso celular_cadete (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/cadetes/{id}/celular")
public class CelularCadeteAdminController {

    public record CelularResponse(String modelo, Instant vinculadoEn, int intentosOtroCelular,
                                  Instant ultimoIntentoOtroCelularEn, String ultimoIntentoOtroCelularModelo) {
        static CelularResponse de(Cadete c) {
            return new CelularResponse(c.getCelularId() == null ? null : c.getCelularModelo(), c.getCelularVinculadoEn(),
                    c.getIntentosOtroCelular(), c.getUltimoIntentoOtroCelularEn(), c.getUltimoIntentoOtroCelularModelo());
        }
    }

    private final CelularCadeteService service;

    public CelularCadeteAdminController(CelularCadeteService service) {
        this.service = service;
    }

    @GetMapping
    public CelularResponse ver(@PathVariable String id) {
        return CelularResponse.de(service.buscar(id));
    }

    @PostMapping("/habilitar-nuevo")
    public CelularResponse habilitarNuevo(@PathVariable String id) {
        return CelularResponse.de(service.habilitarNuevoCelular(id));
    }
}
