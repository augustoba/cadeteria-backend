package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.PedidoDtos.FinalizarRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.OfertaPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Doble toque / reintento de la app (2026-09-26): repetir aceptar, retirar o finalizar no cobra la
 * comisión dos veces ni manda otro SMS — devuelve el pedido como quedó. Y todas esas acciones leen
 * el pedido con bloqueo de fila (findByIdParaActualizar) para que dos a la vez no se pisen.
 */
class PedidoServiceDobleToqueTest {

    private PedidoRepository repo;
    private OfertaPedidoRepository ofertaRepo;
    private MovimientoCreditoRepository movimientoRepo;
    private SmsGatewayService sms;
    private EntityManager em;
    private PedidoService service;
    private Pedido pedido;
    private Cadete cadete;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        CadeteRepository cadeteRepo = mock(CadeteRepository.class);
        EstadoPedidoRepository estadoRepo = mock(EstadoPedidoRepository.class);
        ResultadoOfertaRepository resultadoRepo = mock(ResultadoOfertaRepository.class);
        ofertaRepo = mock(OfertaPedidoRepository.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        movimientoRepo = mock(MovimientoCreditoRepository.class);
        sms = mock(SmsGatewayService.class);
        service = new PedidoService(
                repo, cadeteRepo, estadoRepo, resultadoRepo, ofertaRepo, mock(EstadoCadeteRepository.class), config,
                mock(WebSocketPublisher.class), mock(FcmService.class), sms,
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), movimientoRepo,
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        em = mock(EntityManager.class);
        ReflectionTestUtils.setField(service, "em", em);

        when(config.getString(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        when(config.getBigDecimal(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        for (String id : new String[]{"PENDIENTE", "EN_CURSO", "FINALIZADO"}) {
            EstadoPedido e = new EstadoPedido();
            e.setId(id);
            when(estadoRepo.findById(id)).thenReturn(Optional.of(e));
        }
        ResultadoOferta aceptado = new ResultadoOferta();
        aceptado.setId("ACEPTADO");
        when(resultadoRepo.findById("ACEPTADO")).thenReturn(Optional.of(aceptado));

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setUsername("30111222");
        cadete.setModalidadPago("PORCENTAJE");
        cadete.setCreditoDisponible(new BigDecimal("1000.00"));
        when(cadeteRepo.findByUsername("30111222")).thenReturn(Optional.of(cadete));

        pedido = new Pedido();
        pedido.setId("p1");
        pedido.setNumero(1500001L);
        pedido.setTokenSeguimiento("tok");
        pedido.setClienteTelefono("3815550000");
        pedido.setPrecio(new BigDecimal("2000.00"));
        pedido.setEstado(estadoRepo.findById("PENDIENTE").orElseThrow());
        pedido.setCadeteAsignado(cadete);
        when(repo.findByIdParaActualizar("p1")).thenReturn(Optional.of(pedido));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        OfertaPedido oferta = new OfertaPedido();
        oferta.setId("o1");
        oferta.setPedido(pedido);
        oferta.setCadete(cadete);
        oferta.setExpiraEn(Instant.now().plusSeconds(60));
        // Solo la primera vez hay oferta pendiente: después ya quedó ACEPTADA.
        when(ofertaRepo.findFirstByPedidoIdAndCadeteIdAndResultadoId("p1", "c1", "PENDIENTE"))
                .thenReturn(Optional.of(oferta), Optional.empty());
    }

    @Test
    void aceptarDosVecesCobraLaComisionUnaSolaVez() {
        Pedido primera = service.aceptar("p1", "30111222");
        Pedido segunda = service.aceptar("p1", "30111222");

        assertSame(primera, segunda);
        assertEquals("EN_CURSO", segunda.getEstado().getId());
        assertEquals(new BigDecimal("800.00"), cadete.getCreditoDisponible(), "10% de 2000 descontado una sola vez");
        verify(movimientoRepo, times(1)).save(any());
        verify(sms, times(1)).enviar(eq("p1"), eq("3815550000"), anyString());
    }

    @Test
    void aceptarLeeElPedidoYElCreditoConBloqueo() {
        service.aceptar("p1", "30111222");

        verify(repo).findByIdParaActualizar("p1");
        verify(repo, never()).findById("p1");
        verify(em).refresh(cadete, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void retirarDosVecesNoFalla() {
        service.aceptar("p1", "30111222");
        Pedido r1 = service.registrarRecepcion("p1", "30111222", null, null, null, false);
        Instant retiro = r1.getRetiradoEn();

        Pedido r2 = service.registrarRecepcion("p1", "30111222", null, null, null, false);

        assertEquals(retiro, r2.getRetiradoEn(), "no pisa la hora del primer retiro");
    }

    @Test
    void finalizarDosVecesNoMandaOtroSms() {
        service.aceptar("p1", "30111222");
        FinalizarRequest req = new FinalizarRequest("Lucía Pérez", "https://img/entrega.jpg", null, null, null, null, null);
        service.finalizar("p1", "30111222", req);
        service.finalizar("p1", "30111222", req);

        assertEquals("FINALIZADO", pedido.getEstado().getId());
        // 1 SMS al aceptar + 1 al finalizar
        verify(sms, times(2)).enviar(eq("p1"), eq("3815550000"), anyString());
    }

    @Test
    void siElViajeYaEsDeOtroCadeteEs409() {
        Cadete otro = new Cadete();
        otro.setId("c2");
        pedido.setCadeteAsignado(otro);

        assertThrows(ConflictException.class, () -> service.aceptar("p1", "30111222"));
    }
}
