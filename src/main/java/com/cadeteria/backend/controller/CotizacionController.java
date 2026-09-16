package com.cadeteria.backend.controller;

import com.cadeteria.backend.service.CotizacionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Sugerencia de precio por GPS (mejora pedida por el dueño 2026-09-16) — pública porque la
 * usa tanto el panel admin como la página "/pedir" (sin login, el cliente carga su propio
 * pedido). Nunca es un precio final/vinculante, solo una ayuda editable.
 */
@RestController
@RequestMapping("/api/publico/cotizar")
public class CotizacionController {

    private final CotizacionService service;

    public CotizacionController(CotizacionService service) {
        this.service = service;
    }

    public record CotizacionResponse(BigDecimal precioSugerido, String metodo, String zonaId, String zonaNombre, Double distanciaKm) {
        static CotizacionResponse vacia() {
            return new CotizacionResponse(null, null, null, null, null);
        }
    }

    @GetMapping
    public CotizacionResponse cotizar(
            @RequestParam double origenLat, @RequestParam double origenLng,
            @RequestParam(required = false) Double destinoLat, @RequestParam(required = false) Double destinoLng) {
        return service.cotizar(origenLat, origenLng, destinoLat, destinoLng)
                .map(c -> new CotizacionResponse(c.precioSugerido(), c.metodo(), c.zonaId(), c.zonaNombre(), c.distanciaKm()))
                .orElseGet(CotizacionResponse::vacia);
    }
}
