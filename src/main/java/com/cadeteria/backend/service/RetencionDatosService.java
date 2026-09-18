package com.cadeteria.backend.service;

import com.cadeteria.backend.model.ChatMensaje;
import com.cadeteria.backend.model.WhatsappMensaje;
import com.cadeteria.backend.repository.ChatMensajeRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.repository.WhatsappMensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Purga el historial de WhatsApp y chat interno más viejo que lo configurado (mejora
 * 2026-09-17, pedida por el dueño) — apagado por defecto (0 días = nunca borrar, el
 * comportamiento de siempre). No es por espacio en disco (a este volumen la tabla
 * aguanta años sin problema), es por no acumular teléfonos y conversaciones de clientes
 * para siempre sin que nadie lo haya decidido.
 * <p>
 * Nunca borra un WhatsApp ligado a un pedido que todavía no terminó (FINALIZADO o
 * CANCELADO) — aunque sea viejo, mientras el pedido siga abierto puede hacer falta
 * revisar ese historial.
 */
@Service
public class RetencionDatosService {

    private static final Logger log = LoggerFactory.getLogger(RetencionDatosService.class);

    private final WhatsappMensajeRepository whatsappRepo;
    private final ChatMensajeRepository chatRepo;
    private final PedidoRepository pedidoRepo;
    private final ConfiguracionService configuracionService;

    public RetencionDatosService(WhatsappMensajeRepository whatsappRepo, ChatMensajeRepository chatRepo,
                                  PedidoRepository pedidoRepo, ConfiguracionService configuracionService) {
        this.whatsappRepo = whatsappRepo;
        this.chatRepo = chatRepo;
        this.pedidoRepo = pedidoRepo;
        this.configuracionService = configuracionService;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgarMensajesViejos() {
        purgarWhatsapp();
        purgarChat();
    }

    private void purgarWhatsapp() {
        int dias = configuracionService.getInt("retencion_whatsapp_dias", 0);
        if (dias <= 0) return;
        Instant corte = Instant.now().minus(dias, ChronoUnit.DAYS);
        List<WhatsappMensaje> candidatos = whatsappRepo.findByCreadoEnBefore(corte);
        List<WhatsappMensaje> aBorrar = candidatos.stream()
                .filter(m -> m.getPedidoId() == null || pedidoTerminado(m.getPedidoId()))
                .toList();
        if (aBorrar.isEmpty()) return;
        whatsappRepo.deleteAll(aBorrar);
        log.info("Retención: borrados {} mensajes de WhatsApp más viejos que {} días (de {} candidatos, el resto sigue ligado a un pedido abierto).",
                aBorrar.size(), dias, candidatos.size());
    }

    private boolean pedidoTerminado(String pedidoId) {
        return pedidoRepo.findById(pedidoId)
                .map(p -> "FINALIZADO".equals(p.getEstado().getId()) || "CANCELADO".equals(p.getEstado().getId()))
                .orElse(true); // el pedido ya no existe -> no hay motivo para retener el mensaje
    }

    private void purgarChat() {
        int dias = configuracionService.getInt("retencion_chat_dias", 0);
        if (dias <= 0) return;
        Instant corte = Instant.now().minus(dias, ChronoUnit.DAYS);
        List<ChatMensaje> viejos = chatRepo.findByEnviadoEnBefore(corte);
        if (viejos.isEmpty()) return;
        chatRepo.deleteAll(viejos);
        log.info("Retención: borrados {} mensajes de chat interno más viejos que {} días.", viejos.size(), dias);
    }
}
