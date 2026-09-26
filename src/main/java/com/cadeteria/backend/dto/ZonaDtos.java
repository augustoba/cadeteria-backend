package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.common.Validaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public final class ZonaDtos {

    private ZonaDtos() {}

    /** Vértice de un polígono libre de zona (ronda 3, punto 27). */
    public record PuntoDto(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                           @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {}

    public record ZonaRequest(
            @NotBlank(message = "Falta el nombre de la zona.") @Size(max = 60, message = "El nombre de la zona puede tener hasta 60 caracteres.")
            String nombre,
            @NotNull(message = "Falta el centro de la zona.") @DecimalMin("-90") @DecimalMax("90") Double centroLat,
            @NotNull(message = "Falta el centro de la zona.") @DecimalMin("-180") @DecimalMax("180") Double centroLng,
            @NotNull(message = "Falta el radio.") @Min(value = 50, message = "El radio tiene que ser de al menos 50 m.")
            @Max(value = 100_000, message = "El radio puede ser de hasta 100 km.") Integer radioM,
            /** Precio sugerido al cargar un pedido en esta zona — null = sin sugerencia. */
            @PositiveOrZero(message = "La tarifa sugerida no puede ser negativa.") BigDecimal tarifaSugerida,
            /** null o menos de 3 puntos = la zona sigue siendo el círculo de siempre. */
            @Size(max = 500, message = "El polígono puede tener hasta 500 puntos.") List<@Valid PuntoDto> poligono
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
