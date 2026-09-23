package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Al desasignar un pedido hay que borrar la marca de asignación, no solo el cadete.
 *
 * El bug que motivó este test: `quitarCadete` y la rama "sin candidatos" de
 * `liberarYReasignar` limpiaban `cadeteAsignado` y el estado pero dejaban `asignadoEn` puesto.
 * El panel dibuja la línea del timeline con solo que `asignadoEn` exista
 * (dashboard.component.ts:566) y el nombre del cadete vive en otro bloque que con
 * `cadeteAsignado: null` no se renderiza — así que el pedido se veía "Asignado" sin decir a
 * quién. El propio `reintentarEntrega` ya hacía lo correcto (limpia la tanda entera de
 * timestamps); estos dos caminos se habían quedado a medias.
 */
class PedidoServiceQuitarCadeteTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private OfertaPedidoRepository ofertaRepo;
    private EstadoPedidoRepository estadoPedidoRepo;
    private PedidoService service;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        ofertaRepo = mock(OfertaPedidoRepository.class);
        estadoPedidoRepo = mock(EstadoPedidoRepository.class);

        service = new PedidoService(
                repo, cadeteRepo, estadoPedidoRepo, mock(ResultadoOfertaRepository.class), ofertaRepo,
                mock(EstadoCadeteRepository.class), mock(ConfiguracionService.class),
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());

        when(estadoPedidoRepo.findById("SIN_ASIGNAR")).thenReturn(Optional.of(new EstadoPedido()));
        when(ofertaRepo.findFirstByPedidoIdAndCadeteIdAndResultadoId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Pedido pedidoAsignado() {
        Cadete cadete = new Cadete();
        cadete.setId("c1");
        cadete.setNombre("Juan");
        cadete.setApellido("Perez");

        Pedido p = new Pedido();
        p.setId("p1");
        p.setNumero(9100003L);
        p.setEstado(new EstadoPedido());
        p.setCadeteAsignado(cadete);
        p.setAsignadoEn(Instant.parse("2026-09-20T22:23:16Z"));
        p.setAsignadoPorUsername("admin");
        when(repo.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void quitarCadeteBorraLaMarcaDeAsignacion() {
        Pedido pedido = pedidoAsignado();

        service.quitarCadete("p1", true);

        assertNull(pedido.getCadeteAsignado(), "el cadete tiene que quedar desasignado");
        assertNull(pedido.getAsignadoEn(),
                "y la marca de asignación tambien: si sobrevive, el panel muestra 'Asignado' sin cadete");
        assertNull(pedido.getAsignadoPorUsername(), "y quien lo asigno, por el mismo motivo");
    }

    @Test
    void unPedidoDesasignadoNoConservaEstadoDeAsignacion() {
        Pedido pedido = pedidoAsignado();

        service.quitarCadete("p1", true);

        assertNotNull(pedido.getEstado(), "el estado se reemplaza por SIN_ASIGNAR, no se vacia");
        assertNull(pedido.getAsignadoEn());
    }

    @Test
    void conDevolverComisionFalseNoLeDevuelveElCreditoAlCadetePorcentaje() {
        Pedido pedido = pedidoAsignado();
        Cadete cadete = pedido.getCadeteAsignado();
        cadete.setModalidadPago("PORCENTAJE");
        cadete.setCreditoDisponible(new java.math.BigDecimal("500"));
        pedido.setComisionDescontada(new java.math.BigDecimal("350"));

        service.quitarCadete("p1", false);

        assertEquals(new java.math.BigDecimal("500"), cadete.getCreditoDisponible(),
                "sin devolverComision, el saldo no cambia: la comision ya cobrada queda cobrada");
        assertEquals(new java.math.BigDecimal("350"), pedido.getComisionDescontada(),
                "y el pedido sigue marcando cuanto se le cobro, no se limpia");
    }

    @Test
    void conDevolverComisionTrueLeDevuelveElCreditoAlCadetePorcentaje() {
        Pedido pedido = pedidoAsignado();
        Cadete cadete = pedido.getCadeteAsignado();
        cadete.setModalidadPago("PORCENTAJE");
        cadete.setCreditoDisponible(new java.math.BigDecimal("500"));
        pedido.setComisionDescontada(new java.math.BigDecimal("350"));
        when(cadeteRepo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));

        service.quitarCadete("p1", true);

        assertEquals(new java.math.BigDecimal("850"), cadete.getCreditoDisponible(),
                "con devolverComision, se le acredita de vuelta lo que se le habia descontado");
        assertNull(pedido.getComisionDescontada(), "y se limpia la marca de comision descontada");
    }
}
