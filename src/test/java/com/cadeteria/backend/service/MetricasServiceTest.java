package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.MetricasDtos.ZonaMetricaResponse;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.IncidenciaRepository;
import com.cadeteria.backend.repository.OfertaPedidoRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.repository.PedidoUbicacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cubre el bug de revisión final: con la asignación por distancia, PedidoService.crear() ya no
 * setea Pedido.zona (spec-asignacion-por-distancia.md §7), así que metricasPorZona() tiene que
 * poder recorrer una mezcla de pedidos con y sin zona sin romperse — antes tiraba NPE apenas
 * encontraba el primer pedido sin zona.
 */
class MetricasServiceTest {

    private PedidoRepository pedidoRepo;
    private MetricasService service;

    @BeforeEach
    void setUp() {
        pedidoRepo = mock(PedidoRepository.class);
        service = new MetricasService(pedidoRepo, mock(OfertaPedidoRepository.class),
                mock(CadeteRepository.class), mock(CadeteSesionService.class),
                mock(PedidoUbicacionRepository.class), mock(IncidenciaRepository.class));
    }

    @Test
    void metricasPorZonaIgnoraPedidosSinZonaSinTirarNpe() {
        Instant desde = Instant.parse("2026-09-01T00:00:00Z");
        Instant hasta = Instant.parse("2026-09-02T00:00:00Z");

        Pedido conZona = pedido(zona("centro", "Centro"), "FINALIZADO", new BigDecimal("1000"));
        Pedido sinZona = pedido(null, "FINALIZADO", new BigDecimal("2000"));
        when(pedidoRepo.findByCreadoEnBetween(desde, hasta)).thenReturn(List.of(conZona, sinZona));

        List<ZonaMetricaResponse> resultado = assertDoesNotThrow(() -> service.metricasPorZona(desde, hasta));

        assertEquals(1, resultado.size());
        assertEquals("centro", resultado.get(0).zonaId());
        assertEquals(1L, resultado.get(0).cantidadPedidos());
        assertEquals(new BigDecimal("1000"), resultado.get(0).montoCobradoTotal());
    }

    @Test
    void metricasPorZonaDevuelveVacioSiNingunPedidoTieneZona() {
        Instant desde = Instant.parse("2026-09-01T00:00:00Z");
        Instant hasta = Instant.parse("2026-09-02T00:00:00Z");
        when(pedidoRepo.findByCreadoEnBetween(desde, hasta))
                .thenReturn(List.of(pedido(null, "PENDIENTE", null)));

        List<ZonaMetricaResponse> resultado = assertDoesNotThrow(() -> service.metricasPorZona(desde, hasta));

        assertEquals(0, resultado.size());
    }

    private Pedido pedido(Zona zona, String estadoId, BigDecimal precio) {
        Pedido p = new Pedido();
        p.setZona(zona);
        p.setEstado(estado(estadoId));
        p.setPrecio(precio);
        p.setCreadoEn(Instant.now());
        return p;
    }

    private Zona zona(String id, String nombre) {
        Zona z = new Zona();
        z.setId(id);
        z.setNombre(nombre);
        return z;
    }

    private EstadoPedido estado(String id) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        e.setNombre(id);
        return e;
    }
}
