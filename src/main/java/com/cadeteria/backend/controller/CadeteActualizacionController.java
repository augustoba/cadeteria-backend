package com.cadeteria.backend.controller;

import jakarta.validation.Valid;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.ActualizacionCadeteRequest;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.ActualizacionResponse;
import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.service.CadeteActualizacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** El cadete propone cambios de foto/datos del vehículo desde la app (mejora 2026-09-23). */
@RestController
@RequestMapping("/api/cadetes/me/actualizaciones")
public class CadeteActualizacionController {

    private final CadeteActualizacionService service;

    public CadeteActualizacionController(CadeteActualizacionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ActualizacionResponse> crear(Authentication auth, @Valid @RequestBody ActualizacionCadeteRequest req) {
        CadeteActualizacion lote = service.crear(auth.getName(), req);
        return ResponseEntity.status(201).body(ActualizacionResponse.from(lote, service.camposDe(lote.getId())));
    }

    @GetMapping
    public List<ActualizacionResponse> listar(Authentication auth) {
        return service.misActualizaciones(auth.getName()).stream()
                .map(lote -> ActualizacionResponse.from(lote, service.camposDe(lote.getId())))
                .toList();
    }
}
