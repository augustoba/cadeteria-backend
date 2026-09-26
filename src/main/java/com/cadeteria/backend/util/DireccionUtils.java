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

    private static final java.util.Map<String, String> ABREVIATURAS = java.util.Map.ofEntries(
            java.util.Map.entry("av", "Avenida"), java.util.Map.entry("avda", "Avenida"),
            java.util.Map.entry("gral", "General"), java.util.Map.entry("pte", "Presidente"),
            java.util.Map.entry("pres", "Presidente"), java.util.Map.entry("dr", "Doctor"),
            java.util.Map.entry("pje", "Pasaje"), java.util.Map.entry("psje", "Pasaje"),
            java.util.Map.entry("bv", "Bulevar"), java.util.Map.entry("blvd", "Bulevar"),
            java.util.Map.entry("cnel", "Coronel"), java.util.Map.entry("tte", "Teniente"),
            java.util.Map.entry("ing", "Ingeniero"), java.util.Map.entry("sgto", "Sargento"));

    /**
     * "Av. Gral. Paz" -> "Avenida General Paz" (2026-09-26). El Geocoder del teléfono (datos de
     * Google) abrevia y OpenStreetMap escribe completo: sin esto la misma calle quedaba como dos
     * calles distintas en la cache.
     */
    public static String expandirAbreviaturas(String calle) {
        if (calle == null) return null;
        StringBuilder out = new StringBuilder();
        for (String palabra : calle.trim().split("\\s+")) {
            String clave = palabra.replace(".", "").toLowerCase(java.util.Locale.ROOT);
            if (out.length() > 0) out.append(' ');
            out.append(ABREVIATURAS.getOrDefault(clave, palabra));
        }
        return out.toString();
    }

    /** Bloque de cuadra (centena) de una altura: 1502 -> 1500 (ver spec §5.1). */
    public static int cuadra(int numero) {
        return (numero / 100) * 100;
    }
}
