package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.MovimientoCredito;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Editar el precio de un pedido ya aceptado (2026-10-19): al cadete PORCENTAJE se le cobra o devuelve
 * la diferencia de comisión, con el mismo % que se le cobró al aceptar (no el de Configuración).
 */
class PedidoServiceEditarPrecioComisionTest {

    private PedidoService service;
    private MovimientoCreditoRepository movimientoRepo;
    private ConfiguracionService config;
    private Pedido pedido;
    private Cadete cadete;

    @BeforeEach
    void setUp() {
        PedidoRepository repo = mock(PedidoRepository.class);
        movimientoRepo = mock(MovimientoCreditoRepository.class);
        config = mock(ConfiguracionService.class);
        service = new PedidoService(
                repo, mock(CadeteRepository.class), mock(EstadoPedidoRepository.class), mock(ResultadoOfertaRepository.class),
                mock(OfertaPedidoRepository.class), mock(EstadoCadeteRepository.class), config,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), movimientoRepo,
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        ReflectionTestUtils.setField(service, "em", mock(EntityManager.class));
        when(config.getBigDecimal(anyString(), any())).thenAnswer(i -> i.getArgument(1));

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setModalidadPago("PORCENTAJE");
        cadete.setCreditoDisponible(new BigDecimal("1000.00"));

        EstadoPedido enCurso = new EstadoPedido();
        enCurso.setId("EN_CURSO");
        pedido = new Pedido();
        pedido.setId("p1");
        pedido.setNumero(1500108L);
        pedido.setEstado(enCurso);
        pedido.setPrecio(new BigDecimal("800.00"));
        pedido.setCadeteAsignado(cadete);
        pedido.setComisionDescontada(new BigDecimal("200.00")); // 25% de 800
        when(repo.findByIdParaActualizar("p1")).thenReturn(Optional.of(pedido));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void subirElPrecioCobraLaDiferenciaConElPorcentajeDelAceptar() {
        // Configuración dice 10% (default del mock), pero al aceptar se cobró 25%: manda el 25%.
        service.editarPrecio("p1", new BigDecimal("2000"), "admin");

        assertEquals(new BigDecimal("500.00"), pedido.getComisionDescontada());
        assertEquals(new BigDecimal("700.00"), cadete.getCreditoDisponible());
        ArgumentCaptor<MovimientoCredito> m = ArgumentCaptor.forClass(MovimientoCredito.class);
        verify(movimientoRepo).save(m.capture());
        assertEquals("AJUSTE_RESTA", m.getValue().getTipo());
        assertEquals(new BigDecimal("300.00"), m.getValue().getMonto());
        assertEquals(1500108L, m.getValue().getPedidoNumero());
        assertEquals("admin", m.getValue().getCreadoPorUsername());
    }

    @Test
    void bajarElPrecioDevuelveLaDiferencia() {
        service.editarPrecio("p1", new BigDecimal("400"), "admin");

        assertEquals(new BigDecimal("100.00"), pedido.getComisionDescontada());
        assertEquals(new BigDecimal("1100.00"), cadete.getCreditoDisponible());
        ArgumentCaptor<MovimientoCredito> m = ArgumentCaptor.forClass(MovimientoCredito.class);
        verify(movimientoRepo).save(m.capture());
        assertEquals("AJUSTE_SUMA", m.getValue().getTipo());
    }

    @Test
    void sinComisionCobradaNoTocaElCredito() {
        pedido.setComisionDescontada(null);
        service.editarPrecio("p1", new BigDecimal("2000"), "admin");

        assertEquals(new BigDecimal("1000.00"), cadete.getCreditoDisponible());
        verify(movimientoRepo, never()).save(any());
    }
}
