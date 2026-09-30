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
            @PositiveOrZero Float precision,
            /**
             * Calle y altura según el Geocoder del teléfono (2026-09-29): opcionales. Con ellas el aviso
             * dice lo mismo que el panel; sin ellas (APK vieja o sin Geocoder) se usa el mapa.
             */
            @jakarta.validation.constraints.Size(max = 120) String calle,
            @PositiveOrZero Integer altura) {}

    /** "¿Sigue ahí?" (segunda etapa, 2026-09-29). */
    public record VotoRequest(
            @NotBlank(message = "Falta la respuesta.")
            @Pattern(regexp = "SIGUE|YA_NO_ESTA", message = "La respuesta tiene que ser SIGUE o YA_NO_ESTA.") String voto) {}

    /**
     * Ficha del cadete: cuántos avisos mandó, a cuántos otro cadete les contestó "ya no está" y cuántos
     * se bajaron (dos "ya no está" de cadetes distintos). Sirve para ver quién avisa cosas que no son.
     */
    public record ResumenAvisosCadete(long avisados, long marcadosYaNoEsta, long bajados) {}

    /**
     * Lo que ven los cadetes y el panel. cadeteNombre (quién avisó) va solo para el panel: a los
     * cadetes les llega null. mio (2026-09-29): si lo avisó el cadete que lo pide — la app no le pregunta
     * "¿Sigue ahí?" por los suyos; null en el panel y en lo que llega en vivo (que nunca va al autor).
     */
    public record AvisoCalleResponse(String id, String tipo, String tipoTexto, Double lat, Double lng, String calle,
                                     String cadeteId, String cadeteNombre, Instant creadoEn, Instant venceEn,
                                     Boolean mio) {

        public static AvisoCalleResponse paraPanel(AvisoCalle a) {
            String nombre = a.getCadete() == null ? null
                    : ((a.getCadete().getNombre() == null ? "" : a.getCadete().getNombre()) + " "
                    + (a.getCadete().getApellido() == null ? "" : a.getCadete().getApellido())).trim();
            return new AvisoCalleResponse(a.getId(), a.getTipo(), TIPOS.getOrDefault(a.getTipo(), a.getTipo()),
                    a.getLat(), a.getLng(), a.getCalle(), a.getCadete() == null ? null : a.getCadete().getId(), nombre,
                    a.getCreadoEn(), a.getVenceEn(), null);
        }

        public static AvisoCalleResponse paraCadete(AvisoCalle a) {
            return new AvisoCalleResponse(a.getId(), a.getTipo(), TIPOS.getOrDefault(a.getTipo(), a.getTipo()),
                    a.getLat(), a.getLng(), a.getCalle(), null, null, a.getCreadoEn(), a.getVenceEn(), null);
        }

        /** Para el cadete que lo pide: con si lo avisó él (sin decirle a nadie quién lo avisó). */
        public static AvisoCalleResponse paraCadete(AvisoCalle a, String cadeteUsername) {
            boolean mio = a.getCadete() != null && cadeteUsername != null && cadeteUsername.equals(a.getCadete().getUsername());
            return new AvisoCalleResponse(a.getId(), a.getTipo(), TIPOS.getOrDefault(a.getTipo(), a.getTipo()),
                    a.getLat(), a.getLng(), a.getCalle(), null, null, a.getCreadoEn(), a.getVenceEn(), mio);
        }
    }
}
