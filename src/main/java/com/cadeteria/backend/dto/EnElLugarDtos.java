package com.cadeteria.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Registros del control "Retirado / Entregado solo en el lugar" (carril B, 2026-09-28): cuántas veces
 * un cadete marcó fuera de zona con "Estoy en el lugar", intentó con GPS falso, marcó con el GPS
 * impreciso o le finalizaron un pedido desde el panel. Nadie aprueba nada: es para mirar.
 */
public final class EnElLugarDtos {

    private EnElLugarDtos() {}

    /**
     * Un pedido con algo anotado. tipos: RETIRO_FUERA_ZONA, ENTREGA_FUERA_ZONA, PARADA_FUERA_ZONA,
     * UBICACION_SIMULADA, UBICACION_IMPRECISA, FINALIZADO_POR_ADMIN.
     */
    public record RegistroPedido(String pedidoId, Long numero, Instant creadoEn, String cadeteId, String cadeteNombre,
                                 List<String> tipos, Integer retiroDistanciaM, Integer entregaDistanciaM,
                                 String finalizadoPorAdmin, String finalizadoAdminMotivo) {}

    /**
     * Totales de un cadete. vecesFueraZona = veces que usó "Estoy en el lugar" lejos (cada retiro, parada
     * o entrega cuenta una). intentosUbicacionSimulada es de siempre (va en el cadete, no en el rango).
     */
    public record ResumenCadete(String cadeteId, String cadeteNombre, long vecesFueraZona, long vecesImprecisa,
                                long vecesFinalizadoPorAdmin, int intentosUbicacionSimulada,
                                Instant ultimoIntentoUbicacionSimuladaEn) {}

    /** Ficha del cadete: sus totales y los últimos pedidos con algo anotado. */
    public record FichaCadete(ResumenCadete resumen, List<RegistroPedido> ultimos) {}
}
