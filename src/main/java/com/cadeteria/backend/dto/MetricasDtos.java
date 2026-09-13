package com.cadeteria.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Pantalla de Métricas del panel admin — todo acotado a un rango de fechas [desde, hasta). */
public final class MetricasDtos {

    private MetricasDtos() {}

    /** Conteo de pedidos creados en el rango, por cómo terminaron. */
    public record ResumenDiaResponse(
            long totalPedidos,
            long finalizados,
            long cancelados,
            long canceladosCliente,
            long canceladosOtro,
            long sinAsignar,
            long pendientes,
            long enCurso
    ) {}

    /** Una fila por cadete: actividad y desempeño en el rango. */
    public record CadeteMetricaResponse(
            String cadeteId,
            String nombre,
            String apellido,
            LookupResponse tipoVehiculo,
            double horasOnline,
            long viajesAceptados,
            long viajesRechazados,
            /** Ofertas que vencieron sin respuesta y se reasignaron a otro cadete (spec sección 4). */
            long viajesNoAceptados,
            int viajesFinalizados,
            BigDecimal montoTransportadoTotal,
            BigDecimal montoCobradoTotal,
            double kmTotal,
            double promedioViajesPorHora,
            double promedioPrecioPorHora,
            /** null si ningún cliente lo calificó todavía en el rango. */
            Double promedioCalificacion,
            long cantidadCalificaciones
    ) {}

    /** Un rechazo con motivo, para la sección "Motivos de rechazo" de Métricas. */
    public record RechazoResponse(Long pedidoNumero, String cadeteNombre, String motivo, Instant ofrecidoEn) {}

    /** Pedidos creados por hora del día (0-23, hora local) — para el gráfico de Métricas (ronda 5, punto 31). */
    public record PorHoraResponse(int hora, long cantidad) {}

    /** Una fila por zona: volumen e ingresos en el rango (ronda 5, punto 34). */
    public record ZonaMetricaResponse(
            String zonaId, String zonaNombre, long cantidadPedidos, long finalizados, BigDecimal montoCobradoTotal
    ) {}
}
