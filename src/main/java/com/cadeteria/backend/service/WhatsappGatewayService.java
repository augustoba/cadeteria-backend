package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.WhatsappDtos.ChipEstado;
import com.cadeteria.backend.dto.WhatsappDtos.ChipResponse;
import com.cadeteria.backend.dto.WhatsappDtos.ComandoEnvio;
import com.cadeteria.backend.dto.WhatsappDtos.ComandoVincularChip;
import com.cadeteria.backend.dto.WhatsappDtos.EstadoGatewayResponse;
import com.cadeteria.backend.dto.WhatsappDtos.MensajeResponse;
import com.cadeteria.backend.dto.WhatsappDtos.MensajesPaginaResponse;
import com.cadeteria.backend.dto.WhatsappDtos.RespuestaResponse;
import com.cadeteria.backend.dto.WhatsappDtos.RespuestasPaginaResponse;
import com.cadeteria.backend.model.WhatsappChip;
import com.cadeteria.backend.model.WhatsappMensaje;
import com.cadeteria.backend.model.WhatsappRespuesta;
import com.cadeteria.backend.repository.WhatsappChipRepository;
import com.cadeteria.backend.repository.WhatsappMensajeRepository;
import com.cadeteria.backend.repository.WhatsappMensajeRepository.EstadisticaChipRow;
import com.cadeteria.backend.repository.WhatsappRespuestaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gateway propio de WhatsApp (Baileys + chips descartables en una PC local, ver memoria
 * del proyecto "whatsapp-gateway"). Cada mensaje se guarda en cola ANTES de publicarlo
 * por STOMP porque el backend (en la nube) no sabe en tiempo real si la PC local está
 * prendida — si el gateway está desconectado en ese momento, el mensaje queda PENDIENTE
 * y se reenvía solo cuando reconecta (ver reenviarPendientes, llamado desde
 * WhatsappGatewayConnectionListener).
 */
