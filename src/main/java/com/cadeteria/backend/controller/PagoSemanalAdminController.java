package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.PagoSemanalDtos.PendienteResponse;
import com.cadeteria.backend.service.PagoSemanalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Agregados de Pagos semanales que no son por cadete puntual (recordatorio, ronda 6 punto 35). */
@RestController
@RequestMapping("/api/admin/pagos")
public class PagoSemanalAdminController {

    private final PagoSemanalService service;

    public PagoSemanalAdminController(PagoSemanalService service) {
        this.service = service;
    }

    /** Cadetes activos sin pago registrado para la última semana ya cerrada. */
    @GetMapping("/pendientes")
    public List<PendienteResponse> pendientes() {
        return service.cadetesConPagoPendiente().stream().map(PendienteResponse::from).toList();
    }
}
