package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.OfertaPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pendiente #1 de la sesion 2026-09-20 (documentacion/pendientes.md): con un solo cadete
 * libre, rechazar un pedido se lo volvia a ofrecer en el acto una y otra vez, aunque hubiera
 * otros pedidos SIN_ASIGNAR esperando. Ahora, con "asignacion_automatica" prendida,
 * liberarYReasignar busca primero un pedido alternativo (el mas viejo) para el que el mismo
 * cadete tambien sea candidato, antes de devolverle el que acaba de rechazar.
 */
class PedidoServiceReasignacionTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private OfertaPedidoRepository ofertaRepo;
    private EstadoPedidoRepository estadoPedidoRepo;
    private ResultadoOfertaRepository resultadoOfertaRepo;
    private ConfiguracionService configuracionService;
    private PedidoService service;

    private Zona zona;
    private TipoVehiculo moto;
    private Cadete cadete;

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
                repo, cadeteRepo, mock(ZonaRepository.class), mock(TipoVehiculoRepository.class),
                estadoPedidoRepo, resultadoOfertaRepo, ofertaRepo,
                mock(EstadoCadeteRepository.class), configuracionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), new AppProperties());

        zona = new Zona();
        zona.setId("centro");

        moto = new TipoVehiculo();
        moto.setId("MOTO");

        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setNombre("Juan");
        cadete.setApellido("Perez");
        cadete.setEstado(libre);
        cadete.setTipoVehiculo(moto);
        cadete.setZonaActual(zona);

        when(estadoPedidoRepo.findById("SIN_ASIGNAR")).thenReturn(Optional.of(estadoPedido("SIN_ASIGNAR")));
        when(estadoPedidoRepo.findById("PENDIENTE")).thenReturn(Optional.of(estadoPedido("PENDIENTE")));
        when(resultadoOfertaRepo.findById("RECHAZADO")).thenReturn(Optional.of(resultadoOferta("RECHAZADO")));
        when(resultadoOfertaRepo.findById("PENDIENTE")).thenReturn(Optional.of(resultadoOferta("PENDIENTE")));
        when(cadeteRepo.findByUsername(any())).thenReturn(Optional.of(cadete));
        when(cadeteRepo.findAll()).thenReturn(List.of(cadete));
        when(ofertaRepo.findByPedidoId(any())).thenReturn(List.of());
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        when(configuracionService.getInt(any(), any(Integer.class))).thenAnswer(i -> i.getArgument(1));
    }

    private Pedido pedido(String id, Instant creadoEn) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setEstado(estadoPedido("PENDIENTE"));
        p.setZona(zona);
        p.setTipoVehiculoRequerido(moto);
        p.setCreadoEn(creadoEn);
        when(repo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private void ofertaPendienteSobre(Pedido pedido) {
        OfertaPedido oferta = new OfertaPedido();
        oferta.setPedido(pedido);
        oferta.setCadete(cadete);
        when(ofertaRepo.findFirstByPedidoIdAndCadeteIdAndResultadoId(pedido.getId(), "c1", "PENDIENTE"))
                .thenReturn(Optional.of(oferta));
    }

    @Test
    void alRechazarNoLeVuelveAOfrecerElMismoPedidoSiHayOtroMasViejoEsperando() {
        when(configuracionService.getBoolean("asignacion_automatica", false)).thenReturn(true);

        Pedido rechazado = pedido("p-rechazado", Instant.parse("2026-09-20T20:00:00Z"));
        Pedido masViejo = pedido("p-viejo", Instant.parse("2026-09-20T19:00:00Z"));
        rechazado.setCadeteAsignado(cadete);
        when(repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("SIN_ASIGNAR")))
                .thenReturn(List.of(masViejo));
        ofertaPendienteSobre(rechazado);

        service.rechazar("p-rechazado", "juanp", null);

        assertEquals("PENDIENTE", masViejo.getEstado().getId(),
                "el pedido mas viejo tiene que quedar ofertado al cadete");
        assertEquals(cadete, masViejo.getCadeteAsignado());
        assertNull(rechazado.getCadeteAsignado(), "el rechazado vuelve a quedar sin cadete");
        assertEquals("SIN_ASIGNAR", rechazado.getEstado().getId());
    }

    @Test
    void siNoHayOtroPedidoEsperandoLeVuelveAOfrecerElMismo() {
        when(configuracionService.getBoolean("asignacion_automatica", false)).thenReturn(true);

        Pedido rechazado = pedido("p-unico", Instant.parse("2026-09-20T20:00:00Z"));
        rechazado.setCadeteAsignado(cadete);
        when(repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("SIN_ASIGNAR"))).thenReturn(List.of());
        ofertaPendienteSobre(rechazado);

        service.rechazar("p-unico", "juanp", null);

        assertEquals("PENDIENTE", rechazado.getEstado().getId(),
                "sin alternativa, el unico pedido se le vuelve a ofrecer al mismo cadete");
        assertEquals(cadete, rechazado.getCadeteAsignado());
    }

    @Test
    void conAsignacionAutomaticaApagadaNoTocaLaColaManual() {
        when(configuracionService.getBoolean("asignacion_automatica", false)).thenReturn(false);

        Pedido rechazado = pedido("p-rechazado", Instant.parse("2026-09-20T20:00:00Z"));
        Pedido masViejo = pedido("p-viejo", Instant.parse("2026-09-20T19:00:00Z"));
        rechazado.setCadeteAsignado(cadete);
        when(repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("SIN_ASIGNAR")))
                .thenReturn(List.of(masViejo));
        ofertaPendienteSobre(rechazado);

        service.rechazar("p-rechazado", "juanp", null);

        assertNull(masViejo.getCadeteAsignado(),
                "con asignacion automatica apagada, el pedido mas viejo es cola manual del admin: no se toca");
        assertEquals("PENDIENTE", rechazado.getEstado().getId(),
                "el cadete sigue siendo el unico candidato, asi que se le vuelve a ofrecer el mismo");
    }
}
