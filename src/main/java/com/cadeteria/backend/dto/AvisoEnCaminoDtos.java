package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Editor del aviso "en camino" por WhatsApp en Configuración (3n, 2026-09-28). */
public final class AvisoEnCaminoDtos {

    private AvisoEnCaminoDtos() {}

    /**
     * texto: el que se usa hoy (el guardado, o el original si no hay). personalizado: si hay uno guardado.
     * editadoPor/editadoEn: la última edición desde el panel (null si nunca se editó).
     */
    public record AvisoEnCaminoResponse(String texto, String textoOriginal, boolean personalizado,
                                        String editadoPor, Instant editadoEn) {}

    /** Vacío = volver al texto original. 500 es el largo de la columna de configuración. */
    public record AvisoEnCaminoRequest(@NotNull @Size(max = 500) String texto) {}

    public record VistaPreviaRequest(@NotNull @Size(max = 500) String texto) {}

    /** El aviso armado con un pedido real; pedidoNumero dice con cuál. */
    public record VistaPreviaResponse(String texto, Long pedidoNumero) {}
}
