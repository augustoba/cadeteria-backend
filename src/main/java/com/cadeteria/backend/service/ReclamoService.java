package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.PedidoDtos.PedidoResponse;
import com.cadeteria.backend.model.Incidencia;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.IncidenciaRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Seguimiento de los reclamos del cliente (2026-09-26) — pensado para que se resuelvan sin nadie en
 * la oficina: los de demora se cierran solos cuando el cadete retira/entrega (PedidoService), y el
 * de problema con la entrega abre un incidente que bloquea al cadete hasta que:
 * <ul>
 *   <li>el cliente toca "Ya se solucionó" en el seguimiento, o</li>
 *   <li>pasados {@code reclamo_seguimiento_min} (10) se le manda un mensaje por WhatsApp (o SMS)
 *       preguntando, y si no responde en {@code reclamo_cierre_min} (10) se cierra solo, o</li>
 *   <li>un admin lo cierra desde el panel.</li>
 * </ul>
 * Si el cliente dice que sigue el problema, el reclamo queda en CONTACTO: no se cierra solo, y el
 * botón le abre el WhatsApp de {@code whatsapp_atencion_cliente} (puede ser el celular del dueño).
 */
@Service
@Transactional
public class ReclamoService {

    private static final Logger log = LoggerFactory.getLogger(ReclamoService.class);

    public static final String CONFIG_MIN_SEGUIMIENTO = "reclamo_seguimiento_min";
    public static final String CONFIG_MIN_CIERRE = "reclamo_cierre_min";
    public static final String CONFIG_WHATSAPP_ATENCION = "whatsapp_atencion_cliente";

    private final PedidoRepository pedidoRepo;
    private final IncidenciaRepository incidenciaRepo;
    private final PedidoService pedidoService;
    private final ConfiguracionService configuracionService;
    private final WhatsappGatewayService whatsappGatewayService;
    private final SmsGatewayService smsGatewayService;
    private final WebSocketPublisher publisher;
    private final String frontBaseUrl;

    public ReclamoService(PedidoRepository pedidoRepo, IncidenciaRepository incidenciaRepo, PedidoService pedidoService,
                          ConfiguracionService configuracionService, WhatsappGatewayService whatsappGatewayService,
                          SmsGatewayService smsGatewayService, WebSocketPublisher publisher, AppProperties props) {
        this.pedidoRepo = pedidoRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.pedidoService = pedidoService;
        this.configuracionService = configuracionService;
        this.whatsappGatewayService = whatsappGatewayService;
        this.smsGatewayService = smsGatewayService;
        this.publisher = publisher;
        this.frontBaseUrl = props.getFrontBaseUrl();
    }

    // --- Panel ---

    /** "Visto": la fila deja de parpadear y queda con el color fijo. */
    public Pedido marcarVisto(String pedidoId) {
        Pedido p = pedidoService.get(pedidoId);
        if ("ABIERTO".equals(p.getReclamoEstado())) {
            p.setReclamoEstado("VISTO");
            pedidoRepo.save(p);
            publisher.publicarPedido(PedidoResponse.fromResumen(p));
        }
        return p;
    }

    /** Cerrar el reclamo desde el panel (y su incidente, si tiene). */
    public Pedido cerrarDesdePanel(String pedidoId, String adminUsername) {
        Pedido p = pedidoService.get(pedidoId);
        cerrar(p, adminUsername, "Cerrado desde el panel");
        return p;
    }

    /** Para el cuadro "Reclamos abiertos" del dashboard: los pedidos ya entregados con el reclamo sin cerrar. */
    @Transactional(readOnly = true)
    public List<Pedido> entregadosConReclamoAbierto() {
        return incidenciaRepo.findByEstadoAndOrigen("ABIERTA", PedidoService.ORIGEN_RECLAMO).stream()
                .map(i -> pedidoRepo.findById(i.getPedidoId()).orElse(null))
                .filter(p -> p != null && p.isReclamoAbierto())
                .toList();
    }

    // --- Cliente (página de seguimiento) ---

    /** "Ya se solucionó": se cierra el incidente y el cadete vuelve a recibir pedidos. */
    public void clienteSolucionado(String token) {
        Pedido p = pedidoService.getPorToken(token);
        exigirReclamoDeProblemaAbierto(p);
        cerrar(p, "cliente (seguimiento)", "Solucionado según el cliente");
    }

    /**
     * "Sigue el problema": queda esperando contacto — no se cierra solo. Devuelve el número de
     * WhatsApp de atención configurado (la página arma el link wa.me con un mensaje escrito).
     */
    public String clienteSigueElProblema(String token) {
        Pedido p = pedidoService.getPorToken(token);
        exigirReclamoDeProblemaAbierto(p);
        p.setReclamoEstado("CONTACTO");
        pedidoRepo.save(p);
        PedidoResponse dto = PedidoResponse.fromResumen(p);
        publisher.publicarPedido(dto);
        if (p.getCadeteAsignado() != null) {
            publisher.publicarAlertaReclamo(p.getCadeteAsignado(), dto,
                    "El cliente del pedido N° " + p.getNumero() + " dice que SIGUE el problema con la entrega y pidió que lo contacten.");
        }
        return configuracionService.getString(CONFIG_WHATSAPP_ATENCION, "");
    }

    // --- App del cadete ---

