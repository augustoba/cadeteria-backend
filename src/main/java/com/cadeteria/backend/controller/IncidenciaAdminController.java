package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.IncidenciaDtos.IncidenciaRequest;
import com.cadeteria.backend.dto.IncidenciaDtos.IncidenciaResponse;
import com.cadeteria.backend.service.IncidenciaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Incidencias/tickets generales del admin (ronda 4, punto 63) — no atadas a un pedido puntual. */
@RestController
@RequestMapping("/api/admin/incidencias")
public class IncidenciaAdminController {

    private final IncidenciaService service;

    public IncidenciaAdminController(IncidenciaService service) {
        this.service = service;
    }

    @GetMapping
    public List<IncidenciaResponse> listar(@RequestParam(required = false) String estado,
                                            @RequestParam(required = false) String pedidoId,
                                            @RequestParam(required = false) String cadeteId) {
        List<com.cadeteria.backend.model.Incidencia> lista = pedidoId != null && !pedidoId.isBlank()
                ? service.porPedido(pedidoId)
                : cadeteId != null && !cadeteId.isBlank()
                ? service.porCadete(cadeteId)
                : service.listar(estado);
        return lista.stream().map(IncidenciaResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<IncidenciaResponse> crear(@Valid @RequestBody IncidenciaRequest req, Authentication auth) {
        return ResponseEntity.status(201).body(IncidenciaResponse.from(service.crear(req, auth.getName())));
    }

    @PostMapping("/{id}/cerrar")
    public IncidenciaResponse cerrar(@PathVariable String id, Authentication auth) {
        return IncidenciaResponse.from(service.cerrar(id, auth.getName()));
    }

    @PostMapping("/{id}/reabrir")
    public IncidenciaResponse reabrir(@PathVariable String id) {
        return IncidenciaResponse.from(service.reabrir(id));
    }
}
