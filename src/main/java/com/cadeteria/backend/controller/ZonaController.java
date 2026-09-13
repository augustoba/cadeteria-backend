package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.ZonaDtos.AdyacenteRequest;
import com.cadeteria.backend.dto.ZonaDtos.ZonaRequest;
import com.cadeteria.backend.dto.ZonaDtos.ZonaResponse;
import com.cadeteria.backend.service.ZonaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/zonas")
public class ZonaController {

    private final ZonaService service;

    public ZonaController(ZonaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ZonaResponse> list() {
        return service.findAll().stream().map(ZonaResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ZonaResponse get(@PathVariable String id) {
        return ZonaResponse.from(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ZonaResponse> create(@Valid @RequestBody ZonaRequest req) {
        return ResponseEntity.status(201).body(ZonaResponse.from(service.create(req)));
    }

    @PutMapping("/{id}")
    public ZonaResponse update(@PathVariable String id, @Valid @RequestBody ZonaRequest req) {
        return ZonaResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Desactivar/reactivar sin borrar (ronda 10, punto 99). */
    @PatchMapping("/{id}/activo")
    public ZonaResponse setActivo(@PathVariable String id, @RequestBody com.cadeteria.backend.dto.CadeteDtos.ActivoRequest req) {
        return ZonaResponse.from(service.setActivo(id, req.activo()));
    }

    @PostMapping("/{id}/adyacentes")
    public ZonaResponse agregarAdyacente(@PathVariable String id, @Valid @RequestBody AdyacenteRequest req) {
        return ZonaResponse.from(service.agregarAdyacente(id, req.zonaVecinaId()));
    }

    @DeleteMapping("/{id}/adyacentes/{zonaVecinaId}")
    public ZonaResponse quitarAdyacente(@PathVariable String id, @PathVariable String zonaVecinaId) {
        return ZonaResponse.from(service.quitarAdyacente(id, zonaVecinaId));
    }
}
