package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.common.CreditoInsuficienteException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.OfertaPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Crédito del cadete PORCENTAJE (2026-10-19): al ofrecer cuentan también las comisiones de lo que ya
 * tiene ofrecido sin aceptar, y al aceptar se vuelve a mirar el saldo. Antes, con $100 y varias
 * ofertas de $90, aceptaba todas y quedaba en negativo.
 */
class PedidoServiceCreditoComprometidoTest {

    private PedidoRepository repo;
    private OfertaPedidoRepository ofertaRepo;
    private MovimientoCreditoRepository movimientoRepo;
    private PedidoService service;
    private Cadete cadete;
    private final java.util.Map<String, EstadoPedido> estados = new java.util.HashMap<>();
    private final java.util.Map<String, ResultadoOferta> resultados = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        CadeteRepository cadeteRepo = mock(CadeteRepository.class);
        EstadoPedidoRepository estadoRepo = mock(EstadoPedidoRepository.class);
        ResultadoOfertaRepository resultadoRepo = mock(ResultadoOfertaRepository.class);
        ofertaRepo = mock(OfertaPedidoRepository.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        movimientoRepo = mock(MovimientoCreditoRepository.class);
        EstadoCadeteRepository estadoCadeteRepo = mock(EstadoCadeteRepository.class);
        service = new PedidoService(
                repo, cadeteRepo, estadoRepo, resultadoRepo, ofertaRepo, estadoCadeteRepo, config,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), movimientoRepo,
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        ReflectionTestUtils.setField(service, "em", mock(EntityManager.class));
        when(config.getString(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        when(config.getBigDecimal(anyString(), any())).thenAnswer(i -> i.getArgument(1)); // comisión 10%
        when(config.getInt(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> i.getArgument(1));
        for (String id : new String[]{"SIN_ASIGNAR", "PENDIENTE", "EN_CURSO"}) {
            EstadoPedido e = new EstadoPedido();
            e.setId(id);
            estados.put(id, e);
            when(estadoRepo.findById(id)).thenReturn(Optional.of(e));
        }
        for (String id : new String[]{"PENDIENTE", "ACEPTADO", "RECHAZADO", "SIN_CREDITO"}) {
            ResultadoOferta r = new ResultadoOferta();
            r.setId(id);
            resultados.put(id, r);
            when(resultadoRepo.findById(id)).thenReturn(Optional.of(r));
        }
        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        when(estadoCadeteRepo.findById(anyString())).thenReturn(Optional.of(libre));

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setUsername("30111222");
        cadete.setActivo(true);
        cadete.setEstado(libre);
        cadete.setModalidadPago("PORCENTAJE");
        cadete.setMaxViajesSimultaneos(5);
        cadete.setCreditoDisponible(new BigDecimal("100.00"));
        when(cadeteRepo.findById("c1")).thenReturn(Optional.of(cadete));
        when(cadeteRepo.findByUsername("30111222")).thenReturn(Optional.of(cadete));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Pedido pedido(String id, String estado) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setNumero(1500000L + id.hashCode() % 1000);
        p.setTokenSeguimiento("tok-" + id);
        p.setClienteTelefono("3815550000");
        p.setPrecio(new BigDecimal("900.00")); // comisión 90
        p.setMontoDeclarado(BigDecimal.ZERO);
        p.setEstado(estados.get(estado));
        when(repo.findByIdParaActualizar(id)).thenReturn(Optional.of(p));
        when(repo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void noSeLeOfreceSiNoLeAlcanzaContandoLoYaOfrecido() {
        Pedido yaOfrecido = pedido("p1", "PENDIENTE");
        yaOfrecido.setCadeteAsignado(cadete);
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(eq("c1"), any())).thenReturn(List.of(yaOfrecido));
        pedido("p2", "SIN_ASIGNAR");

        // $100 alcanza para una comisión de $90, no para dos.
        assertThrows(ConflictException.class, () -> service.asignar("p2", "c1", "admin"));
    }

    @Test
    void conUnaSolaOfertaSiAlcanza() {
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(eq("c1"), any())).thenReturn(List.of());
        pedido("p2", "SIN_ASIGNAR");

        service.asignar("p2", "c1", "admin");
    }

    @Test
    void alAceptarSinCreditoNoLoTomaYPasaAOtro() {
        cadete.setCreditoDisponible(new BigDecimal("50.00"));
        Pedido p = pedido("p1", "PENDIENTE");
        p.setCadeteAsignado(cadete);
        OfertaPedido oferta = new OfertaPedido();
        oferta.setId("o1");
        oferta.setPedido(p);
        oferta.setCadete(cadete);
        oferta.setResultado(resultados.get("PENDIENTE"));
        oferta.setExpiraEn(Instant.now().plusSeconds(60));
        when(ofertaRepo.findFirstByPedidoIdAndCadeteIdAndResultadoId("p1", "c1", "PENDIENTE")).thenReturn(Optional.of(oferta));

        assertThrows(CreditoInsuficienteException.class, () -> service.aceptar("p1", "30111222"));

        assertEquals("SIN_CREDITO", oferta.getResultado().getId(), "no cuenta como rechazo");
        assertEquals(new BigDecimal("50.00"), cadete.getCreditoDisponible(), "no se le descontó nada");
        verify(movimientoRepo, never()).save(any());
    }
}
