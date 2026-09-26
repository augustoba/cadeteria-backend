package com.cadeteria.backend.controller;

import com.cadeteria.backend.common.ForbiddenException;
import com.cadeteria.backend.dto.ConfiguracionDtos.ConfiguracionUpdateRequest;
import com.cadeteria.backend.service.ApiKeyPoolService;
import com.cadeteria.backend.service.ConfiguracionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Superadmin (2026-09-26): un admin no ve ni cambia lo técnico, y en sus API keys puede agregar pero
 * no quitar — aunque arme el pedido a mano, sin pasar por el panel.
 */
class ConfiguracionControllerTest {

    private ConfiguracionService service;
    private ConfiguracionController controller;

    @BeforeEach
    void setUp() {
        service = mock(ConfiguracionService.class);
        controller = new ConfiguracionController(service, mock(ApiKeyPoolService.class));
        Map<String, String> todo = new LinkedHashMap<>();
        todo.put("precio_por_km", "320");
        todo.put("cloudinary_cloud_name", "cuenta");
        todo.put("geoapify_keys", "SIS1");
        todo.put("geoapify_keys_cliente", "CLI1");
        when(service.findAll()).thenReturn(todo);
        when(service.getString("geoapify_keys_cliente", "")).thenReturn("CLI1");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void comoUsuarioCon(String... permisos) {
        List<SimpleGrantedAuthority> auth = Arrays.stream(permisos).map(p -> new SimpleGrantedAuthority("PERM_" + p)).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", null, auth));
    }

    @Test
    void unAdminNoVeLoTecnicoNiLasKeysDelSistema() {
        comoUsuarioCon("configuracion");
        Map<String, String> valores = controller.get().valores();
        assertTrue(valores.containsKey("precio_por_km"));
        assertTrue(valores.containsKey("geoapify_keys_cliente"));
        assertFalse(valores.containsKey("cloudinary_cloud_name"));
        assertFalse(valores.containsKey("geoapify_keys"));
    }

    @Test
    void elSuperadminVeTodo() {
        comoUsuarioCon("configuracion", "sistema");
        assertTrue(controller.get().valores().containsKey("cloudinary_cloud_name"));
    }

    @Test
    void unAdminNoPuedeCambiarLoTecnico() {
        comoUsuarioCon("configuracion");
        assertThrows(ForbiddenException.class,
                () -> controller.set(new ConfiguracionUpdateRequest("google_cache_pausar_borrado", "false")));
        verify(service, never()).set(anyString(), anyString());
    }

    @Test
    void unAdminPuedeAgregarKeysPeroNoQuitar() {
        comoUsuarioCon("configuracion");
        controller.set(new ConfiguracionUpdateRequest("geoapify_keys_cliente", "CLI1\nCLI2"));
        verify(service).set("geoapify_keys_cliente", "CLI1\nCLI2");

        assertThrows(ForbiddenException.class,
                () -> controller.set(new ConfiguracionUpdateRequest("geoapify_keys_cliente", "CLI2")));
    }

    @Test
    void elSuperadminPuedeBorrarKeysDeLaCadeteria() {
        comoUsuarioCon("configuracion", "sistema");
        controller.set(new ConfiguracionUpdateRequest("geoapify_keys_cliente", "CLI2"));
        verify(service).set("geoapify_keys_cliente", "CLI2");
    }
}
