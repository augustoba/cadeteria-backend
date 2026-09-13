package com.cadeteria.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * El endpoint /ws es permitAll a nivel HTTP (SecurityConfig) porque lo comparten el
 * panel, la app de cadetes y ahora el gateway de WhatsApp. Sin este interceptor,
 * cualquiera que encuentre el endpoint podria suscribirse a /topic/whatsapp/comandos
 * (veria telefonos y textos de clientes en texto plano) o mandar acks falsos a
 * /app/whatsapp/ack. Se exige un token compartido (app.whatsapp.gateway-token) solo en
 * el CONNECT, y solo los destinos de whatsapp lo verifican despues — el resto de los
 * topics/queues (chat, ubicaciones, etc.) sigue igual que antes.
 */
@Component
public class WhatsappGatewayAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WhatsappGatewayAuthInterceptor.class);
    private static final String ATRIBUTO_ES_GATEWAY = "esGatewayWhatsapp";
    private static final String DESTINO_COMANDOS = "/topic/whatsapp/comandos";
    private static final String DESTINO_ACK = "/app/whatsapp/ack";

    private final AppProperties props;

    public WhatsappGatewayAuthInterceptor(AppProperties props) {
        this.props = props;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("gateway-token");
            String esperado = props.getWhatsapp().getGatewayToken();
            if (token != null && !esperado.isBlank() && token.equals(esperado)) {
                accessor.getSessionAttributes().put(ATRIBUTO_ES_GATEWAY, Boolean.TRUE);
            }
            return message;
        }

        boolean esDestinoWhatsapp = DESTINO_COMANDOS.equals(accessor.getDestination())
                || DESTINO_ACK.equals(accessor.getDestination());
        if (!esDestinoWhatsapp) return message;

        boolean autorizado = accessor.getSessionAttributes() != null
                && Boolean.TRUE.equals(accessor.getSessionAttributes().get(ATRIBUTO_ES_GATEWAY));
        if (!autorizado) {
            log.warn("Conexion sin token valido intento {} en {} (sessionId={})",
                    accessor.getCommand(), accessor.getDestination(), accessor.getSessionId());
            return null; // descarta el frame en silencio, no rompe la conexion STOMP entera
        }
        return message;
    }
}
