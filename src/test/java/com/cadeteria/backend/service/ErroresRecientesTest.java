package com.cadeteria.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pantalla "Sistema" (2026-10-05): la ventana de los últimos errores del backend. */
class ErroresRecientesTest {

    private final ErroresRecientes errores = new ErroresRecientes();

    @AfterEach
    void desconectar() {
        errores.desconectar();
    }

    @Test
    void juntaLosErroresDelRegistroYNoLosAvisos() {
        errores.conectar();
        var log = LoggerFactory.getLogger("com.cadeteria.backend.Prueba");

        log.warn("esto es un aviso");
        log.error("no se pudo guardar el pedido {}", 42, new IllegalStateException("sin conexión"));

        assertEquals(1, errores.total());
        var e = errores.ultimos().get(0);
        assertEquals("Prueba", e.origen());
        assertEquals("no se pudo guardar el pedido 42", e.mensaje());
        assertEquals("java.lang.IllegalStateException: sin conexión", e.excepcion());
    }

    @Test
    void guardaSoloLosUltimosYCuentaPorDia() {
        Instant ayer = Instant.parse("2026-10-04T15:00:00Z");
        Instant hoy = Instant.parse("2026-10-05T15:00:00Z");
        errores.registrar(ayer, "a.B", "viejo", null);
        for (int i = 0; i < ErroresRecientes.MAXIMO + 5; i++) errores.registrar(hoy, "a.B", "error " + i, null);

        assertEquals(ErroresRecientes.MAXIMO, errores.ultimos().size());
        assertEquals("error " + (ErroresRecientes.MAXIMO + 4), errores.ultimos().get(0).mensaje());
        assertTrue(errores.ultimos().stream().noneMatch(e -> e.mensaje().equals("viejo")));
        assertEquals(ErroresRecientes.MAXIMO + 6, errores.total());
        assertEquals(1, errores.porDia().get(LocalDate.of(2026, 10, 4)));
        assertEquals(ErroresRecientes.MAXIMO + 5, errores.porDia().get(LocalDate.of(2026, 10, 5)));
    }
}
