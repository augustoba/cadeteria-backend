package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.WhatsappChip;
import com.cadeteria.backend.model.WhatsappMensaje;
import com.cadeteria.backend.model.WhatsappRespuesta;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/** Gateway propio de WhatsApp (Baileys + chips descartables, ver memoria del proyecto). */
public final class WhatsappDtos {

    private WhatsappDtos() {}

    /** Publicado por STOMP a /topic/whatsapp/comandos — lo consume el gateway (Node). */
    public record ComandoEnvio(String mensajeId, String telefono, String texto) {}

    /** Lo manda el gateway a /app/whatsapp/ack una vez que intentó el envío. */
    public record AckRequest(@NotBlank String mensajeId, boolean ok, String chipUsado, String error, String waMessageId) {}

    /** Endpoint de prueba manual desde el panel, sin pedido asociado. */
    public record TestEnviarRequest(@NotBlank String telefono, @NotBlank String mensaje) {}

    public record EstadoGatewayResponse(boolean conectado, Instant ultimoCambio) {}

    /** Pedido del panel de cargar un chip nuevo. */
    public record VincularChipRequest(@NotBlank String chipId, @NotBlank String numero) {}

    /** Publicado por STOMP a /topic/whatsapp/vincular-chip — lo consume el gateway. */
    public record ComandoVincularChip(String chipId, String numero) {}

    /** Lo manda el gateway a /app/whatsapp/pairing-codigo con el código de 8 dígitos para vincular. */
    public record PairingCodigoRequest(@NotBlank String chipId, @NotBlank String codigo) {}

    /** Lo manda el gateway a /app/whatsapp/chips-estado — snapshot completo o parcial de chips. */
    public record ChipsEstadoRequest(List<ChipEstado> chips) {}

    public record ChipEstado(@NotBlank String chipId, String numero, @NotBlank String estado) {}

    /** Lo manda el gateway a /app/whatsapp/entrega cuando un mensaje se entrega o se lee. */
    public record EntregaRequest(@NotBlank String mensajeId, @NotBlank String tipo) {}

    /** Lo manda el gateway a /app/whatsapp/respuesta cuando un cliente contesta. */
    public record RespuestaEntranteRequest(String chipId, @NotBlank String telefono, @NotBlank String texto) {}

    /** mensajesUltimas24h/entregadosUltimas24h — indicador de "shadowban" (WhatsApp deja de entregar en silencio, sin desconectar el chip). */
    public record ChipResponse(String id, String numero, String estado, String pairingCodigo, Instant ultimoCambioEstado,
                                long mensajesUltimas24h, long entregadosUltimas24h) {
        public static ChipResponse from(WhatsappChip c, long mensajesUltimas24h, long entregadosUltimas24h) {
            return new ChipResponse(c.getId(), c.getNumero(), c.getEstado(), c.getPairingCodigo(), c.getUltimoCambioEstado(),
                    mensajesUltimas24h, entregadosUltimas24h);
        }
    }

    public record MensajeResponse(
            String id, String pedidoId, String telefono, String texto, String estado, String chipUsado, String error,
            Instant creadoEn, Instant enviadoEn, Instant entregadoEn, Instant leidoEn) {
        public static MensajeResponse from(WhatsappMensaje m) {
            return new MensajeResponse(m.getId(), m.getPedidoId(), m.getTelefono(), m.getTexto(), m.getEstado(),
                    m.getChipUsado(), m.getError(), m.getCreadoEn(), m.getEnviadoEn(), m.getEntregadoEn(), m.getLeidoEn());
        }
    }

    public record RespuestaResponse(String id, String telefono, String chipId, String texto, Instant recibidoEn) {
        public static RespuestaResponse from(WhatsappRespuesta r) {
            return new RespuestaResponse(r.getId(), r.getTelefono(), r.getChipId(), r.getTexto(), r.getRecibidoEn());
        }
    }

    public record MensajesPaginaResponse(List<MensajeResponse> items, long total, int pagina, int totalPaginas) {}

    public record RespuestasPaginaResponse(List<RespuestaResponse> items, long total, int pagina, int totalPaginas) {}
}
