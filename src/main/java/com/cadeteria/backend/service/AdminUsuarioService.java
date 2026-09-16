package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.AdminUsuarioDtos.AdminUsuarioResponse;
import com.cadeteria.backend.dto.AdminUsuarioDtos.CrearAdminRequest;
import com.cadeteria.backend.dto.AdminUsuarioDtos.CrearAdminResponse;
import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.repository.AdminRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * ABM de usuarios del panel (roles DUENO/OPERADOR, mejora pedida por el dueño
 * 2026-09-16). Todo este controlador/servicio ya vive detrás de
 * {@code hasRole("ADMIN_DUENO")} en {@link com.cadeteria.backend.config.SecurityConfig} —
 * un OPERADOR nunca llega a llamar nada de acá.
 */
@Service
@Transactional
public class AdminUsuarioService {

    private static final String ALFABETO_PASSWORD = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final AdminRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public AdminUsuarioService(AdminRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUsuarioResponse> listar() {
        return repo.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(this::toResponse)
                .toList();
    }

    public CrearAdminResponse crear(CrearAdminRequest req) {
        String username = req.username().trim();
        if (repo.findByUsername(username).isPresent()) {
            throw new BadRequestException("Ya existe un usuario admin con ese nombre.");
        }
        String passwordTemporal = generarPasswordTemporal();
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID().toString());
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(passwordTemporal));
        admin.setEnabled(true);
        admin.setRol(req.rol());
        repo.save(admin);
        return new CrearAdminResponse(toResponse(admin), passwordTemporal);
    }

    public AdminUsuarioResponse cambiarRol(String id, String rol) {
        Admin admin = get(id);
        if (admin.isDueno() && "OPERADOR".equals(rol) && ultimoDuenoHabilitado(admin)) {
            throw new BadRequestException("No podés dejar sin ningún DUEÑO habilitado — es el último.");
        }
        admin.setRol(rol);
        repo.save(admin);
        return toResponse(admin);
    }

    public AdminUsuarioResponse habilitar(String id, boolean enabled) {
        Admin admin = get(id);
        if (!enabled && admin.isDueno() && ultimoDuenoHabilitado(admin)) {
            throw new BadRequestException("No podés deshabilitar al último DUEÑO habilitado.");
        }
        admin.setEnabled(enabled);
        if (!enabled) {
            admin.setSessionToken(UUID.randomUUID().toString());
        }
        repo.save(admin);
        return toResponse(admin);
    }

    public com.cadeteria.backend.dto.AdminUsuarioDtos.ResetearPasswordResponse resetearPassword(String id) {
        Admin admin = get(id);
        String passwordTemporal = generarPasswordTemporal();
        admin.setPasswordHash(passwordEncoder.encode(passwordTemporal));
        admin.setSessionToken(UUID.randomUUID().toString());
        repo.save(admin);
        return new com.cadeteria.backend.dto.AdminUsuarioDtos.ResetearPasswordResponse(passwordTemporal);
    }

    private boolean ultimoDuenoHabilitado(Admin excluir) {
        return repo.findAll().stream()
                .filter(a -> !a.getId().equals(excluir.getId()))
                .noneMatch(a -> a.isEnabled() && a.isDueno());
    }

    private Admin get(String id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Usuario admin", id));
    }

    private AdminUsuarioResponse toResponse(Admin a) {
        return new AdminUsuarioResponse(a.getId(), a.getUsername(), a.getRol(), a.isEnabled(), a.getCreatedAt());
    }

    private String generarPasswordTemporal() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALFABETO_PASSWORD.charAt(random.nextInt(ALFABETO_PASSWORD.length())));
        }
        return sb.toString();
    }
}
