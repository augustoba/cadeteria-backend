package com.cadeteria.backend.service;

import com.cadeteria.backend.service.GeocodingProxyService.GeoAddress;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HERE como segundo intento del buscador (2026-10-05): cómo se lee cada resultado. */
class GeocodingProxyServiceHereTest {

    @Test
    void alturaUbicadaConBuenPuntajeEsExacta() {
        GeoAddress a = GeocodingProxyService.desdeHere(
                item("houseNumber", "Corrientes", "480", "San Miguel de Tucumán", "Tucumán", 0.97), 480);

        assertEquals("Corrientes 480, San Miguel de Tucumán", a.label());
        assertEquals("Corrientes", a.street());
        assertEquals(480, a.number());
        assertFalse(a.approximate());
        assertEquals(GeocodingProxyService.PROVEEDOR_HERE, a.proveedor());
    }

    @Test
    void puntajeBajoQuedaComoAproximada() {
        // "Lamadrid 3150" devolvió "Madrid 3202" con 0,82 en la medición: no se da por buena.
        GeoAddress a = GeocodingProxyService.desdeHere(
                item("houseNumber", "Madrid", "3202", "San Miguel de Tucumán", "Tucumán", 0.82), 3150);

        assertTrue(a.approximate());
    }

    @Test
    void soloLaCalleEsAproximada() {
        GeoAddress a = GeocodingProxyService.desdeHere(item("street", "Corrientes", null, "San Miguel de Tucumán", "Tucumán", 0.99), 480);

        assertTrue(a.approximate());
    }

    @Test
    void loQueNoEsDeTucumanSeDescarta() {
        assertNull(GeocodingProxyService.desdeHere(item("houseNumber", "Corrientes", "480", "Buenos Aires", "Ciudad Autónoma de Buenos Aires", 0.99), 480));
    }

    private static Map<String, Object> item(String tipo, String calle, String altura, String ciudad, String provincia, double puntaje) {
        Map<String, Object> direccion = new HashMap<>();
        direccion.put("street", calle);
        direccion.put("houseNumber", altura);
        direccion.put("city", ciudad);
        direccion.put("state", provincia);
        Map<String, Object> item = new HashMap<>();
        item.put("resultType", tipo);
        item.put("address", direccion);
        item.put("position", Map.of("lat", -26.82305, "lng", -65.20242));
        item.put("scoring", Map.of("queryScore", puntaje));
        return item;
    }
}
