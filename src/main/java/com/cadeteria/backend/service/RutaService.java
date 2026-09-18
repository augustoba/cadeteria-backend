package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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
 * Cada uno de los dos con key admite VARIAS keys (varias cuentas gratuitas propias) separadas
 * por coma o salto de línea en su mismo campo de Configuración — {@link ApiKeyPoolService} va
 * rotando a la siguiente apenas una devuelve "sin cupo" (HTTP 429/403), sin esperar a que el
 * dueño se dé cuenta y cargue otra a mano. El semáforo de cada key se ve en Configuración.
 * <p>
 * Si las tres fallan (o ninguna key está cargada, o todas sin cupo), quien llama cae a la
 * línea recta — ninguna de estas fuentes bloquea el flujo si no responde.
 */
@Service
public class RutaService {

    private static final Logger log = LoggerFactory.getLogger(RutaService.class);
    private static final String OSRM_URL = "https://router.project-osrm.org";
    private static final String GRAPHHOPPER_URL = "https://graphhopper.com/api/1/route";
    public static final String PROVEEDOR_GRAPHHOPPER = "graphhopper";
    public static final String PROVEEDOR_ORS = "openrouteservice";
    public static final String CONFIG_GRAPHHOPPER_KEYS = "graphhopper_key";
    public static final String CONFIG_ORS_KEYS = "open_route_service_key";
    /** Tope de keys a probar por proveedor en una misma consulta (nunca deberían ser tantas, es solo un resguardo). */
    private static final int MAX_INTENTOS_POR_PROVEEDOR = 10;

    private final AppProperties props;
    private final ConfiguracionService configuracionService;
    private final ApiKeyPoolService apiKeyPool;
    private final RestClient restClient = RestClient.create();

    /** Distancia/tiempo estimado, en metros/minutos — resumen liviano de {@link #calcularRuta}. */
    public record Resumen(double distanciaM, int duracionMin) {}

    public RutaService(AppProperties props, ConfiguracionService configuracionService, ApiKeyPoolService apiKeyPool) {
        this.props = props;
        this.configuracionService = configuracionService;
        this.apiKeyPool = apiKeyPool;
    }

    /**
     * Ruta completa (geometría + resumen) para mostrarle el trayecto sugerido al cadete al
     * aceptar un viaje (spec 5.7, estilo Uber) — a diferencia de {@link #resumenSiDisponible}
     * no tiene fallback entre proveedores porque el front espera el formato GeoJSON propio
     * de OpenRouteService; requiere al menos una API key de ORS configurada y con cupo.
     * @param tipoVehiculoId "MOTO" o "BICI" — define el perfil de ruteo.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcularRuta(double origenLat, double origenLng,
                                             double destinoLat, double destinoLng, String tipoVehiculoId) {
        String perfil = "BICI".equals(tipoVehiculoId) ? "cycling-regular" : "driving-car";
        // ORS espera las coordenadas como [lng, lat], al reves que el resto del sistema.
        Map<String, Object> body = Map.of(
                "coordinates", List.of(
                        List.of(origenLng, origenLat),
                        List.of(destinoLng, destinoLat)
                )
        );
        String orsUrl = configuracionService.getString("open_route_service_url", props.getMaps().getOpenRouteServiceUrl());

        for (int intento = 0; intento < MAX_INTENTOS_POR_PROVEEDOR; intento++) {
            String key = apiKeyPool.siguienteClave(PROVEEDOR_ORS, CONFIG_ORS_KEYS);
            if (key == null) {
                throw new BadRequestException("No hay ninguna API key de OpenRouteService con cupo disponible en Configuración.");
            }
            try {
                apiKeyPool.registrarUso(PROVEEDOR_ORS, CONFIG_ORS_KEYS, key);
                ResponseEntity<Map> resp = restClient.post()
                        .uri(orsUrl + "/v2/directions/" + perfil + "/geojson")
                        .header("Authorization", key)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(body)
                        .retrieve()
                        .toEntity(Map.class);
                actualizarRestanteOrs(key, resp.getHeaders());
                return resp.getBody();
            } catch (HttpClientErrorException e) {
                if (esErrorDeCupo(e)) {
                    apiKeyPool.marcarAgotada(PROVEEDOR_ORS, CONFIG_ORS_KEYS, key);
                    continue;
                }
                throw e;
            }
        }
        throw new BadRequestException("No hay ninguna API key de OpenRouteService con cupo disponible en Configuración.");
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
                .or(() -> intentarOpenRouteServiceSilencioso(origenLat, origenLng, destinoLat, destinoLng, tipoVehiculoId));
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

    /** Gratis con key propia (500 req/día) — admite varias keys/cuentas, rotando a la siguiente si una se queda sin cupo. */
    @SuppressWarnings("unchecked")
    private Optional<Resumen> intentarGraphHopper(double origenLat, double origenLng, double destinoLat, double destinoLng,
                                                    String tipoVehiculoId) {
        for (int intento = 0; intento < MAX_INTENTOS_POR_PROVEEDOR; intento++) {
            String key = apiKeyPool.siguienteClave(PROVEEDOR_GRAPHHOPPER, CONFIG_GRAPHHOPPER_KEYS);
            if (key == null) return Optional.empty();
            try {
                apiKeyPool.registrarUso(PROVEEDOR_GRAPHHOPPER, CONFIG_GRAPHHOPPER_KEYS, key);
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
            } catch (HttpClientErrorException e) {
                if (esErrorDeCupo(e)) {
                    apiKeyPool.marcarAgotada(PROVEEDOR_GRAPHHOPPER, CONFIG_GRAPHHOPPER_KEYS, key);
                    continue;
                }
                log.debug("GraphHopper no disponible para calcular distancia: {}", e.getMessage());
                return Optional.empty();
            } catch (Exception e) {
                log.debug("GraphHopper no disponible para calcular distancia: {}", e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Igual que {@link #calcularRuta} pero sin tirar excepción — acá un 429/sin key solo significa "probar otra fuente". */
    @SuppressWarnings("unchecked")
    private Optional<Resumen> intentarOpenRouteServiceSilencioso(double origenLat, double origenLng, double destinoLat, double destinoLng,
                                                                    String tipoVehiculoId) {
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

    /** 429 = cupo agotado; algunos free tier devuelven 403 cuando se vence el plan gratuito — tratamos igual, se prueba la siguiente key. */
    private boolean esErrorDeCupo(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        return status == 429 || status == 403;
    }

    /** OpenRouteService informa el cupo diario restante en este header — si no viene, no rompe nada, solo no se muestra "quedan: N". */
    private void actualizarRestanteOrs(String key, HttpHeaders headers) {
        String restante = headers.getFirst("X-Ratelimit-Remaining");
        if (restante == null) return;
        try {
            apiKeyPool.actualizarRestanteInformado(PROVEEDOR_ORS, CONFIG_ORS_KEYS, key, Integer.parseInt(restante));
        } catch (NumberFormatException ignored) {
            // header con formato inesperado, no es crítico
        }
    }
}
