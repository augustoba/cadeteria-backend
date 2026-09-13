package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.PagoSemanal;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PagoSemanalDtos {

    private PagoSemanalDtos() {}

    public record PagoRequest(
            @NotNull LocalDate semanaInicio,
            boolean pagado
    ) {}

    public record PagoResponse(
            String id, LocalDate semanaInicio, boolean pagado, Instant registradoEn, String registradoPorUsername
    ) {
        public static PagoResponse from(PagoSemanal p) {
            return new PagoResponse(p.getId(), p.getSemanaInicio(), p.isPagado(), p.getRegistradoEn(),
                    p.getRegistradoPor() == null ? null : p.getRegistradoPor().getUsername());
        }
    }

    /** Para que el cadete vea en la app cuánto facturó esta semana y si ya se le pagó. */
    public record MiSemanaResponse(LocalDate semanaInicio, BigDecimal facturado, int viajesFinalizados, boolean pagado) {}

    /** Cadete sin pago registrado para la última semana cerrada (ronda 6, punto 35). */
    public record PendienteResponse(String cadeteId, String nombre, String apellido) {
        public static PendienteResponse from(com.cadeteria.backend.model.Cadete c) {
            return new PendienteResponse(c.getId(), c.getNombre(), c.getApellido());
        }
    }
}
