package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.model.VerificacionTelefono;
import com.cadeteria.backend.repository.VerificacionTelefonoRepository;
import com.cadeteria.backend.util.TelefonoUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Confirma que quien carga un pedido en "/pedir" (sin login) es dueño del teléfono que
 * puso, antes de dejarlo enviar la solicitud — mejora 2026-09-17 pedida por el dueño
 * ("cualquiera puede mandar pedidos falsos a nombre de cualquier número"). Mismo patrón
 * que {@link PasswordRecoveryService}: código de 6 dígitos por SMS, pocos intentos, vence
 * rápido. A diferencia de esa, acá el resultado de verificar es un token de un solo uso
 * que {@link SolicitudPedidoService#crear} exige y consume — no hay sesión de por medio.
 */
@Service
@Transactional
public class VerificacionTelefonoService {

    private static final int EXPIRA_CODIGO_MINUTOS = 10;
    private static final int EXPIRA_TOKEN_MINUTOS = 30;
    private static final int MAX_INTENTOS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificacionTelefonoRepository repo;
    private final SmsGatewayService smsGatewayService;
    private final RateLimitService rateLimitService;

    public VerificacionTelefonoService(VerificacionTelefonoRepository repo, SmsGatewayService smsGatewayService,
                                        RateLimitService rateLimitService) {
        this.repo = repo;
        this.smsGatewayService = smsGatewayService;
        this.rateLimitService = rateLimitService;
    }

    public void enviarCodigo(String telefonoCrudo) {
        String telefono = TelefonoUtils.normalizar(telefonoCrudo);
        if (telefono == null || telefono.isBlank()) {
            throw new BadRequestException("Ingresá un teléfono válido.");
        }
        if (!rateLimitService.permitir("otp-tel:" + telefono, 3, Duration.ofMinutes(10))) {
            throw new BadRequestException("Ya te mandamos un código hace poco — esperá unos minutos antes de pedir otro.");
        }

        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        VerificacionTelefono v = new VerificacionTelefono();
        v.setId(UUID.randomUUID().toString());
        v.setTelefono(telefono);
        v.setCodigo(codigo);
        v.setExpiraEn(Instant.now().plus(EXPIRA_CODIGO_MINUTOS, ChronoUnit.MINUTES));
        repo.save(v);

        smsGatewayService.enviar(telefono, "Tu código para confirmar el pedido es " + codigo
                + ". Vence en " + EXPIRA_CODIGO_MINUTOS + " minutos.");
    }

    public String verificarCodigo(String telefonoCrudo, String codigo) {
        String telefono = TelefonoUtils.normalizar(telefonoCrudo);
        VerificacionTelefono v = telefono == null ? null
                : repo.findTopByTelefonoAndVerificadoEnIsNullOrderByCreadoEnDesc(telefono).orElse(null);

        if (v == null || v.getExpiraEn().isBefore(Instant.now()) || v.getIntentos() >= MAX_INTENTOS) {
            throw new BadRequestException("Código inválido o vencido — pedí uno nuevo.");
        }
        if (codigo == null || !v.getCodigo().equals(codigo.trim())) {
            v.setIntentos(v.getIntentos() + 1);
            repo.save(v);
            throw new BadRequestException("Código incorrecto.");
        }

        v.setVerificadoEn(Instant.now());
        v.setToken(UUID.randomUUID().toString());
        repo.save(v);
        return v.getToken();
    }

    /** Lo usa SolicitudPedidoService.crear — consume el token, exige que coincida con el teléfono declarado. */
    public void consumirToken(String token, String telefonoDeclaradoCrudo) {
        String telefonoDeclarado = TelefonoUtils.normalizar(telefonoDeclaradoCrudo);
        VerificacionTelefono v = token == null ? null : repo.findByToken(token).orElse(null);

        boolean valido = v != null
                && v.getVerificadoEn() != null
                && v.getTokenUsadoEn() == null
                && v.getVerificadoEn().plus(EXPIRA_TOKEN_MINUTOS, ChronoUnit.MINUTES).isAfter(Instant.now())
                && v.getTelefono().equals(telefonoDeclarado);

        if (!valido) {
            throw new BadRequestException("Verificá tu teléfono antes de enviar el pedido.");
        }
        v.setTokenUsadoEn(Instant.now());
        repo.save(v);
    }
}
