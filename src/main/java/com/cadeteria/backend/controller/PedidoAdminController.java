package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.CadeteDtos.CadeteResponse;
import com.cadeteria.backend.dto.PedidoDtos.AsignarLoteRequest;
import com.cadeteria.backend.dto.PedidoDtos.AsignarRequest;
import com.cadeteria.backend.dto.PedidoDtos.CancelarRequest;
import com.cadeteria.backend.dto.PedidoDtos.ComentarioRequest;
import com.cadeteria.backend.dto.PedidoDtos.ComentarioResponse;
import com.cadeteria.backend.dto.PedidoDtos.ClienteEncontradoResponse;
import com.cadeteria.backend.dto.PedidoDtos.FinalizarRequest;
import com.cadeteria.backend.dto.PedidoDtos.PedidoRequest;
import com.cadeteria.backend.dto.PedidoDtos.PedidoResponse;
import com.cadeteria.backend.dto.PedidoDtos.PuntoTrayectoResponse;
import com.cadeteria.backend.dto.PedidoDtos.QuitarRequest;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.service.GeocodingProxyService;
import com.cadeteria.backend.service.PdfComprobanteService;
import com.cadeteria.backend.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/pedidos")
public class PedidoAdminController {

    private final PedidoService service;
    private final PdfComprobanteService pdfService;

    private final GeocodingProxyService geocodingProxyService;

    public PedidoAdminController(PedidoService service, PdfComprobanteService pdfService,
                                 GeocodingProxyService geocodingProxyService) {
        this.geocodingProxyService = geocodingProxyService;
        this.service = service;
        this.pdfService = pdfService;
    }

    /**
     * tipo=activos (default) | programados — "finalizados" tiene su propio endpoint paginado, ver abajo.
     * Usa {@link PedidoResponse#fromResumen} (sin paradas): la tabla del dashboard no muestra el
     * detalle de paradas por fila, y mapearlas acá disparaba una query lazy por pedido (N+1) en
     * la lista que más tráfico recibe. El detalle sí las trae, ver {@link #get}.
     */
    @GetMapping
    public List<PedidoResponse> list(@RequestParam(defaultValue = "activos") String tipo) {
        List<Pedido> pedidos = switch (tipo) {
            case "programados" -> service.listarProgramados();
            default -> service.listarActivos();
        };
        return pedidos.stream().map(PedidoResponse::fromResumen).toList();
    }

