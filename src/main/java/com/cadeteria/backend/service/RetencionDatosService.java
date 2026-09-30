package com.cadeteria.backend.service;

import com.cadeteria.backend.model.ChatMensaje;
import com.cadeteria.backend.model.Pedido;
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
    private final CloudinaryService cloudinaryService;

    public RetencionDatosService(WhatsappMensajeRepository whatsappRepo, ChatMensajeRepository chatRepo,
                                  PedidoRepository pedidoRepo, ConfiguracionService configuracionService,
                                  CloudinaryService cloudinaryService) {
        this.whatsappRepo = whatsappRepo;
        this.chatRepo = chatRepo;
        this.pedidoRepo = pedidoRepo;
        this.configuracionService = configuracionService;
        this.cloudinaryService = cloudinaryService;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgarMensajesViejos() {
        purgarWhatsapp();
        purgarChat();
        purgarArchivosChat();
        purgarImagenesPedidos();
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
        // Antes solo se borraba la fila y la foto o el audio quedaban en Cloudinary para siempre (2026-09-29).
        viejos.forEach(m -> {
            cloudinaryService.borrarSiCorresponde(m.getImagenUrl());
            cloudinaryService.borrarSiCorresponde(m.getAudioUrl());
        });
        chatRepo.deleteAll(viejos);
        log.info("Retención: borrados {} mensajes de chat interno más viejos que {} días.", viejos.size(), dias);
    }

    /** Días que duran las fotos y audios del chat (2026-09-29); el texto sigue según retencion_chat_dias. 0 = nunca. */
    static final String CLAVE_ARCHIVOS_CHAT_DIAS = "retencion_chat_archivos_dias";

    /**
     * Fotos y notas de voz del chat más viejas que {@link #CLAVE_ARCHIVOS_CHAT_DIAS} (30 por defecto): se
     * borran de Cloudinary —son lo que llena el plan gratis— y el mensaje queda con un texto que lo explica,
     * así la conversación no pierde el hilo.
     */
    private void purgarArchivosChat() {
        int dias = configuracionService.getInt(CLAVE_ARCHIVOS_CHAT_DIAS, 30);
        if (dias <= 0) return;
        List<ChatMensaje> conArchivos = chatRepo.findConArchivosEnviadosAntesDe(Instant.now().minus(dias, ChronoUnit.DAYS));
        if (conArchivos.isEmpty()) return;
        for (ChatMensaje m : conArchivos) {
            String nota = null;
            if (m.getImagenUrl() != null) {
                cloudinaryService.borrarSiCorresponde(m.getImagenUrl());
                m.setImagenUrl(null);
                nota = "📷 Foto borrada (tenía más de " + dias + " días)";
            }
            if (m.getAudioUrl() != null) {
                cloudinaryService.borrarSiCorresponde(m.getAudioUrl());
                m.setAudioUrl(null);
                nota = "🎤 Audio borrado (tenía más de " + dias + " días)";
            }
            if (m.getTexto() == null || m.getTexto().isBlank()) m.setTexto(nota);
            else m.setTexto(m.getTexto() + "\n" + nota);
        }
        chatRepo.saveAll(conArchivos);
        log.info("Retención: borradas las fotos/audios de {} mensajes de chat de más de {} días.", conArchivos.size(), dias);
    }

    /**
     * Borra las fotos/firma de pedidos ya terminados (mejora 2026-09-23) — nunca toca un
     * pedido todavía abierto. Intenta borrar el archivo de Cloudinary de verdad
     * (CloudinaryService, no-op si no hay api_key/api_secret configurados todavía) y en
     * cualquier caso limpia la referencia en la base, para no acumular fotos de clientes
     * para siempre sin que nadie lo haya decidido — mismo espíritu que purgarWhatsapp/Chat.
     */
    private void purgarImagenesPedidos() {
        int dias = configuracionService.getInt("retencion_imagenes_pedido_dias", 60);
        if (dias <= 0) return;
        Instant corte = Instant.now().minus(dias, ChronoUnit.DAYS);
        List<Pedido> candidatos = pedidoRepo.findConImagenesTerminadosAntesDe(corte);
        if (candidatos.isEmpty()) return;
        for (Pedido p : candidatos) {
            cloudinaryService.borrarSiCorresponde(p.getFotoRecepcionUrl());
            cloudinaryService.borrarSiCorresponde(p.getEntregaFotoUrl());
            cloudinaryService.borrarSiCorresponde(p.getFirmaReceptorUrl());
            p.setFotoRecepcionUrl(null);
            p.setEntregaFotoUrl(null);
            p.setFirmaReceptorUrl(null);
        }
        pedidoRepo.saveAll(candidatos);
        log.info("Retención: limpiadas las imágenes de {} pedidos terminados hace más de {} días.", candidatos.size(), dias);
    }
}
