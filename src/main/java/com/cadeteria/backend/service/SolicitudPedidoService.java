package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.PedidoDtos.PedidoRequest;
import com.cadeteria.backend.dto.SolicitudPedidoDtos.SolicitudPedidoRequest;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.SolicitudPedido;
import com.cadeteria.backend.repository.SolicitudPedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Pedido cargado por el cliente mismo desde "/pedir" (mejora pedida por el dueño): en vez
 * de llamar/escribir por WhatsApp y que el admin lo tipee, el cliente completa un
 * formulario público. El admin lo revisa acá, le carga zona/vehículo/precio y elige:
 * confirmar directo (ya tiene el precio acordado, ej. de memoria) o mandar una cotización
 * con un link que el cliente confirma solo (sin llamar). En ambos casos, el Pedido real
 * recién se crea (reusando PedidoService.crear) cuando queda confirmado — antes de eso es
 * solo una solicitud, no aparece en el dashboard ni ocupa un número de pedido.
 */
@Service
@Transactional
public class SolicitudPedidoService {

    private final SolicitudPedidoRepository repo;
    private final PedidoService pedidoService;
    private final SmsGatewayService smsGatewayService;
    private final WebSocketPublisher publisher;
    private final VerificacionTelefonoService verificacionTelefonoService;
    private final ConfiguracionService configuracionService;
    private final ClienteService clienteService;
    private final WhatsappGatewayService whatsappGatewayService;
    private final GeocodingProxyService geocodingProxyService;
    private final String frontBaseUrl;

    private static final ZoneId ZONA_ART = ZoneId.of("America/Argentina/Buenos_Aires");

    public SolicitudPedidoService(SolicitudPedidoRepository repo, PedidoService pedidoService,
                                   SmsGatewayService smsGatewayService, WebSocketPublisher publisher,
                                   VerificacionTelefonoService verificacionTelefonoService,
                                   ConfiguracionService configuracionService,
                                   ClienteService clienteService, WhatsappGatewayService whatsappGatewayService,
                                   GeocodingProxyService geocodingProxyService, AppProperties props) {
        this.repo = repo;
        this.pedidoService = pedidoService;
        this.smsGatewayService = smsGatewayService;
        this.publisher = publisher;
        this.verificacionTelefonoService = verificacionTelefonoService;
        this.configuracionService = configuracionService;
        this.clienteService = clienteService;
        this.whatsappGatewayService = whatsappGatewayService;
        this.geocodingProxyService = geocodingProxyService;
        this.frontBaseUrl = props.getFrontBaseUrl();
    }

    /**
     * verificarTelefono: si "/pedir" tiene que pedir el código antes de enviar (clave
     * `verificacion_telefono_activa`). Apagado por ahora (2026-09-24) para poder probar sin
     * SMS/WhatsApp — pendiente probarlo prendido con el gateway real.
     */
    public record EstadoDisponibilidad(boolean disponible, String mensaje, boolean verificarTelefono) {}

    /** Default false mientras no esté probado el envío real del código (ver pendientes.md). */
    private boolean verificacionActiva() {
        return configuracionService.getBoolean("verificacion_telefono_activa", false);
    }

    /**
     * "Pausar pedidos" y "horario de atención" (mejora 2026-09-17) — el dueño pidió poder
     * cortar la toma de pedidos nuevos desde "/pedir" ya sea a mano (falta de cadetes,
     * cualquier motivo puntual) o por franja horaria fija. La pausa manual gana siempre,
     * sin importar el horario configurado.
     */
    @Transactional(readOnly = true)
    public EstadoDisponibilidad estadoDisponibilidad() {
        if (configuracionService.getBoolean("pedidos_pausados", false)) {
            return new EstadoDisponibilidad(false,
                    configuracionService.getString("pedidos_pausados_mensaje", "Estamos pausados temporalmente, disculpá las molestias."),
                    verificacionActiva());
        }
        if (configuracionService.getBoolean("horario_atencion_activo", false)) {
            String desdeStr = configuracionService.getString("horario_atencion_desde", "08:00");
            String hastaStr = configuracionService.getString("horario_atencion_hasta", "22:00");
            LocalTime desde = LocalTime.parse(desdeStr);
            LocalTime hasta = LocalTime.parse(hastaStr);
            LocalTime ahora = LocalTime.now(ZONA_ART);
            boolean dentro = desde.isBefore(hasta)
                    ? (!ahora.isBefore(desde) && ahora.isBefore(hasta))
                    : (!ahora.isBefore(desde) || ahora.isBefore(hasta));
            if (!dentro) {
                return new EstadoDisponibilidad(false, "Fuera de nuestro horario de atención (" + desdeStr + " a " + hastaStr + ").",
                        verificacionActiva());
            }
        }
        return new EstadoDisponibilidad(true, null, verificacionActiva());
    }

