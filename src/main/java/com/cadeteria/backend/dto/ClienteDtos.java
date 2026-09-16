package com.cadeteria.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Ficha de cliente por teléfono (ronda 4, puntos 44/58/67). */
public final class ClienteDtos {

    private ClienteDtos() {}

    public record ClienteRequest(
            /** Va siempre por la URL (@PathVariable); acá es opcional, el controller la pisa igual. */
            String telefono,
            String nombreContacto,
            String empresa,
            BigDecimal tarifaEspecial,
            boolean problematico,
            String notasProblematico,
            boolean activo,
            /** "CONTADO" | "CUENTA_CORRIENTE" (ronda 10, punto 100) — null/blank se toma como "CONTADO". */
            String modalidadFacturacion
    ) {}

    public record ClienteResponse(
            String telefono, String nombreContacto, String empresa,
            BigDecimal tarifaEspecial, boolean problematico, String notasProblematico,
            boolean activo, int cantidadPedidos, BigDecimal montoTotal, Instant ultimoPedidoEn,
            String modalidadFacturacion,
            /** Solo tiene sentido con modalidadFacturacion=CUENTA_CORRIENTE — suma de finalizados desde la última liquidación. */
            BigDecimal saldoPendiente
    ) {}

    /** Botón "Liquidar cuenta corriente" — marca todo lo finalizado hasta ahora como ya cobrado. */
    public record LiquidarCuentaCorrienteResponse(BigDecimal montoLiquidado, Instant liquidadaHasta) {}

    public record PedidoResumenResponse(String id, Long numero, Instant creadoEn, BigDecimal precio, String estadoId) {}

    public record ClienteFichaResponse(ClienteResponse cliente, List<PedidoResumenResponse> pedidosRecientes) {}

    /** Listado paginado (mejora 2026-09-16) — ver {@link com.cadeteria.backend.service.ClienteService#listarPaginado}. */
    public record ClientesPaginaResponse(List<ClienteResponse> items, long total, int pagina, int totalPaginas) {}

    /** Aviso rápido al cargar un pedido nuevo — si el teléfono es problemático o tiene tarifa especial. */
    public record ClienteAvisoResponse(boolean problematico, String notasProblematico, BigDecimal tarifaEspecial) {}
}
