package com.cadeteria.backend.common;

import java.time.Instant;
import java.util.Map;

/**
 * Cuerpo de error uniforme para todas las respuestas 4xx/5xx.
 * <ul>
 *   <li>{@code message}: texto para mostrarle a la persona, en castellano.</li>
 *   <li>{@code codigo}: identificador fijo del tipo de error (ej. {@code VALIDACION}, {@code CONFLICTO}) para
 *       que el front y la app decidan qué hacer sin comparar textos (2026-09-26).</li>
 *   <li>{@code fieldErrors}: campo → problema, solo en errores de validación.</li>
 *   <li>{@code referencia}: solo en los 500 — el mismo código queda en el log del servidor.</li>
 * </ul>
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String codigo,
        String message,
        Map<String, String> fieldErrors,
        String referencia
) {
    public static ApiError of(int status, String error, String codigo, String message, Map<String, String> fieldErrors,
                              String referencia) {
        return new ApiError(Instant.now(), status, error, codigo, message, fieldErrors, referencia);
    }

    public static ApiError of(int status, String error, String codigo, String message) {
        return of(status, error, codigo, message, null, null);
    }
}
