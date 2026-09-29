package com.cadeteria.backend.controller;

import com.cadeteria.backend.model.RecordatorioConfirmacion;
import com.cadeteria.backend.service.RecordatoriosAppService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Cartel "Antes de arrancar" de la app (2026-09-29): el cadete confirma con "Entendido" y la ficha del
 * cadete muestra cuándo. Los textos van en /api/cadetes/me/configuracion.
 */
@RestController
public class RecordatoriosAppController {

    public record ConfirmacionResponse(Instant confirmadoEn, String titulo, List<String> textos) {
        static ConfirmacionResponse from(RecordatorioConfirmacion c) {
            List<String> textos = c.getTextos().isEmpty() ? List.of() : List.of(c.getTextos().split("\n"));
            return new ConfirmacionResponse(c.getConfirmadoEn(), c.getTitulo(), textos);
        }
    }

    private final RecordatoriosAppService service;

    public RecordatoriosAppController(RecordatoriosAppService service) {
        this.service = service;
    }

    @PostMapping("/api/cadetes/me/recordatorios/entendido")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfirmacionResponse entendido(Authentication auth) {
        return ConfirmacionResponse.from(service.confirmar(auth.getName()));
    }

    /** Ficha del cadete: los últimos 10 "Entendido", con lo que decía el cartel en ese momento. */
    @GetMapping("/api/admin/cadetes/{id}/recordatorios")
    public List<ConfirmacionResponse> confirmaciones(@PathVariable String id) {
        return service.confirmacionesDe(id).stream().map(ConfirmacionResponse::from).toList();
    }
}
