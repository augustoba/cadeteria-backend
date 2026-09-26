package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.common.Validaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PedidoDtos {

    private PedidoDtos() {}

    public record PedidoRequest(
            @NotBlank(message = "Falta el teléfono del cliente.")
            @Pattern(regexp = Validaciones.TELEFONO, message = Validaciones.MSJ_TELEFONO) String clienteTelefono,
            @NotBlank(message = "Falta el nombre del cliente.")
            @Pattern(regexp = Validaciones.NOMBRE_CLIENTE, message = Validaciones.MSJ_NOMBRE_CLIENTE) String clienteNombre,
            @NotBlank(message = "Falta la dirección de retiro.") @Size(max = 500, message = "La dirección de retiro es demasiado larga.")
            String origenDireccion,
            @NotNull(message = "Falta ubicar la dirección de retiro en el mapa.") @DecimalMin(value = "-90", message = "Latitud inválida.")
            @DecimalMax(value = "90", message = "Latitud inválida.") Double origenLat,
            @NotNull(message = "Falta ubicar la dirección de retiro en el mapa.") @DecimalMin(value = "-180", message = "Longitud inválida.")
            @DecimalMax(value = "180", message = "Longitud inválida.") Double origenLng,
            @NotBlank(message = "Falta la dirección de entrega.") @Size(max = 500, message = "La dirección de entrega es demasiado larga.")
            String destinoDireccion,
            @NotNull(message = "Falta ubicar la dirección de entrega en el mapa.") @DecimalMin(value = "-90", message = "Latitud inválida.")
            @DecimalMax(value = "90", message = "Latitud inválida.") Double destinoLat,
            @NotNull(message = "Falta ubicar la dirección de entrega en el mapa.") @DecimalMin(value = "-180", message = "Longitud inválida.")
            @DecimalMax(value = "180", message = "Longitud inválida.") Double destinoLng,
            @NotNull(message = "Falta el precio.") @PositiveOrZero(message = "El precio no puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "El precio no es válido.") BigDecimal precio,
            @PositiveOrZero(message = "El monto declarado no puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "El monto declarado no es válido.") BigDecimal montoDeclarado,
            /** Declarado por el cliente (mejora 2026-09-23) — ver Pedido.llevaValores. */
            boolean llevaValores,
            /** Valor declarado de los objetos de valor (2026-09-24), null si no lleva. */
            @PositiveOrZero(message = "El valor declarado no puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "El valor declarado no es válido.") BigDecimal montoValores,
            @Size(max = 1000, message = "El detalle puede tener hasta 1000 caracteres.") String detalle,
            /** Piso, depto y observaciones de cada dirección (mejora 2026-09-24), opcionales. */
            @Size(max = 20, message = "El piso puede tener hasta 20 caracteres.") String origenPiso,
            @Size(max = 20, message = "El depto puede tener hasta 20 caracteres.") String origenDepto,
            @Size(max = 300, message = "Las observaciones pueden tener hasta 300 caracteres.") String origenObservaciones,
            @Size(max = 20, message = "El piso puede tener hasta 20 caracteres.") String destinoPiso,
            @Size(max = 20, message = "El depto puede tener hasta 20 caracteres.") String destinoDepto,
            @Size(max = 300, message = "Las observaciones pueden tener hasta 300 caracteres.") String destinoObservaciones,
            boolean requiereMoto,
            boolean programado,
            Instant fechaProgramada,
            /** Paradas intermedias, en orden (ronda 3, punto 38) — null o vacío si el pedido es simple. */
            @Size(max = 20, message = "Hasta 20 paradas por pedido.") List<@Valid ParadaRequest> paradasAdicionales,
            /**
             * De dónde salió el pin de cada dirección (2026-09-25): "manual" (ubicado a mano) o
             * "google_link" (link de Google Maps pegado) se aprenden en la cache de direcciones
             * (GeocodingProxyService#aprenderPin); el nombre de un buscador o null, no.
             */
            String origenFuente, String destinoFuente
    ) {}

    /** Una parada intermedia al cargar el pedido (spec: repartos con varias entregas en una vuelta). */
    public record ParadaRequest(
            @NotBlank(message = "Falta la dirección de la parada.") @Size(max = 500, message = "La dirección de la parada es demasiado larga.")
            String direccion,
            @NotNull(message = "Falta ubicar la parada en el mapa.") @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull(message = "Falta ubicar la parada en el mapa.") @DecimalMin("-180") @DecimalMax("180") Double lng) {}

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
            BigDecimal precio, BigDecimal montoDeclarado, boolean llevaValores, BigDecimal montoValores, String detalle,
            /** Piso, depto y observaciones (mejora 2026-09-24) — null para el cadete hasta que acepta, ver {@link #paraCadete}. */
            String origenPiso, String origenDepto, String origenObservaciones,
            String destinoPiso, String destinoDepto, String destinoObservaciones,
            boolean requiereMoto, LookupResponse estado,
            CadeteResumen cadeteAsignado,
            boolean programado, Instant fechaProgramada,
            Instant creadoEn, Instant asignadoEn,
            /** Cuándo el cadete abrió la pantalla del viaje por primera vez estando la oferta PENDIENTE — null si todavía no la abrió. */
            Instant vistoEn,
            Instant aceptadoEn, Instant retiradoEn, Instant finalizadoEn,
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
            boolean prioritario,
            /** WEB | PANEL | null (anterior a la mejora 2026-09-24) — y quién lo cargó si fue PANEL. */
            String origenCarga, String creadoPorUsername,
            /** Último reclamo del cliente desde el seguimiento (2026-09-25) y cuándo — null si no reclamó. */
            String reclamoDetalle, Instant reclamoEn,
            /** DEMORA_RETIRO | DEMORA_ENTREGA | PROBLEMA_ENTREGA y ABIERTO | VISTO | CONTACTO | CERRADO (2026-09-26). */
            String reclamoTipo, String reclamoEstado
    ) {
        public static PedidoResponse from(Pedido p) {
            return from(p, true, false);
        }

        /**
         * Lo que ve el cadete (API de la app y eventos STOMP a su cola): mientras no aceptó el
         * viaje, el detalle del pedido, el piso/depto y las observaciones de las direcciones van
         * en null (mejora 2026-09-24) — decide con los mismos datos de siempre (origen, destino,
         * precio, dinero, valores, moto) y el resto le aparece al aceptar. Se mira el estado y
         * no solo aceptadoEn porque "Quitar" un pedido EN_CURSO no limpia aceptadoEn: el
         * siguiente cadete lo recibiría PENDIENTE con la marca del anterior.
         */
        public static PedidoResponse paraCadete(Pedido p) {
            return paraCadete(p, true);
        }

        /** Igual que {@link #paraCadete(Pedido)}; sin paradas para listados (historial), que no las muestran. */
        public static PedidoResponse paraCadete(Pedido p, boolean incluirParadas) {
            String estado = p.getEstado() == null ? null : p.getEstado().getId();
            boolean sinAceptar = p.getAceptadoEn() == null || estado == null
                    || java.util.Set.of("PENDIENTE", "SIN_ASIGNAR", "PROGRAMADO").contains(estado);
            return from(p, incluirParadas, sinAceptar);
        }

        /**
         * Variante liviana para el listado de "activos"/"programados" (GET /api/admin/pedidos):
         * la tabla del dashboard no muestra el detalle de paradas por fila, así que evitamos
         * tocar `p.getParadas()` (lazy @OneToMany) para no disparar una query por pedido (N+1).
         * El detalle (GET /api/admin/pedidos/{id}) y el broadcast por WebSocket siguen usando
         * {@link #from(Pedido)}, que sí las incluye.
         */
        public static PedidoResponse fromResumen(Pedido p) {
            return from(p, false, false);
        }

        private static PedidoResponse from(Pedido p, boolean incluirParadas, boolean ocultarDetalle) {
            return new PedidoResponse(
                    p.getId(), p.getNumero(), p.getClienteTelefono(), p.getClienteNombre(),
                    p.getOrigenDireccion(), p.getOrigenLat(), p.getOrigenLng(),
                    p.getDestinoDireccion(), p.getDestinoLat(), p.getDestinoLng(),
                    p.getPrecio(), p.getMontoDeclarado(), p.isLlevaValores(), p.getMontoValores(),
                    ocultarDetalle ? null : p.getDetalle(),
                    ocultarDetalle ? null : p.getOrigenPiso(), ocultarDetalle ? null : p.getOrigenDepto(), ocultarDetalle ? null : p.getOrigenObservaciones(),
                    ocultarDetalle ? null : p.getDestinoPiso(), ocultarDetalle ? null : p.getDestinoDepto(), ocultarDetalle ? null : p.getDestinoObservaciones(),
                    p.isRequiereMoto(), LookupResponse.from(p.getEstado()),
                    CadeteResumen.from(p.getCadeteAsignado()),
                    p.isProgramado(), p.getFechaProgramada(),
                    p.getCreadoEn(), p.getAsignadoEn(), p.getVistoEn(),
                    p.getAceptadoEn(), p.getRetiradoEn(), p.getFinalizadoEn(),
                    p.getCanceladoEn(),
                    p.getFotoRecepcionUrl(), p.getEntregaReceptorNombre(), p.getEntregaFotoUrl(), p.getFirmaReceptorUrl(),
                    p.getRetiroLat(), p.getRetiroLng(), p.getEntregaLat(), p.getEntregaLng(),
                    p.getMotivoCancelacion(), p.getTokenSeguimiento(), p.isSmsFallido(),
                    p.getCalificacionEstrellas(), p.getCalificacionComentario(),
                    p.getMotivoNoEntrega(), p.getNoEntregadoEn(),
                    incluirParadas
                            ? p.getParadas().stream().sorted(java.util.Comparator.comparingInt(com.cadeteria.backend.model.PedidoParada::getOrden))
                                    .map(ParadaResponse::from).toList()
                            : List.of(),
                    p.getAsignadoPorUsername(), p.getCanceladoPorUsername(), p.isPrioritario(),
                    p.getOrigenCarga(), p.getCreadoPorUsername(),
                    p.getReclamoDetalle(), p.getUltimoReclamoEn(),
                    p.getReclamoTipo(), p.getReclamoEstado());
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

    /**
     * Dirección que un cliente ya usó (2026-09-25): para cargarla con un clic como origen o
     * destino. lat/lng, piso, depto y observaciones son los de la última vez que se usó.
     */
    public record DireccionFrecuenteResponse(String direccion, Double lat, Double lng,
                                             String piso, String depto, String observaciones,
                                             int vecesOrigen, int vecesDestino, Instant ultimaVez) {}

    public record AsignarRequest(@NotBlank String cadeteId) {}

    /** Boton "Quitar": si no se manda, se devuelve la comisión (comportamiento de siempre). */
    public record QuitarRequest(Boolean devolverComision) {}

    /** Agrupar pedidos con orígenes cercanos en una sola oferta a un cadete (ronda 4, punto 61). */
    public record AsignarLoteRequest(@NotBlank String cadeteId, @NotEmpty List<String> pedidoIds) {}

    /** Boton "Anular": motivo "CLIENTE" u "OTRO" (para métricas), null si no se especifica. */
    public record CancelarRequest(
            @Pattern(regexp = "^$|CLIENTE|OTRO", message = "El motivo de anulación tiene que ser CLIENTE u OTRO.") String motivo) {}

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
    /**
     * archivoPerdido: la app encoló el "Retirado" sin señal y, al reintentar, el archivo local
     * de la foto ya no existía (se limpió la cache). Se acepta sin foto aunque sea obligatoria,
     * antes que dejar el viaje trabado en la cola para siempre — y queda un comentario.
     */
    public record RecepcionRequest(@Size(max = 500) String fotoUrl,
                                   @DecimalMin("-90") @DecimalMax("90") Double lat,
                                   @DecimalMin("-180") @DecimalMax("180") Double lng,
                                   Boolean archivoPerdido,
                                   /** Error del GPS en metros (APK 2026-09-26) — con buena precisión se aprende la dirección. */
                                   @PositiveOrZero Float precision) {}

    /** Botón "Rechazar": motivo opcional, texto libre (spec Métricas: detectar patrones de rechazo). */
    public record RechazarRequest(@Size(max = 300, message = "El motivo puede tener hasta 300 caracteres.") String motivo) {}

    /** Botón "No se pudo entregar" (ej. el cliente no atendió): motivo opcional, texto libre. */
    public record NoEntregadoRequest(@Size(max = 500, message = "El motivo puede tener hasta 500 caracteres.") String motivo) {}

    /**
     * receptorNombre y fotoUrl obligatorios al finalizar; firmaUrl es obligatoria u opcional
     * según "firma_receptor_obligatoria" en Configuración (ronda 3, punto 51).
     * lat/lng: ubicación del cadete al finalizar (opcional), misma idea que en RecepcionRequest.
     */
    /** archivoPerdido: igual que en {@link RecepcionRequest}, para la foto y la firma de la entrega. */
    public record FinalizarRequest(
            @Size(max = 100, message = "El nombre de quien recibe puede tener hasta 100 letras.")
            @Pattern(regexp = Validaciones.VACIO_O + Validaciones.NOMBRE_PERSONA, message = Validaciones.MSJ_RECEPTOR) String receptorNombre,
            @Size(max = 500) String fotoUrl, @Size(max = 500) String firmaUrl,
            @DecimalMin("-90") @DecimalMax("90") Double lat, @DecimalMin("-180") @DecimalMax("180") Double lng,
            Boolean archivoPerdido,
            /** Error del GPS en metros (APK 2026-09-26) — con buena precisión se aprende la dirección. */
            @PositiveOrZero Float precision) {
        public boolean seperdioElArchivo() {
            return Boolean.TRUE.equals(archivoPerdido);
        }
    }

    public record RutaResponse(Object geoJson) {}

    /** Un punto del trayecto GPS real guardado mientras el pedido estaba EN_CURSO. */
    public record PuntoTrayectoResponse(Double lat, Double lng, Instant capturadoEn) {
        public static PuntoTrayectoResponse from(com.cadeteria.backend.model.PedidoUbicacion p) {
            return new PuntoTrayectoResponse(p.getLat(), p.getLng(), p.getCapturadoEn());
        }
    }

    /** Nota de texto libre que el cadete deja sobre el pedido (ej. "entregado en porteria a Fulano"). */
    public record ComentarioRequest(
            @NotBlank(message = "Escribí el comentario.") @Size(max = 500, message = "El comentario puede tener hasta 500 caracteres.") String texto) {}

    /** Mejora 75 — editar el precio de un pedido ya cargado. */
    public record PrecioRequest(
            @NotNull(message = "Falta el precio.") @PositiveOrZero(message = "El precio no puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "El precio no es válido.") java.math.BigDecimal precio) {}

    public record PrecioLogResponse(String id, java.math.BigDecimal precioAnterior, java.math.BigDecimal precioNuevo,
                                     String cambiadoPorUsername, Instant cambiadoEn) {
        public static PrecioLogResponse from(com.cadeteria.backend.model.PedidoPrecioLog l) {
            return new PrecioLogResponse(l.getId(), l.getPrecioAnterior(), l.getPrecioNuevo(), l.getCambiadoPorUsername(), l.getCambiadoEn());
        }
    }

    /** "Pedidos finalizados" paginado (mejora 2026-09-16) — ver {@link com.cadeteria.backend.service.PedidoService#paginaFinalizados}. */
    public record PaginaPedidosResponse(java.util.List<PedidoResponse> items, long total, int pagina, int totalPaginas) {}

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
