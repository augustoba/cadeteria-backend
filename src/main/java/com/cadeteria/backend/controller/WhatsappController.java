package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.WhatsappDtos.AckRequest;
import com.cadeteria.backend.dto.WhatsappDtos.EstadoGatewayResponse;
import com.cadeteria.backend.dto.WhatsappDtos.TestEnviarRequest;
import com.cadeteria.backend.service.WhatsappGatewayService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway propio de WhatsApp (Baileys + chips descartables). El endpoint REST es para
 * el panel (probar el mecanismo a mano y ver si el gateway está conectado); el
 * @MessageMapping es el que consume el gateway (Node) para confirmar cada envío.
 */
@Controller
public class WhatsappController {

    private final WhatsappGatewayService service;

    public WhatsappController(WhatsappGatewayService service) {
        this.service = service;
    }

    /** Recibido por STOMP en /app/whatsapp/ack — solo el gateway autenticado puede mandar acá (ver WhatsappGatewayAuthInterceptor). */
    @MessageMapping("/whatsapp/ack")
    public void ack(AckRequest req) {
        service.confirmar(req.mensajeId(), req.ok(), req.chipUsado(), req.error());
    }

    @RestController
    @RequestMapping("/api/admin/whatsapp")
    public static class Admin {

        private final WhatsappGatewayService service;

        public Admin(WhatsappGatewayService service) {
            this.service = service;
        }

        @GetMapping("/estado")
        public EstadoGatewayResponse estado() {
            return service.estado();
        }

        /** Prueba manual desde el panel, sin pedido asociado — parte 2 del prototipo. */
        @PostMapping("/test")
        public ResponseEntity<Void> test(@Valid @RequestBody TestEnviarRequest req) {
            service.enviar(req.telefono(), req.mensaje(), null);
            return ResponseEntity.accepted().build();
        }
    }
}
