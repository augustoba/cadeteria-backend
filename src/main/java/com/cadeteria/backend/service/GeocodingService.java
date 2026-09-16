package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.repository.ZonaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Resuelve la zona de un punto (lat/lng) por distancia geometrica al centro de cada
 * zona (formula de Haversine), comparada contra el radio_m configurado de cada una.
 * <p>
 * El diseno original (diseno-tecnico.md sección 5.1) hablaba de reverse geocoding
 * contra un servicio externo (Nominatim/OSM) para obtener la zona. Como cada Zona ya
 * guarda su centro y radio (para el propio ABM del admin), calcularlo localmente es
 * mas simple, no depende de un servicio externo ni de su latencia/limite de uso, y
 * da el mismo resultado practico. Si ningun circulo contiene el punto, no se resuelve
 * zona automaticamente y el admin puede fijarla a mano (fallback ya previsto en la spec).
 */
@Service
@Transactional(readOnly = true)
public class GeocodingService {

    private static final double RADIO_TIERRA_M = 6_371_000;

    private final ZonaRepository zonaRepo;

    public GeocodingService(ZonaRepository zonaRepo) {
        this.zonaRepo = zonaRepo;
    }

    /**
     * Si el punto cae dentro de varias zonas a la vez (ej. zonas concéntricas — "4
     * avenidas" adentro de una zona más grande que las rodea), gana la de área más chica,
     * no la primera que encuentre ni la de centro más cercano (con centros iguales o
     * parecidos, "más cercano" queda indefinido entre zonas concéntricas).
     */
    public Optional<Zona> resolverZona(double lat, double lng) {
        List<Zona> zonas = zonaRepo.findAll();
        Zona mejor = null;
        double mejorArea = Double.MAX_VALUE;
        for (Zona z : zonas) {
            if (!z.isActivo()) continue;
            double distancia = distanciaMetros(lat, lng, z.getCentroLat(), z.getCentroLng());
            if (!z.contienePunto(lat, lng, distancia)) continue;
            double area = z.aproxArea();
            if (mejor == null || area < mejorArea) {
                mejor = z;
                mejorArea = area;
            }
        }
        return Optional.ofNullable(mejor);
    }

    public static double distanciaKm(double lat1, double lng1, double lat2, double lng2) {
        return distanciaMetros(lat1, lng1, lat2, lng2) / 1000.0;
    }

    private static double distanciaMetros(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RADIO_TIERRA_M * c;
    }
}
