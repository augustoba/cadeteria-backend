package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Matching 100% por distancia (sin zona) y topes de BICI, tras sacar el requisito de zona
 * y de tipo de vehículo del pedido — ver documentacion/spec-asignacion-por-distancia.md.
 */
class PedidoServiceMatchingPorDistanciaTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private OfertaPedidoRepository ofertaRepo;
    private EstadoPedidoRepository estadoPedidoRepo;
    private ConfiguracionService configuracionService;
    private PedidoService service;

    private TipoVehiculo moto;
    private TipoVehiculo bici;

    // Origen del pedido: Av. Mate de Luna 1800, San Miguel de Tucumán.
    private static final double ORIGEN_LAT = -26.8135;
    private static final double ORIGEN_LNG = -65.2245;
    // Yerba Buena — a varios km del origen.
    private static final double LEJOS_LAT = -26.8161;
    private static final double LEJOS_LNG = -65.3086;

    private static EstadoPedido estadoPedido(String id) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        return e;
    }

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        ofertaRepo = mock(OfertaPedidoRepository.class);
        estadoPedidoRepo = mock(EstadoPedidoRepository.class);
        configuracionService = mock(ConfiguracionService.class);

        service = new PedidoService(
                repo, cadeteRepo, estadoPedidoRepo, mock(ResultadoOfertaRepository.class), ofertaRepo,
                mock(EstadoCadeteRepository.class), configuracionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());

        moto = new TipoVehiculo();
        moto.setId("MOTO");
        bici = new TipoVehiculo();
        bici.setId("BICI");

        when(ofertaRepo.findByPedidoId(any())).thenReturn(List.of());
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(any(), any())).thenReturn(List.of());
        when(configuracionService.getInt(any(), any(Integer.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBoolean(any(), any(Boolean.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBigDecimal(any(), any(BigDecimal.class))).thenAnswer(i -> i.getArgument(1));
    }

    private Cadete cadete(String id, TipoVehiculo tipo, double lat, double lng) {
        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        Cadete c = new Cadete();
        c.setId(id);
        c.setNombre(id);
        c.setApellido("Test");
        c.setEstado(libre);
        c.setTipoVehiculo(tipo);
        c.setLat(lat);
        c.setLng(lng);
        return c;
    }

    private Pedido pedido(boolean requiereMoto) {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setEstado(estadoPedido("SIN_ASIGNAR"));
        p.setRequiereMoto(requiereMoto);
        p.setOrigenLat(ORIGEN_LAT);
        p.setOrigenLng(ORIGEN_LNG);
        p.setDestinoLat(ORIGEN_LAT);
        p.setDestinoLng(ORIGEN_LNG);
        when(repo.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void bicicletaLejosDelRetiroQuedaAfueraPeroMotoEnElMismoLugarNo() {
        when(configuracionService.getBigDecimal("distancia_maxima_bici_retiro_km", BigDecimal.ZERO))
                .thenReturn(BigDecimal.valueOf(2));
        pedido(false);
        Cadete bicicletero = cadete("bici1", bici, LEJOS_LAT, LEJOS_LNG);
        Cadete motoquero = cadete("moto1", moto, LEJOS_LAT, LEJOS_LNG);
        when(cadeteRepo.findAll()).thenReturn(List.of(bicicletero, motoquero));

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertEquals("moto1", resultado.orElseThrow().getId(),
                "la bici queda afuera por el tope de retiro, la moto no tiene tope de distancia");
    }

    @Test
    void sinRequerirMotoUnaBiciDentroDelTopeEsCandidata() {
        when(configuracionService.getBigDecimal("distancia_maxima_bici_retiro_km", BigDecimal.ZERO))
                .thenReturn(BigDecimal.valueOf(2));
        pedido(false);
        Cadete bicicletero = cadete("bici1", bici, ORIGEN_LAT, ORIGEN_LNG);
        when(cadeteRepo.findAll()).thenReturn(List.of(bicicletero));

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertEquals("bici1", resultado.orElseThrow().getId());
    }

    @Test
    void requiereMotoDejaAfueraALasBicisSinImportarDistancia() {
        pedido(true);
        Cadete bicicletero = cadete("bici1", bici, ORIGEN_LAT, ORIGEN_LNG);
        when(cadeteRepo.findAll()).thenReturn(List.of(bicicletero));

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertTrue(resultado.isEmpty(), "sin motos disponibles, no hay candidato");
    }

    @Test
    void viajeLargoEnBiciNoBloqueaATodosSigueOfreciendoseAUnaMoto() {
        when(configuracionService.getBigDecimal("distancia_maxima_bici_km", BigDecimal.ZERO))
                .thenReturn(BigDecimal.valueOf(2));
        Pedido p = pedido(false);
        p.setDestinoLat(LEJOS_LAT);
        p.setDestinoLng(LEJOS_LNG);
        Cadete motoquero = cadete("moto1", moto, ORIGEN_LAT, ORIGEN_LNG);
        when(cadeteRepo.findAll()).thenReturn(List.of(motoquero));

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertEquals("moto1", resultado.orElseThrow().getId());
    }
}
