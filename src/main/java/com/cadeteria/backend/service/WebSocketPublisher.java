package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.CadeteDtos.CadeteResponse;
import com.cadeteria.backend.dto.ChatDtos.MensajeResponse;
import com.cadeteria.backend.dto.PedidoDtos.PedidoResponse;
import com.cadeteria.backend.dto.WhatsappDtos.ComandoEnvio;
import com.cadeteria.backend.dto.WhatsappDtos.ComandoVincularChip;
import com.cadeteria.backend.model.Cadete;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publica en los topics/queues STOMP definidos en diseno-tecnico.md sección 4:
 * ubicacion en tiempo real para el admin, eventos de viaje dirigidos a un cadete
 * (incluido el fix del bug: VIAJE_QUITADO), y chat.
 */
@Service
public class WebSocketPublisher {

    private final SimpMessagingTemplate template;

    public WebSocketPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void publicarUbicacion(Cadete cadete) {
        template.convertAndSend("/topic/admin/ubicaciones", CadeteResponse.from(cadete));
    }

    public void publicarEventoViaje(String cadeteId, String evento, PedidoResponse pedido) {
        template.convertAndSend("/queue/cadete/" + cadeteId + "/viajes",
                new EventoViaje(evento, pedido));
    }

    /** Broadcast para que el dashboard del admin refleje cambios de pedidos sin tener que refrescar. */
    public void publicarPedido(PedidoResponse pedido) {
        template.convertAndSend("/topic/admin/pedidos", pedido);
    }

    public void publicarMensajeChat(String cadeteId, MensajeResponse mensaje) {
        template.convertAndSend("/queue/cadete/" + cadeteId + "/chat", mensaje);
        template.convertAndSend("/queue/admin/chat", mensaje);
    }

    /**
     * Avisos al admin que ameritan una alerta visible ya (spec: "que salga una ventana
     * avisando"), no solo un refresco silencioso de una lista. Por ahora: cuando un
     * cadete rechaza un viaje explícitamente (no cuando se le vence el tiempo sin
     * responder — eso lo reasigna el job automático sin que sea necesariamente un aviso).
     */
    public void publicarAlertaRechazo(Cadete cadete, PedidoResponse pedido) {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "RECHAZO",
                "cadeteId", cadete.getId(),
                "cadeteNombre", cadete.getNombre(),
                "cadeteApellido", cadete.getApellido(),
                "pedido", pedido
        ));
    }

    /**
     * Alerta al admin si un cadete con un viaje EN_CURSO no manda ubicación nueva hace
     * rato (posible batería muerta, sin señal, o abandonó el pedido sin avisar) — spec,
     * mismo canal que la alerta de rechazo.
     */
    public void publicarAlertaInactividad(Cadete cadete, PedidoResponse pedido, long minutosSinUbicacion) {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "INACTIVIDAD_SOSPECHOSA",
                "cadeteId", cadete.getId(),
                "cadeteNombre", cadete.getNombre(),
                "cadeteApellido", cadete.getApellido(),
                "pedido", pedido,
                "minutosSinUbicacion", minutosSinUbicacion
        ));
    }

    /** El cliente reclamó desde la página de seguimiento (2026-09-25) — el admin se entera además del cadete. */
    public void publicarAlertaReclamo(Cadete cadete, PedidoResponse pedido, String detalle) {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "RECLAMO_CLIENTE",
                "cadeteId", cadete.getId(),
                "cadeteNombre", cadete.getNombre(),
                "cadeteApellido", cadete.getApellido(),
                "pedido", pedido,
                "detalle", detalle
        ));
    }

    /** Pedido nuevo sin asignar recién cargado — para el aviso sonoro/visual del dashboard (ronda 4, punto 26). */
    public void publicarAlertaPedidoNuevo(PedidoResponse pedido) {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "PEDIDO_NUEVO",
                "pedido", pedido
        ));
    }

    /** Pedido cargado por el cliente mismo desde "/pedir", esperando revisión del admin. */
    public void publicarAlertaSolicitudPedido(com.cadeteria.backend.model.SolicitudPedido s) {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "SOLICITUD_PEDIDO_NUEVA",
                "solicitudId", s.getId(),
                "clienteNombre", s.getClienteNombre(),
                "origenDireccion", s.getOrigenDireccion()
        ));
    }

    /** Aviso operativo del admin a un cadete puntual (general, o recordatorio de demora). */
    public void publicarAviso(String cadeteId, String mensaje) {
        template.convertAndSend("/queue/cadete/" + cadeteId + "/avisos", Map.of("mensaje", mensaje));
    }

    /** Aviso general con id — le permite a la app confirmar la lectura contra ese aviso puntual. */
    public void publicarAviso(String cadeteId, String mensaje, String avisoId) {
        template.convertAndSend("/queue/cadete/" + cadeteId + "/avisos", Map.of("mensaje", mensaje, "avisoId", avisoId));
    }

    /** Comando de envío para el gateway propio de WhatsApp (Baileys + chips descartables). */
    public void publicarComandoWhatsapp(ComandoEnvio comando) {
        template.convertAndSend("/topic/whatsapp/comandos", comando);
    }

    /** Pedido al gateway de vincular un chip nuevo (pairing code). */
    public void publicarComandoVincularChip(ComandoVincularChip comando) {
        template.convertAndSend("/topic/whatsapp/vincular-chip", comando);
    }

    /** Pedido al gateway de desloguear un chip y borrar su sesión — no se va a volver a usar. */
    public void publicarComandoDarDeBajaChip(String chipId) {
        template.convertAndSend("/topic/whatsapp/dar-de-baja-chip", Map.of("chipId", chipId));
    }

    /** Alerta visible (mismo canal que rechazos/inactividad) cuando un chip pasa a BANEADO. */
    public void publicarAlertaChipWhatsappBaneado(String chipId, String numero) {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "WHATSAPP_CHIP_BANEADO",
                "chipId", chipId,
                "numero", numero == null ? "" : numero
        ));
    }

    /**
     * Aviso liviano al panel de que algo del módulo WhatsApp cambió (chip, mensaje o
     * respuesta nueva) — igual que el patrón de /queue/admin/chat, el panel recarga la
     * lista que le interesa en vez de tratar de reconstruir el estado desde el evento.
     */
    public void publicarPanelWhatsapp(String evento) {
        template.convertAndSend("/topic/whatsapp/panel", Map.of("evento", evento));
    }

    /** Mismo canal que el chip de WhatsApp baneado: ninguna cuenta cargada de este proveedor tiene cupo. */
    public void publicarAlertaApiKeyPoolAgotado(String proveedor) {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "API_KEY_POOL_AGOTADO",
                "proveedor", proveedor
        ));
    }

    /** Aviso preventivo: a la última cuenta con cupo del proveedor le queda poco — conviene cargar otra antes de que se corte. */
    public void publicarAlertaApiKeyPoolBajo(String proveedor, int restante) {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "API_KEY_POOL_BAJO",
                "proveedor", proveedor,
                "restante", restante
        ));
    }

    /** Varios envíos de SMS seguidos (cada uno tras agotar sus propios reintentos) fallaron — probablemente el gateway está caído, no solo un número puntual. */
    public void publicarAlertaSmsGatewayCaido() {
        template.convertAndSend("/topic/admin/alertas", Map.of(
                "tipo", "SMS_GATEWAY_CAIDO"
        ));
    }

    public record EventoViaje(String tipo, PedidoResponse pedido) {}
}
