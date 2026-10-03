package com.cadeteria.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La versión mínima de la app también se exige al activarse, no solo al iniciar sesión (2026-10-03). */
class CadeteServiceVersionAppTest {

    @Test
    void laAppNuevaMandaSuVersionYEsaEsLaQueCuenta() {
        assertFalse(CadeteService.versionVieja(3, 2, 3));
        // Actualizó la app sin cerrar sesión: el último login quedó con la 2, pero ya manda la 3.
        assertFalse(CadeteService.versionVieja(3, null, 3));
        assertTrue(CadeteService.versionVieja(2, 3, 3));
    }

    @Test
    void laAppViejaNoMandaVersionYValeLaDelUltimoLogin() {
        assertTrue(CadeteService.versionVieja(null, 2, 3));
        assertFalse(CadeteService.versionVieja(null, 2, 2));
    }

    @Test
    void sinNingunaVersionSoloPasaSiNoSeExigeNinguna() {
        assertFalse(CadeteService.versionVieja(null, null, 0));
        assertTrue(CadeteService.versionVieja(null, null, 1));
    }
}
