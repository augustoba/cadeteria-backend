package com.cadeteria.backend.service;

import com.cadeteria.backend.common.TooManyRequestsException;
import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.VerificacionTelefonoDtos.EnviarCodigoResponse;
import com.cadeteria.backend.model.TelefonoValidado;
import com.cadeteria.backend.model.VerificacionTelefono;
import com.cadeteria.backend.repository.TelefonoValidadoRepository;
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
 * que {@link PasswordRecoveryService}: código de 6 dígitos, pocos intentos, vence rápido.
 * A diferencia de esa, acá el resultado de verificar es un token de un solo uso que
 * {@link SolicitudPedidoService#crear} exige y consume — no hay sesión de por medio.
 *
 * <p>Transporte y degradación (spec-antiabuso-pedidos-publicos.md, Fase 2 y §6): el código
 * sale por WhatsApp si el gateway está conectado, si no por SMS, y si no hay ningún medio
 * la solicitud entra igual marcada "sin verificar" — nunca se pierde una venta. Antes, con
 * SMS apagado (el default) el cliente se quedaba esperando un código que nunca salía.
 */
@Service
@Transactional
public class VerificacionTelefonoService {

    public static final String CODIGO_WHATSAPP = "CODIGO_WHATSAPP";
    public static final String CODIGO_SMS = "CODIGO_SMS";
    public static final String YA_VALIDADO = "YA_VALIDADO";
    public static final String SIN_VERIFICAR = "SIN_VERIFICAR";

    private static final int EXPIRA_CODIGO_MINUTOS = 10;
    private static final int EXPIRA_TOKEN_MINUTOS = 30;
    private static final int MAX_INTENTOS = 5;
    private static final String SIN_CODIGO = "------";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificacionTelefonoRepository repo;
    private final TelefonoValidadoRepository validadoRepo;
    private final SmsGatewayService smsGatewayService;
    private final WhatsappGatewayService whatsappGatewayService;
    private final RateLimitService rateLimitService;
    private final ConfiguracionService configuracionService;
    private final AppProperties props;

    public VerificacionTelefonoService(VerificacionTelefonoRepository repo, TelefonoValidadoRepository validadoRepo,
                                        SmsGatewayService smsGatewayService, WhatsappGatewayService whatsappGatewayService,
                                        RateLimitService rateLimitService, ConfiguracionService configuracionService,
                                        AppProperties props) {
        this.repo = repo;
        this.validadoRepo = validadoRepo;
        this.smsGatewayService = smsGatewayService;
        this.whatsappGatewayService = whatsappGatewayService;
        this.rateLimitService = rateLimitService;
        this.configuracionService = configuracionService;
        this.props = props;
    }

    public EnviarCodigoResponse enviarCodigo(String telefonoCrudo) {
        String telefono = TelefonoUtils.normalizar(telefonoCrudo);
        if (telefono == null || telefono.isBlank()) {
            throw new BadRequestException("Ingresá un teléfono válido.");
        }
        if (!rateLimitService.permitir("otp-tel:" + telefono, 3, Duration.ofMinutes(10))) {
            throw new TooManyRequestsException("Ya te mandamos un código hace poco — esperá unos minutos antes de pedir otro.");
        }

        // Lista blanca (Fase 2): un teléfono que ya pasó el código no lo vuelve a pedir.
        // Configurable porque afloja la protección contra suplantación para esos números.
        if (configuracionService.getBoolean("verificacion_recordar_telefonos", true)
                && validadoRepo.existsById(telefono)) {
            return new EnviarCodigoResponse(YA_VALIDADO, emitirTokenSinCodigo(telefono, YA_VALIDADO));
        }

        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        String texto = "Tu código para confirmar el pedido es " + codigo
                + ". Vence en " + EXPIRA_CODIGO_MINUTOS + " minutos.";
        String via = mandar(telefono, texto);
        if (via == null) {
            return new EnviarCodigoResponse(SIN_VERIFICAR, emitirTokenSinCodigo(telefono, SIN_VERIFICAR));
        }

        VerificacionTelefono v = new VerificacionTelefono();
        v.setId(UUID.randomUUID().toString());
        v.setTelefono(telefono);
        v.setCodigo(codigo);
        v.setVia(via);
        v.setExpiraEn(Instant.now().plus(EXPIRA_CODIGO_MINUTOS, ChronoUnit.MINUTES));
        repo.save(v);
        return new EnviarCodigoResponse("WHATSAPP".equals(via) ? CODIGO_WHATSAPP : CODIGO_SMS, null);
    }

    /** WhatsApp → SMS según `app.verificacion.transporte`. Devuelve el medio usado, o null si no hubo ninguno. */
    private String mandar(String telefono, String texto) {
        String transporte = props.getVerificacion().getTransporte();
        boolean probarWhatsapp = !"SMS".equalsIgnoreCase(transporte);
        boolean probarSms = !"WHATSAPP".equalsIgnoreCase(transporte);
        if (probarWhatsapp && whatsappGatewayService.enviarYa(telefono, texto)) {
            return "WHATSAPP";
        }
        if (probarSms && smsGatewayService.isHabilitado()) {
            smsGatewayService.enviar(telefono, texto);
            return "SMS";
        }
        return null;
    }

    /** Token ya verificado, sin código de por medio — para YA_VALIDADO y SIN_VERIFICAR. */
    private String emitirTokenSinCodigo(String telefono, String via) {
        VerificacionTelefono v = new VerificacionTelefono();
        v.setId(UUID.randomUUID().toString());
        v.setTelefono(telefono);
        v.setCodigo(SIN_CODIGO);
        v.setVia(via);
        v.setExpiraEn(Instant.now());
        v.setVerificadoEn(Instant.now());
        v.setToken(UUID.randomUUID().toString());
        repo.save(v);
        return v.getToken();
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
        marcarValidado(telefono, v.getVia() == null ? "SMS" : v.getVia());
        return v.getToken();
    }

    /** Suma el teléfono a la lista blanca (no pisa la fecha si ya estaba). `via`: WHATSAPP | SMS | ADMIN. */
    public void marcarValidado(String telefonoCrudo, String via) {
        String telefono = TelefonoUtils.normalizar(telefonoCrudo);
        if (telefono == null || telefono.isBlank() || validadoRepo.existsById(telefono)) return;
        TelefonoValidado t = new TelefonoValidado();
        t.setTelefono(telefono);
        t.setVia(via);
        validadoRepo.save(t);
    }

    /**
     * Lo usa SolicitudPedidoService.crear — consume el token, exige que coincida con el
     * teléfono declarado. Devuelve true si la solicitud tiene que entrar "sin verificar"
     * (no se pudo mandar el código por ningún medio).
     */
    public boolean consumirToken(String token, String telefonoDeclaradoCrudo) {
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
        return SIN_VERIFICAR.equals(v.getVia());
    }
}
