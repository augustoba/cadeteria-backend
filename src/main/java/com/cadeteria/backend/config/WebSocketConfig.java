package com.cadeteria.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Hub STOMP unico para ubicacion en tiempo real, eventos de asignacion y chat
 * (diseno-tecnico.md sección 4). El endpoint se expone tanto con SockJS (para el
 * front admin en el navegador) como sin el (para el cliente STOMP nativo de la app
 * de cadetes en Android).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WhatsappGatewayAuthInterceptor whatsappGatewayAuthInterceptor;

    public WebSocketConfig(WhatsappGatewayAuthInterceptor whatsappGatewayAuthInterceptor) {
        this.whatsappGatewayAuthInterceptor = whatsappGatewayAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(whatsappGatewayAuthInterceptor);
    }
}
