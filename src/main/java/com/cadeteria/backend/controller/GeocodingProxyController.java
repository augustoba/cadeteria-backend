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

    @GetMapping("/reverse")
    public GeoAddress reverse(@RequestParam double lat, @RequestParam double lng) {
        return service.reverse(lat, lng);
    }
}
