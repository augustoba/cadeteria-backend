package com.cadeteria.backend.util;

/**
 * Normaliza teléfonos argentinos para que "011 15 5551234", "+54 9 11 5551234" y
 * "5491155551234" no generen registros/clientes duplicados solo por el formato en que
 * se tipearon. Pensado para el área de San Miguel de Tucumán (código 381): solo ahí se
 * reconoce y saca el infijo móvil viejo "15" (ej. "0381-15-551234"), porque fuera de esa
 * zona no hay forma confiable de saber dónde termina el código de área sin una tabla
 * completa de códigos de Argentina — no vale la pena esa complejidad para un negocio que
 * opera solo en Tucumán.
 */
public final class TelefonoUtils {

    private static final String CODIGO_AREA_TUCUMAN = "381";

    private TelefonoUtils() {
    }

    public static String normalizar(String telefono) {
        if (telefono == null) return null;
        String d = telefono.replaceAll("\\D", "");
        if (d.isEmpty()) return telefono.trim();

        if (d.startsWith("00")) d = d.substring(2);
        if (d.length() > 10 && d.startsWith("54")) d = d.substring(2);
        if (d.length() > 10 && d.startsWith("9")) d = d.substring(1);
        if (d.startsWith("0")) d = d.substring(1);
        if (d.startsWith(CODIGO_AREA_TUCUMAN + "15")) {
            d = CODIGO_AREA_TUCUMAN + d.substring(5);
        }
        return d;
    }
}
