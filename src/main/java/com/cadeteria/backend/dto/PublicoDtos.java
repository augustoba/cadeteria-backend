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
            Integer etaMinutos
    ) {
        public static SeguimientoResponse from(Pedido p) {
            return from(p, null);
        }

        public static SeguimientoResponse from(Pedido p, Integer etaMinutos) {
            boolean finalizado = "FINALIZADO".equals(p.getEstado().getId());
            boolean enCurso = "EN_CURSO".equals(p.getEstado().getId());
            Cadete cadete = p.getCadeteAsignado();
            return new SeguimientoResponse(
                    p.getEstado().getNombre(),
                    p.getOrigenDireccion(),
                    p.getDestinoDireccion(),
                    p.getPrecio(),
                    CadeteInfo.from(cadete),
                    finalizado,
                    finalizado ? p.getEntregaReceptorNombre() : null,
                    finalizado ? p.getEntregaFotoUrl() : null,
                    finalizado && p.getCalificadoEn() == null,
                    p.getCalificacionEstrellas(),
                    p.getCalificacionComentario(),
                    enCurso && cadete != null ? cadete.getLat() : null,
                    enCurso && cadete != null ? cadete.getLng() : null,
                    enCurso ? p.getDestinoLat() : null,
                    enCurso ? p.getDestinoLng() : null,
                    enCurso ? etaMinutos : null
            );
        }
    }

    /** Body de POST /api/publico/pedidos/{token}/calificacion — comentario es opcional. */
    public record CalificarRequest(int estrellas, String comentario) {}

    public record CadeteInfo(
            String nombre, String fotoUrl, String tipoVehiculo, String vehiculoColor, String vehiculoPatente,
            /** Para que el cliente le transfiera si prefiere pagar así — el cadete los carga desde la app. */
            String cbu, String aliasCbu
    ) {
        public static CadeteInfo from(Cadete c) {
            if (c == null) return null;
            return new CadeteInfo(c.getNombre(), c.getFotoUrl(),
                    c.getTipoVehiculo() == null ? null : c.getTipoVehiculo().getNombre(),
                    c.getVehiculoColor(), c.getVehiculoPatente(), c.getCbu(), c.getAliasCbu());
        }
    }
}