    /**
     * "Pedidos finalizados" paginado en la base, no en memoria (mejora 2026-09-16, ver
     * {@link PedidoService#paginaFinalizados}) — desde/hasta en ISO-8601
     * (`2026-09-16T00:00:00Z`); sin ninguno de los dos = sin límite de fecha (el front lo
     * manda siempre explícito, default "Hoy"). Sin paradas, igual que {@link #list}: la tabla
     * no las muestra y el detalle se pide aparte (auditoría de endpoints 2026-09-24).
     */
    @GetMapping("/finalizados-pagina")
    public com.cadeteria.backend.dto.PedidoDtos.PaginaPedidosResponse finalizadosPagina(
            @RequestParam(required = false) java.time.Instant desde,
            @RequestParam(required = false) java.time.Instant hasta,
            @RequestParam(required = false) String cadeteId,
            @RequestParam(required = false) String tipoEstado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "15") int tamano) {
        var r = service.paginaFinalizados(desde, hasta, cadeteId, tipoEstado, pagina, tamano);
        return new com.cadeteria.backend.dto.PedidoDtos.PaginaPedidosResponse(
                r.items().stream().map(PedidoResponse::fromResumen).toList(), r.total(), r.pagina(), r.totalPaginas());
    }

    /** Para el ícono de alertas centralizado del panel (ronda 4, punto 18). */
    @GetMapping("/alertas/sms-fallidos")
    public java.util.Map<String, Long> smsFallidos() {
        return java.util.Map.of("cantidad", service.contarSmsFallidos());
    }

    /** Trayecto GPS real guardado mientras el pedido estaba EN_CURSO — para "ver el recorrido" en el mapa. */
    @GetMapping("/{id}/trayecto")
    public List<PuntoTrayectoResponse> trayecto(@PathVariable String id) {
        return service.trayectoDe(id).stream().map(PuntoTrayectoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PedidoResponse get(@PathVariable String id) {
        return PedidoResponse.from(service.get(id));
    }

    /** Comentarios que fue dejando el cadete sobre el pedido — para la pestaña "Comentarios" del detalle. */
    @GetMapping("/{id}/comentarios")
    public List<ComentarioResponse> comentarios(@PathVariable String id) {
        return service.comentariosDe(id).stream().map(ComentarioResponse::from).toList();
    }

    /** Mejora 101 — el admin también puede dejar comentarios propios, no solo leer los del cadete. */
    @PostMapping("/{id}/comentarios")
    public ComentarioResponse agregarComentario(@PathVariable String id, @Valid @RequestBody ComentarioRequest req,
                                                 Authentication auth) {
        return ComentarioResponse.from(service.agregarComentarioAdmin(id, auth.getName(), req.texto()));
    }

    /** Mejora 75 — editar el precio de un pedido ya cargado, dejando registro de quién y cuándo. */
    @PutMapping("/{id}/precio")
    public PedidoResponse editarPrecio(@PathVariable String id, @Valid @RequestBody com.cadeteria.backend.dto.PedidoDtos.PrecioRequest req,
                                        Authentication auth) {
        return PedidoResponse.from(service.editarPrecio(id, req.precio(), auth.getName()));
    }

    @GetMapping("/{id}/precio-historial")
    public List<com.cadeteria.backend.dto.PedidoDtos.PrecioLogResponse> historialPrecios(@PathVariable String id) {
        return service.historialPreciosDe(id).stream().map(com.cadeteria.backend.dto.PedidoDtos.PrecioLogResponse::from).toList();
    }

    /** Mejora 93 — marcar/desmarcar un pedido como prioritario/urgente. */
    @PutMapping("/{id}/prioritario")
    public PedidoResponse setPrioritario(@PathVariable String id, @RequestBody java.util.Map<String, Boolean> body) {
        return PedidoResponse.from(service.setPrioritario(id, Boolean.TRUE.equals(body.get("prioritario"))));
    }

    /** Direcciones habituales del cliente de ese teléfono (2026-09-25) — vacía si nunca pidió. */
    @GetMapping("/cliente/direcciones")
    public List<com.cadeteria.backend.dto.PedidoDtos.DireccionFrecuenteResponse> direccionesCliente(@RequestParam String telefono) {
        return service.direccionesFrecuentes(telefono);
    }

    /** Autocompletar nombre por teléfono al cargar un pedido (spec 5.6) — 204 si nunca se vio ese número. */
    @GetMapping("/cliente")
    public ResponseEntity<ClienteEncontradoResponse> cliente(@RequestParam String telefono) {
        return service.nombreClientePorTelefono(telefono)
                .map(nombre -> ResponseEntity.ok(new ClienteEncontradoResponse(nombre)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> create(@Valid @RequestBody PedidoRequest req,
                                                 org.springframework.security.core.Authentication auth) {
        Pedido pedido = service.crear(req, PedidoService.ORIGEN_PANEL, auth.getName());
        geocodingProxyService.aprenderPin(req.origenDireccion(), req.origenLat(), req.origenLng(), req.origenFuente());
        geocodingProxyService.aprenderPin(req.destinoDireccion(), req.destinoLat(), req.destinoLng(), req.destinoFuente());
        return ResponseEntity.status(201).body(PedidoResponse.from(pedido));
    }

    @GetMapping("/{id}/sugerencia")
    public ResponseEntity<CadeteResponse> sugerencia(@PathVariable String id) {
        return service.sugerirCandidato(id)
                .map(c -> ResponseEntity.ok(CadeteResponse.from(c)))
                .orElse(ResponseEntity.noContent().build());
    }

    /** Solo los cadetes que hoy pasarían los chequeos de "Asignar" — para no ofrecer opciones que van a fallar seguro. */
    @GetMapping("/{id}/candidatos-validos")
    public List<CadeteResponse> candidatosValidos(@PathVariable String id) {
        return service.candidatosValidos(id).stream().map(CadeteResponse::from).toList();
    }

    @PostMapping("/{id}/asignar")
    public PedidoResponse asignar(@PathVariable String id, @Valid @RequestBody AsignarRequest req, Authentication auth) {
        return PedidoResponse.from(service.asignar(id, req.cadeteId(), auth.getName()));
    }

    /** Agrupar pedidos de la misma zona en una sola oferta a un cadete (ronda 4, punto 61). */
    @PostMapping("/asignar-lote")
    public List<PedidoResponse> asignarLote(@Valid @RequestBody AsignarLoteRequest req, Authentication auth) {
        return service.asignarLote(req.pedidoIds(), req.cadeteId(), auth.getName()).stream().map(PedidoResponse::from).toList();
    }

    /** Reasignar a otro cadete con un clic (ronda 4, punto 40) — sin pasar por "Quitar" primero. */
    @PostMapping("/{id}/reasignar")
    public PedidoResponse reasignar(@PathVariable String id, @Valid @RequestBody AsignarRequest req, Authentication auth) {
        return PedidoResponse.from(service.reasignar(id, req.cadeteId(), auth.getName()));
    }

    /**
     * Boton "Quitar" del panel: desasigna sin anular el pedido. Sin body (o con
     * devolverComision=null), devuelve la comisión al cadete PORCENTAJE — mismo
     * comportamiento de siempre; el panel manda explícitamente false cuando el admin elige
     * no devolverla.
     */
    @PostMapping("/{id}/quitar")
    public PedidoResponse quitar(@PathVariable String id, @RequestBody(required = false) QuitarRequest req) {
        boolean devolverComision = req == null || req.devolverComision() == null || req.devolverComision();
        return PedidoResponse.from(service.quitarCadete(id, devolverComision));
    }

    /** Boton "Anular". */
    @PostMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable String id, @RequestBody(required = false) CancelarRequest req, Authentication auth) {
        return PedidoResponse.from(service.cancelar(id, req == null ? null : req.motivo(), auth.getName()));
    }

    /** Botón "Reintentar entrega": vuelve a SIN_ASIGNAR un pedido NO_ENTREGADO, sin anularlo ni cargarlo de cero. */
    @PostMapping("/{id}/reintentar-entrega")
    public PedidoResponse reintentarEntrega(@PathVariable String id) {
        return PedidoResponse.from(service.reintentarEntrega(id));
    }

    /** Boton "Finalizar": cierre manual para cuando el cadete no tiene internet para hacerlo el mismo. */
    @PostMapping("/{id}/finalizar")
    public PedidoResponse finalizar(@PathVariable String id, @RequestBody FinalizarRequest req) {
        return PedidoResponse.from(service.finalizarComoAdmin(id, req));
    }

    /** Boton "Reenviar SMS": reenvia a mano el link de seguimiento (ronda 3, punto 21). */
    @PostMapping("/{id}/reenviar-sms")
    public ResponseEntity<Void> reenviarSms(@PathVariable String id) {
        service.reenviarSmsSeguimiento(id);
        return ResponseEntity.noContent().build();
    }

    /** Boton "Imprimir": mismo comprobante que ve el cliente en la pagina de seguimiento. */
    @GetMapping("/{id}/comprobante")
    public ResponseEntity<byte[]> comprobante(@PathVariable String id) {
        Pedido pedido = service.get(id);
        byte[] pdf = pdfService.generar(pedido);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=comprobante-" + id + ".pdf")
                .body(pdf);
    }
}
