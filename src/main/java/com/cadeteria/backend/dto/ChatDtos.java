package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.ChatMensaje;

import java.time.Instant;

public final class ChatDtos {

    private ChatDtos() {}

    /** Al menos uno de los tres tiene que venir con contenido — lo valida ChatService. */
    public record MensajeRequest(String texto, String audioUrl, String imagenUrl) {}

    public record MensajeResponse(
            String id, String cadeteId, String autor, String texto, String audioUrl, String imagenUrl,
            Instant enviadoEn, boolean leido
    ) {
        public static MensajeResponse from(ChatMensaje m) {
            return new MensajeResponse(
                    m.getId(), m.getCadete().getId(), m.getAutor().getId(), m.getTexto(), m.getAudioUrl(),
                    m.getImagenUrl(), m.getEnviadoEn(), m.isLeido());
        }
    }
}
