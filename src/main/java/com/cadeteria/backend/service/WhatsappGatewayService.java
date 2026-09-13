package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.WhatsappDtos.ComandoEnvio;
import com.cadeteria.backend.dto.WhatsappDtos.EstadoGatewayResponse;
import com.cadeteria.backend.model.WhatsappMensaje;
import com.cadeteria.backend.repository.WhatsappMensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
    private final WebSocketPublisher publisher;

    /** Estado de conexión en memoria — no persiste entre reinicios del backend, no hace falta. */
    private final AtomicReference<Instant> ultimoCambioEstado = new AtomicReference<>(null);
    private volatile boolean conectado = false;

    public WhatsappGatewayService(WhatsappMensajeRepository repo, WebSocketPublisher publisher) {
        this.repo = repo;
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
    public void confirmar(String mensajeId, boolean ok, String chipUsado, String error) {
        repo.findById(mensajeId).ifPresentOrElse(m -> {
            m.setEstado(ok ? "ENVIADO" : "FALLIDO");
            m.setChipUsado(chipUsado);
            m.setError(error);
            m.setEnviadoEn(Instant.now());
            repo.save(m);
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
}
