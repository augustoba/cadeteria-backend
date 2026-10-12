package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Cadete;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** El login de la app no deja entrar al que no está al día con el cobro (2026-10-11). */
class CadeteServiceMotivoSinPagoTest {

    private static Cadete cadete(String modalidad, boolean habilitado, String credito) {
        Cadete c = new Cadete();
        c.setModalidadPago(modalidad);
        c.setHabilitadoPago(habilitado);
        c.setCreditoDisponible(new BigDecimal(credito));
        return c;
    }

    @Test
    void semanalSoloEntraHabilitado() {
        assertNull(CadeteService.motivoSinPago(cadete("SEMANAL", true, "0")));
        assertNotNull(CadeteService.motivoSinPago(cadete("SEMANAL", false, "1000")));
    }

    @Test
    void porcentajeSoloEntraConCredito() {
        assertNull(CadeteService.motivoSinPago(cadete("PORCENTAJE", false, "150")));
        assertNotNull(CadeteService.motivoSinPago(cadete("PORCENTAJE", true, "0")));
        assertNotNull(CadeteService.motivoSinPago(cadete("PORCENTAJE", true, "-20")));
    }
}
