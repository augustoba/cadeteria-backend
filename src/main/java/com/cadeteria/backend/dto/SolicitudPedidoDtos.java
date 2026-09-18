package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.SolicitudPedido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class SolicitudPedidoDtos {

    private SolicitudPedidoDtos() {}

    /** Lo que completa el cliente en la página pública "/pedir". */
    public record SolicitudPedidoRequest(
            @NotBlank String origenDireccion, @NotNull Double origenLat, @NotNull Double origenLng,
            @NotBlank String destinoDireccion, @NotNull Double destinoLat, @NotNull Double destinoLng,
            boolean llevaDinero, BigDecimal montoDeclarado,
            boolean retornaAlOrigen,
            @NotBlank String clienteNombre, @NotBlank String clienteTelefono,
            String detalle,
            /** Token de VerificacionTelefonoService.verificarCodigo — confirma que el teléfono es real (mejora 2026-09-17). */
            @NotBlank String verificacionToken
    ) {}

    public record SolicitudPedidoResponse(
            String id,
            String origenDireccion, Double origenLat, Double origenLng,
            String destinoDireccion, Double destinoLat, Double destinoLng,
            boolean llevaDinero, BigDecimal montoDeclarado,
            boolean retornaAlOrigen,
            String clienteNombre, String clienteTelefono,
            String detalle,
            String estado,
            LookupResponse zona, LookupResponse tipoVehiculoRequerido, BigDecimal precio,
            String pedidoCreadoId, String motivoRechazo,
            Instant creadoEn
    ) {
        public static SolicitudPedidoResponse from(SolicitudPedido s) {
            return new SolicitudPedidoResponse(
                    s.getId(), s.getOrigenDireccion(), s.getOrigenLat(), s.getOrigenLng(),
                    s.getDestinoDireccion(), s.getDestinoLat(), s.getDestinoLng(),
                    s.isLlevaDinero(), s.getMontoDeclarado(), s.isRetornaAlOrigen(),
                    s.getClienteNombre(), s.getClienteTelefono(), s.getDetalle(), s.getEstado(),
                    LookupResponse.from(s.getZona()), LookupResponse.from(s.getTipoVehiculoRequerido()), s.getPrecio(),
                    s.getPedidoCreadoId(), s.getMotivoRechazo(), s.getCreadoEn());
        }
    }

    /** Confirmar directo (ya se acordó el precio) o mandar cotización (el cliente confirma solo) — mismos campos. */
    public record RevisarSolicitudRequest(
            @NotBlank String zonaId, @NotBlank String tipoVehiculoRequeridoId,
            @NotNull BigDecimal precio, BigDecimal montoDeclarado
    ) {}

    public record RechazarSolicitudRequest(String motivo) {}

    /** Lo que ve la página pública "/confirmar-pedido/:token" antes (y después) de confirmar. */
    public record ConfirmacionPublicaResponse(
            String estado, String origenDireccion, String destinoDireccion, BigDecimal precio,
            String tokenSeguimiento
    ) {}
}
