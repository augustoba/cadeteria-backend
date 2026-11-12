package com.cadeteria.backend.service;

import com.cadeteria.backend.util.TelefonoUtils;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Filtros de la pantalla "Pedidos" del panel (2026-11-11): el historial completo para poder
 * contestar un reclamo de un pedido viejo. Todo opcional; null = sin ese filtro.
 *
 * <ul>
 *   <li>{@code retiro}: 0 = no importa, 1 = todavía no retiró, 2 = ya retiró. Separa "En curso"
 *       de "Retirado", que en la base son el mismo estado EN_CURSO.</li>
 *   <li>{@code desde}/{@code hasta}: sobre la creación; hasta es exclusivo.</li>
 *   <li>{@code minutoDesde}/{@code minutoHasta}: rango horario de cualquier día, en minutos desde
 *       las 00:00 de Argentina, los dos incluidos. Si desde &gt; hasta cruza la medianoche
 *       (22:00 a 02:00).</li>
 *   <li>{@code ajusteMinutos}: lo que se le suma a la hora UTC guardada para llevarla a la hora
 *       de Argentina, ya sumado 1440 para que el MOD de la consulta nunca vea un negativo.</li>
 * </ul>
 */
public record BusquedaPedidos(
        Long numero,
        String telefono,
        String cadeteId,
        String estadoId,
        int retiro,
        Instant desde,
        Instant hasta,
        Integer minutoDesde,
        Integer minutoHasta,
        int ajusteMinutos) {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int MINUTOS_DIA = 24 * 60;

    /**
     * @param retirado null = no importa; false = solo sin retirar; true = solo retirados
     * @param horaDesde/horaHasta si viene una sola, la otra es el principio o el fin del día
     */
    public static BusquedaPedidos de(Long numero, String telefono, String cadeteId, String estadoId,
                                     Boolean retirado, Instant desde, Instant hasta,
                                     LocalTime horaDesde, LocalTime horaHasta) {
        String tel = (telefono == null || telefono.isBlank()) ? null : TelefonoUtils.normalizar(telefono);
        String cad = (cadeteId == null || cadeteId.isBlank()) ? null : cadeteId;
        String est = (estadoId == null || estadoId.isBlank()) ? null : estadoId;
        int ret = retirado == null ? 0 : (retirado ? 2 : 1);
        Integer minDesde = null;
        Integer minHasta = null;
        if (horaDesde != null || horaHasta != null) {
            minDesde = horaDesde == null ? 0 : horaDesde.getHour() * 60 + horaDesde.getMinute();
            minHasta = horaHasta == null ? MINUTOS_DIA - 1 : horaHasta.getHour() * 60 + horaHasta.getMinute();
        }
        // Argentina no tiene horario de verano desde 2009: el corrimiento es fijo (-180). Se toma
        // de la zona igual, por si algún día vuelve.
        int offset = ZONA.getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
        return new BusquedaPedidos(numero, tel, cad, est, ret, desde, hasta, minDesde, minHasta, MINUTOS_DIA + offset);
    }
}
