package com.cadeteria.backend.controller;

import com.cadeteria.backend.service.GeocodingProxyService;
import com.cadeteria.backend.service.GeocodingProxyService.GeoAddress;
import com.cadeteria.backend.service.LinkGoogleMapsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Buscador de direcciones — usado por "/pedir" y por el panel admin. Ver GeocodingProxyService. */
@RestController
@RequestMapping("/api/publico/direcciones")
public class GeocodingProxyController {

    private final GeocodingProxyService service;
    private final LinkGoogleMapsService linkService;

    public GeocodingProxyController(GeocodingProxyService service, LinkGoogleMapsService linkService) {
        this.service = service;
        this.linkService = linkService;
    }

    @GetMapping("/buscar")
    public List<GeoAddress> buscar(@RequestParam String q) {
        return service.buscar(q);
    }

    /** Solo la base propia: contesta al instante (el panel la muestra mientras llegan las externas). */
    @GetMapping("/buscar-propias")
    public List<GeoAddress> buscarPropias(@RequestParam String q) {
        return service.buscarPropias(q);
    }

    /** Solo los servicios de afuera; vacío si la base ya lo resolvió o si la búsqueda externa está apagada. */
    @GetMapping("/buscar-externas")
    public List<GeoAddress> buscarExternas(@RequestParam String q) {
        return service.buscarExternas(q);
    }

    /** "No está mi dirección — buscar de nuevo": sin cache y con Google si hay key (ver el service). */
    @GetMapping("/buscar-ampliado")
    public List<GeoAddress> buscarAmpliado(@RequestParam String q) {
        return service.buscarAmpliado(q);
    }

    /** Link de Google Maps pegado en el buscador (largo o corto de la app) -> lat/lng o mensaje de error. */
    @GetMapping("/link")
    public LinkGoogleMapsService.ResultadoLink link(@RequestParam String url) {
        return linkService.resolver(url);
    }

    /** Calles conocidas para lo escrito sin la altura: el buscador las ofrece para completar (2026-10-03). */
    @GetMapping("/calles")
    public java.util.List<String> calles(@RequestParam String q) {
        return service.sugerirCalles(q);
    }

    @GetMapping("/reverse")
    public GeoAddress reverse(@RequestParam double lat, @RequestParam double lng) {
        return service.reverseParaConsulta(lat, lng);
    }
}
