package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteResetPassword;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.CadeteResetPasswordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * "Olvidé mi contraseña" del cadete (app Android, sin sesión): pide el código por mail,
 * lo tipea junto con la contraseña nueva y no puede salir de esa pantalla hasta
 * cambiarla (ver cadete-app). El código dura {@value #EXPIRA_MINUTOS} minutos, un solo
 * uso, y se invalida a los {@value #MAX_INTENTOS} intentos fallidos.
 */
@Service
@Transactional
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final int EXPIRA_MINUTOS = 15;
    private static final int MAX_INTENTOS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CadeteRepository cadeteRepo;
    private final CadeteResetPasswordRepository resetRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public PasswordRecoveryService(CadeteRepository cadeteRepo, CadeteResetPasswordRepository resetRepo,
                                    EmailService emailService, PasswordEncoder passwordEncoder) {
        this.cadeteRepo = cadeteRepo;
        this.resetRepo = resetRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Nunca revela si el usuario existe o no (evita que alguien use esto para averiguar
     * qué DNIs están dados de alta) — el controller siempre responde OK, exista o no.
     */
    public void solicitar(String username) {
        cadeteRepo.findByUsername(username.trim()).ifPresent(cadete -> {
            if (cadete.getEmail() == null || cadete.getEmail().isBlank()) {
                log.warn("Pedido de recuperar contraseña para {} pero no tiene mail cargado — no se puede mandar el código.",
                        cadete.getUsername());
                return;
            }
            String codigo = generarCodigo();
            CadeteResetPassword r = new CadeteResetPassword();
            r.setId(UUID.randomUUID().toString());
            r.setCadeteId(cadete.getId());
            r.setCodigo(codigo);
            r.setExpiraEn(Instant.now().plus(EXPIRA_MINUTOS, ChronoUnit.MINUTES));
            resetRepo.save(r);

            emailService.enviar(cadete.getEmail(), "Código para recuperar tu contraseña",
                    "Hola " + cadete.getNombre() + "!\n\n" +
                            "Tu código para recuperar la contraseña es: " + codigo + "\n\n" +
                            "Ingresalo en la app dentro de los próximos " + EXPIRA_MINUTOS + " minutos. " +
                            "Si vos no lo pediste, ignorá este mail.");
        });
    }

    public void confirmar(String username, String codigo, String nuevaPassword) {
        Cadete cadete = cadeteRepo.findByUsername(username.trim()).orElse(null);
        CadeteResetPassword r = cadete == null ? null
                : resetRepo.findTopByCadeteIdAndUsadoEnIsNullOrderByCreadoEnDesc(cadete.getId()).orElse(null);

        if (cadete == null || r == null || r.getExpiraEn().isBefore(Instant.now()) || r.getIntentos() >= MAX_INTENTOS) {
            throw new BadRequestException("Código inválido o vencido.");
        }
        if (!r.getCodigo().equals(codigo.trim())) {
            r.setIntentos(r.getIntentos() + 1);
            resetRepo.save(r);
            throw new BadRequestException("Código inválido o vencido.");
        }
        if (nuevaPassword == null || nuevaPassword.isBlank()) {
            throw new BadRequestException("La contraseña nueva no puede estar vacía.");
        }

        cadete.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        cadeteRepo.save(cadete);
        r.setUsadoEn(Instant.now());
        resetRepo.save(r);
    }

    private String generarCodigo() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
