package com.cadeteria.backend.controller;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.dto.PublicoDtos.CalificarRequest;
import com.cadeteria.backend.dto.PublicoDtos.SeguimientoResponse;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.service.PdfComprobanteService;
import com.cadeteria.backend.service.PedidoService;
import com.cadeteria.backend.service.RutaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pagina publica de seguimiento del pedido (spec 5.7/6, diseno-tecnico.md sección 8):
 * sin login, el token del pedido hace de "contrasena" del link que se manda por SMS.
 */
@RestController
@RequestMapping("/api/publico/pedidos/{token}")
public class PublicoController {

    private final PedidoService service;
    private final PdfComprobanteService pdfService;
    private final RutaService rutaService;

    public PublicoController(PedidoService service, PdfComprobanteService pdfService, RutaService rutaService) {
        this.service = service;
        this.pdfService = pdfService;
        this.rutaService = rutaService;
    }

    @GetMapping
    public SeguimientoResponse seguimiento(@PathVariable String token) {
        Pedido pedido = service.getPorToken(token);
        Cadete cadete = pedido.getCadeteAsignado();
        Integer etaMinutos = null;
        if ("EN_CURSO".equals(pedido.getEstado().getId()) && cadete != null
                && cadete.getLat() != null && cadete.getLng() != null) {
            etaMinutos = rutaService.resumenSiDisponible(
                    cadete.getLat(), cadete.getLng(), pedido.getDestinoLat(), pedido.getDestinoLng(),
                    cadete.getTipoVehiculo().getId()
            ).map(RutaService.Resumen::duracionMin).orElse(null);
        }
        return SeguimientoResponse.from(pedido, etaMinutos);
    }

    /** El cliente califica desde esta misma página, una sola vez, cuando el pedido ya está FINALIZADO. */
    @PostMapping("/calificacion")
    public SeguimientoResponse calificar(@PathVariable String token, @RequestBody CalificarRequest req) {
        return SeguimientoResponse.from(service.calificar(token, req.estrellas(), req.comentario()));
    }

    public record PushSubscribeRequest(String endpoint, String p256dh, String auth) {}

    /** Mejora 89 — el cliente se suscribe a Web Push desde su propia página de seguimiento. */
    @PostMapping("/push-subscribe")
    public void pushSubscribe(@PathVariable String token, @RequestBody PushSubscribeRequest req) {
        service.suscribirPush(token, req.endpoint(), req.p256dh(), req.auth());
    }

    public record RepetirResponse(String nuevoToken) {}

    /** Mejora 87 — el cliente repite su pedido sin llamar; el front navega al seguimiento del pedido nuevo con el token que devuelve. */
    @PostMapping("/repetir")
    public RepetirResponse repetir(@PathVariable String token) {
        Pedido nuevo = service.repetirPorToken(token);
        return new RepetirResponse(nuevo.getTokenSeguimiento());
    }

    @GetMapping("/comprobante")
    public ResponseEntity<byte[]> comprobante(@PathVariable String token) {
        Pedido pedido = service.getPorToken(token);
        if (!"FINALIZADO".equals(pedido.getEstado().getId())) {
            throw new BadRequestException("El comprobante todavia no esta disponible.");
        }
        byte[] pdf = pdfService.generar(pedido);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=comprobante.pdf")
                .body(pdf);
    }
}
