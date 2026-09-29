package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.AvisoCalleDtos.AvisoCalleRequest;
import com.cadeteria.backend.dto.AvisoCalleDtos.AvisoCalleResponse;
import com.cadeteria.backend.model.AvisoCalle;
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

    /**
     * Activos cerca de donde está el cadete ("Avisos cerca tuyo" al abrir la app o al pasar a Libre).
     * Sin lat/lng, todos los activos: el "Mapa de la calle" de la app (2026-09-29) muestra la ciudad.
     */
    @GetMapping("/api/cadetes/me/avisos-calle")
    public List<AvisoCalleResponse> cerca(@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
        List<AvisoCalle> avisos = lat == null || lng == null ? service.activos() : service.activosCerca(lat, lng);
        return avisos.stream().map(AvisoCalleResponse::paraCadete).toList();
    }

    @GetMapping("/api/admin/avisos-calle")
    public List<AvisoCalleResponse> activos() {
        return service.activos().stream().map(AvisoCalleResponse::paraPanel).toList();
    }

    /** "¿Sigue ahí?" (segunda etapa, 2026-09-29): SIGUE o YA_NO_ESTA de un cadete que pasa cerca. */
    @PostMapping("/api/cadetes/me/avisos-calle/{id}/voto")
    public AvisoCalleResponse votar(@PathVariable String id,
                                    @Valid @RequestBody com.cadeteria.backend.dto.AvisoCalleDtos.VotoRequest req,
                                    Authentication auth) {
        return AvisoCalleResponse.paraCadete(service.votar(auth.getName(), id, req.voto()));
    }

    /** Ficha del cadete: avisos que mandó y cuántos otros marcaron "ya no está". */
    @GetMapping("/api/admin/cadetes/{id}/avisos-calle")
    public com.cadeteria.backend.dto.AvisoCalleDtos.ResumenAvisosCadete resumenCadete(@PathVariable String id) {
        return service.resumenCadete(id);
    }
}
