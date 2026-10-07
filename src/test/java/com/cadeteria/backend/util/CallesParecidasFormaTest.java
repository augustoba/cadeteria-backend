package com.cadeteria.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** La "forma" de un nombre de calle (2026-10-07): casos reales de las uniones a mano del 6 de octubre. */
class CallesParecidasFormaTest {

    @Test
    void avenidaPasajeYCalleNoCambianLaForma() {
        assertEquals(CallesParecidas.forma("camino del peru"), CallesParecidas.forma("avenida camino del peru"));
        assertEquals(CallesParecidas.forma("chubut"), CallesParecidas.forma("pasaje chubut"));
        assertEquals(CallesParecidas.forma("lucio victor mansilla"), CallesParecidas.forma("calle lucio victor mansilla"));
    }

    @Test
    void elOrdenYLasInicialesSueltasNoCambianLaForma() {
        assertEquals(CallesParecidas.forma("manuel alberti"), CallesParecidas.forma("alberti manuel m"));
        assertEquals(CallesParecidas.forma("lucas cordoba"), CallesParecidas.forma("lucas a cordoba"));
    }

    @Test
    void seComparaDeOido() {
        assertEquals(CallesParecidas.forma("payro"), CallesParecidas.forma("pasaje pairo"));
    }

    @Test
    void unNumeroSueltoSiDistingue() {
        assertNotEquals(CallesParecidas.forma("diagonal 1"), CallesParecidas.forma("diagonal 2"));
    }

    @Test
    void unaPalabraDeMasEsOtraForma() {
        assertNotEquals(CallesParecidas.forma("alsina"), CallesParecidas.forma("adolfo alsina"));
    }

    @Test
    void sinPalabrasPropiasOConUnaSolaMuyCortaNoHayForma() {
        assertEquals("", CallesParecidas.forma("pasaje"));
        assertEquals("", CallesParecidas.forma("avenida paz"));
        assertEquals("", CallesParecidas.forma(null));
    }
}
