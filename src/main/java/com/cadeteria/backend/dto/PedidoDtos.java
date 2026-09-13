package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Pedido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PedidoDtos {

    private PedidoDtos() {}

    public record PedidoRequest(
            @NotBlank String clienteTelefono,
            @NotBlank String clienteNombre,
            @NotBlank String origenDireccion,
            @NotNull Double origenLat,
            @NotNull Double origenLng,
            @NotBlank String destinoDireccion,
            @NotNull Double destinoLat,
            @NotNull Double destinoLng,
            @NotNull BigDecimal precio,
            BigDecimal montoDeclarado,
            String detalle,
            @NotBlank String zonaId,
            @NotBlank String tipoVehiculoRequeridoId,
            boolean programado,
            Instant fechaProgramada,
            /** Paradas intermedias, en orden (ronda 3, punto 38) — null o vacío si el pedido es simple. */
            List<ParadaRequest> paradasAdicionales
    ) {}

    /** Una parada intermedia al cargar el pedido (spec: repartos con varias entregas en una vuelta). */
    public record ParadaRequest(@NotBlank String direccion, @NotNull Double lat, @NotNull Double lng) {}

    public record ParadaResponse(String id, int orden, String direccion, Double lat, Double lng, Instant entregadoEn) {
        public static ParadaResponse from(com.cadeteria.backend.model.PedidoParada p) {
            return new ParadaResponse(p.getId(), p.getOrden(), p.getDireccion(), p.getLat(), p.getLng(), p.getEntregadoEn());
        }
    }

    public record CadeteResumen(String id, String nombre, String apellido, String fotoUrl, LookupResponse tipoVehiculo) {
        public static CadeteResumen from(Cadete c) {
            return c == null ? null : new CadeteResumen(
                    c.getId(), c.getNombre(), c.getApellido(), c.getFotoUrl(), LookupResponse.from(c.getTipoVehiculo()));
        }
    }

    /** Incluye el timeline completo — es la respuesta tanto de lista como de detalle (spec 3/diseno sección 3). */
    public record PedidoResponse(
            String id, Long numero, String clienteTelefono, String clienteNombre,
            String origenDireccion, Double origenLat, Double origenLng,
            String destinoDireccion, Double destinoLat, Double destinoLng,
            BigDecimal precio, BigDecimal montoDeclarado, String detalle,
            LookupResponse zona, LookupResponse tipoVehiculoRequerido, LookupResponse estado,
            CadeteResumen cadeteAsignado,
            boolean programado, Instant fechaProgramada,
            Instant creadoEn, Instant asignadoEn, Instant aceptadoEn, Instant retiradoEn, Instant finalizadoEn,
            Instant canceladoEn,
            String fotoRecepcionUrl, String entregaReceptorNombre, String entregaFotoUrl, String firmaReceptorUrl,
            Double retiroLat, Double retiroLng, Double entregaLat, Double entregaLng,
            String motivoCancelacion, String tokenSeguimiento,
            /** true si se agotaron los reintentos y el SMS de aviso al cliente no se pudo mandar. */
            boolean smsFallido,
            Integer calificacionEstrellas, String calificacionComentario,
            /** "No se pudo entregar" (ronda 3, punto 56) — distinto de motivoCancelacion, el pedido sigue vivo. */
            String motivoNoEntrega, Instant noEntregadoEn,
            /** Paradas intermedias, en orden (ronda 3, punto 38) — vacío si el pedido es simple. */
            List<ParadaResponse> paradas,
            /** Auditoría (ronda 4, punto 28): qué admin asignó/canceló, null si fue automático o no aplica. */
            String asignadoPorUsername, String canceladoPorUsername,
            /** Marca manual del admin para destacarlo en el dashboard (mejora 93). */
            boolean prioritario
    ) {
        public static PedidoResponse from(Pedido p) {
            return new PedidoResponse(
                    p.getId(), p.getNumero(), p.getClienteTelefono(), p.getClienteNombre(),
                    p.getOrigenDireccion(), p.getOrigenLat(), p.getOrigenLng(),
                    p.getDestinoDireccion(), p.getDestinoLat(), p.getDestinoLng(),
                    p.getPrecio(), p.getMontoDeclarado(), p.getDetalle(),
                    LookupResponse.from(p.getZona()), LookupResponse.from(p.getTipoVehiculoRequerido()),
                    LookupResponse.from(p.getEstado()),
                    CadeteResumen.from(p.getCadeteAsignado()),
                    p.isProgramado(), p.getFechaProgramada(),
                    p.getCreadoEn(), p.getAsignadoEn(), p.getAceptadoEn(), p.getRetiradoEn(), p.getFinalizadoEn(),
                    p.getCanceladoEn(),
                    p.getFotoRecepcionUrl(), p.getEntregaReceptorNombre(), p.getEntregaFotoUrl(), p.getFirmaReceptorUrl(),
                    p.getRetiroLat(), p.getRetiroLng(), p.getEntregaLat(), p.getEntregaLng(),
                    p.getMotivoCancelacion(), p.getTokenSeguimiento(), p.isSmsFallido(),
                    p.getCalificacionEstrellas(), p.getCalificacionComentario(),
                    p.getMotivoNoEntrega(), p.getNoEntregadoEn(),
                    p.getParadas().stream().sorted(java.util.Comparator.comparingInt(com.cadeteria.backend.model.PedidoParada::getOrden))
                            .map(ParadaResponse::from).toList(),
                    p.getAsignadoPorUsername(), p.getCanceladoPorUsername(), p.isPrioritario());
        }
    }

    /**
     * Sección "Finalizados" de la app de cadetes: listado + resumen. `cantidadRechazados`
     * y `cantidadNoAceptados` (se le vencio la oferta y se reasigno a otro, spec sección 4)
     * salen de oferta_pedido, no de pedido — ver PedidoService.historialDe.
     */
    public record HistorialResponse(
            List<PedidoResponse> pedidos, int cantidadViajes, BigDecimal montoTotal,
            long cantidadRechazados, long cantidadNoAceptados
    ) {}

    /** Sugerencia de nombre al cargar un pedido con un teléfono ya visto (spec 5.6). */
    public record ClienteEncontradoResponse(String nombre) {}

    public record AsignarRequest(@NotBlank String cadeteId) {}

    /** Agrupar pedidos de la misma zona en una sola oferta a un cadete (ronda 4, punto 61). */
    public record AsignarLoteRequest(@NotBlank String cadeteId, @NotEmpty List<String> pedidoIds) {}

    /** Boton "Anular": motivo "CLIENTE" u "OTRO" (para métricas), null si no se especifica. */
    public record CancelarRequest(String motivo) {}

    public static final class MotivoCancelacion {
        private MotivoCancelacion() {}
        public static final String CLIENTE = "CLIENTE";
        public static final String OTRO = "OTRO";
    }

    /**
     * Boton "Retirado" en la app: marca que el cadete paso por lo del cliente. Foto opcional.
     * lat/lng son la ubicación del celular del cadete en ese momento (opcional, si el GPS no
     * está disponible) — el admin la usa para verificar que el retiro fue en la dirección real.
     */
    public record RecepcionRequest(String fotoUrl, Double lat, Double lng) {}

    /** Botón "Rechazar": motivo opcional, texto libre (spec Métricas: detectar patrones de rechazo). */
    public record RechazarRequest(String motivo) {}

    /** Botón "No se pudo entregar" (ej. el cliente no atendió): motivo opcional, texto libre. */
    public record NoEntregadoRequest(String motivo) {}

    /**
     * receptorNombre y fotoUrl obligatorios al finalizar; firmaUrl es obligatoria u opcional
     * según "firma_receptor_obligatoria" en Configuración (ronda 3, punto 51).
     * lat/lng: ubicación del cadete al finalizar (opcional), misma idea que en RecepcionRequest.
     */
    public record FinalizarRequest(String receptorNombre, String fotoUrl, String firmaUrl, Double lat, Double lng) {}

    public record RutaResponse(Object geoJson) {}

    /** Un punto del trayecto GPS real guardado mientras el pedido estaba EN_CURSO. */
    public record PuntoTrayectoResponse(Double lat, Double lng, Instant capturadoEn) {
        public static PuntoTrayectoResponse from(com.cadeteria.backend.model.PedidoUbicacion p) {
            return new PuntoTrayectoResponse(p.getLat(), p.getLng(), p.getCapturadoEn());
        }
    }

    /** Nota de texto libre que el cadete deja sobre el pedido (ej. "entregado en porteria a Fulano"). */
    public record ComentarioRequest(@NotBlank String texto) {}

    /** Mejora 75 — editar el precio de un pedido ya cargado. */
    public record PrecioRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero java.math.BigDecimal precio) {}

    public record PrecioLogResponse(String id, java.math.BigDecimal precioAnterior, java.math.BigDecimal precioNuevo,
                                     String cambiadoPorUsername, Instant cambiadoEn) {
        public static PrecioLogResponse from(com.cadeteria.backend.model.PedidoPrecioLog l) {
            return new PrecioLogResponse(l.getId(), l.getPrecioAnterior(), l.getPrecioNuevo(), l.getCambiadoPorUsername(), l.getCambiadoEn());
        }
    }

    public record ComentarioResponse(String id, String texto, String cadeteNombre, boolean esAdmin, Instant creadoEn) {
        public static ComentarioResponse from(com.cadeteria.backend.model.PedidoComentario c) {
            boolean esAdmin = c.getAdminUsername() != null;
            String autor = esAdmin
                    ? "Admin (" + c.getAdminUsername() + ")"
                    : c.getCadete().getNombre() + " " + c.getCadete().getApellido();
            return new ComentarioResponse(c.getId(), c.getTexto(), autor, esAdmin, c.getCreadoEn());
        }
    }
}
