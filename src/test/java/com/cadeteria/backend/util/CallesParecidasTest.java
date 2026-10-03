package com.cadeteria.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallesParecidasTest {

    @Test
    void erroresDeDedo() {
        assertEquals(1, CallesParecidas.puntaje("belgarno", "belgrano"));
        assertEquals(1, CallesParecidas.puntaje("suipcha", "suipacha"));
        // De oído son iguales (s/z), así que no cuenta como error; con una letra de menos, uno.
        assertEquals(0, CallesParecidas.puntaje("crisostomo alvares", "crisostomo alvarez"));
        assertEquals(1, CallesParecidas.puntaje("crisotomo albarex", "crisostomo alvarez"));
    }

    @Test
    void erroresDeOido() {
        assertEquals(0, CallesParecidas.puntaje("bolibar", "bolivar"));
        assertEquals(0, CallesParecidas.puntaje("balcarse", "balcarce"));
        assertEquals(0, CallesParecidas.puntaje("irigoyen", "yrigoyen"));
        assertEquals(0, CallesParecidas.puntaje("abellaneda", "avellaneda"));
        assertEquals(0, CallesParecidas.puntaje("ipolito irigoyen", "hipolito yrigoyen"));
    }

    @Test
    void lasPalabrasGenericasNoCuentan() {
        assertEquals(0, CallesParecidas.puntaje("av mate de luna", "avenida mate de luna"));
        assertEquals(0, CallesParecidas.puntaje("gral paz", "general paz"));
        assertEquals(CallesParecidas.NO, CallesParecidas.puntaje("avenida", "avenida belgrano"));
    }

    @Test
    void palabrasSueltasYEnOtroOrden() {
        assertEquals(0.5, CallesParecidas.puntaje("roca julio", "julio argentino roca"));
        assertEquals(0.5, CallesParecidas.puntaje("suipacha", "batalla de suipacha"));
        assertEquals(1.5, CallesParecidas.puntaje("suipcha", "batalla de suipacha"));
    }

    @Test
    void otraCalleNoSeParece() {
        assertEquals(CallesParecidas.NO, CallesParecidas.puntaje("roca", "boca"));
        assertEquals(CallesParecidas.NO, CallesParecidas.puntaje("san juan", "san martin"));
        assertEquals(CallesParecidas.NO, CallesParecidas.puntaje("colombia", "mate de luna"));
        // Una palabra corta sola no alcanza para ofrecer todas las calles que la llevan.
        assertEquals(CallesParecidas.NO, CallesParecidas.puntaje("paz", "marcos paz"));
        assertTrue(CallesParecidas.puntaje("cordoba", "cordoba") == 0);
    }

    @Test
    void laDistanciaCuentaLaInversionComoUnSoloError() {
        assertEquals(1, CallesParecidas.distancia("belgarno", "belgrano", 2));
        assertEquals(3, CallesParecidas.distancia("abcdef", "uvwxyz", 2));
    }
}
