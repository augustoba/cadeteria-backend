package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.PedidoDtos.PedidoResponse;
import com.cadeteria.backend.dto.SolicitudPedidoDtos.RechazarSolicitudRequest;
import com.cadeteria.backend.dto.SolicitudPedidoDtos.RevisarSolicitudRequest;
import com.cadeteria.backend.dto.SolicitudPedidoDtos.SolicitudPedidoResponse;
import com.cadeteria.backend.service.SolicitudPedidoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Revisión admin de los pedidos que carga el cliente mismo desde "/pedir". */
@RestController
@RequestMapping("/api/admin/solicitudes-pedido")
public class SolicitudPedidoAdminController {

    private final SolicitudPedidoService service;

    public SolicitudPedidoAdminController(SolicitudPedidoService service) {
        this.service = service;
    }

    @GetMapping
    public List<SolicitudPedidoResponse> listar(@RequestParam(required = false) String estado) {
        return service.listar(estado).stream().map(SolicitudPedidoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SolicitudPedidoResponse get(@PathVariable String id) {
        return SolicitudPedidoResponse.from(service.get(id));
    }

    /** Ya se acordó el precio (ej. por teléfono) — crea el pedido de una. */
    @PostMapping("/{id}/confirmar-directo")
    public PedidoResponse confirmarDirecto(@PathVariable String id, @Valid @RequestBody RevisarSolicitudRequest req) {
        return PedidoResponse.from(
                service.confirmarDirecto(id, req.requiereMoto(), req.precio(), req.montoDeclarado()));
    }

    /** Le manda la cotización por SMS con un link — el cliente confirma solo, sin llamar. */
    @PostMapping("/{id}/cotizar")
    public SolicitudPedidoResponse cotizar(@PathVariable String id, @Valid @RequestBody RevisarSolicitudRequest req) {
        return SolicitudPedidoResponse.from(
                service.cotizar(id, req.requiereMoto(), req.precio(), req.montoDeclarado()));
    }

    @PostMapping("/{id}/rechazar")
    public SolicitudPedidoResponse rechazar(@PathVariable String id, @RequestBody(required = false) RechazarSolicitudRequest req) {
        return SolicitudPedidoResponse.from(service.rechazar(id, req == null ? null : req.motivo()));
    }
}
