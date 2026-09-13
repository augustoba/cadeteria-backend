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
 * Proxy a OpenRouteService (spec 5.7): ruta sugerida al cadete al aceptar un viaje,
 * estilo Uber. Gratuito hasta 2500 requests/dia, muy por encima del volumen esperado.
 */
@Service
public class RutaService {

    private static final Logger log = LoggerFactory.getLogger(RutaService.class);

    private final AppProperties props;
    private final RestClient restClient = RestClient.create();

    /** Distancia/tiempo estimado, en metros/minutos — resumen liviano de {@link #calcularRuta}. */
    public record Resumen(double distanciaM, int duracionMin) {}

    public RutaService(AppProperties props) {
        this.props = props;
    }

    /** @param tipoVehiculoId "MOTO" o "BICI" — define el perfil de ruteo. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> calcularRuta(double origenLat, double origenLng,
                                             double destinoLat, double destinoLng, String tipoVehiculoId) {
        if (props.getMaps().getOpenRouteServiceKey().isBlank()) {
            throw new BadRequestException("app.maps.open-route-service-key no configurada.");
        }
        String perfil = "BICI".equals(tipoVehiculoId) ? "cycling-regular" : "driving-car";
        // ORS espera las coordenadas como [lng, lat], al reves que el resto del sistema.
        Map<String, Object> body = Map.of(
                "coordinates", List.of(
                        List.of(origenLng, origenLat),
                        List.of(destinoLng, destinoLat)
                )
        );
        return restClient.post()
                .uri(props.getMaps().getOpenRouteServiceUrl() + "/v2/directions/" + perfil + "/geojson")
                .header("Authorization", props.getMaps().getOpenRouteServiceKey())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    /**
     * Para la página pública de seguimiento (ronda 4, punto 30, "ETA"): no rompe nada si
     * ORS no está configurado o falla — simplemente no hay ETA para mostrar, la misma
     * idea de degradar sin romper que FcmService/EmailService.
     */
    @SuppressWarnings("unchecked")
    public Optional<Resumen> resumenSiDisponible(double origenLat, double origenLng,
                                                  double destinoLat, double destinoLng, String tipoVehiculoId) {
        if (props.getMaps().getOpenRouteServiceKey().isBlank()) {
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
            log.warn("No se pudo calcular el resumen de ruta para el seguimiento publico: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