    /** Exige que el teléfono ya haya pasado por VerificacionTelefonoService (mejora 2026-09-17). */
    public SolicitudPedido crear(SolicitudPedidoRequest req) {
        EstadoDisponibilidad estado = estadoDisponibilidad();
        if (!estado.disponible()) {
            throw new BadRequestException(estado.mensaje());
        }
        // Con la verificación apagada no se exige token (y no se marca "sin verificar": es una
        // decisión del admin, no una falla de envío). Si viene uno igual, se consume.
        boolean sinVerificar = false;
        if (verificacionActiva() || (req.verificacionToken() != null && !req.verificacionToken().isBlank())) {
            sinVerificar = verificacionTelefonoService.consumirToken(req.verificacionToken(), req.clienteTelefono());
        }
        validarDatosDelCliente(req);
        SolicitudPedido s = new SolicitudPedido();
        s.setSinVerificar(sinVerificar);
        s.setId(UUID.randomUUID().toString());
        s.setOrigenDireccion(req.origenDireccion().trim());
        s.setOrigenLat(req.origenLat());
        s.setOrigenLng(req.origenLng());
        s.setDestinoDireccion(req.destinoDireccion().trim());
        s.setDestinoLat(req.destinoLat());
        s.setDestinoLng(req.destinoLng());
        s.setLlevaDinero(req.llevaDinero());
        s.setMontoDeclarado(req.montoDeclarado());
        s.setLlevaValores(req.llevaValores());
        s.setMontoValores(req.llevaValores() && req.montoValores() != null && req.montoValores().signum() > 0
                ? req.montoValores() : null);
        s.setRequiereMoto(req.requiereMoto());
        s.setRetornaAlOrigen(req.retornaAlOrigen());
        s.setClienteNombre(req.clienteNombre().trim());
        s.setClienteTelefono(req.clienteTelefono().trim());
        s.setDetalle(textoOpcional(req.detalle()));
        s.setOrigenPiso(textoOpcional(req.origenPiso()));
        s.setOrigenDepto(textoOpcional(req.origenDepto()));
        s.setOrigenObservaciones(textoOpcional(req.origenObservaciones()));
        s.setDestinoPiso(textoOpcional(req.destinoPiso()));
        s.setDestinoDepto(textoOpcional(req.destinoDepto()));
        s.setDestinoObservaciones(textoOpcional(req.destinoObservaciones()));
        s.setOrigenFuente(textoOpcional(req.origenFuente()));
        s.setDestinoFuente(textoOpcional(req.destinoFuente()));
        s.setEstado("PENDIENTE");
        s.setTokenConfirmacion(UUID.randomUUID().toString());
        s = repo.save(s);
        publisher.publicarAlertaSolicitudPedido(s);
        return s;
    }

