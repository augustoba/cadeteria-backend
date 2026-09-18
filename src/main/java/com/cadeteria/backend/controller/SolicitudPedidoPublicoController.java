package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.SolicitudPedidoDtos.ConfirmacionPublicaResponse;
import com.cadeteria.backend.dto.SolicitudPedidoDtos.SolicitudPedidoRequest;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.SolicitudPedido;
import com.cadeteria.backend.service.SolicitudPedidoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Página pública "/pedir" (sin login) y "/confirmar-pedido/:token" — el cliente carga y confirma su propio pedido. */
@RestController
@RequestMapping("/api/publico/solicitudes-pedido")
public class SolicitudPedidoPublicoController {

    private final SolicitudPedidoService service;

    public SolicitudPedidoPublicoController(SolicitudPedidoService service) {
        this.service = service;
    }

    public record SolicitudCreadaResponse(String id) {}

    @PostMapping
    public SolicitudCreadaResponse crear(@Valid @RequestBody SolicitudPedidoRequest req) {
        return new SolicitudCreadaResponse(service.crear(req).getId());
    }

    /** Para que "/pedir" sepa si mostrar el formulario o un aviso (pausa manual u horario) antes de que el cliente cargue nada. */
    @GetMapping("/estado")
    public SolicitudPedidoService.EstadoDisponibilidad estado() {
        return service.estadoDisponibilidad();
    }

    /** Para que la página de confirmación muestre la cotización antes de que el cliente toque "Confirmar". */
    @GetMapping("/confirmar/{token}")
    public ConfirmacionPublicaResponse verCotizacion(@PathVariable String token) {
        SolicitudPedido s = service.getPorToken(token);
        return new ConfirmacionPublicaResponse(s.getEstado(), s.getOrigenDireccion(), s.getDestinoDireccion(), s.getPrecio(), null);
    }

    @PostMapping("/confirmar/{token}")
    public ConfirmacionPublicaResponse confirmar(@PathVariable String token) {
        Pedido pedido = service.confirmarPorToken(token);
        return new ConfirmacionPublicaResponse("CONFIRMADA", pedido.getOrigenDireccion(), pedido.getDestinoDireccion(),
                pedido.getPrecio(), pedido.getTokenSeguimiento());
    }
}
