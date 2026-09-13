package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.PagoSemanalDtos.PagoRequest;
import com.cadeteria.backend.dto.PagoSemanalDtos.PagoResponse;
import com.cadeteria.backend.service.PagoSemanalService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cadetes/{cadeteId}/pagos")
public class PagoSemanalController {

    private final PagoSemanalService service;

    public PagoSemanalController(PagoSemanalService service) {
        this.service = service;
    }

    @GetMapping
    public List<PagoResponse> list(@PathVariable String cadeteId) {
        return service.listar(cadeteId).stream().map(PagoResponse::from).toList();
    }

    @PostMapping
    public PagoResponse registrar(@PathVariable String cadeteId, @Valid @RequestBody PagoRequest req,
                                   Authentication auth) {
        return PagoResponse.from(service.registrar(cadeteId, req, auth.getName()));
    }
}
