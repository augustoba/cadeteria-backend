package com.cadeteria.backend.controller;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.dto.AvisoEnCaminoDtos.AvisoEnCaminoRequest;
import com.cadeteria.backend.dto.AvisoEnCaminoDtos.AvisoEnCaminoResponse;
import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Editor del aviso "en camino" en Configuración (3n, 2026-09-28). */
class AvisoEnCaminoControllerTest {

    private final Map<String, String> guardado = new HashMap<>();
    private ConfiguracionService config;
    private AvisoEnCaminoController controller;
    private final Principal admin = () -> "admin";

    @BeforeEach
    void setUp() {
        config = mock(ConfiguracionService.class);
        when(config.getString(anyString(), anyString())).thenAnswer(i -> {
            String v = guardado.get(i.getArgument(0, String.class));
            return v == null || v.isBlank() ? i.getArgument(1) : v;
        });
        doAnswer(i -> {
            guardado.put(i.getArgument(0), i.getArgument(1));
            guardado.put(i.getArgument(0) + ConfiguracionService.SUFIJO_EDITADO, i.getArgument(2) + "|" + Instant.now());
            return null;
        }).when(config).setConAutor(anyString(), anyString(), anyString());
        controller = new AvisoEnCaminoController(config, mock(PedidoService.class));
    }

    @Test
    void sinEditarDevuelveElOriginalYNadieLoEdito() {
        AvisoEnCaminoResponse r = controller.get();
        assertEquals(PedidoService.WHATSAPP_EN_CAMINO_DEFAULT, r.texto());
        assertFalse(r.personalizado());
        assertNull(r.editadoPor());
    }

    @Test
    void guardarRegistraQuienYCuando() {
        AvisoEnCaminoResponse r = controller.set(new AvisoEnCaminoRequest("Hola, {cadete} va en camino: {link}"), admin);
        assertEquals("Hola, {cadete} va en camino: {link}", r.texto());
        assertTrue(r.personalizado());
        assertEquals("admin", r.editadoPor());
        assertNotNull(r.editadoEn());
    }

    @Test
    void sinLinkNoSeGuarda() {
        assertThrows(BadRequestException.class,
                () -> controller.set(new AvisoEnCaminoRequest("Tu cadete va en camino"), admin));
        verify(config, never()).setConAutor(anyString(), anyString(), anyString());
    }

    @Test
    void vacioOIgualAlOriginalVuelveAlTextoDelCodigo() {
        controller.set(new AvisoEnCaminoRequest("Otro {link}"), admin);
        AvisoEnCaminoResponse r = controller.set(new AvisoEnCaminoRequest(PedidoService.WHATSAPP_EN_CAMINO_DEFAULT), admin);
        assertFalse(r.personalizado());
        assertEquals(PedidoService.WHATSAPP_EN_CAMINO_DEFAULT, r.texto());
        assertEquals("admin", r.editadoPor(), "volver al original también queda registrado");
    }
}