    /** El incidente por reclamo que lo tiene bloqueado, si hay (para el aviso en la app). */
    @Transactional(readOnly = true)
    public Optional<Incidencia> incidenteAbiertoDe(String cadeteId) {
        return incidenciaRepo.findByCadeteIdAndOrigenAndEstado(cadeteId, PedidoService.ORIGEN_RECLAMO, "ABIERTA")
                .stream().findFirst();
    }

    // --- Job ---

    /**
     * Cada minuto: a los N minutos del reclamo le escribe al cliente; si pasados M minutos más no
     * respondió, lo cierra. Los que quedaron en CONTACTO no se tocan (los cierra un admin).
     */
    @Scheduled(fixedDelay = 60_000)
    // Sin una transacción que abarque todo: si falla un reclamo (ej. el envío), los demás igual se
    // procesan, y el cierre de uno no se deshace por un error en otro (bug del 2026-09-26).
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void seguimientoAutomatico() {
        int minSeguimiento = configuracionService.getInt(CONFIG_MIN_SEGUIMIENTO, 10);
        int minCierre = configuracionService.getInt(CONFIG_MIN_CIERRE, 10);
        Instant ahora = Instant.now();
        for (Incidencia i : incidenciaRepo.findByEstadoAndOrigen("ABIERTA", PedidoService.ORIGEN_RECLAMO)) {
            try {
                Pedido p = i.getPedidoId() == null ? null : pedidoRepo.findById(i.getPedidoId()).orElse(null);
                if (p == null || "CONTACTO".equals(p.getReclamoEstado())) continue;
                if (i.getSeguimientoEnviadoEn() == null) {
                    if (i.getCreadaEn().plus(Duration.ofMinutes(minSeguimiento)).isBefore(ahora)) {
                        // Se marca como enviado aunque el envío falle: si no, nunca se cerraría.
                        i.setSeguimientoEnviadoEn(ahora);
                        incidenciaRepo.save(i);
                        enviarSeguimiento(p, minCierre);
                    }
                } else if (i.getSeguimientoEnviadoEn().plus(Duration.ofMinutes(minCierre)).isBefore(ahora)) {
                    cerrar(p, "automático", "Cerrado sin respuesta del cliente");
                }
            } catch (RuntimeException e) {
                log.warn("Falló el seguimiento automático del reclamo {} (pedido {}): {}", i.getId(), i.getPedidoNumero(), e.getMessage());
            }
        }
    }

    private void enviarSeguimiento(Pedido p, int minCierre) {
        String marca = configuracionService.getString("nombre_cadeteria", "Cadetería");
        String loQueDijo = p.getReclamoDetalle() != null && p.getReclamoDetalle().contains(": \"")
                ? p.getReclamoDetalle().substring(p.getReclamoDetalle().indexOf(": \"") + 2) : "";
        if (loQueDijo.endsWith(".")) loQueDijo = loQueDijo.substring(0, loQueDijo.length() - 1);
        if (loQueDijo.length() > 120) loQueDijo = loQueDijo.substring(0, 117) + "...\"";
        String texto = marca + ": notamos que tuvo un inconveniente con el pedido N° " + p.getNumero()
                + (loQueDijo.isEmpty() ? "" : " (" + loQueDijo + ")")
                + ". Si ya se solucionó, no hace falta hacer nada. Si sigue el problema, entre a este link y avísenos: "
                + frontBaseUrl + "/seguimiento/" + p.getTokenSeguimiento()
                + " — si no hay respuesta, el reclamo se cerrará en " + minCierre + (minCierre == 1 ? " minuto." : " minutos.");
        // WhatsApp si el gateway está conectado; si no, SMS (mismo criterio que el código de verificación).
        if (whatsappGatewayService.enviarYa(p.getClienteTelefono(), texto)) return;
        if (smsGatewayService.isHabilitado()) {
            smsGatewayService.enviar(p.getId(), p.getClienteTelefono(), texto);
            return;
        }
        log.warn("No se pudo mandar el seguimiento del reclamo del pedido {}: ni WhatsApp ni SMS disponibles.", p.getNumero());
    }

    private void cerrar(Pedido p, String quien, String motivo) {
        for (Incidencia i : incidenciaRepo.findByPedidoIdAndOrigenAndEstado(p.getId(), PedidoService.ORIGEN_RECLAMO, "ABIERTA")) {
            i.setEstado("CERRADA");
            i.setCerradaEn(Instant.now());
            i.setCerradaPorUsername(quien);
            i.setMotivoCierre(motivo);
            incidenciaRepo.save(i);
        }
        if (p.isReclamoAbierto()) {
            p.setReclamoEstado("CERRADO");
            pedidoRepo.save(p);
            publisher.publicarPedido(PedidoResponse.fromResumen(p));
            // La app recarga al recibir cualquier evento de viaje: así desaparece el aviso de bloqueo.
            if (p.getCadeteAsignado() != null) {
                publisher.publicarEventoViaje(p.getCadeteAsignado().getId(), "RECLAMO_CERRADO", PedidoResponse.paraCadete(p, false));
            }
        }
    }

    private void exigirReclamoDeProblemaAbierto(Pedido p) {
        if (!"PROBLEMA_ENTREGA".equals(p.getReclamoTipo()) || !p.isReclamoAbierto()) {
            throw new BadRequestException("Este pedido no tiene un reclamo abierto.");
        }
    }

    Pedido get(String pedidoId) {
        return pedidoRepo.findById(pedidoId).orElseThrow(() -> ResourceNotFoundException.of("Pedido", pedidoId));
    }
}
