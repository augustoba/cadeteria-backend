package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tope de dinero del cadete (2026-10-24): suma dinero y valores, no el dinero a disponer, y cuenta los No entregado. */
class PedidoServiceTopeDineroTest {

    private PedidoService service;
    private PedidoRepository repo;
    private Cadete cadete;
    private Pedido nuevo;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        CadeteRepository cadeteRepo = mock(CadeteRepository.class);
        EstadoPedidoRepository estadoRepo = mock(EstadoPedidoRepository.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        service = new PedidoService(
                repo, cadeteRepo, estadoRepo, mock(ResultadoOfertaRepository.class),
                mock(OfertaPedidoRepository.class), mock(EstadoCadeteRepository.class), config,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        ReflectionTestUtils.setField(service, "em", mock(EntityManager.class));
        when(config.getBigDecimal(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));

        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setActivo(true);
        cadete.setEstado(libre);
        cadete.setModalidadPago("SEMANAL");
        cadete.setHabilitadoPago(true);
        cadete.setMontoMaximoTransportado(new BigDecimal("10000"));
        when(cadeteRepo.findById("c1")).thenReturn(Optional.of(cadete));

        nuevo = pedido("p1", "SIN_ASIGNAR", "0");
        when(repo.findByIdParaActualizar("p1")).thenReturn(Optional.of(nuevo));
        when(repo.findById("p1")).thenReturn(Optional.of(nuevo));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(eq("c1"), any())).thenReturn(List.of());
    }

    private static Pedido pedido(String id, String estado, String dinero) {
        EstadoPedido e = new EstadoPedido();
        e.setId(estado);
        Pedido p = new Pedido();
        p.setId(id);
        p.setNumero(1L);
        p.setEstado(e);
        p.setPrecio(new BigDecimal("1000"));
        p.setMontoDeclarado(new BigDecimal(dinero));
        return p;
    }

    @Test
    void montoTransportadoSumaDineroYValoresPeroNoLoQueDisponeElCadete() {
        Pedido p = pedido("x", "SIN_ASIGNAR", "3000");
        p.setLlevaValores(true);
        p.setMontoValores(new BigDecimal("2000"));
        p.setMontoDisponer(new BigDecimal("50000"));
        assertEquals(new BigDecimal("5000"), PedidoService.montoTransportado(p));
    }

    @Test
    void losValoresCuentanParaElTope() {
        nuevo.setMontoDeclarado(new BigDecimal("6000"));
        nuevo.setLlevaValores(true);
        nuevo.setMontoValores(new BigDecimal("5000"));
        assertThrows(ConflictException.class, () -> service.asignar("p1", "c1", "admin"));
    }

    @Test
    void unNoEntregadoTodaviaCuentaParaElTope() {
        Pedido noEntregado = pedido("p0", "NO_ENTREGADO", "8000");
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(eq("c1"), eq(List.of("PENDIENTE", "EN_CURSO", "NO_ENTREGADO"))))
                .thenReturn(List.of(noEntregado));
        nuevo.setMontoDeclarado(new BigDecimal("3000"));
        assertThrows(ConflictException.class, () -> service.asignar("p1", "c1", "admin"));
    }
}
