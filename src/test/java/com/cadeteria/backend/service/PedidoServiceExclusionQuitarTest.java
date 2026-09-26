package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoCadeteExcluido;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Quitar" un pedido sin reasignarlo a nadie excluye a ese cadete de volver a ser candidato de
 * la asignación automática para ESE pedido puntual (mejora 2026-09-23, pedida por el dueño) —
 * sin tocar OfertaPedido/la tasa de rechazo, porque no fue una decisión del cadete.
 */
class PedidoServiceExclusionQuitarTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private OfertaPedidoRepository ofertaRepo;
    private EstadoPedidoRepository estadoPedidoRepo;
    private ConfiguracionService configuracionService;
    private PedidoCadeteExcluidoRepository excluidoRepo;
    private PedidoService service;

    private static final double ORIGEN_LAT = -26.8135;
    private static final double ORIGEN_LNG = -65.2245;

    private static EstadoPedido estadoPedido(String id) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        return e;
    }

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        // La lectura con bloqueo (2026-09-26) devuelve lo mismo que findById en estos tests.
        when(repo.findByIdParaActualizar(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> repo.findById(inv.getArgument(0)));
        cadeteRepo = mock(CadeteRepository.class);
        ofertaRepo = mock(OfertaPedidoRepository.class);
        estadoPedidoRepo = mock(EstadoPedidoRepository.class);
        configuracionService = mock(ConfiguracionService.class);
        excluidoRepo = mock(PedidoCadeteExcluidoRepository.class);

        service = new PedidoService(
                repo, cadeteRepo, estadoPedidoRepo, mock(ResultadoOfertaRepository.class), ofertaRepo,
                mock(EstadoCadeteRepository.class), configuracionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), excluidoRepo, new AppProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em",
                mock(jakarta.persistence.EntityManager.class));

        when(estadoPedidoRepo.findById("SIN_ASIGNAR")).thenReturn(Optional.of(estadoPedido("SIN_ASIGNAR")));
        when(ofertaRepo.findFirstByPedidoIdAndCadeteIdAndResultadoId(any(), any(), any())).thenReturn(Optional.empty());
        when(ofertaRepo.findByPedidoId(any())).thenReturn(List.of());
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(any(), any())).thenReturn(List.of());
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        when(configuracionService.getInt(any(), any(Integer.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBoolean(any(), any(Boolean.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBigDecimal(any(), any(BigDecimal.class))).thenAnswer(i -> i.getArgument(1));
    }

    private Cadete cadete(String id) {
        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        Cadete c = new Cadete();
        c.setId(id);
        c.setNombre(id);
        c.setApellido("Test");
        c.setEstado(libre);
        com.cadeteria.backend.model.TipoVehiculo moto = new com.cadeteria.backend.model.TipoVehiculo();
        moto.setId("MOTO");
        c.setTipoVehiculo(moto);
        c.setLat(ORIGEN_LAT);
        c.setLng(ORIGEN_LNG);
        return c;
    }

    private Pedido pedidoAsignado(String id, Cadete cadete) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setNumero(1L);
        p.setEstado(estadoPedido("PENDIENTE"));
        p.setCadeteAsignado(cadete);
        p.setAsignadoEn(Instant.now());
        p.setOrigenLat(ORIGEN_LAT);
        p.setOrigenLng(ORIGEN_LNG);
        p.setDestinoLat(ORIGEN_LAT);
        p.setDestinoLng(ORIGEN_LNG);
        p.setCreadoEn(Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void quitarCadeteRegistraLaExclusionUnaSolaVez() {
        Cadete cadete = cadete("c1");
        pedidoAsignado("p1", cadete);
        when(excluidoRepo.existsByPedidoIdAndCadeteId("p1", "c1")).thenReturn(false);

        service.quitarCadete("p1", true);

        verify(excluidoRepo, times(1)).save(any(PedidoCadeteExcluido.class));
    }

    @Test
    void quitarCadeteNoDuplicaLaExclusionSiYaEstaba() {
        Cadete cadete = cadete("c1");
        pedidoAsignado("p1", cadete);
        when(excluidoRepo.existsByPedidoIdAndCadeteId("p1", "c1")).thenReturn(true);

        service.quitarCadete("p1", true);

        verify(excluidoRepo, never()).save(any(PedidoCadeteExcluido.class));
    }

    @Test
    void unCadeteExcluidoDeUnPedidoNoVuelveASerCandidatoParaEsePedido() {
        Cadete cadete = cadete("c1");
        Pedido pedido = pedidoAsignado("p1", cadete);
        pedido.setCreadoEn(Instant.now()); // no urgente
        when(cadeteRepo.findAll()).thenReturn(List.of(cadete));
        when(excluidoRepo.existsByPedidoIdAndCadeteId("p1", "c1")).thenReturn(true);

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertTrue(resultado.isEmpty(), "el unico cadete disponible esta excluido de este pedido puntual");
    }

    @Test
    void laExclusionEsPorPedidoNoAfectaOtroPedidoDelMismoCadete() {
        Cadete cadete = cadete("c1");
        pedidoAsignado("p1", cadete);
        // p2 es un pedido distinto: la exclusion de p1 no le aplica.
        Pedido p2 = new Pedido();
        p2.setId("p2");
        p2.setEstado(estadoPedido("SIN_ASIGNAR"));
        p2.setOrigenLat(ORIGEN_LAT);
        p2.setOrigenLng(ORIGEN_LNG);
        p2.setDestinoLat(ORIGEN_LAT);
        p2.setDestinoLng(ORIGEN_LNG);
        p2.setCreadoEn(Instant.now());
        when(repo.findById("p2")).thenReturn(Optional.of(p2));
        when(cadeteRepo.findAll()).thenReturn(List.of(cadete));
        when(excluidoRepo.existsByPedidoIdAndCadeteId("p1", "c1")).thenReturn(true);
        when(excluidoRepo.existsByPedidoIdAndCadeteId("p2", "c1")).thenReturn(false);

        Optional<Cadete> resultado = service.sugerirCandidato("p2");

        assertEquals("c1", resultado.orElseThrow().getId());
    }

    @Test
    void unPedidoUrgenteIgnoraLaExclusionParaNoQuedarFlotandoParaSiempre() {
        Cadete cadete = cadete("c1");
        Pedido pedido = pedidoAsignado("p1", cadete);
        pedido.setCreadoEn(Instant.now().minusSeconds(60 * 60)); // hace 1 hora: supera el default de 30 min
        when(cadeteRepo.findAll()).thenReturn(List.of(cadete));
        when(excluidoRepo.existsByPedidoIdAndCadeteId("p1", "c1")).thenReturn(true);

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertEquals("c1", resultado.orElseThrow().getId(),
                "pasado el umbral de urgencia, se ignora la exclusion antes que dejarlo sin nadie");
    }
}
