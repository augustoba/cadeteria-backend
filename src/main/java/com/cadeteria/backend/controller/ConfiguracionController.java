package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.ConfiguracionDtos.ConfiguracionResponse;
import com.cadeteria.backend.dto.ConfiguracionDtos.ConfiguracionUpdateRequest;
import com.cadeteria.backend.service.ApiKeyPoolService;
import com.cadeteria.backend.service.ApiKeyPoolService.EstadoClave;
import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.GeocodingProxyService;
import com.cadeteria.backend.service.RutaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/admin/configuracion")
public class ConfiguracionController {

    private final ConfiguracionService service;
    private final ApiKeyPoolService apiKeyPool;

    public ConfiguracionController(ConfiguracionService service, ApiKeyPoolService apiKeyPool) {
        this.service = service;
        this.apiKeyPool = apiKeyPool;
    }

    @GetMapping
    public ConfiguracionResponse get() {
        return new ConfiguracionResponse(service.findAll());
    }

    @PutMapping
    public ConfiguracionResponse set(@Valid @RequestBody ConfiguracionUpdateRequest req) {
        service.set(req.clave(), req.valor());
        return new ConfiguracionResponse(service.findAll());
    }

    /**
     * Semáforo de cada API key cargada (Geoapify para direcciones, GraphHopper/OpenRouteService
     * para distancia real) — verde/rojo según si {@link ApiKeyPoolService} la marcó sin cupo, y
     * el cupo restante cuando el proveedor lo informa (hoy solo OpenRouteService).
     */
    @GetMapping("/api-keys/estado")
    public List<EstadoClave> estadoApiKeys() {
        return Stream.of(
                apiKeyPool.estadoDe(GeocodingProxyService.PROVEEDOR_GEOAPIFY, GeocodingProxyService.CONFIG_GEOAPIFY_KEYS),
                apiKeyPool.estadoDe(GeocodingProxyService.PROVEEDOR_GOOGLE, GeocodingProxyService.CONFIG_GOOGLE_KEYS),
                apiKeyPool.estadoDe(RutaService.PROVEEDOR_GRAPHHOPPER, RutaService.CONFIG_GRAPHHOPPER_KEYS),
                apiKeyPool.estadoDe(RutaService.PROVEEDOR_ORS, RutaService.CONFIG_ORS_KEYS)
        ).flatMap(List::stream).toList();
    }
}
