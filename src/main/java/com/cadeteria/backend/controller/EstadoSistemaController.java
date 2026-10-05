package com.cadeteria.backend.controller;

import com.cadeteria.backend.service.EstadoSistemaService;
import com.cadeteria.backend.service.EstadoSistemaService.EstadoSistema;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Para saber cómo anda el servidor (2026-10-05):
 * <ul>
 *   <li>{@code GET /api/publico/salud}: sin sesión, para un servicio de afuera que lo consulte cada
 *       pocos minutos y avise al celular si deja de responder. 200 si el backend y la base andan,
 *       503 si la base no contesta. No dice nada más.</li>
 *   <li>{@code GET /api/admin/sistema/estado}: la pantalla "Sistema" del panel, solo superadmin
 *       (permiso {@code sistema}, ver SecurityConfig).</li>
 * </ul>
 */
@RestController
public class EstadoSistemaController {

    private final EstadoSistemaService service;
    private final JdbcTemplate jdbc;

    public EstadoSistemaController(EstadoSistemaService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/publico/salud")
    public ResponseEntity<Map<String, String>> salud() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("estado", "ok"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("estado", "sin base de datos"));
        }
    }

    @GetMapping("/api/admin/sistema/estado")
    public EstadoSistema estado() {
        return service.estado();
    }
}
