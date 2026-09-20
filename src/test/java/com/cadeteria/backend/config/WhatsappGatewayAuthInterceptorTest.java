package com.cadeteria.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * El interceptor existe para que nadie sin token pueda leer los destinos del gateway de
 * WhatsApp, pero corre sobre TODOS los frames de ALL los clientes (panel, app de cadetes,
 * gateway). El bug que motivó estos tests: `DESTINOS_GATEWAY` es un `Set.of(...)` y los sets
 * inmutables de Java tiran NullPointerException si les pasás null — a diferencia de un
 * HashSet, que devuelve false. Un frame sin destino (heart-beat, DISCONNECT) hacía explotar
 * el preSend, y la excepción tumbaba la sesión STOMP entera: la app se reconectaba y se
 * volvía a caer cada ~15s, así que casi nunca estaba suscripta cuando el backend publicaba.
 * El síntoma visible era "el cadete sigue viendo el viaje que ya le sacaron".
 */
class WhatsappGatewayAuthInterceptorTest {

    private WhatsappGatewayAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getWhatsapp().setGatewayToken("token-de-prueba");
        interceptor = new WhatsappGatewayAuthInterceptor(props);
    }

    private Message<?> frame(StompCommand comando, Map<String, Object> sesion) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(comando);
        if (sesion != null) accessor.setSessionAttributes(sesion);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /** El caso del bug: un heart-beat no tiene destino, y no puede hacer explotar el preSend. */
    @Test
    void unFrameSinDestinoNoRompe() {
        Message<?> heartBeat = MessageBuilder.withPayload("\n").build();
        Message<?> resultado = interceptor.preSend(heartBeat, null);
        assertNotNull(resultado, "un frame sin destino tiene que pasar tal cual, no descartarse ni romper");
    }

    /** Lo mismo para DISCONNECT, que el cliente manda sin destino y es un frame real. */
    @Test
    void unDisconnectSinDestinoNoRompe() {
        Message<?> disconnect = frame(StompCommand.DISCONNECT, new HashMap<>());
        assertNotNull(interceptor.preSend(disconnect, null));
    }

    /** Y un frame normal de otra pantalla sigue pasando igual. */
    @Test
    void unDestinoComunPasa() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin/pedidos");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertSame(msg, interceptor.preSend(msg, null));
    }

    /** El motivo por el que el interceptor existe: sin token, un destino del gateway se descarta. */
    @Test
    void unDestinoDelGatewaySinTokenSeDescarta() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/whatsapp/comandos");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertNull(interceptor.preSend(msg, null), "sin token, el frame del gateway tiene que descartarse");
    }

    /** Y con el token correcto en el CONNECT, el mismo destino sí pasa. */
    @Test
    void unDestinoDelGatewayConTokenPasa() {
        Map<String, Object> sesion = new HashMap<>();

        StompHeaderAccessor conectar = StompHeaderAccessor.create(StompCommand.CONNECT);
        conectar.setSessionAttributes(sesion);
        conectar.addNativeHeader("gateway-token", "token-de-prueba");
        conectar.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], conectar.getMessageHeaders()), null);

        StompHeaderAccessor acceso = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acceso.setDestination("/topic/whatsapp/comandos");
        acceso.setSessionAttributes(sesion);
        acceso.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], acceso.getMessageHeaders());
        assertSame(msg, interceptor.preSend(msg, null));
    }
}
