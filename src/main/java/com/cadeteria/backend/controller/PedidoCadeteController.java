package com.cadeteria.backend.controller;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.dto.PedidoDtos.ComentarioRequest;
import com.cadeteria.backend.dto.PedidoDtos.ComentarioResponse;
import com.cadeteria.backend.dto.PedidoDtos.FinalizarRequest;
import com.cadeteria.backend.dto.PedidoDtos.NoEntregadoRequest;
import com.cadeteria.backend.dto.PedidoDtos.HistorialResponse;
import com.cadeteria.backend.dto.PedidoDtos.PedidoResponse;
import com.cadeteria.backend.dto.PedidoDtos.RecepcionRequest;
import com.cadeteria.backend.dto.PedidoDtos.RechazarRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.service.CadeteService;
import com.cadeteria.backend.service.PedidoService;
import com.cadeteria.backend.service.RutaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos/me")
public class PedidoCadeteController {

    private final PedidoService service;
    private final CadeteService cadeteService;
    private final RutaService rutaService;

    public PedidoCadeteController(PedidoService service, CadeteService cadeteService, RutaService rutaService) {
        this.service = service;
        this.cadeteService = cadeteService;
        this.rutaService = rutaService;
    }

    @GetMapping("/activo")
    public ResponseEntity<PedidoResponse> activo(Authentication auth) {
        return service.activoDe(auth.getName())
                .map(p -> ResponseEntity.ok(PedidoResponse.paraCadete(p)))
                .orElse(ResponseEntity.noContent().build());
    }

    /** Sección "Asignados y en curso" de la app — a diferencia de /activo, no asume uno solo. */
    @GetMapping("/activos")
    public List<PedidoResponse> activos(Authentication auth) {
        return service.activosDe(auth.getName()).stream().map(PedidoResponse::paraCadete).toList();
    }

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    /**
     * Sección "Finalizados" de la app: listado + resumen (viajes, monto total, rechazos,
     * no-aceptados). `desde`/`hasta` en yyyy-MM-dd, ambos inclusive — sin parámetros trae
     * todo el historial de siempre (auditoría UX 2026-09-13, punto 5: antes la app no
     * tenía forma de acotar por fecha para comparar contra la liquidación semanal).
     * `soloResumen=true` devuelve los números con la lista vacía — para las estadísticas de
     * Inicio, que no necesitan los pedidos (auditoría de endpoints 2026-09-24). La lista va
     * sin paradas: la pantalla de Finalizados no las muestra.
     */
    @GetMapping("/historial")
    public HistorialResponse historial(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "false") boolean soloResumen, Authentication auth) {
        Instant desdeInstant = null;
        Instant hastaInstant = null;
        if (desde != null && !desde.isBlank()) {
            desdeInstant = LocalDate.parse(desde).atStartOfDay(ZONA).toInstant();
            hastaInstant = LocalDate.parse(hasta != null && !hasta.isBlank() ? hasta : desde)
                    .plusDays(1).atStartOfDay(ZONA).toInstant();
        }
        PedidoService.HistorialCadete h = service.historialDe(auth.getName(), desdeInstant, hastaInstant);
        List<PedidoResponse> pedidos = soloResumen ? List.of()
                : h.finalizados().stream().map(p -> PedidoResponse.paraCadete(p, false)).toList();
        BigDecimal montoTotal = h.finalizados().stream()
                .map(Pedido::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HistorialResponse(pedidos, h.finalizados().size(), montoTotal, h.cantidadRechazados(), h.cantidadNoAceptados());
    }

    /** Detalle de un pedido puntual propio (para abrir uno de la lista de "Asignados"/"Finalizados"). */
    @GetMapping("/{id}")
    public PedidoResponse detalle(@PathVariable String id, Authentication auth) {
        return PedidoResponse.paraCadete(service.getDeCadete(id, auth.getName()));
    }

    @PostMapping("/{id}/aceptar")
    public PedidoResponse aceptar(@PathVariable String id, Authentication auth) {
        return PedidoResponse.paraCadete(service.aceptar(id, auth.getName()));
    }

    @PostMapping("/{id}/rechazar")
    public PedidoResponse rechazar(@PathVariable String id, @RequestBody(required = false) RechazarRequest req, Authentication auth) {
        return PedidoResponse.paraCadete(service.rechazar(id, auth.getName(), req == null ? null : req.motivo()));
    }

    @PostMapping("/{id}/recepcion")
    public PedidoResponse recepcion(@PathVariable String id, @Valid @RequestBody RecepcionRequest req,
                                     Authentication auth) {
        return PedidoResponse.paraCadete(service.registrarRecepcion(id, auth.getName(), req.fotoUrl(), req.lat(), req.lng()));
    }

    @PostMapping("/{id}/finalizar")
    public PedidoResponse finalizar(@PathVariable String id, @RequestBody FinalizarRequest req, Authentication auth) {
        return PedidoResponse.paraCadete(service.finalizar(id, auth.getName(), req));
    }

    /** Botón "No se pudo entregar" (ej. el cliente no atendió) — el pedido no se anula, el admin lo puede reintentar. */
    @PostMapping("/{id}/no-entregado")
    public PedidoResponse noEntregado(@PathVariable String id, @RequestBody(required = false) NoEntregadoRequest req,
                                       Authentication auth) {
        return PedidoResponse.paraCadete(service.marcarNoEntregado(id, auth.getName(), req == null ? null : req.motivo()));
    }

    /** Marca una parada intermedia como entregada (ronda 3, punto 38: varias entregas en la misma vuelta). */
    @PostMapping("/{id}/paradas/{paradaId}/entregada")
    public PedidoResponse marcarParadaEntregada(@PathVariable String id, @PathVariable String paradaId, Authentication auth) {
        return PedidoResponse.paraCadete(service.marcarParadaEntregada(id, auth.getName(), paradaId));
    }

    /** Nota de texto libre sobre el viaje (ej. "entregado en porteria a Fulano") — se ve en el detalle del panel. */
    @PostMapping("/{id}/comentarios")
    public ComentarioResponse agregarComentario(@PathVariable String id, @Valid @RequestBody ComentarioRequest req,
                                                 Authentication auth) {
        return ComentarioResponse.from(service.agregarComentario(id, auth.getName(), req.texto()));
    }

    /** Ruta sugerida estilo Uber (spec 5.7): desde la ubicacion actual del cadete hasta el destino. */
    @GetMapping("/{id}/ruta")
    public Map<String, Object> ruta(@PathVariable String id, Authentication auth) {
        Cadete cadete = cadeteService.getByUsername(auth.getName());
        if (cadete.getLat() == null || cadete.getLng() == null) {
            throw new BadRequestException("Todavia no se registro tu ubicacion.");
        }
        Pedido pedido = service.get(id);
        return rutaService.calcularRuta(cadete.getLat(), cadete.getLng(),
                pedido.getDestinoLat(), pedido.getDestinoLng(), cadete.getTipoVehiculo().getId());
    }
}
