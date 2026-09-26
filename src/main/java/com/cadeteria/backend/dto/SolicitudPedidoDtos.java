package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.SolicitudPedido;
import com.cadeteria.backend.common.Validaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public final class SolicitudPedidoDtos {

    private SolicitudPedidoDtos() {}

    /** Lo que completa el cliente en la página pública "/pedir". */
    public record SolicitudPedidoRequest(
            @NotBlank(message = "Falta la dirección de retiro.") @Size(max = 255, message = "La dirección de retiro es demasiado larga.")
            String origenDireccion,
            @NotNull(message = "Falta ubicar la dirección de retiro en el mapa.") @DecimalMin("-90") @DecimalMax("90") Double origenLat,
            @NotNull(message = "Falta ubicar la dirección de retiro en el mapa.") @DecimalMin("-180") @DecimalMax("180") Double origenLng,
            @NotBlank(message = "Falta la dirección de entrega.") @Size(max = 255, message = "La dirección de entrega es demasiado larga.")
            String destinoDireccion,
            @NotNull(message = "Falta ubicar la dirección de entrega en el mapa.") @DecimalMin("-90") @DecimalMax("90") Double destinoLat,
            @NotNull(message = "Falta ubicar la dirección de entrega en el mapa.") @DecimalMin("-180") @DecimalMax("180") Double destinoLng,
            boolean llevaDinero,
            @PositiveOrZero(message = "El monto no puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "El monto no es válido.") BigDecimal montoDeclarado,
            boolean llevaValores,
            @PositiveOrZero(message = "El valor declarado no puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "El valor declarado no es válido.") BigDecimal montoValores,
            /** Lo pide el cliente (mejora 2026-09-24) — el admin lo puede cambiar al revisar. */
            boolean requiereMoto,
            boolean retornaAlOrigen,
            @NotBlank(message = "Falta tu nombre.") @Pattern(regexp = Validaciones.NOMBRE_CLIENTE, message = Validaciones.MSJ_NOMBRE_CLIENTE)
            String clienteNombre,
            @NotBlank(message = "Falta tu teléfono.") @Pattern(regexp = Validaciones.TELEFONO, message = Validaciones.MSJ_TELEFONO) String clienteTelefono,
            @Size(max = 1000, message = "El detalle puede tener hasta 1000 caracteres.") String detalle,
            /** Piso, depto y observaciones de cada dirección (mejora 2026-09-24), opcionales. */
            @Size(max = 20, message = "El piso puede tener hasta 20 caracteres.") String origenPiso,
            @Size(max = 20, message = "El depto puede tener hasta 20 caracteres.") String origenDepto,
            @Size(max = 300, message = "Las observaciones pueden tener hasta 300 caracteres.") String origenObservaciones,
            @Size(max = 20, message = "El piso puede tener hasta 20 caracteres.") String destinoPiso,
            @Size(max = 20, message = "El depto puede tener hasta 20 caracteres.") String destinoDepto,
            @Size(max = 300, message = "Las observaciones pueden tener hasta 300 caracteres.") String destinoObservaciones,
            /** Token de VerificacionTelefonoService.verificarCodigo — confirma que el teléfono es real (mejora 2026-09-17). */
            /** Obligatorio solo con `verificacion_telefono_activa` prendida (lo valida el service). */
            String verificacionToken,
            /**
             * De dónde salió el pin de cada dirección (2026-09-25): "manual" (ubicado a mano) o
             * "google_link" (link de Google Maps pegado) se aprenden en la cache de direcciones
             * (GeocodingProxyService#aprenderPin); el nombre de un buscador o null, no.
             */
            String origenFuente, String destinoFuente
    ) {}

    public record SolicitudPedidoResponse(
            String id,
            String origenDireccion, Double origenLat, Double origenLng,
            String destinoDireccion, Double destinoLat, Double destinoLng,
            boolean llevaDinero, BigDecimal montoDeclarado,
            boolean llevaValores,
            BigDecimal montoValores,
            boolean retornaAlOrigen,
            String clienteNombre, String clienteTelefono,
            String detalle,
            String origenPiso, String origenDepto, String origenObservaciones,
            String destinoPiso, String destinoDepto, String destinoObservaciones,
            String estado,
            boolean requiereMoto, BigDecimal precio,
            String pedidoCreadoId, String motivoRechazo,
            Instant creadoEn,
            /** No se pudo mandar el código por ningún medio — el admin valida el teléfono a mano (spec-antiabuso §6). */
            boolean sinVerificar,
            /** Aviso del teléfono (problemático / reportes de cadetes), null si no hay nada — spec-antiabuso Fase 1. */
            com.cadeteria.backend.dto.ClienteDtos.ClienteAvisoResponse avisoCliente
    ) {
        public static SolicitudPedidoResponse from(SolicitudPedido s) {
            return from(s, null);
        }

        public static SolicitudPedidoResponse from(SolicitudPedido s,
                                                   com.cadeteria.backend.dto.ClienteDtos.ClienteAvisoResponse aviso) {
            return new SolicitudPedidoResponse(
                    s.getId(), s.getOrigenDireccion(), s.getOrigenLat(), s.getOrigenLng(),
                    s.getDestinoDireccion(), s.getDestinoLat(), s.getDestinoLng(),
                    s.isLlevaDinero(), s.getMontoDeclarado(), s.isLlevaValores(), s.getMontoValores(), s.isRetornaAlOrigen(),
                    s.getClienteNombre(), s.getClienteTelefono(), s.getDetalle(),
                    s.getOrigenPiso(), s.getOrigenDepto(), s.getOrigenObservaciones(),
                    s.getDestinoPiso(), s.getDestinoDepto(), s.getDestinoObservaciones(), s.getEstado(),
                    s.isRequiereMoto(), s.getPrecio(),
                    s.getPedidoCreadoId(), s.getMotivoRechazo(), s.getCreadoEn(),
                    s.isSinVerificar(), aviso);
        }
    }

    /** Confirmar directo (ya se acordó el precio) o mandar cotización (el cliente confirma solo) — mismos campos. */
    public record RevisarSolicitudRequest(
            boolean requiereMoto,
            @NotNull(message = "Falta el precio.") @PositiveOrZero(message = "El precio no puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "El precio no es válido.") BigDecimal precio,
            @PositiveOrZero(message = "El monto no puede ser negativo.") BigDecimal montoDeclarado
    ) {}

    public record RechazarSolicitudRequest(@Size(max = 255, message = "El motivo puede tener hasta 255 caracteres.") String motivo) {}

    /** "Marcar como fraudulento" (spec-antiabuso Fase 4) — la nota queda en la ficha del cliente. */
    public record FraudulentoRequest(@Size(max = 500, message = "La nota puede tener hasta 500 caracteres.") String nota) {}

    /** Lo que ve la página pública "/confirmar-pedido/:token" antes (y después) de confirmar. */
    public record ConfirmacionPublicaResponse(
            String estado, String origenDireccion, String destinoDireccion, BigDecimal precio,
            String tokenSeguimiento
    ) {}
}
