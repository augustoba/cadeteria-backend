package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Configuracion;
import com.cadeteria.backend.model.ConfiguracionCambio;
import com.cadeteria.backend.repository.ConfiguracionCambioRepository;
import com.cadeteria.backend.repository.ConfiguracionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Historial de Configuración (2026-10-19): queda quién cambió qué, sin los secretos. */
class ConfiguracionServiceHistorialTest {

    private ConfiguracionRepository repo;
    private ConfiguracionCambioRepository cambioRepo;
    private ConfiguracionService service;

    @BeforeEach
    void setUp() {
        repo = mock(ConfiguracionRepository.class);
        cambioRepo = mock(ConfiguracionCambioRepository.class);
        service = new ConfiguracionService(repo, cambioRepo);
    }

    private void cargada(String clave, String valor) {
        Configuracion c = new Configuracion();
        c.setClave(clave);
        c.setValor(valor);
        when(repo.findById(clave)).thenReturn(Optional.of(c));
    }

    @Test
    void anotaElValorAnteriorElNuevoYQuien() {
        cargada("comision_porcentaje", "10");
        service.setDesdePanel("comision_porcentaje", "25", "admin");

        ArgumentCaptor<ConfiguracionCambio> c = ArgumentCaptor.forClass(ConfiguracionCambio.class);
        verify(cambioRepo).save(c.capture());
        assertEquals("comision_porcentaje", c.getValue().getClave());
        assertEquals("10", c.getValue().getValorAnterior());
        assertEquals("25", c.getValue().getValorNuevo());
        assertEquals("admin", c.getValue().getCambiadoPorUsername());
    }

    @Test
    void claveNuevaQuedaSinValorAnterior() {
        when(repo.findById("en_lugar_radio_m")).thenReturn(Optional.empty());
        service.setDesdePanel("en_lugar_radio_m", "200", "admin");

        ArgumentCaptor<ConfiguracionCambio> c = ArgumentCaptor.forClass(ConfiguracionCambio.class);
        verify(cambioRepo).save(c.capture());
        assertNull(c.getValue().getValorAnterior());
    }

    @Test
    void sinCambioNoAnotaNada() {
        cargada("comision_porcentaje", "25");
        service.setDesdePanel("comision_porcentaje", "25", "admin");
        verify(cambioRepo, never()).save(any());
    }

    @Test
    void losSecretosNoGuardanElValor() {
        cargada("geoapify_keys_cliente", "VIEJA");
        service.setDesdePanel("geoapify_keys_cliente", "VIEJA\nNUEVA", "admin");

        ArgumentCaptor<ConfiguracionCambio> c = ArgumentCaptor.forClass(ConfiguracionCambio.class);
        verify(cambioRepo).save(c.capture());
        assertEquals(ConfiguracionService.OCULTO, c.getValue().getValorAnterior());
        assertEquals(ConfiguracionService.OCULTO, c.getValue().getValorNuevo());
    }
}
