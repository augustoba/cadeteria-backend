package com.cadeteria.backend.service;

import com.cadeteria.backend.config.JwtService;
import com.cadeteria.backend.model.AccesoLog;
import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.repository.AccesoLogRepository;
import com.cadeteria.backend.repository.AdminRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AdminRepository admins;
    private final CadeteRepository cadetes;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ConfiguracionService configuracionService;
    private final AccesoLogRepository accesoLogRepo;

    public AuthService(AdminRepository admins, CadeteRepository cadetes,
                        PasswordEncoder passwordEncoder, JwtService jwtService,
                        ConfiguracionService configuracionService, AccesoLogRepository accesoLogRepo) {
        this.admins = admins;
        this.cadetes = cadetes;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.configuracionService = configuracionService;
        this.accesoLogRepo = accesoLogRepo;
    }

    /** Bloqueo temporal tras varios intentos fallidos (ronda 6, punto 49) — umbral y duración configurables. */
    public JwtService.TokenData loginAdmin(String username, String rawPassword) {
        return loginAdmin(username, rawPassword, null);
    }

    /** Registra el acceso (ronda 5, punto 50) — ip es opcional, viene del controller. */
    @Transactional
    public JwtService.TokenData loginAdmin(String username, String rawPassword, String ip) {
        Admin admin = admins.findByUsername(username == null ? "" : username.trim())
                .filter(Admin::isEnabled)
                .orElse(null);
        if (admin != null && bloqueado(admin.getBloqueadoHasta())) {
            throw new BadCredentialsException(mensajeBloqueo(admin.getBloqueadoHasta()));
        }
        if (admin == null || !passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            if (admin != null) registrarFalloAdmin(admin);
            throw new BadCredentialsException("Usuario o contrasena incorrectos");
        }
        admin.setIntentosFallidos(0);
        admin.setBloqueadoHasta(null);
        String sessionId = UUID.randomUUID().toString();
        admin.setSessionToken(sessionId);
        admins.save(admin);
        AccesoLog log = new AccesoLog();
        log.setId(UUID.randomUUID().toString());
        log.setUsername(admin.getUsername());
        log.setIngresoEn(Instant.now());
        log.setIp(ip);
        accesoLogRepo.save(log);
        return jwtService.generate(admin.getUsername(), JwtService.TIPO_ADMIN, sessionId, admin.getRol());
    }

    @Transactional(readOnly = true)
    public List<AccesoLog> ultimosAccesos(int cantidad) {
        return accesoLogRepo.findAllByOrderByIngresoEnDesc(
                org.springframework.data.domain.PageRequest.of(0, cantidad)).getContent();
    }

    /**
     * Un cadete desactivado (spec 5.4, pago semanal) no puede loguearse. Cada login exitoso
     * regenera el "sid" (spec: un solo dispositivo activo por cuenta) — invalida cualquier
     * sesion anterior en otro celular apenas ese celular haga su proxima request. Mismo
     * bloqueo temporal por intentos fallidos que el admin (ronda 6, punto 49).
     */
    @Transactional
    public JwtService.TokenData loginCadete(String username, String rawPassword) {
        Cadete cadete = cadetes.findByUsername(username == null ? "" : username.trim())
                .filter(Cadete::isActivo)
                .orElse(null);
        if (cadete != null && bloqueado(cadete.getBloqueadoHasta())) {
            throw new BadCredentialsException(mensajeBloqueo(cadete.getBloqueadoHasta()));
        }
        if (cadete == null || !passwordEncoder.matches(rawPassword, cadete.getPasswordHash())) {
            if (cadete != null) registrarFalloCadete(cadete);
            throw new BadCredentialsException("Usuario o contrasena incorrectos");
        }
        if ("SEMANAL".equals(cadete.getModalidadPago()) && !cadete.isHabilitadoPago()) {
            throw new BadCredentialsException("No podes ingresar: falta pagar la cuota semanal. Comunicate con la cadeteria.");
        }
        cadete.setIntentosFallidos(0);
        cadete.setBloqueadoHasta(null);
        String sessionId = UUID.randomUUID().toString();
        cadete.setSessionToken(sessionId);
        cadetes.save(cadete);
        return jwtService.generate(cadete.getUsername(), JwtService.TIPO_CADETE, sessionId);
    }

    /**
     * Botón de emergencia "cerrar todas las sesiones" (ronda 6, punto 64): regenera el
     * sessionToken de todos los admins y cadetes, invalidando cualquier JWT ya emitido
     * (el claim "sid" deja de matchear en {@link com.cadeteria.backend.config.JwtAuthFilter}).
     * El propio admin que ejecuta la acción también queda deslogueado y debe volver a entrar.
     */
    @Transactional
    public int cerrarTodasLasSesiones() {
        List<Admin> todosLosAdmins = admins.findAll();
        todosLosAdmins.forEach(a -> a.setSessionToken(UUID.randomUUID().toString()));
        admins.saveAll(todosLosAdmins);
        List<Cadete> todosLosCadetes = cadetes.findAll();
        todosLosCadetes.forEach(c -> c.setSessionToken(UUID.randomUUID().toString()));
        cadetes.saveAll(todosLosCadetes);
        return todosLosAdmins.size() + todosLosCadetes.size();
    }

    private boolean bloqueado(Instant bloqueadoHasta) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(Instant.now());
    }

    private String mensajeBloqueo(Instant bloqueadoHasta) {
        long minutos = Math.max(1, java.time.Duration.between(Instant.now(), bloqueadoHasta).toMinutes());
        return "Cuenta bloqueada temporalmente por intentos fallidos. Probá de nuevo en " + minutos + " minuto(s).";
    }

    private void registrarFalloAdmin(Admin admin) {
        int maxIntentos = configuracionService.getInt("max_intentos_login", 5);
        int minutosBloqueo = configuracionService.getInt("bloqueo_login_min", 15);
        admin.setIntentosFallidos(admin.getIntentosFallidos() + 1);
        if (admin.getIntentosFallidos() >= maxIntentos) {
            admin.setBloqueadoHasta(Instant.now().plusSeconds(minutosBloqueo * 60L));
            admin.setIntentosFallidos(0);
        }
        admins.save(admin);
    }

    private void registrarFalloCadete(Cadete cadete) {
        int maxIntentos = configuracionService.getInt("max_intentos_login", 5);
        int minutosBloqueo = configuracionService.getInt("bloqueo_login_min", 15);
        cadete.setIntentosFallidos(cadete.getIntentosFallidos() + 1);
        if (cadete.getIntentosFallidos() >= maxIntentos) {
            cadete.setBloqueadoHasta(Instant.now().plusSeconds(minutosBloqueo * 60L));
            cadete.setIntentosFallidos(0);
        }
        cadetes.save(cadete);
    }
}
