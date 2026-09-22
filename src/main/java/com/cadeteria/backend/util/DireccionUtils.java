package com.cadeteria.backend.util;

import java.text.Normalizer;

/**
 * Normalización de texto para la cache de geocoding (ver documentacion/spec-geocoding-cache.md
 * §5.2, Capa 1): minúsculas, sin acentos, sin puntuación, espacios colapsados. A propósito NO se
 * quitan palabras genéricas ("av", "gral", etc.) — la deduplicación real de variantes ("av perón"
 * vs "avenida perón") la hace la calle canónica que devuelve el geocoder (Capa 2, autoritativa);
 * esta normalización solo evita que "Av. Perón", "av perón" y "AV PERON" sean claves de cache
 * distintas por casing, acentos o el punto de la abreviatura.
 */
public final class DireccionUtils {

    private DireccionUtils() {
    }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String sinPuntuacion = sinAcentos.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        return sinPuntuacion.trim().replaceAll("\\s+", " ");
    }

    /** Bloque de cuadra (centena) de una altura: 1502 -> 1500 (ver spec §5.1). */
    public static int cuadra(int numero) {
        return (numero / 100) * 100;
    }
}
