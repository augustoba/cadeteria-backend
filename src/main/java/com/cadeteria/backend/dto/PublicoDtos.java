package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Pedido;

import java.math.BigDecimal;

/** Lo que ve el cliente en la pagina publica de seguimiento (spec 5.7/6, diseno sección 8). */
public final class PublicoDtos {

    private PublicoDtos() {}

    public record SeguimientoResponse(
            String estado,
            String origenDireccion,
            String destinoDireccion,
            BigDecimal precio,
            /** Lo declarado al pedir — se muestra como confirmación de lo que el cliente cargó (mejora 2026-09-23). */
            boolean llevaDinero,
            BigDecimal montoDeclarado,
            boolean llevaValores,
            BigDecimal montoValores,
            CadeteInfo cadete,
            boolean comprobanteDisponible,
            String entregaReceptorNombre,
            String entregaFotoUrl,
            /** true cuando ya está FINALIZADO y todavía no lo calificó nadie — la página muestra el formulario. */
            boolean puedeCalificar,
            Integer calificacionEstrellas,
            String calificacionComentario,
            /** Dinero propio que el cadete tiene que llevar encima (2026-10-14) — null si no hace falta. */
            BigDecimal montoDisponer,
            /** El cadete vuelve al origen después de entregar (2026-10-14). */
            boolean retornaAlOrigen,
            /** Detalle que cargó el cliente o el operador al pedir — puede ser null. */
            String detalle,
            /**
             * Origen y destino para el mapa (2026-10-16). La ubicación del cadete y el tiempo de llegada
             * no se mandan: el cadete puede llevar otros pedidos y no se sabe cuál hace primero.
             */
            Double origenLat,
            Double origenLng,
            Double destinoLat,
            Double destinoLng,
            /** El cadete ya retiró el pedido en el origen (2026-09-25): "va a buscarlo" vs "va hacia el destino". */
            boolean retirado,
            /** Foto que sacó el cadete al retirar — desde que retiró (2026-09-25). */
            String retiroFotoUrl,
            /** Firma de quien recibió — solo entregado. */
            String firmaUrl,
            /** Reclamo del cliente (2026-09-26): tipo y estado, para los botones "Ya se solucionó" / "Sigue el problema". */
            String reclamoTipo, String reclamoEstado,
            /** WhatsApp de atención al cliente (Configuración) — "" si no está cargado. */
            String whatsappAtencion,
            /** Cuándo se cierra solo el reclamo si el cliente no responde — null si no se cierra solo (2026-09-26). */
            java.time.Instant reclamoCierraEn,
            /**
             * Piso y depto de origen y destino (2026-11-11: los clientes reclamaban que no se veían).
             * Las observaciones solo si las escribió el cliente (pedido cargado desde la web): en los
             * pedidos del panel las escribe el operador y pueden ser notas internas.
             */
            String origenPiso, String origenDepto, String origenObservaciones,
            String destinoPiso, String destinoDepto, String destinoObservaciones,
            /** Paradas intermedias en orden, con si ya se entregaron (2026-11-11). */
            java.util.List<ParadaSeguimiento> paradas
    ) {
        public static SeguimientoResponse from(Pedido p) {
            return from(p, "");
        }

        public static SeguimientoResponse from(Pedido p, String whatsappAtencion) {
            return from(p, whatsappAtencion, null);
        }

        public static SeguimientoResponse from(Pedido p, String whatsappAtencion,
                                               java.time.Instant reclamoCierraEn) {
            boolean finalizado = "FINALIZADO".equals(p.getEstado().getId());
            boolean enCurso = "EN_CURSO".equals(p.getEstado().getId());
            Cadete cadete = p.getCadeteAsignado();
            boolean delCliente = com.cadeteria.backend.service.PedidoService.ORIGEN_WEB.equals(p.getOrigenCarga());
            return new SeguimientoResponse(
                    p.getEstado().getNombre(),
                    p.getOrigenDireccion(),
                    p.getDestinoDireccion(),
                    p.getPrecio(),
                    p.getMontoDeclarado() != null && p.getMontoDeclarado().signum() > 0,
                    p.getMontoDeclarado(),
                    p.isLlevaValores(),
                    p.getMontoValores(),
                    CadeteInfo.from(cadete),
                    finalizado || enCurso,
                    finalizado ? p.getEntregaReceptorNombre() : null,
                    finalizado ? p.getEntregaFotoUrl() : null,
                    finalizado && p.getCalificadoEn() == null,
                    p.getCalificacionEstrellas(),
                    p.getCalificacionComentario(),
                    p.getMontoDisponer() != null && p.getMontoDisponer().signum() > 0 ? p.getMontoDisponer() : null,
                    p.isRetornaAlOrigen(),
                    p.getDetalle(),
                    p.getOrigenLat(),
                    p.getOrigenLng(),
                    p.getDestinoLat(),
                    p.getDestinoLng(),
                    p.getRetiradoEn() != null,
                    p.getRetiradoEn() != null ? p.getFotoRecepcionUrl() : null,
                    finalizado ? p.getFirmaReceptorUrl() : null,
                    p.getReclamoTipo(), p.getReclamoEstado(),
                    whatsappAtencion == null ? "" : whatsappAtencion,
                    reclamoCierraEn,
                    p.getOrigenPiso(), p.getOrigenDepto(), delCliente ? p.getOrigenObservaciones() : null,
                    p.getDestinoPiso(), p.getDestinoDepto(), delCliente ? p.getDestinoObservaciones() : null,
                    p.getParadas() == null ? java.util.List.of() : p.getParadas().stream()
                            .sorted(java.util.Comparator.comparingInt(com.cadeteria.backend.model.PedidoParada::getOrden))
                            .map(pa -> new ParadaSeguimiento(pa.getOrden(), pa.getDireccion(), pa.getEntregadoEn() != null))
                            .toList()
            );
        }
    }

