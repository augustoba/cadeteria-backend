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
            boolean llevaValores,
            BigDecimal montoValores,
            /** Lo pide el cliente (mejora 2026-09-24) — el admin lo puede cambiar al revisar. */
            boolean requiereMoto,
            boolean retornaAlOrigen,
            @NotBlank String clienteNombre, @NotBlank String clienteTelefono,
            String detalle,
            /** Piso, depto y observaciones de cada dirección (mejora 2026-09-24), opcionales. */
            String origenPiso, String origenDepto, String origenObservaciones,
            String destinoPiso, String destinoDepto, String destinoObservaciones,
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
            boolean requiereMoto, @NotNull BigDecimal precio, BigDecimal montoDeclarado
    ) {}

    public record RechazarSolicitudRequest(String motivo) {}

    /** "Marcar como fraudulento" (spec-antiabuso Fase 4) — la nota queda en la ficha del cliente. */
    public record FraudulentoRequest(String nota) {}

    /** Lo que ve la página pública "/confirmar-pedido/:token" antes (y después) de confirmar. */
    public record ConfirmacionPublicaResponse(
            String estado, String origenDireccion, String destinoDireccion, BigDecimal precio,
            String tokenSeguimiento
    ) {}
}
