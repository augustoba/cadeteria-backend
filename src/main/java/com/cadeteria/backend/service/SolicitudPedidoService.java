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
    private final String frontBaseUrl;

    private static final ZoneId ZONA_ART = ZoneId.of("America/Argentina/Buenos_Aires");

    public SolicitudPedidoService(SolicitudPedidoRepository repo, PedidoService pedidoService,
                                   SmsGatewayService smsGatewayService, WebSocketPublisher publisher,
                                   VerificacionTelefonoService verificacionTelefonoService,
                                   ConfiguracionService configuracionService,
                                   AppProperties props) {
        this.repo = repo;
        this.pedidoService = pedidoService;
        this.smsGatewayService = smsGatewayService;
        this.publisher = publisher;
        this.verificacionTelefonoService = verificacionTelefonoService;
        this.configuracionService = configuracionService;
        this.frontBaseUrl = props.getFrontBaseUrl();
    }

    public record EstadoDisponibilidad(boolean disponible, String mensaje) {}

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
                    configuracionService.getString("pedidos_pausados_mensaje", "Estamos pausados temporalmente, disculpá las molestias."));
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
                return new EstadoDisponibilidad(false, "Fuera de nuestro horario de atención (" + desdeStr + " a " + hastaStr + ").");
            }
        }
        return new EstadoDisponibilidad(true, null);
    }

    /** Exige que el teléfono ya haya pasado por VerificacionTelefonoService (mejora 2026-09-17). */
    public SolicitudPedido crear(SolicitudPedidoRequest req) {
        EstadoDisponibilidad estado = estadoDisponibilidad();
        if (!estado.disponible()) {
            throw new BadRequestException(estado.mensaje());
        }
        verificacionTelefonoService.consumirToken(req.verificacionToken(), req.clienteTelefono());
        SolicitudPedido s = new SolicitudPedido();
        s.setId(UUID.randomUUID().toString());
        s.setOrigenDireccion(req.origenDireccion().trim());
        s.setOrigenLat(req.origenLat());
        s.setOrigenLng(req.origenLng());
        s.setDestinoDireccion(req.destinoDireccion().trim());
        s.setDestinoLat(req.destinoLat());
        s.setDestinoLng(req.destinoLng());
        s.setLlevaDinero(req.llevaDinero());
        s.setMontoDeclarado(req.montoDeclarado());
        s.setRetornaAlOrigen(req.retornaAlOrigen());
        s.setClienteNombre(req.clienteNombre().trim());
        s.setClienteTelefono(req.clienteTelefono().trim());
        s.setDetalle(req.detalle() == null || req.detalle().isBlank() ? null : req.detalle().trim());
        s.setEstado("PENDIENTE");
        s.setTokenConfirmacion(UUID.randomUUID().toString());
        s = repo.save(s);
        publisher.publicarAlertaSolicitudPedido(s);
        return s;
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
        if (s.getDetalle() != null) detalle.append(s.getDetalle());

        PedidoRequest req = new PedidoRequest(
                s.getClienteTelefono(), s.getClienteNombre(),
                s.getOrigenDireccion(), s.getOrigenLat(), s.getOrigenLng(),
                s.getDestinoDireccion(), s.getDestinoLat(), s.getDestinoLng(),
                precio, montoDeclarado, detalle.length() == 0 ? null : detalle.toString().trim(),
                requiereMoto, false, null, null);
        return pedidoService.crear(req);
    }

    private void enviarSmsConfirmado(Pedido pedido) {
        String link = frontBaseUrl + "/seguimiento/" + pedido.getTokenSeguimiento();
        smsGatewayService.enviar(pedido.getId(), pedido.getClienteTelefono(),
                "Tu pedido fue confirmado. Seguilo acá: " + link);
    }
}
