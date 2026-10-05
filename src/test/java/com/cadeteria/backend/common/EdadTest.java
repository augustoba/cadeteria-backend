package com.cadeteria.backend.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Fecha de nacimiento del cadete (2026-10-05): la cuenta de los 18 años. */
class EdadTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 5);

    @Test
    void cumpleLos18ElMismoDia() {
        assertEquals(18, Edad.de(LocalDate.of(2008, 10, 5), HOY));
        assertDoesNotThrow(() -> Edad.exigirMayor(LocalDate.of(2008, 10, 5), HOY));
    }

    @Test
    void unDiaAntesDeCumplirlosTodaviaEsMenor() {
        var e = assertThrows(BadRequestException.class, () -> Edad.exigirMayor(LocalDate.of(2008, 10, 6), HOY));
        assertEquals(Edad.MSJ_MENOR, e.getMessage());
    }

    @Test
    void sinFechaOConUnaFechaQueNoPuedeSer() {
        assertEquals(Edad.MSJ_FALTA, assertThrows(BadRequestException.class, () -> Edad.exigirMayor(null, HOY)).getMessage());
        assertEquals(Edad.MSJ_INVALIDA,
                assertThrows(BadRequestException.class, () -> Edad.exigirMayor(LocalDate.of(2027, 1, 1), HOY)).getMessage());
        assertEquals(Edad.MSJ_INVALIDA,
                assertThrows(BadRequestException.class, () -> Edad.exigirMayor(LocalDate.of(1900, 1, 1), HOY)).getMessage());
    }
}
