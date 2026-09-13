package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Cliente del gateway de SMS propio (android-sms-gateway sobre un Android dedicado,
 * diseno-tecnico.md sección 6/8). Se llama de forma asincrona ({@link Async}) para que,
 * si el telefono esta sin senal o el gateway no responde, no bloquee ni rompa el flujo
 * de aceptar/finalizar un pedido.
 * <p>
 * Reintenta unas pocas veces con una espera corta entre intento e intento (todo dentro
 * del thread async, no bloquea el flujo principal); si se agotan los reintentos, marca
 * el pedido con `smsFallido = true` para que el admin vea un ícono de aviso en el
 * dashboard en vez de que el fallo se pierda en silencio en el log.
 * <p>
 * OJO: el cuerpo del POST de abajo es el formato tipico de ese proyecto (id/message/
 * phoneNumbers), pero conviene verificarlo contra la documentacion que expone el
 * propio gateway una vez instalado, por si la version usada difiere.
 */
@Service
public class SmsGatewayService {

    private static final Logger log = LoggerFactory.getLogger(SmsGatewayService.class);
    private static final int INTENTOS_MAXIMOS = 3;
    private static final long ESPERA_ENTRE_INTENTOS_MS = 3_000L;

    private final AppProperties props;
    private final PedidoRepository pedidoRepo;
    private final RestClient restClient = RestClient.create();

    public SmsGatewayService(AppProperties props, PedidoRepository pedidoRepo) {
        this.props = props;
        this.pedidoRepo = pedidoRepo;
    }

    /** Uso sin pedido asociado (no hay donde marcar el fallo, solo se loguea). */
    @Async
    public void enviar(String telefono, String mensaje) {
        enviarConReintentos(telefono, mensaje);
    }

    /** Uso normal: si se agotan los reintentos, deja marcado `pedido.smsFallido` para el dashboard. */
    @Async
    public void enviar(String pedidoId, String telefono, String mensaje) {
        boolean ok = enviarConReintentos(telefono, mensaje);
        if (!ok) marcarFallido(pedidoId);
    }

    private boolean enviarConReintentos(String telefono, String mensaje) {
        if (!props.getSms().isEnabled() || props.getSms().getGatewayUrl().isBlank()) {
            log.info("SMS gateway deshabilitado (app.sms.enabled=false) — no se envia a {}: {}", telefono, mensaje);
            return true; // deshabilitado a propósito no cuenta como fallo
        }
        for (int intento = 1; intento <= INTENTOS_MAXIMOS; intento++) {
            try {
                restClient.post()
                        .uri(props.getSms().getGatewayUrl() + "/message")
                        .headers(h -> h.setBasicAuth(props.getSms().getGatewayUser(), props.getSms().getGatewayPassword()))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(Map.of(
                                "message", mensaje,
                                "phoneNumbers", List.of(telefono)
                        ))
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (Exception e) {
                log.error("Fallo el envio de SMS a {} (intento {}/{}): {}", telefono, intento, INTENTOS_MAXIMOS, e.getMessage());
                if (intento < INTENTOS_MAXIMOS) dormir(ESPERA_ENTRE_INTENTOS_MS);
            }
        }
        log.error("Se agotaron los reintentos de SMS a {} — el mensaje no se pudo enviar.", telefono);
        return false;
    }

    private void marcarFallido(String pedidoId) {
        pedidoRepo.findById(pedidoId).ifPresent(p -> {
            p.setSmsFallido(true);
            pedidoRepo.save(p);
        });
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
