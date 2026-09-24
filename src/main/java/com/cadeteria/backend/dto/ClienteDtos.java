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

    /**
     * Aviso rápido al cargar un pedido nuevo o revisar una solicitud web — si el teléfono es
     * problemático, tiene tarifa especial o acumula reportes de cadetes (spec-antiabuso Fase 4:
     * "Este teléfono tiene 3 reportes: 2 de demora, 1 de valores no declarados").
     */
    public record ClienteAvisoResponse(
            boolean problematico, String notasProblematico, BigDecimal tarifaEspecial,
            int cantidadReportes,
            /** Cantidad por tipo (DEMORO, NO_DECLARO_VALORES, PEDIDO_FALSO, OTRO), solo los que tienen alguno. */
            java.util.Map<String, Long> reportesPorTipo,
            Instant ultimoReporteEn
    ) {
        public static final ClienteAvisoResponse VACIO =
                new ClienteAvisoResponse(false, null, null, 0, java.util.Map.of(), null);

        /** true si hay algo para mostrarle al admin (el cartel ámbar). */
        public boolean hayAviso() {
            return problematico || tarifaEspecial != null || cantidadReportes > 0;
        }
    }

    /** Un reporte de cadete, para el historial en la ficha del cliente (spec-antiabuso Fase 4). */
    public record ReporteClienteResponse(
            String id, String tipo, Long pedidoNumero, String pedidoId, String cadeteNombre, String nota, Instant creadoEn
    ) {
        public static ReporteClienteResponse from(com.cadeteria.backend.model.ReporteCliente r) {
            return new ReporteClienteResponse(r.getId(), r.getTipo(), r.getPedidoNumero(), r.getPedidoId(),
                    r.getCadeteNombre(), r.getNota(), r.getCreadoEn());
        }
    }
}