@Service
@Transactional
public class WhatsappGatewayService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappGatewayService.class);

    private final WhatsappMensajeRepository repo;
    private final WhatsappChipRepository chipRepo;
    private final WhatsappRespuestaRepository respuestaRepo;
    private final WebSocketPublisher publisher;

    /** Estado de conexión en memoria — no persiste entre reinicios del backend, no hace falta. */
    private final AtomicReference<Instant> ultimoCambioEstado = new AtomicReference<>(null);
    private volatile boolean conectado = false;

    public WhatsappGatewayService(WhatsappMensajeRepository repo, WhatsappChipRepository chipRepo,
                                   WhatsappRespuestaRepository respuestaRepo, WebSocketPublisher publisher) {
        this.repo = repo;
        this.chipRepo = chipRepo;
        this.respuestaRepo = respuestaRepo;
        this.publisher = publisher;
    }

    /** Uso normal: encola y publica. Sin pedido asociado, pasar pedidoId null (ej. envío de prueba). */
    public WhatsappMensaje enviar(String telefono, String texto, String pedidoId) {
        WhatsappMensaje m = new WhatsappMensaje();
        m.setId(UUID.randomUUID().toString());
        m.setPedidoId(pedidoId);
        m.setTelefono(telefono);
        m.setTexto(texto);
        repo.save(m);
        publicarComando(m);
        return m;
    }

    private void publicarComando(WhatsappMensaje m) {
        publisher.publicarComandoWhatsapp(new ComandoEnvio(m.getId(), m.getTelefono(), m.getTexto()));
    }

    /** Llamado por el gateway vía /app/whatsapp/ack cuando intentó el envío (ok o no). */
    public void confirmar(String mensajeId, boolean ok, String chipUsado, String error, String waMessageId) {
        repo.findById(mensajeId).ifPresentOrElse(m -> {
            m.setEstado(ok ? "ENVIADO" : "FALLIDO");
            m.setChipUsado(chipUsado);
            m.setError(error);
            m.setWaMessageId(waMessageId);
            m.setEnviadoEn(Instant.now());
            repo.save(m);
            publisher.publicarPanelWhatsapp("MENSAJE");
        }, () -> log.warn("Ack de WhatsApp para un mensaje que no existe (id={})", mensajeId));
    }

    /** Llamado por WhatsappGatewayConnectionListener cuando el gateway se suscribe (se conecta). */
    public void marcarConectado() {
        conectado = true;
        ultimoCambioEstado.set(Instant.now());
        log.info("Gateway de WhatsApp conectado.");
        reenviarPendientes();
    }

    /** Llamado por WhatsappGatewayConnectionListener cuando se cae la conexión del gateway. */
    public void marcarDesconectado() {
        conectado = false;
        ultimoCambioEstado.set(Instant.now());
        log.warn("Gateway de WhatsApp desconectado.");
    }

    @Transactional(readOnly = true)
    public void reenviarPendientes() {
        List<WhatsappMensaje> pendientes = repo.findByEstadoOrderByCreadoEnAsc("PENDIENTE");
        if (!pendientes.isEmpty()) {
            log.info("Reenviando {} mensaje(s) de WhatsApp pendiente(s) al reconectar el gateway.", pendientes.size());
        }
        pendientes.forEach(this::publicarComando);
    }

    @Transactional(readOnly = true)
    public EstadoGatewayResponse estado() {
        return new EstadoGatewayResponse(conectado, ultimoCambioEstado.get());
    }

    /** Pedido del panel de cargar un chip nuevo — queda VINCULANDO hasta que el gateway confirme la conexión. */
    public ChipResponse vincularChip(String chipId, String numero) {
        WhatsappChip chip = chipRepo.findById(chipId).orElseGet(WhatsappChip::new);
        chip.setId(chipId);
        chip.setNumero(numero);
        chip.setEstado("VINCULANDO");
        chip.setPairingCodigo(null);
        chip.setUltimoCambioEstado(Instant.now());
        chipRepo.save(chip);
        publisher.publicarComandoVincularChip(new ComandoVincularChip(chipId, numero));
        publisher.publicarPanelWhatsapp("CHIP");
        return ChipResponse.from(chip, 0, 0);
    }

    /** Llamado por /app/whatsapp/pairing-codigo con el código de 8 dígitos para mostrar en el panel. */
    public void guardarPairingCodigo(String chipId, String codigo) {
        chipRepo.findById(chipId).ifPresent(chip -> {
            chip.setPairingCodigo(codigo);
            chipRepo.save(chip);
            publisher.publicarPanelWhatsapp("CHIP");
        });
    }

    /** Llamado por /app/whatsapp/chips-estado — el gateway informa conectado/desconectado/baneado por chip. */
    public void actualizarEstadoChips(List<ChipEstado> chips) {
        for (ChipEstado ce : chips) {
            WhatsappChip chip = chipRepo.findById(ce.chipId()).orElseGet(WhatsappChip::new);
            String estadoAnterior = chip.getId() == null ? null : chip.getEstado();
            chip.setId(ce.chipId());
            if (ce.numero() != null) chip.setNumero(ce.numero());
            chip.setEstado(ce.estado());
            chip.setUltimoCambioEstado(Instant.now());
            if ("CONECTADO".equals(ce.estado())) chip.setPairingCodigo(null);
            chipRepo.save(chip);
            // Alerta visible solo en la transición A baneado, no en cada snapshot periódico (evita spamear el mismo aviso cada 60s).
            if ("BANEADO".equals(ce.estado()) && !"BANEADO".equals(estadoAnterior)) {
                publisher.publicarAlertaChipWhatsappBaneado(chip.getId(), chip.getNumero());
            }
        }
        publisher.publicarPanelWhatsapp("CHIP");
    }

    /** El chip se rompió o no se va a usar más — le pide al gateway que cierre sesión y borre la carpeta local. */
    public void darDeBajaChip(String chipId) {
        chipRepo.findById(chipId).ifPresent(chip -> {
            chip.setEstado("BAJA");
            chip.setPairingCodigo(null);
            chip.setUltimoCambioEstado(Instant.now());
            chipRepo.save(chip);
            publisher.publicarComandoDarDeBajaChip(chipId);
            publisher.publicarPanelWhatsapp("CHIP");
        });
    }

    /**
     * Borra el registro definitivamente (mejora: sin esto, la lista de chips crece para
     * siempre con chips rotos que ya nadie va a volver a usar). Solo se permite si ya
     * está BAJA — si todavía está conectado/vinculando hay que darlo de baja primero
     * (eso es lo que le avisa al gateway que cierre la sesión de WhatsApp).
     */
    public void eliminarChip(String chipId) {
        WhatsappChip chip = chipRepo.findById(chipId).orElseThrow(() -> ResourceNotFoundException.of("Chip", chipId));
        if (!"BAJA".equals(chip.getEstado())) {
            throw new BadRequestException("Primero hay que dar de baja el chip antes de eliminarlo.");
        }
        chipRepo.delete(chip);
        publisher.publicarPanelWhatsapp("CHIP");
    }

    /** Llamado por /app/whatsapp/entrega cuando un mensaje se marca entregado o leído. */
    public void registrarEntrega(String mensajeId, String tipo) {
        repo.findById(mensajeId).ifPresent(m -> {
            Instant ahora = Instant.now();
            if ("LEIDO".equals(tipo)) {
                m.setLeidoEn(ahora);
                if (m.getEntregadoEn() == null) m.setEntregadoEn(ahora);
            } else if ("ENTREGADO".equals(tipo) && m.getEntregadoEn() == null) {
                m.setEntregadoEn(ahora);
            }
            repo.save(m);
            publisher.publicarPanelWhatsapp("MENSAJE");
        });
    }

    /** Llamado por /app/whatsapp/respuesta cuando un cliente contesta un mensaje. */
    public void registrarRespuesta(String chipId, String telefono, String texto) {
        WhatsappRespuesta r = new WhatsappRespuesta();
        r.setId(UUID.randomUUID().toString());
        r.setChipId(chipId);
        r.setTelefono(telefono);
        r.setTexto(texto);
        respuestaRepo.save(r);
        publisher.publicarPanelWhatsapp("RESPUESTA");
    }

    /** mensajesUltimas24h/entregadosUltimas24h por chip — indicador de "shadowban" (ver ChipResponse). */
    @Transactional(readOnly = true)
    public List<ChipResponse> chips() {
        Map<String, EstadisticaChipRow> stats = repo.estadisticasPorChip(Instant.now().minus(24, ChronoUnit.HOURS)).stream()
                .collect(java.util.stream.Collectors.toMap(EstadisticaChipRow::getChipId, r -> r));
        return chipRepo.findAllByOrderByIdAsc().stream()
                .map(c -> {
                    EstadisticaChipRow row = stats.get(c.getId());
                    return ChipResponse.from(c, row == null ? 0 : row.getMandados(), row == null ? 0 : row.getEntregados());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MensajesPaginaResponse mensajes(String telefono, Instant desde, Instant hasta, int pagina, int tamano) {
        int paginaSegura = Math.max(0, pagina);
        int tamanoSeguro = Math.min(Math.max(1, tamano), 100);
        Page<WhatsappMensaje> page = repo.pagina(vacioANull(telefono), desde, hasta,
                PageRequest.of(paginaSegura, tamanoSeguro, Sort.by(Sort.Direction.DESC, "creadoEn")));
        return new MensajesPaginaResponse(page.getContent().stream().map(MensajeResponse::from).toList(),
                page.getTotalElements(), paginaSegura, page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RespuestasPaginaResponse respuestas(String telefono, Instant desde, Instant hasta, int pagina, int tamano) {
        int paginaSegura = Math.max(0, pagina);
        int tamanoSeguro = Math.min(Math.max(1, tamano), 100);
        Page<WhatsappRespuesta> page = respuestaRepo.pagina(vacioANull(telefono), desde, hasta,
                PageRequest.of(paginaSegura, tamanoSeguro, Sort.by(Sort.Direction.DESC, "recibidoEn")));
        return new RespuestasPaginaResponse(page.getContent().stream().map(RespuestaResponse::from).toList(),
                page.getTotalElements(), paginaSegura, page.getTotalPages());
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
