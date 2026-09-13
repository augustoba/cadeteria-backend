package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.LookupResponse;
import com.cadeteria.backend.model.Lookup;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Listas de solo lectura de las tablas de parametria que el front necesita para
 * combos (ej. tipo de vehiculo al cargar un cadete o un pedido) — ver
 * diseno-tecnico.md sección 8 (parametrias en vez de enums).
 */
@RestController
@RequestMapping("/api/admin/lookups")
public class LookupController {

    private final TipoVehiculoRepository tipoVehiculoRepo;

    public LookupController(TipoVehiculoRepository tipoVehiculoRepo) {
        this.tipoVehiculoRepo = tipoVehiculoRepo;
    }

    @GetMapping("/tipos-vehiculo")
    public List<LookupResponse> tiposVehiculo() {
        return tipoVehiculoRepo.findAll().stream().map((Lookup l) -> LookupResponse.from(l)).toList();
    }
}
