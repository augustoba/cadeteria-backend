package com.cadeteria.backend.common;

import java.time.Instant;
import java.util.Map;

/** Cuerpo de error uniforme para todas las respuestas 4xx/5xx. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
