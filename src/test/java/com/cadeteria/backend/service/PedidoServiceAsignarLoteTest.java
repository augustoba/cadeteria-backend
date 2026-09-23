package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** `asignarLote` reemplaza el chequeo "misma zona" por cercanía entre orígenes (spec-asignacion-por-distancia.md). */
class PedidoServiceAsignarLoteTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private OfertaPedidoRepository ofertaRepo;
    private EstadoPedidoRepository estadoPedidoRepo;
    private ResultadoOfertaRepository resultadoOfertaRepo;
    private ConfiguracionService configuracionService;
    private PedidoService service;

    private static EstadoPedido estadoPedido(String id) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        return e;
    }

    private static ResultadoOferta resultadoOferta(String id) {
        ResultadoOferta r = new ResultadoOferta();
        r.setId(id);
        return r;
    }

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        ofertaRepo = mock(OfertaPedidoRepository.class);
        estadoPedidoRepo = mock(EstadoPedidoRepository.class);
        resultadoOfertaRepo = mock(ResultadoOfertaRepository.class);
        configuracionService = mock(ConfiguracionService.class);

        service = new PedidoService(
                repo, cadeteRepo, estadoPedidoRepo, resultadoOfertaRepo, ofertaRepo,
                mock(EstadoCadeteRepository.class), configuracionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());

        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        Cadete cadete = new Cadete();
        cadete.setId("c1");
        cadete.setEstado(libre);

        when(cadeteRepo.findById("c1")).thenReturn(Optional.of(cadete));
        when(estadoPedidoRepo.findById("PENDIENTE")).thenReturn(Optional.of(estadoPedido("PENDIENTE")));
        when(resultadoOfertaRepo.findById("PENDIENTE")).thenReturn(Optional.of(resultadoOferta("PENDIENTE")));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(any(), any())).thenReturn(List.of());
        when(configuracionService.getInt(any(), any(Integer.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBigDecimal("distancia_maxima_lote_km", BigDecimal.valueOf(3)))
                .thenReturn(BigDecimal.valueOf(3));
    }

    private Pedido pedido(String id, double origenLat, double origenLng) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setNumero(1L);
        p.setEstado(estadoPedido("SIN_ASIGNAR"));
        p.setOrigenLat(origenLat);
        p.setOrigenLng(origenLng);
        p.setMontoDeclarado(BigDecimal.ZERO);
        when(repo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void rechazaElLoteSiLosOrigenesEstanLejosEntreSi() {
        pedido("p1", -26.8135, -65.2245);
        pedido("p2", -26.8161, -65.3086); // varios km del anterior

        assertThrows(BadRequestException.class, () -> service.asignarLote(List.of("p1", "p2"), "c1", "admin"));
    }

    @Test
    void aceptaElLoteSiLosOrigenesEstanCerca() {
        Pedido p1 = pedido("p1", -26.8135, -65.2245);
        Pedido p2 = pedido("p2", -26.8140, -65.2250); // pocos metros del anterior

        List<Pedido> resultado = service.asignarLote(List.of("p1", "p2"), "c1", "admin");

        assertEquals(2, resultado.size());
        assertEquals("PENDIENTE", p1.getEstado().getId());
        assertEquals("PENDIENTE", p2.getEstado().getId());
    }
}
