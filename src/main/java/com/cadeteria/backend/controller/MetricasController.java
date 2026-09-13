package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.MetricasDtos.CadeteMetricaResponse;
import com.cadeteria.backend.dto.MetricasDtos.PorHoraResponse;
import com.cadeteria.backend.dto.MetricasDtos.RechazoResponse;
import com.cadeteria.backend.dto.MetricasDtos.ResumenDiaResponse;
import com.cadeteria.backend.dto.MetricasDtos.ZonaMetricaResponse;
import com.cadeteria.backend.service.MetricasService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Pantalla de Métricas del panel admin — pedidos del día/rango y desempeño por cadete. */
@RestController
@RequestMapping("/api/admin/metricas")
public class MetricasController {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final MetricasService service;

    public MetricasController(MetricasService service) {
        this.service = service;
    }

    /** `desde`/`hasta` en formato yyyy-MM-dd, ambos inclusive. Sin parámetros: hoy. */
    @GetMapping("/resumen")
    public ResumenDiaResponse resumen(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        Instant[] rango = resolverRango(desde, hasta);
        return service.resumenDia(rango[0], rango[1]);
    }

    @GetMapping("/cadetes")
    public List<CadeteMetricaResponse> cadetes(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        Instant[] rango = resolverRango(desde, hasta);
        return service.metricasCadetes(rango[0], rango[1]);
    }

    /** Motivos de rechazo cargados por los cadetes en el rango — para detectar patrones. */
    @GetMapping("/rechazos")
    public List<RechazoResponse> rechazos(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        Instant[] rango = resolverRango(desde, hasta);
        return service.motivosDeRechazo(rango[0], rango[1]);
    }

    /** Para el gráfico de pedidos por hora del día (ronda 5, punto 31). */
    @GetMapping("/por-hora")
    public List<PorHoraResponse> porHora(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        Instant[] rango = resolverRango(desde, hasta);
        return service.pedidosPorHora(rango[0], rango[1]);
    }

    /** Puntos de origen para el heatmap de demanda en el mapa en vivo (mejora 92). */
    @GetMapping("/heatmap")
    public List<double[]> heatmap(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        Instant[] rango = resolverRango(desde, hasta);
        return service.puntosDeDemanda(rango[0], rango[1]);
    }

    /** Volumen e ingresos por zona (ronda 5, punto 34). */
    @GetMapping("/zonas")
    public List<ZonaMetricaResponse> zonas(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        Instant[] rango = resolverRango(desde, hasta);
        return service.metricasPorZona(rango[0], rango[1]);
    }

    private Instant[] resolverRango(String desdeStr, String hastaStr) {
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate desdeFecha = desdeStr != null && !desdeStr.isBlank() ? LocalDate.parse(desdeStr) : hoy;
        LocalDate hastaFecha = hastaStr != null && !hastaStr.isBlank() ? LocalDate.parse(hastaStr) : hoy;
        Instant desde = desdeFecha.atStartOfDay(ZONA).toInstant();
        Instant hasta = hastaFecha.plusDays(1).atStartOfDay(ZONA).toInstant();
        return new Instant[]{desde, hasta};
    }
}
