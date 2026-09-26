package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.*;
import com.cadeteria.backend.service.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Link de seguimiento (2026-09-25): solo por token, y vence N horas después de terminado el pedido. */
class PedidoServiceSeguimientoTest {

    private PedidoRepository repo;
    private ConfiguracionService config;
    private PedidoService service;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        config = mock(ConfiguracionService.class);
        service = new PedidoService(
                repo, mock(CadeteRepository.class), mock(EstadoPedidoRepository.class), mock(ResultadoOfertaRepository.class),
                mock(OfertaPedidoRepository.class), mock(EstadoCadeteRepository.class), config,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        when(config.getInt(eq(PedidoService.CONFIG_SEGUIMIENTO_VENCE_HORAS), anyInt())).thenReturn(2);
    }

    @Test
    void unPedidoEnCursoSiempreAbre() {
        tokenDe(pedido("EN_CURSO", null));
        assertDoesNotThrow(() -> service.getPorToken("tok"));
    }

    @Test
    void entregadoHaceMenosDeDosHorasAbre() {
        tokenDe(pedido("FINALIZADO", Instant.now().minus(Duration.ofMinutes(90))));
        assertDoesNotThrow(() -> service.getPorToken("tok"));
    }

    @Test
    void entregadoHaceMasDeDosHorasYaNoAbre() {
        tokenDe(pedido("FINALIZADO", Instant.now().minus(Duration.ofHours(3))));
        assertThrows(BadRequestException.class, () -> service.getPorToken("tok"));
    }

    @Test
    void conCeroHorasNoVence() {
        when(config.getInt(eq(PedidoService.CONFIG_SEGUIMIENTO_VENCE_HORAS), anyInt())).thenReturn(0);
        tokenDe(pedido("FINALIZADO", Instant.now().minus(Duration.ofDays(30))));
        assertDoesNotThrow(() -> service.getPorToken("tok"));
    }

    @Test
    void unCanceladoTambienVence() {
        Pedido p = pedido("CANCELADO", null);
        p.setCanceladoEn(Instant.now().minus(Duration.ofHours(5)));
        tokenDe(p);
        assertThrows(BadRequestException.class, () -> service.getPorToken("tok"));
    }

    @Test
    void unTokenQueNoExisteNoDiceNada() {
        when(repo.findByTokenSeguimiento("otro")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getPorToken("otro"));
    }

    private void tokenDe(Pedido p) {
        when(repo.findByTokenSeguimiento("tok")).thenReturn(Optional.of(p));
    }

    private Pedido pedido(String estado, Instant finalizadoEn) {
        EstadoPedido e = new EstadoPedido();
        e.setId(estado);
        Pedido p = new Pedido();
        p.setId("p1");
        p.setEstado(e);
        p.setFinalizadoEn(finalizadoEn);
        return p;
    }
}
