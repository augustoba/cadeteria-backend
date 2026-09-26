package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.PedidoDtos.PedidoResponse;
import com.cadeteria.backend.dto.SolicitudPedidoDtos.FraudulentoRequest;
import com.cadeteria.backend.dto.SolicitudPedidoDtos.RechazarSolicitudRequest;
import com.cadeteria.backend.model.SolicitudPedido;
import com.cadeteria.backend.util.TelefonoUtils;
import org.springframework.security.core.Authentication;
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

    /** Con el aviso del teléfono de cada solicitud ya resuelto (spec-antiabuso Fase 1), en 2 queries para toda la lista. */
    @GetMapping
    public List<SolicitudPedidoResponse> listar(@RequestParam(required = false) String estado) {
        List<SolicitudPedido> solicitudes = service.listar(estado);
        var avisos = service.avisosDe(solicitudes);
        return solicitudes.stream()
                .map(s -> SolicitudPedidoResponse.from(s, avisos.get(TelefonoUtils.normalizar(s.getClienteTelefono()))))
                .toList();
    }

    @GetMapping("/{id}")
    public SolicitudPedidoResponse get(@PathVariable String id) {
        return conAviso(service.get(id));
    }

    @PostMapping("/{id}/fraudulento")
    public SolicitudPedidoResponse fraudulento(@PathVariable String id, @Valid @RequestBody(required = false) FraudulentoRequest req,
                                               Authentication auth) {
        return conAviso(service.marcarFraudulenta(id, req == null ? null : req.nota(), auth.getName()));
    }

    @PostMapping("/{id}/whatsapp-confirmacion")
    public void whatsappConfirmacion(@PathVariable String id) {
        service.pedirConfirmacionWhatsapp(id);
    }

    @PostMapping("/{id}/validar-telefono")
    public SolicitudPedidoResponse validarTelefono(@PathVariable String id) {
        return conAviso(service.validarTelefono(id));
    }

    private SolicitudPedidoResponse conAviso(SolicitudPedido s) {
        var avisos = service.avisosDe(List.of(s));
        return SolicitudPedidoResponse.from(s, avisos.get(TelefonoUtils.normalizar(s.getClienteTelefono())));
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
    public SolicitudPedidoResponse rechazar(@PathVariable String id, @Valid @RequestBody(required = false) RechazarSolicitudRequest req) {
        return SolicitudPedidoResponse.from(service.rechazar(id, req == null ? null : req.motivo()));
    }
}