    /**
     * Lo mismo que valida "/pedir" (2026-09-25), por si llega un pedido armado a mano: si tildó que
     * lleva dinero o valores, el monto es obligatorio (sin monto no hay recargo ni responsabilidad
     * clara), y el teléfono tiene que tener la característica (10 a 13 dígitos).
     */
    private void validarDatosDelCliente(SolicitudPedidoRequest req) {
        String digitos = req.clienteTelefono().replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 13) {
            throw new BadRequestException("El celular tiene que tener la característica, ej: 381 555 1234.");
        }
        if (req.llevaDinero() && (req.montoDeclarado() == null || req.montoDeclarado().signum() <= 0)) {
            throw new BadRequestException("Indicá cuánto dinero lleva.");
        }
        if (req.llevaValores() && (req.montoValores() == null || req.montoValores().signum() <= 0)) {
            throw new BadRequestException("Indicá cuánto valen los objetos de valor.");
        }
    }

    @Transactional(readOnly = true)
    public List<SolicitudPedido> listar(String estado) {
        return (estado == null || estado.isBlank())
                ? repo.findAllByOrderByCreadoEnDesc()
                : repo.findByEstadoOrderByCreadoEnDesc(estado);
    }

    @Transactional(readOnly = true)
    public SolicitudPedido get(String id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Solicitud de pedido", id));
    }

    @Transactional(readOnly = true)
    public SolicitudPedido getPorToken(String token) {
        return repo.findByTokenConfirmacion(token)
                .orElseThrow(() -> ResourceNotFoundException.of("Solicitud de pedido", token));
    }

    /** El admin ya tiene el precio acordado (ej. lo charló por teléfono) — crea el pedido ya mismo. */
    public Pedido confirmarDirecto(String id, boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado) {
        SolicitudPedido s = exigirPendiente(id);
        Pedido pedido = crearPedidoDesde(s, requiereMoto, precio, montoDeclarado);

        s.setRequiereMoto(requiereMoto);
        s.setPrecio(precio);
        s.setEstado("CONFIRMADA");
        s.setPedidoCreadoId(pedido.getId());
        repo.save(s);

        enviarSmsConfirmado(pedido);
        return pedido;
    }

    /** El admin no tiene un precio ya charlado — le manda una cotización, el cliente confirma solo con el link. */
    public SolicitudPedido cotizar(String id, boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado) {
        SolicitudPedido s = exigirPendiente(id);
        s.setRequiereMoto(requiereMoto);
        s.setPrecio(precio);
        s.setMontoDeclarado(montoDeclarado);
        s.setEstado("COTIZADO");
        s = repo.save(s);

        String link = frontBaseUrl + "/confirmar-pedido/" + s.getTokenConfirmacion();
        smsGatewayService.enviar(s.getClienteTelefono(),
                "Cotización de tu envío: $" + precio + ". Para confirmarlo entrá acá: " + link);
        return s;
    }

    /** El cliente entra al link de la cotización y confirma — recién ahí se crea el Pedido real. Idempotente. */
    public Pedido confirmarPorToken(String token) {
        SolicitudPedido s = getPorToken(token);
        if ("CONFIRMADA".equals(s.getEstado())) {
            return pedidoService.get(s.getPedidoCreadoId());
        }
        if (!"COTIZADO".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud no tiene una cotización esperando confirmación.");
        }
        Pedido pedido = crearPedidoDesde(s, s.isRequiereMoto(), s.getPrecio(), s.getMontoDeclarado());
        s.setEstado("CONFIRMADA");
        s.setPedidoCreadoId(pedido.getId());
        repo.save(s);

        enviarSmsConfirmado(pedido);
        return pedido;
    }

    /**
     * "Marcar como fraudulento" (spec-antiabuso Fase 4): prende "cliente problemático" en la
     * ficha del teléfono — así alimenta la lista negra — y, si la solicitud todavía no se
     * resolvió, la rechaza. No bloquea futuros pedidos: los avisa.
     */
    public SolicitudPedido marcarFraudulenta(String id, String nota, String adminUsername) {
        SolicitudPedido s = get(id);
        String detalle = "Solicitud web marcada como fraudulenta por " + adminUsername
                + " (" + java.time.LocalDate.now(ZONA_ART) + ")"
                + (nota == null || nota.isBlank() ? "" : ": " + nota.trim());
        clienteService.marcarProblematico(s.getClienteTelefono(), detalle);
        if ("PENDIENTE".equals(s.getEstado()) || "COTIZADO".equals(s.getEstado())) {
            s.setEstado("RECHAZADA");
            s.setMotivoRechazo("Fraudulenta");
            s = repo.save(s);
        }
        return s;
    }

    /**
     * Botón "Pedir confirmación por WhatsApp" (spec-antiabuso Fase 4): le manda al cliente
     * los datos del pedido para que confirme. La respuesta se ve en la pestaña "Respuestas"
     * del panel de WhatsApp. Usa la cola normal: si el gateway está apagado sale al volver.
     */
    public void pedirConfirmacionWhatsapp(String id) {
        SolicitudPedido s = get(id);
        String texto = "Hola " + s.getClienteNombre() + ", recibimos un pedido de envío a nombre de este número: desde "
                + s.getOrigenDireccion() + " hasta " + s.getDestinoDireccion()
                + ". ¿Confirmás el envío? Respondé SÍ o NO.";
        whatsappGatewayService.enviar(s.getClienteTelefono(), texto, null);
    }

    /** El admin confirmó a mano que el teléfono es real (ej. llamó) — saca la marca y lo suma a la lista blanca. */
    public SolicitudPedido validarTelefono(String id) {
        SolicitudPedido s = get(id);
        verificacionTelefonoService.marcarValidado(s.getClienteTelefono(), "ADMIN");
        s.setSinVerificar(false);
        return repo.save(s);
    }

    /** Avisos de los teléfonos de una lista de solicitudes, resueltos de una vez (sin N+1). */
    @Transactional(readOnly = true)
    public java.util.Map<String, com.cadeteria.backend.dto.ClienteDtos.ClienteAvisoResponse> avisosDe(List<SolicitudPedido> solicitudes) {
        return clienteService.avisos(solicitudes.stream().map(SolicitudPedido::getClienteTelefono).toList());
    }

    public SolicitudPedido rechazar(String id, String motivo) {
        SolicitudPedido s = exigirPendiente(id);
        s.setEstado("RECHAZADA");
        s.setMotivoRechazo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        return repo.save(s);
    }

    private SolicitudPedido exigirPendiente(String id) {
        SolicitudPedido s = get(id);
        if (!"PENDIENTE".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud ya fue revisada.");
        }
        return s;
    }

    private Pedido crearPedidoDesde(SolicitudPedido s, boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado) {
        StringBuilder detalle = new StringBuilder();
        if (s.isRetornaAlOrigen()) detalle.append("🔁 Retorna al origen. ");
        if (s.isLlevaDinero()) detalle.append("💵 Lleva dinero. ");
        if (s.isLlevaValores()) detalle.append(s.getMontoValores() != null
                ? "💎 Transporta valores ($" + s.getMontoValores().toPlainString() + "). " : "💎 Transporta valores. ");
        if (s.getDetalle() != null) detalle.append(s.getDetalle());

        PedidoRequest req = new PedidoRequest(
                s.getClienteTelefono(), s.getClienteNombre(),
                s.getOrigenDireccion(), s.getOrigenLat(), s.getOrigenLng(),
                s.getDestinoDireccion(), s.getDestinoLat(), s.getDestinoLng(),
                precio, montoDeclarado, s.isLlevaValores(), s.getMontoValores(), detalle.length() == 0 ? null : detalle.toString().trim(),
                s.getOrigenPiso(), s.getOrigenDepto(), s.getOrigenObservaciones(),
                s.getDestinoPiso(), s.getDestinoDepto(), s.getDestinoObservaciones(),
                requiereMoto, false, null, null, s.getOrigenFuente(), s.getDestinoFuente());
        Pedido pedido = pedidoService.crear(req, PedidoService.ORIGEN_WEB, null);
        // El pin del cliente se aprende recién acá, con la solicitud ya revisada por el admin:
        // desde /pedir cualquiera podría mandar un pin mal puesto a propósito.
        geocodingProxyService.aprenderPin(s.getOrigenDireccion(), s.getOrigenLat(), s.getOrigenLng(), s.getOrigenFuente());
        geocodingProxyService.aprenderPin(s.getDestinoDireccion(), s.getDestinoLat(), s.getDestinoLng(), s.getDestinoFuente());
        return pedido;
    }

    private void enviarSmsConfirmado(Pedido pedido) {
        String link = frontBaseUrl + "/seguimiento/" + pedido.getTokenSeguimiento();
        smsGatewayService.enviar(pedido.getId(), pedido.getClienteTelefono(),
                "Tu pedido fue confirmado. Seguilo acá: " + link);
    }

    /** Texto libre opcional: recortado, y null si vino vacío. */
    private static String textoOpcional(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
