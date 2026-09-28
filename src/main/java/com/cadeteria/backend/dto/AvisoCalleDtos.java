package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.AvisoCalle;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.Map;

/** "Avisos de la calle" (carril C, 2026-09-28). */
public final class AvisoCalleDtos {

    private AvisoCalleDtos() {}

    /** Tipo → texto corto. "Control" es una categoría más: la app no es para esquivar controles. */
    public static final Map<String, String> TIPOS = Map.of(
            "CONTROL", "Control",
            "CALLE_CORTADA", "Calle cortada",
            "ACCIDENTE", "Accidente",
            "PIQUETE", "Piquete");

    public record AvisoCalleRequest(
            @NotBlank(message = "Elegí qué pasa.")
            @Pattern(regexp = "CONTROL|CALLE_CORTADA|ACCIDENTE|PIQUETE", message = "Ese tipo de aviso no existe.") String tipo,
            @NotNull(message = "Falta tu ubicación.") @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull(message = "Falta tu ubicación.") @DecimalMin("-180") @DecimalMax("180") Double lng,
            @PositiveOrZero Float precision) {}

    /**
     * Lo que ven los cadetes y el panel. cadeteNombre (quién avisó) va solo para el panel: a los
     * cadetes les llega null.
     */
    public record AvisoCalleResponse(String id, String tipo, String tipoTexto, Double lat, Double lng, String calle,
                                     String cadeteId, String cadeteNombre, Instant creadoEn, Instant venceEn) {

        public static AvisoCalleResponse paraPanel(AvisoCalle a) {
            String nombre = a.getCadete() == null ? null
                    : ((a.getCadete().getNombre() == null ? "" : a.getCadete().getNombre()) + " "
                    + (a.getCadete().getApellido() == null ? "" : a.getCadete().getApellido())).trim();
            return new AvisoCalleResponse(a.getId(), a.getTipo(), TIPOS.getOrDefault(a.getTipo(), a.getTipo()),
                    a.getLat(), a.getLng(), a.getCalle(), a.getCadete() == null ? null : a.getCadete().getId(), nombre,
                    a.getCreadoEn(), a.getVenceEn());
        }

        public static AvisoCalleResponse paraCadete(AvisoCalle a) {
            return new AvisoCalleResponse(a.getId(), a.getTipo(), TIPOS.getOrDefault(a.getTipo(), a.getTipo()),
                    a.getLat(), a.getLng(), a.getCalle(), null, null, a.getCreadoEn(), a.getVenceEn());
        }
    }
}
