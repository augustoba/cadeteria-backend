package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reparto en dos pasadas de asignarAutomaticamente() (mejora 2026-09-23, pedido del dueño):
 * con varios cadetes libres a la vez, la primera pasada reparte 1 viaje por cadete antes de
 * darle un segundo a cualquiera — recién si a un pedido no le queda candidato 100% libre, la
 * segunda pasada deja que alguien que ya tiene uno tome otro (hasta el tope configurado).
 *
 * Los mocks de PedidoRepository.save/countByCadeteAsignadoIdAndEstadoIdIn están cableados
 * entre sí (ver pendientesPorCadete) para simular lo que en Hibernate real pasaría solo: que
 * dentro de la misma transacción, un cadete recién ofertado ya cuenta como "con algo
 * pendiente" para la siguiente iteración del mismo loop.
 */
class PedidoServiceAsignacionAutomaticaRepartoTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private OfertaPedidoRepository ofertaRepo;
    private EstadoPedidoRepository estadoPedidoRepo;
    private ResultadoOfertaRepository resultadoOfertaRepo;
    private ConfiguracionService configuracionService;
    private PedidoService service;

    private final Map<String, Integer> pendientesPorCadete = new HashMap<>();

    // Origen de todos los pedidos de prueba: mismo punto para simplificar el desempate por distancia.
    private static final double LAT = -26.8135;
    private static final double LNG = -65.2245;

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

        pendientesPorCadete.clear();
        when(ofertaRepo.findByPedidoId(any())).thenReturn(List.of());
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(any(), any())).thenReturn(List.of());
        when(configuracionService.getInt(any(), any(Integer.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBigDecimal(any(), any(BigDecimal.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBoolean(anyString(), any(Boolean.class)))
                .thenAnswer(i -> "asignacion_automatica".equals(i.getArgument(0)) || (Boolean) i.getArgument(1));

        when(estadoPedidoRepo.findById("PENDIENTE")).thenReturn(Optional.of(lookupEstado("PENDIENTE")));
        when(resultadoOfertaRepo.findById("PENDIENTE")).thenReturn(Optional.of(lookupResultado("PENDIENTE")));

        // Simula, dentro de la misma "transacción", que un cadete recién ofertado ya cuenta
        // como "con algo pendiente" para la siguiente iteración del mismo loop.
        when(repo.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getCadeteAsignado() != null && "PENDIENTE".equals(p.getEstado().getId())) {
                pendientesPorCadete.merge(p.getCadeteAsignado().getId(), 1, Integer::sum);
            }
            return p;
        });
        when(repo.countByCadeteAsignadoIdAndEstadoIdIn(anyString(), any()))
                .thenAnswer(inv -> (long) pendientesPorCadete.getOrDefault((String) inv.getArgument(0), 0));
    }

    private EstadoPedido lookupEstado(String id) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        return e;
    }

    private ResultadoOferta lookupResultado(String id) {
        ResultadoOferta r = new ResultadoOferta();
        r.setId(id);
        return r;
    }

    private Cadete cadete(String id) {
        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        TipoVehiculo moto = new TipoVehiculo();
        moto.setId("MOTO");
        Cadete c = new Cadete();
        c.setId(id);
        c.setNombre(id);
        c.setApellido("Test");
        c.setEstado(libre);
        c.setTipoVehiculo(moto);
        c.setLat(LAT);
        c.setLng(LNG);
        c.setOrdenColaEspera(Instant.now());
        return c;
    }

    private Pedido pedidoSinAsignar(String id) {
        Pedido p = new Pedido();
        p.setId(id);
        EstadoPedido sinAsignar = new EstadoPedido();
        sinAsignar.setId("SIN_ASIGNAR");
        p.setEstado(sinAsignar);
        p.setOrigenLat(LAT);
        p.setOrigenLng(LNG);
        p.setDestinoLat(LAT);
        p.setDestinoLng(LNG);
        p.setMontoDeclarado(BigDecimal.ZERO);
        p.setCreadoEn(Instant.now());
        return p;
    }

    @Test
    void conTantosCadetesLibresComoPedidosCadaUnoRecibeUnoSolo() {
        Cadete c1 = cadete("c1");
        Cadete c2 = cadete("c2");
        Cadete c3 = cadete("c3");
        when(cadeteRepo.findAll()).thenReturn(List.of(c1, c2, c3));

        Pedido p1 = pedidoSinAsignar("p1");
        Pedido p2 = pedidoSinAsignar("p2");
        Pedido p3 = pedidoSinAsignar("p3");
        when(repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("SIN_ASIGNAR"))).thenReturn(List.of(p1, p2, p3));

        service.asignarAutomaticamente();

        List<String> asignados = List.of(p1, p2, p3).stream()
                .map(p -> p.getCadeteAsignado() == null ? null : p.getCadeteAsignado().getId())
                .toList();
        assertTrue(asignados.stream().allMatch(id -> id != null), "los 3 pedidos deben quedar asignados: " + asignados);
        assertEquals(3, asignados.stream().distinct().count(), "cada pedido debe ir a un cadete distinto: " + asignados);
    }

    @Test
    void conMenosCadetesLibresQueLosPedidosReciénAhiAlguienTomaUnSegundo() {
        Cadete c1 = cadete("c1");
        Cadete c2 = cadete("c2");
        when(cadeteRepo.findAll()).thenReturn(List.of(c1, c2));

        Pedido p1 = pedidoSinAsignar("p1");
        Pedido p2 = pedidoSinAsignar("p2");
        Pedido p3 = pedidoSinAsignar("p3");
        when(repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("SIN_ASIGNAR"))).thenReturn(List.of(p1, p2, p3));
        // Tope real más alto que 1: recién en la segunda pasada un cadete puede tomar otro.
        when(configuracionService.getInt(eq("asignacion_automatica_max_viajes_cadete"), any(Integer.class))).thenReturn(3);

        service.asignarAutomaticamente();

        assertTrue(p1.getCadeteAsignado() != null && p2.getCadeteAsignado() != null && p3.getCadeteAsignado() != null,
                "los 3 deben terminar asignados, aunque solo haya 2 cadetes libres");
        // El tercer pedido tuvo que ir a alguno de los 2 cadetes que ya tenía uno (recién en la 2da pasada).
        long c1Count = List.of(p1, p2, p3).stream().filter(p -> "c1".equals(p.getCadeteAsignado().getId())).count();
        long c2Count = List.of(p1, p2, p3).stream().filter(p -> "c2".equals(p.getCadeteAsignado().getId())).count();
        assertEquals(3, c1Count + c2Count);
        assertTrue(c1Count >= 1 && c2Count >= 1, "en la 1ra pasada ambos cadetes ya deben haber recibido 1 cada uno");
    }
}
