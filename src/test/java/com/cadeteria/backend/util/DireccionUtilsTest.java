package com.cadeteria.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DireccionUtilsTest {

    @Test
    void normalizaAcentosCasingYEspacios() {
        assertEquals("av peron", DireccionUtils.normalizar("  Av.  Perón "));
    }

    @Test
    void noQuitaPalabrasGenericas() {
        // A propósito: la deduplicación real la hace la calle canónica del geocoder, no esta normalización.
        assertEquals("avenida peron", DireccionUtils.normalizar("Avenida Perón"));
    }

    @Test
    void nullSeNormalizaComoVacio() {
        assertEquals("", DireccionUtils.normalizar(null));
    }

    @Test
    void calculaElBloqueDeCuadraPorCentena() {
        assertEquals(1500, DireccionUtils.cuadra(1502));
        assertEquals(1500, DireccionUtils.cuadra(1599));
        assertEquals(1600, DireccionUtils.cuadra(1600));
        assertEquals(0, DireccionUtils.cuadra(42));
    }
}
