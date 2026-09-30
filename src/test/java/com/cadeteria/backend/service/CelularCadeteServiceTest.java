package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ForbiddenException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.repository.CadeteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Un celular por cadete (pedido del usuario, 2026-09-29): que no le pase usuario y contraseña a otro para
 * que trabaje por él. El primer login vincula el celular; "Habilitar nuevo celular" en el panel borra el
 * viejo (nunca quedan dos) y cierra su sesión.
 */
class CelularCadeteServiceTest {

    private CelularCadeteService service;
    private ConfiguracionService config;
    private CadeteRepository cadeteRepo;
    private Cadete cadete;

    @BeforeEach
    void setUp() {
        cadeteRepo = mock(CadeteRepository.class);
        config = mock(ConfiguracionService.class);
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(i -> i.getArgument(1));
        service = new CelularCadeteService(cadeteRepo, config);
        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setUsername("30111222");
        cadete.setSessionToken("sesion-vieja");
        when(cadeteRepo.findById("c1")).thenReturn(Optional.of(cadete));
        when(cadeteRepo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void elPrimerLoginVinculaElCelular() {
        service.controlarAlLoguear(cadete, "cel-A", "motorola moto g32");
        assertEquals("cel-A", cadete.getCelularId());
        assertEquals("motorola moto g32", cadete.getCelularModelo());
        assertNotNull(cadete.getCelularVinculadoEn());
    }

    @Test
    void conElMismoCelularEntra() {
        service.controlarAlLoguear(cadete, "cel-A", "motorola moto g32");
        assertDoesNotThrow(() -> service.controlarAlLoguear(cadete, "cel-A", "motorola moto g32"));
    }

    @Test
    void desdeOtroCelularNoEntraYQuedaRegistrado() {
        service.controlarAlLoguear(cadete, "cel-A", "motorola moto g32");
        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> service.controlarAlLoguear(cadete, "cel-B", "samsung a14"));
        assertTrue(e.getMessage().contains("otro celular"), e.getMessage());
        assertEquals("cel-A", cadete.getCelularId(), "el vinculado no cambia");
        assertEquals(1, cadete.getIntentosOtroCelular());
        assertEquals("samsung a14", cadete.getUltimoIntentoOtroCelularModelo());
        assertNotNull(cadete.getUltimoIntentoOtroCelularEn());
    }

    @Test
    void habilitarNuevoCelularBorraElViejoYCierraSuSesion() {
        service.controlarAlLoguear(cadete, "cel-A", "motorola moto g32");
        service.habilitarNuevoCelular("c1");
        assertNull(cadete.getCelularId());
        assertNull(cadete.getCelularModelo());
        assertNotEquals("sesion-vieja", cadete.getSessionToken(), "el celular viejo queda deslogueado");

        // El próximo login (el celular nuevo) queda como el único; el viejo ya no entra.
        service.controlarAlLoguear(cadete, "cel-B", "samsung a14");
        assertEquals("cel-B", cadete.getCelularId());
        assertThrows(ForbiddenException.class, () -> service.controlarAlLoguear(cadete, "cel-A", "motorola moto g32"));
    }

    @Test
    void laApkViejaQueNoMandaElCelularEntraIgual() {
        service.controlarAlLoguear(cadete, "cel-A", "motorola moto g32");
        assertDoesNotThrow(() -> service.controlarAlLoguear(cadete, null, null));
        assertDoesNotThrow(() -> service.controlarAlLoguear(cadete, "  ", null));
    }

    @Test
    void conElControlApagadoNoBloqueaPeroAnota() {
        when(config.getBoolean(CelularCadeteService.CLAVE_ACTIVO, true)).thenReturn(false);
        service.controlarAlLoguear(cadete, "cel-A", "motorola moto g32");
        assertDoesNotThrow(() -> service.controlarAlLoguear(cadete, "cel-B", "samsung a14"));
        assertEquals("cel-A", cadete.getCelularId());
        assertEquals(1, cadete.getIntentosOtroCelular());
    }
}
