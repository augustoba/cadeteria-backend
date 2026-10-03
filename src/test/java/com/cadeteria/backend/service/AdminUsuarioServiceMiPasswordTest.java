package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.repository.AdminRepository;
import com.cadeteria.backend.repository.RolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El usuario del panel cambia su propia contraseña (2026-10-03). */
class AdminUsuarioServiceMiPasswordTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AdminRepository repo;
    private AdminUsuarioService service;
    private Admin admin;

    @BeforeEach
    void setUp() {
        repo = mock(AdminRepository.class);
        service = new AdminUsuarioService(repo, mock(RolRepository.class), mock(RolService.class), encoder);
        admin = new Admin();
        admin.setUsername("operador");
        admin.setPasswordHash(encoder.encode("temporal1"));
        admin.setSessionToken("sesion-vieja");
        admin.setDebeCambiarPassword(true);
        admin.setPasswordTemporalExpira(Instant.now().plusSeconds(600));
        when(repo.findByUsername("operador")).thenReturn(Optional.of(admin));
    }

    @Test
    void cambiaLaContrasenaYCierraLaSesionAbierta() {
        service.cambiarMiPassword("operador", "temporal1", "miClaveNueva");

        assertTrue(encoder.matches("miClaveNueva", admin.getPasswordHash()));
        assertNotEquals("sesion-vieja", admin.getSessionToken());
        assertFalse(admin.isDebeCambiarPassword());
        assertNull(admin.getPasswordTemporalExpira());
        verify(repo).save(admin);
    }

    @Test
    void conLaActualMalNoCambiaNada() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.cambiarMiPassword("operador", "otra", "miClaveNueva"));

        assertEquals("La contraseña actual no es correcta.", e.getMessage());
        assertTrue(encoder.matches("temporal1", admin.getPasswordHash()));
        verify(repo, never()).save(any());
    }

    @Test
    void laNuevaTieneQueSerDistinta() {
        assertThrows(BadRequestException.class, () -> service.cambiarMiPassword("operador", "temporal1", "temporal1"));
        verify(repo, never()).save(any());
    }
}
