package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.dto.ChatDtos.MensajeResponse;
import com.cadeteria.backend.model.AutorMensaje;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.ChatMensaje;
import com.cadeteria.backend.repository.AutorMensajeRepository;
import com.cadeteria.backend.repository.ChatMensajeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat interno 1 a 1 admin-cadete (spec 5.5), reutilizando el mismo hub STOMP que la
 * ubicacion en tiempo real. Push FCM cuando la app del cadete esta en background/cerrada.
 */
@Service
@Transactional
public class ChatService {

    private final ChatMensajeRepository repo;
    private final AutorMensajeRepository autorRepo;
    private final CadeteService cadeteService;
    private final WebSocketPublisher publisher;
    private final FcmService fcmService;

    public ChatService(ChatMensajeRepository repo, AutorMensajeRepository autorRepo, CadeteService cadeteService,
                        WebSocketPublisher publisher, FcmService fcmService) {
        this.repo = repo;
        this.autorRepo = autorRepo;
        this.cadeteService = cadeteService;
        this.publisher = publisher;
        this.fcmService = fcmService;
    }

    @Transactional(readOnly = true)
    public List<ChatMensaje> historial(String cadeteId) {
        return repo.findByCadeteIdOrderByEnviadoEnAsc(cadeteId);
    }

    /** Para el badge de "mensajes sin leer" en la navegación del panel. */
    @Transactional(readOnly = true)
    public long contarNoLeidosPorAdmin() {
        return repo.countByAutorIdAndLeidoFalse("CADETE");
    }

    /**
     * Cuántos mensajes sin leer hay POR cadete (auditoría UX 2026-09-13) — antes el
     * badge del nav avisaba que había algo nuevo pero, al entrar al Chat, no se veía en
     * la lista de cadetes cuál era el que había escrito.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> noLeidosPorCadete() {
        return repo.findByAutorIdAndLeidoFalse("CADETE").stream()
                .collect(java.util.stream.Collectors.groupingBy(m -> m.getCadete().getId(), java.util.stream.Collectors.counting()));
    }

    public ChatMensaje enviar(String cadeteId, String autorId, String texto, String audioUrl, String imagenUrl) {
        boolean sinTexto = texto == null || texto.isBlank();
        boolean sinAudio = audioUrl == null || audioUrl.isBlank();
        boolean sinImagen = imagenUrl == null || imagenUrl.isBlank();
        if (sinTexto && sinAudio && sinImagen) {
            throw new BadRequestException("El mensaje no puede estar vacío.");
        }
        Cadete cadete = cadeteService.get(cadeteId);
        AutorMensaje autor = autorRepo.findById(autorId)
                .orElseThrow(() -> new BadRequestException("Autor de mensaje inválido: " + autorId));
        ChatMensaje m = new ChatMensaje();
        m.setId(UUID.randomUUID().toString());
        m.setCadete(cadete);
        m.setAutor(autor);
        m.setTexto(sinTexto ? null : texto.trim());
        m.setAudioUrl(sinAudio ? null : audioUrl.trim());
        m.setImagenUrl(sinImagen ? null : imagenUrl.trim());
        m = repo.save(m);

        MensajeResponse dto = MensajeResponse.from(m);
        publisher.publicarMensajeChat(cadeteId, dto);
        if ("ADMIN".equals(autorId)) {
            String aviso = !sinTexto ? texto : !sinImagen ? "📷 Foto" : "🎤 Nota de voz";
            fcmService.enviar(cadete.getFcmToken(), "Nuevo mensaje", aviso, Map.of("tipo", "CHAT"));
        }
        return m;
    }

    public void marcarLeido(String cadeteId, String autorQueLee) {
        // Si lee el admin, marca leidos los mensajes del cadete; si lee el cadete, los del admin.
        String autorContrario = "ADMIN".equals(autorQueLee) ? "CADETE" : "ADMIN";
        for (ChatMensaje m : repo.findByCadeteIdOrderByEnviadoEnAsc(cadeteId)) {
            if (!m.isLeido() && m.getAutor().getId().equals(autorContrario)) {
                m.setLeido(true);
                repo.save(m);
            }
        }
    }
}
