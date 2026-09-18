package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Distancia/ruta real por calle, para no depender de la línea recta (Haversine) que
 * subestima mucho en ciudades con ríos, avenidas cortadas o rodeos (ver CotizacionService).
 * Prueba varias fuentes gratuitas en orden, la primera que responda gana — así casi nunca
 * se gasta el crédito de las que piden API key:
 * <p>
 * 1. **OSRM**: demo pública (router.project-osrm.org), sin key ni límite de crédito, pero
 *    sin garantía de disponibilidad (servidor comunitario) — se prueba siempre primero.
 * 2. **GraphHopper**: gratis con API key propia (500 req/día), cargada desde Configuración
 *    ("graphhopper_key") — varía por cliente, por eso no está en application.yml.
 * 3. **OpenRouteService**: gratis con API key propia (2500 req/día), cargada desde
 *    Configuración ("open_route_service_key") — igual, varía por cliente.
 * <p>
 * Si las tres fallan (o ninguna key está cargada), quien llama cae a la línea recta —
 * ninguna de estas fuentes bloquea el flujo si no responde.
 */
@Service
public class RutaService {

    private static final Logger log = LoggerFactory.getLogger(RutaService.class);
    private static final String OSRM_URL = "https://router.project-osrm.org";
    private static final String GRAPHHOPPER_URL = "https://graphhopper.com/api/1/route";

    private final AppProperties props;
    private final ConfiguracionService configuracionService;
    private final RestClient restClient = RestClient.create();

    /** Distancia/tiempo estimado, en metros/minutos — resumen liviano de {@link #calcularRuta}. */
    public record Resumen(double distanciaM, int duracionMin) {}

    public RutaService(AppProperties props, ConfiguracionService configuracionService) {
        this.props = props;
        this.configuracionService = configuracionService;
    }

    /**
     * Ruta completa (geometría + resumen) para mostrarle el trayecto sugerido al cadete al
     * aceptar un viaje (spec 5.7, estilo Uber) — a diferencia de {@link #resumenSiDisponible}
     * no tiene fallback entre proveedores porque el front espera el formato GeoJSON propio
     * de OpenRouteService; requiere su API key configurada en Configuración.
     * @param tipoVehiculoId "MOTO" o "BICI" — define el perfil de ruteo.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcularRuta(double origenLat, double origenLng,
                                             double destinoLat, double destinoLng, String tipoVehiculoId) {
        String orsKey = configuracionService.getString("open_route_service_key", "");
        if (orsKey.isBlank()) {
            throw new BadRequestException("No hay API key de OpenRouteService cargada en Configuración.");
        }
        String perfil = "BICI".equals(tipoVehiculoId) ? "cycling-regular" : "driving-car";
        // ORS espera las coordenadas como [lng, lat], al reves que el resto del sistema.
        Map<String, Object> body = Map.of(
                "coordinates", List.of(
                        List.of(origenLng, origenLat),
                        List.of(destinoLng, destinoLat)
                )
        );
        String orsUrl = configuracionService.getString("open_route_service_url", props.getMaps().getOpenRouteServiceUrl());
        return restClient.post()
                .uri(orsUrl + "/v2/directions/" + perfil + "/geojson")
                .header("Authorization", orsKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    /**
     * Para la cotización automática (CotizacionService) y la página pública de seguimiento
     * ("ETA", ronda 4 punto 30): no rompe nada si ninguna fuente responde — simplemente no
     * hay distancia real para mostrar/cobrar, misma idea de degradar sin romper que
     * FcmService/EmailService. Prueba OSRM, después GraphHopper, después OpenRouteService.
     */
    public Optional<Resumen> resumenSiDisponible(double origenLat, double origenLng,
                                                  double destinoLat, double destinoLng, String tipoVehiculoId) {
        return intentarOsrm(origenLat, origenLng, destinoLat, destinoLng)
                .or(() -> intentarGraphHopper(origenLat, origenLng, destinoLat, destinoLng, tipoVehiculoId))
                .or(() -> intentarOpenRouteService(origenLat, origenLng, destinoLat, destinoLng, tipoVehiculoId));
    }

    /** Demo pública, sin key — solo perfil "driving" disponible en el servidor comunitario. */
    @SuppressWarnings("unchecked")
    private Optional<Resumen> intentarOsrm(double origenLat, double origenLng, double destinoLat, double destinoLng) {
        try {
            String uri = String.format(
                    "%s/route/v1/driving/%s,%s;%s,%s?overview=false",
                    OSRM_URL, origenLng, origenLat, destinoLng, destinoLat);
            Map<String, Object> resp = restClient.get().uri(uri).retrieve().body(Map.class);
            List<Map<String, Object>> rutas = (List<Map<String, Object>>) resp.get("routes");
            Map<String, Object> ruta = rutas.get(0);
            double distanciaM = ((Number) ruta.get("distance")).doubleValue();
            double duracionSeg = ((Number) ruta.get("duration")).doubleValue();
            return Optional.of(new Resumen(distanciaM, (int) Math.ceil(duracionSeg / 60)));
        } catch (Exception e) {
            log.debug("OSRM no disponible para calcular distancia: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Gratis con key propia (500 req/día) — key cargada desde Configuración, varía por cliente. */
    @SuppressWarnings("unchecked")
    private Optional<Resumen> intentarGraphHopper(double origenLat, double origenLng, double destinoLat, double destinoLng,
                                                    String tipoVehiculoId) {
        String key = configuracionService.getString("graphhopper_key", "");
        if (key.isBlank()) {
            return Optional.empty();
        }
        try {
            String vehiculo = "BICI".equals(tipoVehiculoId) ? "bike" : "car";
            String uri = String.format(
                    "%s?point=%s,%s&point=%s,%s&vehicle=%s&key=%s",
                    GRAPHHOPPER_URL, origenLat, origenLng, destinoLat, destinoLng, vehiculo, key);
            Map<String, Object> resp = restClient.get().uri(uri).retrieve().body(Map.class);
            List<Map<String, Object>> paths = (List<Map<String, Object>>) resp.get("paths");
            Map<String, Object> path = paths.get(0);
            double distanciaM = ((Number) path.get("distance")).doubleValue();
            double duracionMs = ((Number) path.get("time")).doubleValue();
            return Optional.of(new Resumen(distanciaM, (int) Math.ceil(duracionMs / 60000)));
        } catch (Exception e) {
            log.debug("GraphHopper no disponible para calcular distancia: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Gratis con key propia (2500 req/día) — key cargada desde Configuración, varía por cliente. */
    @SuppressWarnings("unchecked")
    private Optional<Resumen> intentarOpenRouteService(double origenLat, double origenLng, double destinoLat, double destinoLng,
                                                         String tipoVehiculoId) {
        String key = configuracionService.getString("open_route_service_key", "");
        if (key.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> geojson = calcularRuta(origenLat, origenLng, destinoLat, destinoLng, tipoVehiculoId);
            List<Map<String, Object>> features = (List<Map<String, Object>>) geojson.get("features");
            Map<String, Object> propiedades = (Map<String, Object>) features.get(0).get("properties");
            Map<String, Object> resumen = (Map<String, Object>) propiedades.get("summary");
            double distanciaM = ((Number) resumen.get("distance")).doubleValue();
            double duracionSeg = ((Number) resumen.get("duration")).doubleValue();
            return Optional.of(new Resumen(distanciaM, (int) Math.ceil(duracionSeg / 60)));
        } catch (Exception e) {
            log.debug("OpenRouteService no disponible para calcular distancia: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
