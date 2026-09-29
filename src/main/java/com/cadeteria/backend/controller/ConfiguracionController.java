package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.ConfiguracionDtos.ConfiguracionResponse;
import com.cadeteria.backend.dto.ConfiguracionDtos.ConfiguracionUpdateRequest;
import com.cadeteria.backend.service.ApiKeyPoolService;
import com.cadeteria.backend.service.ApiKeyPoolService.EstadoClave;
import com.cadeteria.backend.common.ForbiddenException;
import com.cadeteria.backend.config.PermisosActuales;
import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.ConfiguracionSistema;
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
        return new ConfiguracionResponse(visibles());
    }

    @PutMapping
    public ConfiguracionResponse set(@Valid @RequestBody ConfiguracionUpdateRequest req) {
        if (!PermisosActuales.esSuperadmin()) {
            // Superadmin (2026-09-26): lo técnico no lo cambia un admin, aunque arme el pedido a mano.
            if (ConfiguracionSistema.esDeSistema(req.clave())) {
                throw new ForbiddenException("Esa opción solo la puede cambiar el superadmin.");
            }
            // Sus propias API keys: puede agregar, no quitar (las borra el superadmin).
            if (ConfiguracionSistema.esListaDeKeysDelCliente(req.clave())) {
                List<String> nuevas = ApiKeyPoolService.separarClaves(req.valor());
                boolean quitaAlguna = ApiKeyPoolService.separarClaves(service.getString(req.clave(), "")).stream()
                        .anyMatch(k -> !nuevas.contains(k));
                if (quitaAlguna) {
                    throw new ForbiddenException("Podés agregar API keys pero no quitarlas: pedíselo al administrador del sistema.");
                }
            }
        }
        com.cadeteria.backend.service.RecordatoriosAppService.validar(req.clave(), req.valor());
        service.set(req.clave(), req.valor());
        return new ConfiguracionResponse(visibles());
    }

    /** Todo para el superadmin; para el resto, sin la parte técnica ni las keys del sistema. */
    private java.util.Map<String, String> visibles() {
        java.util.Map<String, String> todo = service.findAll();
        if (PermisosActuales.esSuperadmin()) return todo;
        java.util.Map<String, String> filtrado = new java.util.LinkedHashMap<>(todo);
        filtrado.keySet().removeIf(ConfiguracionSistema::esDeSistema);
        return filtrado;
    }

    /**
     * Semáforo de cada API key cargada (Geoapify para direcciones, GraphHopper/OpenRouteService
     * para distancia real) — verde/rojo según si {@link ApiKeyPoolService} la marcó sin cupo, y
     * el cupo restante cuando el proveedor lo informa (hoy solo OpenRouteService).
     */
    @GetMapping("/api-keys/estado")
    public List<EstadoClave> estadoApiKeys() {
        boolean superadmin = PermisosActuales.esSuperadmin();
        return Stream.of(
                apiKeyPool.estadoDe(GeocodingProxyService.PROVEEDOR_GEOAPIFY, GeocodingProxyService.CONFIG_GEOAPIFY_KEYS),
                apiKeyPool.estadoDe(GeocodingProxyService.PROVEEDOR_LOCATIONIQ, GeocodingProxyService.CONFIG_LOCATIONIQ_KEYS),
                apiKeyPool.estadoDe(GeocodingProxyService.PROVEEDOR_GOOGLE, GeocodingProxyService.CONFIG_GOOGLE_KEYS),
                apiKeyPool.estadoDe(RutaService.PROVEEDOR_GRAPHHOPPER, RutaService.CONFIG_GRAPHHOPPER_KEYS),
                apiKeyPool.estadoDe(RutaService.PROVEEDOR_ORS, RutaService.CONFIG_ORS_KEYS)
        ).flatMap(List::stream)
                // El admin ve que las del sistema existen y si tienen cupo, pero no cuáles son.
                .map(e -> superadmin || !e.delSistema() ? e
                        : new EstadoClave(e.proveedor(), "(del sistema)", e.estado(), e.restante(), e.restanteEstimado(), e.actualizadoEn(), true))
                .toList();
    }
}
