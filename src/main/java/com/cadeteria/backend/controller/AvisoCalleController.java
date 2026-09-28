package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.AvisoCalleDtos.AvisoCalleRequest;
import com.cadeteria.backend.dto.AvisoCalleDtos.AvisoCalleResponse;
import com.cadeteria.backend.service.AvisoCalleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * "Avisos de la calle" (carril C, 2026-09-28). Los del cadete van bajo /api/cadetes/me (rol CADETE);
 * el del panel bajo /api/admin, como el resto del Mapa (cualquier admin).
 */
@RestController
public class AvisoCalleController {

    private final AvisoCalleService service;

    public AvisoCalleController(AvisoCalleService service) {
        this.service = service;
    }

    @PostMapping("/api/cadetes/me/avisos-calle")
    @ResponseStatus(HttpStatus.CREATED)
    public AvisoCalleResponse crear(@Valid @RequestBody AvisoCalleRequest req, Authentication auth) {
        return AvisoCalleResponse.paraCadete(service.crear(auth.getName(), req.tipo(), req.lat(), req.lng()));
    }

    /** Activos cerca de donde está el cadete ("Avisos cerca tuyo" al abrir la app o al pasar a Libre). */
    @GetMapping("/api/cadetes/me/avisos-calle")
    public List<AvisoCalleResponse> cerca(@RequestParam double lat, @RequestParam double lng) {
        return service.activosCerca(lat, lng).stream().map(AvisoCalleResponse::paraCadete).toList();
    }

    @GetMapping("/api/admin/avisos-calle")
    public List<AvisoCalleResponse> activos() {
        return service.activos().stream().map(AvisoCalleResponse::paraPanel).toList();
    }
}
