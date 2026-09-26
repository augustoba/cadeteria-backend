package com.cadeteria.backend.dto;

import jakarta.validation.constraints.Size;
import com.cadeteria.backend.model.ChatMensaje;

import java.time.Instant;

public final class ChatDtos {

    private ChatDtos() {}

    /** Al menos uno de los tres tiene que venir con contenido — lo valida ChatService. */
    public record MensajeRequest(
            @Size(max = 2000, message = "El mensaje puede tener hasta 2000 caracteres.") String texto,
            @Size(max = 500) String audioUrl, @Size(max = 500) String imagenUrl) {}

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
