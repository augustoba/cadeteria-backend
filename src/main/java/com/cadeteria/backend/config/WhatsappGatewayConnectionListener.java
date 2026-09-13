package com.cadeteria.backend.config;

import com.cadeteria.backend.service.WhatsappGatewayService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Detecta cuándo el gateway de WhatsApp (la PC local) está conectado: se suscribe a
 * /topic/whatsapp/comandos con el token correcto (verificado por
 * WhatsappGatewayAuthInterceptor, que deja la marca en los atributos de sesión). No se
 * usa SessionConnectedEvent porque el panel y la app de cadetes también se conectan al
 * mismo endpoint /ws — lo que identifica al gateway es la suscripción a ESE destino con
 * el token válido, no la mera conexión.
 */
@Component
public class WhatsappGatewayConnectionListener {

    private static final String DESTINO_COMANDOS = "/topic/whatsapp/comandos";
    private static final String ATRIBUTO_ES_GATEWAY = "esGatewayWhatsapp";

    private final WhatsappGatewayService service;
    private final AtomicReference<String> sessionIdGateway = new AtomicReference<>();

    public WhatsappGatewayConnectionListener(WhatsappGatewayService service) {
        this.service = service;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (!DESTINO_COMANDOS.equals(accessor.getDestination())) return;

        boolean autorizado = accessor.getSessionAttributes() != null
                && Boolean.TRUE.equals(accessor.getSessionAttributes().get(ATRIBUTO_ES_GATEWAY));
        if (!autorizado) return;

        sessionIdGateway.set(accessor.getSessionId());
        service.marcarConectado();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getSessionId() != null && event.getSessionId().equals(sessionIdGateway.get())) {
            sessionIdGateway.set(null);
            service.marcarDesconectado();
        }
    }
}
