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
            /** Solo mientras el pedido está EN_CURSO (ronda 4, punto 12) — tracking en vivo estilo Uber. */
            Double cadeteLat,
            Double cadeteLng,
            Double destinoLat,
            Double destinoLng,
            /** Minutos estimados de llegada (ronda 4, punto 30) — null si ORS no está configurado o falló. */
            Integer etaMinutos,
            /** El cadete ya retiró el pedido en el origen (2026-09-25): "va a buscarlo" vs "va hacia el destino". */
            boolean retirado,
            /** Foto que sacó el cadete al retirar — desde que retiró (2026-09-25). */
            String retiroFotoUrl,
            /** Firma de quien recibió — solo entregado. */
            String firmaUrl,
            /** Reclamo del cliente (2026-09-26): tipo y estado, para los botones "Ya se solucionó" / "Sigue el problema". */
            String reclamoTipo, String reclamoEstado,
            /** WhatsApp de atención al cliente (Configuración) — "" si no está cargado. */
            String whatsappAtencion
    ) {
        public static SeguimientoResponse from(Pedido p) {
            return from(p, null, "");
        }

        public static SeguimientoResponse from(Pedido p, Integer etaMinutos) {
            return from(p, etaMinutos, "");
        }

        public static SeguimientoResponse from(Pedido p, Integer etaMinutos, String whatsappAtencion) {
            boolean finalizado = "FINALIZADO".equals(p.getEstado().getId());
            boolean enCurso = "EN_CURSO".equals(p.getEstado().getId());
            Cadete cadete = p.getCadeteAsignado();
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
                    enCurso && cadete != null ? cadete.getLat() : null,
                    enCurso && cadete != null ? cadete.getLng() : null,
                    enCurso ? p.getDestinoLat() : null,
                    enCurso ? p.getDestinoLng() : null,
                    enCurso ? etaMinutos : null,
                    p.getRetiradoEn() != null,
                    p.getRetiradoEn() != null ? p.getFotoRecepcionUrl() : null,
                    finalizado ? p.getFirmaReceptorUrl() : null,
                    p.getReclamoTipo(), p.getReclamoEstado(),
                    whatsappAtencion == null ? "" : whatsappAtencion
            );
        }
    }

    /** Body de POST /api/publico/pedidos/{token}/calificacion — comentario es opcional. */
    public record CalificarRequest(int estrellas, String comentario) {}

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
            return new CadeteInfo(c.getNombre(), c.getApellido(), c.getDni(), c.getTelefono(), c.getFotoUrl(),
                    c.getTipoVehiculo() == null ? null : c.getTipoVehiculo().getNombre(),
                    c.getVehiculoColor(), c.getVehiculoPatente(), c.getFotoVehiculoUrl(),
                    c.getCbu(), c.getAliasCbu());
        }
    }
}
