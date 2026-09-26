package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.common.GoneException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Link de seguimiento (2026-09-25): solo por token, y vale hasta el final del día en que terminó el pedido. */
class PedidoServiceSeguimientoTest {

    private PedidoRepository repo;
    private ConfiguracionService config;
    private PedidoService service;
    private WebSocketPublisher publisher;
    private FcmService fcm;
    private IncidenciaRepository incidenciaRepo;
    private CadeteRepository cadeteRepo;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        // La lectura con bloqueo (2026-09-26) devuelve lo mismo que findById en estos tests.
        when(repo.findByIdParaActualizar(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> repo.findById(inv.getArgument(0)));
        config = mock(ConfiguracionService.class);
        publisher = mock(WebSocketPublisher.class);
        fcm = mock(FcmService.class);
        incidenciaRepo = mock(IncidenciaRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        service = new PedidoService(
                repo, cadeteRepo, mock(EstadoPedidoRepository.class), mock(ResultadoOfertaRepository.class),
                mock(OfertaPedidoRepository.class), mock(EstadoCadeteRepository.class), config,
                publisher, fcm, mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                incidenciaRepo, mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em",
                mock(jakarta.persistence.EntityManager.class));
    }

    @Test
    void unPedidoEnCursoSiempreAbre() {
        tokenDe(pedido("EN_CURSO", null));
        assertDoesNotThrow(() -> service.getPorToken("tok"));
    }

    @Test
    void entregadoHoyAbre() {
        tokenDe(pedido("FINALIZADO", Instant.now()));
        assertDoesNotThrow(() -> service.getPorToken("tok"));
    }

    @Test
    void entregadoAyerYaNoAbre() {
        tokenDe(pedido("FINALIZADO", Instant.now().minus(Duration.ofDays(1))));
        assertThrows(GoneException.class, () -> service.getPorToken("tok"));
    }

    @Test
    void venceALaMedianocheDeArgentina() {
        // Entregado 23:30 del 25/09 en Argentina (02:30 UTC del 26) -> vale hasta 00:00 del 26 en Argentina (03:00 UTC)
        Instant entregado = Instant.parse("2026-09-26T02:30:00Z");
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-09-26T03:00:00Z"), PedidoService.finDelDia(entregado));
    }

    @Test
    void unCanceladoTambienVence() {
        Pedido p = pedido("CANCELADO", null);
        p.setCanceladoEn(Instant.now().minus(Duration.ofDays(2)));
        tokenDe(p);
        assertThrows(GoneException.class, () -> service.getPorToken("tok"));
    }

    @Test
    void unTokenQueNoExisteNoDiceNada() {
        when(repo.findByTokenSeguimiento("otro")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getPorToken("otro"));
    }

    // --- Reclamo del cliente (2026-09-25) ---

    @Test
    void enCaminoSinRetirarReclamaQueElCadeteNoLlego() {
        Pedido p = conCadete(pedido("EN_CURSO", null));
        tokenDe(p);

        var r = service.reclamoDelCliente("tok");

        org.junit.jupiter.api.Assertions.assertTrue(r.avisado());
        org.mockito.Mockito.verify(fcm).enviar(org.mockito.ArgumentMatchers.eq("fcm-1"), org.mockito.ArgumentMatchers.eq("Reclamo del cliente"),
                org.mockito.ArgumentMatchers.contains("demora en el retiro"), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(publisher).publicarAviso(org.mockito.ArgumentMatchers.eq("c1"),
                org.mockito.ArgumentMatchers.contains("3815550000"));
    }

    @Test
    void retiradoReclamaDemoraYEntregadoReclamaLaEntrega() {
        Pedido retirado = conCadete(pedido("EN_CURSO", null));
        retirado.setRetiradoEn(Instant.now());
        tokenDe(retirado);
        service.reclamoDelCliente("tok");
        org.mockito.Mockito.verify(fcm).enviar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.contains("demora en la entrega"), org.mockito.ArgumentMatchers.any());

        Pedido entregado = conCadete(pedido("FINALIZADO", Instant.now()));
        when(repo.findByTokenSeguimiento("tok2")).thenReturn(Optional.of(entregado));
        // sin contar qué pasó, no
        assertThrows(BadRequestException.class, () -> service.reclamoDelCliente("tok2", "  "));
        service.reclamoDelCliente("tok2", "Llegó el paquete abierto");
        org.mockito.Mockito.verify(fcm).enviar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.contains("\"Llegó el paquete abierto\""), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unSegundoReclamoEnMenosDeDiezMinutosNoVuelveAAvisar() {
        Pedido p = conCadete(pedido("EN_CURSO", null));
        tokenDe(p);
        service.reclamoDelCliente("tok");

        var r = service.reclamoDelCliente("tok");

        org.junit.jupiter.api.Assertions.assertFalse(r.avisado());
        org.mockito.Mockito.verify(fcm, org.mockito.Mockito.times(1)).enviar(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void problemaConLaEntregaAbreUnIncidenteGraveQueBloqueaAlCadete() {
        Pedido entregado = conCadete(pedido("FINALIZADO", Instant.now()));
        tokenDe(entregado);

        service.reclamoDelCliente("tok", "Llegó el paquete abierto");

        var captor = org.mockito.ArgumentCaptor.forClass(com.cadeteria.backend.model.Incidencia.class);
        org.mockito.Mockito.verify(incidenciaRepo).save(captor.capture());
        var incidente = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("GRAVE", incidente.getPrioridad());
        org.junit.jupiter.api.Assertions.assertEquals(PedidoService.ORIGEN_RECLAMO, incidente.getOrigen());
        org.junit.jupiter.api.Assertions.assertEquals("c1", incidente.getCadeteId());
        org.junit.jupiter.api.Assertions.assertEquals("PROBLEMA_ENTREGA", entregado.getReclamoTipo());
        org.junit.jupiter.api.Assertions.assertEquals("ABIERTO", entregado.getReclamoEstado());
    }

    @Test
    void conUnReclamoAbiertoNoSeLePuedeAsignarAMano() {
        Pedido sinAsignar = pedido("SIN_ASIGNAR", null);
        when(repo.findById("p1")).thenReturn(Optional.of(sinAsignar));
        com.cadeteria.backend.model.Cadete c = new com.cadeteria.backend.model.Cadete();
        c.setId("c1");
        c.setActivo(true);
        com.cadeteria.backend.model.EstadoCadete libre = new com.cadeteria.backend.model.EstadoCadete();
        libre.setId("LIBRE");
        c.setEstado(libre);
        c.setModalidadPago("PORCENTAJE");
        c.setCreditoDisponible(new java.math.BigDecimal("100000"));
        when(cadeteRepo.findById("c1")).thenReturn(Optional.of(c));
        when(incidenciaRepo.existsByCadeteIdAndOrigenAndEstado("c1", PedidoService.ORIGEN_RECLAMO, "ABIERTA")).thenReturn(true);

        var error = assertThrows(ConflictException.class, () -> service.asignar("p1", "c1", "admin"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("reclamo"), error.getMessage());
    }

    @Test
    void sinCadeteOCanceladoNoSePuedeReclamar() {
        tokenDe(pedido("SIN_ASIGNAR", null));
        assertThrows(ConflictException.class, () -> service.reclamoDelCliente("tok"));
    }

    private Pedido conCadete(Pedido p) {
        com.cadeteria.backend.model.Cadete c = new com.cadeteria.backend.model.Cadete();
        c.setId("c1");
        c.setNombre("Juan");
        c.setApellido("Pérez");
        c.setFcmToken("fcm-1");
        p.setCadeteAsignado(c);
        p.setNumero(1234L);
        p.setClienteNombre("Marcos");
        p.setClienteTelefono("3815550000");
        return p;
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
