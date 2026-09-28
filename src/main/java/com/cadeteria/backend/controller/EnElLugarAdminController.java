package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.EnElLugarDtos.FichaCadete;
import com.cadeteria.backend.dto.EnElLugarDtos.ResumenCadete;
import com.cadeteria.backend.service.EnElLugarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Registros de "Retirado / Entregado solo en el lugar" (carril B, 2026-09-28). La ficha del cadete va
 * con el resto de /api/admin/cadetes (cualquier admin); el resumen va bajo /api/admin/metricas, así que
 * exige el permiso "metricas" como el resto de esa pantalla.
 */
@RestController
public class EnElLugarAdminController {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final EnElLugarService service;

    public EnElLugarAdminController(EnElLugarService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/cadetes/{id}/en-el-lugar")
    public FichaCadete fichaCadete(@PathVariable String id) {
        return service.fichaCadete(id);
    }

    /** desde/hasta en yyyy-MM-dd, ambos inclusive (como el resto de Métricas). Sin parámetros: hoy. */
    @GetMapping("/api/admin/metricas/en-el-lugar")
    public List<ResumenCadete> metricas(@RequestParam(required = false) String desde,
                                        @RequestParam(required = false) String hasta) {
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate d = desde != null && !desde.isBlank() ? LocalDate.parse(desde) : hoy;
        LocalDate h = hasta != null && !hasta.isBlank() ? LocalDate.parse(hasta) : hoy;
        Instant desdeInstant = d.atStartOfDay(ZONA).toInstant();
        Instant hastaInstant = h.plusDays(1).atStartOfDay(ZONA).toInstant();
        return service.metricas(desdeInstant, hastaInstant);
    }
}
