package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.AdminUsuarioDtos.AdminUsuarioResponse;
import com.cadeteria.backend.dto.AdminUsuarioDtos.CrearAdminRequest;
import com.cadeteria.backend.dto.AdminUsuarioDtos.CrearAdminResponse;
import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.repository.AdminRepository;
import com.cadeteria.backend.repository.RolRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ABM de usuarios del panel (roles configurables, mejora pedida por el dueño
 * 2026-09-16 — antes DUENO/OPERADOR fijo). Todo este controlador/servicio ya vive
 * detrás de {@code hasAuthority("PERM_usuarios")} en
 * {@link com.cadeteria.backend.config.SecurityConfig}.
 */
@Service
@Transactional
public class AdminUsuarioService {

    private static final String ALFABETO_PASSWORD = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    /** Mejora 2026-09-17, pedida por el dueño: la contraseña temporal vence si no se usa a tiempo. */
    private static final int EXPIRA_PASSWORD_TEMPORAL_MIN = 10;

    private final AdminRepository repo;
    private final RolRepository rolRepo;
    private final RolService rolService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public AdminUsuarioService(AdminRepository repo, RolRepository rolRepo, RolService rolService, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.rolRepo = rolRepo;
        this.rolService = rolService;
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
            throw new ConflictException("Ya existe un usuario admin con ese nombre.");
        }
        if (!rolRepo.existsById(req.rol())) {
            throw new BadRequestException("Ese rol no existe.");
        }
        String passwordTemporal = generarPasswordTemporal();
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID().toString());
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(passwordTemporal));
        admin.setEnabled(true);
        admin.setRol(req.rol());
        admin.setDebeCambiarPassword(true);
        admin.setPasswordTemporalExpira(Instant.now().plusSeconds(EXPIRA_PASSWORD_TEMPORAL_MIN * 60L));
        repo.save(admin);
        return new CrearAdminResponse(toResponse(admin), passwordTemporal);
    }

    public AdminUsuarioResponse cambiarRol(String id, String rol) {
        Admin admin = get(id);
        if (!rolRepo.existsById(rol)) {
            throw new BadRequestException("Ese rol no existe.");
        }
        if (rolService.esUltimoConPermiso(admin, "roles") && !rolService.permisosEfectivos(rol).contains("roles")) {
            throw new ConflictException("No podés sacarle el permiso \"roles\" al último admin que lo tiene.");
        }
        admin.setRol(rol);
        repo.save(admin);
        return toResponse(admin);
    }

    public AdminUsuarioResponse habilitar(String id, boolean enabled) {
        Admin admin = get(id);
        if (!enabled && rolService.esUltimoConPermiso(admin, "roles")) {
            throw new ConflictException("No podés deshabilitar al último admin con permiso para administrar roles.");
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
        admin.setDebeCambiarPassword(true);
        admin.setPasswordTemporalExpira(Instant.now().plusSeconds(EXPIRA_PASSWORD_TEMPORAL_MIN * 60L));
        repo.save(admin);
        return new com.cadeteria.backend.dto.AdminUsuarioDtos.ResetearPasswordResponse(passwordTemporal);
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
