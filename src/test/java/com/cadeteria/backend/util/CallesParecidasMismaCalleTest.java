package com.cadeteria.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Si el nombre que trae el teléfono del cadete puede ser el de una calle del dibujo (2026-10-21).
 * Casos reales de producción: el Geocoder de Android (datos de Google) escribe "apellido nombre",
 * con iniciales o con el tipo de calle de más.
 */
class CallesParecidasMismaCalleTest {

    @Test
    void elNombreInvertidoOConElNombreDePilaDeMasEsLaMisma() {
        assertTrue(CallesParecidas.mismaCalle("avenida mitre bartolome", "avenida mitre"));
        assertTrue(CallesParecidas.mismaCalle("esquiu mamerto fray", "fray mamerto esquiu"));
        assertTrue(CallesParecidas.mismaCalle("pueyrredon juan martin de", "pueyrredon"));
        assertTrue(CallesParecidas.mismaCalle("avenida mate de luna fernando", "avenida mate de luna"));
    }

    @Test
    void lasInicialesValenPorElNombre() {
        assertTrue(CallesParecidas.mismaCalle("j hernandez", "jose hernandez"));
        assertTrue(CallesParecidas.mismaCalle("l m drago", "luis maria drago"));
        assertFalse(CallesParecidas.mismaCalle("j hernandez", "rafael hernandez"));
    }

    @Test
    void seToleranErroresDeDedoYDeOido() {
        assertTrue(CallesParecidas.mismaCalle("posse wenceslo", "wenceslao posse"));
        assertTrue(CallesParecidas.mismaCalle("24 de septiembre", "24 de setiembre"));
    }

    @Test
    void losNumerosTienenQueSerLosMismos() {
        assertFalse(CallesParecidas.mismaCalle("calle 12", "calle 21"));
        assertFalse(CallesParecidas.mismaCalle("25 de mayo", "24 de mayo"));
    }

    @Test
    void pasajeBulevarYDiagonalNoSeMezclanConLaCalle() {
        assertFalse(CallesParecidas.mismaCalle("pasaje sarmiento", "sarmiento"));
        assertFalse(CallesParecidas.mismaCalle("9 de julio", "boulevard 9 de julio"));
        assertTrue(CallesParecidas.mismaCalle("diag anselmo rojo", "diagonal anselmo rojo"));
        assertTrue(CallesParecidas.mismaCalle("pje formosa", "pasaje formosa"));
    }

    @Test
    void sinUnaPalabraQueDistingaNoSeDecide() {
        assertFalse(CallesParecidas.mismaCalle("e de las", "de las americas"));
        assertFalse(CallesParecidas.mismaCalle("paz", "general paz"));
        assertFalse(CallesParecidas.mismaCalle("1", "1 de mayo"));
    }

    @Test
    void otraPalabraEsOtraCalle() {
        assertFalse(CallesParecidas.mismaCalle("rojas ricardo", "isaac rojas"));
        assertFalse(CallesParecidas.mismaCalle("santa maria de montserrat", "san martin"));
    }
}
