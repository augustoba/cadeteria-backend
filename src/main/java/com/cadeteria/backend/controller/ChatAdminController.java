package com.cadeteria.backend.controller;

import com.cadeteria.backend.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Agregados de chat para el panel (badge de mensajes sin leer en la navegación). */
@RestController
@RequestMapping("/api/admin/chat")
public class ChatAdminController {

    private final ChatService service;

    public ChatAdminController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/no-leidos")
    public Map<String, Long> noLeidos() {
        return Map.of("cantidad", service.contarNoLeidosPorAdmin());
    }

    /** Por cadete, no solo el total — para marcar en la lista de Chat cuál escribió (auditoría UX 2026-09-13). */
    @GetMapping("/no-leidos-por-cadete")
    public Map<String, Long> noLeidosPorCadete() {
        return service.noLeidosPorCadete();
    }
}
