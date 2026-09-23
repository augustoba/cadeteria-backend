package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.CadeteActualizacionDtos.CampoPendienteAdminResponse;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.CampoResponse;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.RechazarCampoRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteActualizacionCampo;
import com.cadeteria.backend.service.CadeteActualizacionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Revisión admin de las actualizaciones que proponen los cadetes (mejora 2026-09-23). */
@RestController
@RequestMapping("/api/admin/cadetes/actualizaciones")
public class CadeteActualizacionAdminController {

    private final CadeteActualizacionService service;

    public CadeteActualizacionAdminController(CadeteActualizacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<CampoPendienteAdminResponse> listarPendientes() {
        return service.listarPendientes().stream().map(this::toAdminResponse).toList();
    }

    @PostMapping("/campos/{id}/aprobar")
    public CampoResponse aprobar(@PathVariable String id, Authentication auth) {
        return CampoResponse.from(service.aprobarCampo(id, auth.getName()));
    }

    @PostMapping("/campos/{id}/rechazar")
    public CampoResponse rechazar(@PathVariable String id, @RequestBody(required = false) RechazarCampoRequest req, Authentication auth) {
        return CampoResponse.from(service.rechazarCampo(id, req == null ? null : req.motivo(), auth.getName()));
    }

    private CampoPendienteAdminResponse toAdminResponse(CadeteActualizacionCampo c) {
        Cadete cadete = c.getActualizacion().getCadete();
        return new CampoPendienteAdminResponse(c.getActualizacion().getId(), CampoResponse.from(c),
                cadete.getId(), cadete.getNombre(), cadete.getApellido(), cadete.getFotoUrl());
    }
}
