package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Gateway propio de WhatsApp (Baileys + chips descartables, ver memoria del proyecto). */
public final class WhatsappDtos {

    private WhatsappDtos() {}

    /** Publicado por STOMP a /topic/whatsapp/comandos — lo consume el gateway (Node). */
    public record ComandoEnvio(String mensajeId, String telefono, String texto) {}

    /** Lo manda el gateway a /app/whatsapp/ack una vez que intentó el envío. */
    public record AckRequest(@NotBlank String mensajeId, boolean ok, String chipUsado, String error) {}

    /** Endpoint de prueba manual desde el panel, sin pedido asociado. */
    public record TestEnviarRequest(@NotBlank String telefono, @NotBlank String mensaje) {}

    public record EstadoGatewayResponse(boolean conectado, Instant ultimoCambio) {}
}