    /** Parada intermedia en el seguimiento del cliente. */
    public record ParadaSeguimiento(int orden, String direccion, boolean entregada) {}

    /** Body de POST /api/publico/pedidos/{token}/calificacion — comentario es opcional. */
    public record CalificarRequest(
            @jakarta.validation.constraints.Min(value = 1, message = "La calificación tiene que ser de 1 a 5 estrellas.")
            @jakarta.validation.constraints.Max(value = 5, message = "La calificación tiene que ser de 1 a 5 estrellas.") int estrellas,
            @jakarta.validation.constraints.Size(max = 500, message = "El comentario puede tener hasta 500 caracteres.") String comentario) {}

    public record CadeteInfo(
            String nombre,
            /** Apellido y DNI (2026-09-25): el cliente tiene que poder identificar a quién le entrega. */
            String apellido, String dni,
            /** Teléfono del cadete (2026-09-25): para que el cliente lo llame o le escriba. */
            String telefono,
            String fotoUrl, String tipoVehiculo, String vehiculoColor, String vehiculoPatente,
            /** Foto del vehículo (solo tiene sentido con MOTO — null en BICI). */
            String fotoVehiculoUrl,
            /** Para que el cliente le transfiera si prefiere pagar así — el cadete los carga desde la app. */
            String cbu, String aliasCbu
    ) {
        public static CadeteInfo from(Cadete c) {
            if (c == null) return null;
            // En BICI no se muestran los datos de la moto que quedan guardados (2026-11-20): el que
            // recibe el pedido esperaría una moto con esa patente y llega alguien en bici.
            boolean moto = com.cadeteria.backend.service.CadeteService.esMoto(c);
            return new CadeteInfo(c.getNombre(), c.getApellido(), c.getDni(), c.getTelefono(), c.getFotoUrl(),
                    c.getTipoVehiculo() == null ? null : c.getTipoVehiculo().getNombre(),
                    moto ? c.getVehiculoColor() : null, moto ? c.getVehiculoPatente() : null,
                    moto ? c.getFotoVehiculoUrl() : null,
                    c.getCbu(), c.getAliasCbu());
        }
    }
}
