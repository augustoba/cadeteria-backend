package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Zona;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public final class ZonaDtos {

    private ZonaDtos() {}

    /** Vértice de un polígono libre de zona (ronda 3, punto 27). */
    public record PuntoDto(Double lat, Double lng) {}

    public record ZonaRequest(
            @NotBlank String nombre,
            @NotNull Double centroLat,
            @NotNull Double centroLng,
            @NotNull Integer radioM,
            /** Precio sugerido al cargar un pedido en esta zona — null = sin sugerencia. */
            BigDecimal tarifaSugerida,
            /** null o menos de 3 puntos = la zona sigue siendo el círculo de siempre. */
            List<PuntoDto> poligono
    ) {}

    public record ZonaResponse(
            String id, String nombre, Double centroLat, Double centroLng, Integer radioM,
            BigDecimal tarifaSugerida, List<LookupResponse> zonasAledanas, List<PuntoDto> poligono,
            /** Desactivada temporalmente sin borrarla (ronda 10, punto 99). */
            boolean activo
    ) {
        public static ZonaResponse from(Zona z) {
            List<LookupResponse> aledanas = z.getZonasAledanas().stream()
                    .map(a -> new LookupResponse(a.getId(), a.getNombre()))
                    .collect(Collectors.toList());
            List<PuntoDto> poligono = z.getPuntosPoligono().stream()
                    .map(p -> new PuntoDto(p[0], p[1]))
                    .collect(Collectors.toList());
            return new ZonaResponse(z.getId(), z.getNombre(), z.getCentroLat(), z.getCentroLng(),
                    z.getRadioM(), z.getTarifaSugerida(), aledanas, poligono, z.isActivo());
        }
    }

    public record AdyacenteRequest(@NotBlank String zonaVecinaId) {}
}
