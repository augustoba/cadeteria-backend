package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.UbicacionSimuladaException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.PedidoDtos.FinalizarAdminRequest;
import com.cadeteria.backend.dto.PedidoDtos.FinalizarRequest;
import com.cadeteria.backend.dto.PedidoDtos.ParadaEntregadaRequest;
import com.cadeteria.backend.dto.PedidoDtos.RecepcionRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoParada;
import com.cadeteria.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Retirado / Entregado solo en el lugar (carril B, 2026-09-28): orden obligatorio, radio de 150 m con
 * "Estoy en el lugar" + foto como salida, GPS falso, GPS impreciso, hora del toque de la cola sin
 * señal, motivo obligatorio del admin y compatibilidad con las APK viejas.
 */
class PedidoServiceEnElLugarTest {

    /** Origen y destino en San Miguel de Tucumán; 0,0072° de latitud son unos 800 m. */
    private static final double ORIGEN_LAT = -26.8300, ORIGEN_LNG = -65.2000;
    private static final double DESTINO_LAT = -26.8200, DESTINO_LNG = -65.2100;
    private static final double OCHOCIENTOS_M = 0.0072;

    private PedidoService service;
    private Pedido pedido;
    private Cadete cadete;

    @BeforeEach
    void setUp() {
        PedidoRepository repo = mock(PedidoRepository.class);
        CadeteRepository cadeteRepo = mock(CadeteRepository.class);
        EstadoPedidoRepository estadoRepo = mock(EstadoPedidoRepository.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        service = new PedidoService(
                repo, cadeteRepo, estadoRepo, mock(ResultadoOfertaRepository.class), mock(OfertaPedidoRepository.class),
                mock(EstadoCadeteRepository.class), config,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        ReflectionTestUtils.setField(service, "em", mock(EntityManager.class));

        when(config.getString(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(i -> i.getArgument(1));
        for (String id : new String[]{"EN_CURSO", "FINALIZADO", "LIBRE", "OCUPADO"}) {
            EstadoPedido e = new EstadoPedido();
            e.setId(id);
            when(estadoRepo.findById(id)).thenReturn(Optional.of(e));
        }

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setUsername("30111222");
        cadete.setCreditoDisponible(new BigDecimal("1000.00"));
        when(cadeteRepo.findByUsername("30111222")).thenReturn(Optional.of(cadete));
        when(cadeteRepo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));

        pedido = new Pedido();
        pedido.setId("p1");
        pedido.setNumero(1500001L);
        pedido.setTokenSeguimiento("tok");
        pedido.setClienteTelefono("3815550000");
        pedido.setPrecio(new BigDecimal("2000.00"));
        pedido.setOrigenLat(ORIGEN_LAT);
        pedido.setOrigenLng(ORIGEN_LNG);
        pedido.setDestinoLat(DESTINO_LAT);
        pedido.setDestinoLng(DESTINO_LNG);
        pedido.setEstado(estadoRepo.findById("EN_CURSO").orElseThrow());
        pedido.setCadeteAsignado(cadete);
        pedido.setAceptadoEn(Instant.now().minus(Duration.ofHours(1)));
        when(repo.findByIdParaActualizar("p1")).thenReturn(Optional.of(pedido));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static RecepcionRequest retiro(double lat, double lng, Float precision, String foto, Boolean enElLugar,
                                           Boolean simulada, Instant tocadoEn) {
        return new RecepcionRequest(foto, lat, lng, null, precision, null, null, tocadoEn, enElLugar, simulada);
    }

    private Pedido retirar(RecepcionRequest r) {
        return service.registrarRecepcion("p1", "30111222", r.fotoUrl(), r.lat(), r.lng(), false, r);
    }

    private static FinalizarRequest entrega(double lat, double lng, Boolean enElLugar, Instant tocadoEn) {
        return new FinalizarRequest("Lucía Pérez", "https://img/entrega.jpg", null, lat, lng, null, 10f, null, null,
                tocadoEn, enElLugar, null);
    }

    @Test
    void enElLugarRetiraSinMarcas() {
        Pedido p = retirar(retiro(ORIGEN_LAT + 0.0005, ORIGEN_LNG, 10f, null, null, null, Instant.now()));
        assertNotNull(p.getRetiradoEn());
        assertNull(p.getRetiroFueraZona());
        assertTrue(p.getRetiroDistanciaM() < 150);
    }

    @Test
    void noSeEntregaSinRetirado() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.finalizar("p1", "30111222", entrega(DESTINO_LAT, DESTINO_LNG, null, Instant.now())));
        assertEquals("Primero marcá Retirado.", e.getMessage());
    }

    @Test
    void lejosSinEstoyEnElLugarNoDeja() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> retirar(retiro(ORIGEN_LAT + OCHOCIENTOS_M, ORIGEN_LNG, 10f, null, null, null, Instant.now())));
        assertTrue(e.getMessage().startsWith("Estás a 80"), e.getMessage());
        assertTrue(e.getMessage().contains("del retiro"));
        assertNull(pedido.getRetiradoEn());
    }

    @Test
    void lejosConEstoyEnElLugarYFotoMarcaFueraDeZona() {
        Pedido p = retirar(retiro(ORIGEN_LAT + OCHOCIENTOS_M, ORIGEN_LNG, 10f, "https://img/retiro.jpg", true, null, Instant.now()));
        assertNotNull(p.getRetiradoEn());
        assertEquals(Boolean.TRUE, p.getRetiroFueraZona());
        assertTrue(p.getRetiroDistanciaM() > 700 && p.getRetiroDistanciaM() < 900, "distancia " + p.getRetiroDistanciaM());
    }

    @Test
    void estoyEnElLugarSinFotoNoDeja() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> retirar(retiro(ORIGEN_LAT + OCHOCIENTOS_M, ORIGEN_LNG, 10f, null, true, null, Instant.now())));
        assertTrue(e.getMessage().contains("foto"));
    }

    @Test
    void gpsFalsoNoDejaYQuedaRegistrado() {
        assertThrows(UbicacionSimuladaException.class,
                () -> retirar(retiro(ORIGEN_LAT, ORIGEN_LNG, 5f, null, null, true, Instant.now())));
        assertNull(pedido.getRetiradoEn());
        assertEquals(Boolean.TRUE, pedido.getUbicacionSimulada());
        assertEquals(1, cadete.getIntentosUbicacionSimulada());
        assertNotNull(cadete.getUltimoIntentoUbicacionSimuladaEn());
    }

    @Test
    void gpsImprecisoNoBloqueaYQuedaAnotado() {
        // 250 m del origen con 200 m de error (adentro de un local): radio 150 + error 200.
        Pedido p = retirar(retiro(ORIGEN_LAT + 0.00225, ORIGEN_LNG, 200f, null, null, null, Instant.now()));
        assertNotNull(p.getRetiradoEn());
        assertEquals(Boolean.TRUE, p.getUbicacionImprecisa());
        assertNull(p.getRetiroFueraZona());
    }

    @Test
    void sinUbicacionSoloConEstoyEnElLugarYFoto() {
        RecepcionRequest sinGps = new RecepcionRequest(null, null, null, null, null, null, null, Instant.now(), null, null);
        assertThrows(BadRequestException.class, () -> retirar(sinGps));
        RecepcionRequest conFoto = new RecepcionRequest("https://img/r.jpg", null, null, null, null, null, null,
                Instant.now(), true, null);
        Pedido p = retirar(conFoto);
        assertEquals(Boolean.TRUE, p.getRetiroFueraZona());
        assertNull(p.getRetiroDistanciaM());
    }

    @Test
    void laHoraDelToqueSeRespeta() {
        Instant toque = Instant.now().minus(Duration.ofMinutes(25));
        Pedido p = retirar(retiro(ORIGEN_LAT, ORIGEN_LNG, 10f, null, null, null, toque));
        assertEquals(toque, p.getRetiradoEn());

        Instant entregaToque = Instant.now().minus(Duration.ofMinutes(5));
        Pedido f = service.finalizar("p1", "30111222", entrega(DESTINO_LAT, DESTINO_LNG, null, entregaToque));
        assertEquals(entregaToque, f.getFinalizadoEn());
    }

    @Test
    void horasInsensatasSeReemplazanPorLaDeLlegada() {
        Instant antes = Instant.now();
        Pedido p = retirar(retiro(ORIGEN_LAT, ORIGEN_LNG, 10f, null, null, null, Instant.now().plus(Duration.ofHours(3))));
        assertFalse(p.getRetiradoEn().isBefore(antes), "futura -> ahora");

        Instant aceptado = pedido.getAceptadoEn();
        assertEquals(Instant.now().getEpochSecond(), PedidoService.horaDelToque(aceptado.minusSeconds(60), aceptado).getEpochSecond(),
                1, "anterior a la aceptación -> ahora");
    }

    @Test
    void requestViejoSinCamposNuevosNoRompeNiBloquea() {
        // APK vieja: manda lat/lng lejos pero no la hora del toque -> se registra la distancia y marca igual.
        RecepcionRequest viejo = new RecepcionRequest(null, ORIGEN_LAT + OCHOCIENTOS_M, ORIGEN_LNG, null, 10f, null, null);
        Pedido p = retirar(viejo);
        assertNotNull(p.getRetiradoEn());
        assertNull(p.getRetiroFueraZona());
        assertNotNull(p.getRetiroDistanciaM());

        FinalizarRequest finViejo = new FinalizarRequest("Lucía Pérez", "https://img/e.jpg", null, null, null, null, null, null, null);
        assertEquals("FINALIZADO", service.finalizar("p1", "30111222", finViejo).getEstado().getId());
    }

    @Test
    void entregaLejosConEstoyEnElLugar() {
        retirar(retiro(ORIGEN_LAT, ORIGEN_LNG, 10f, null, null, null, Instant.now()));
        assertThrows(BadRequestException.class,
                () -> service.finalizar("p1", "30111222", entrega(DESTINO_LAT + OCHOCIENTOS_M, DESTINO_LNG, null, Instant.now())));
        Pedido p = service.finalizar("p1", "30111222", entrega(DESTINO_LAT + OCHOCIENTOS_M, DESTINO_LNG, true, Instant.now()));
        assertEquals("FINALIZADO", p.getEstado().getId());
        assertEquals(Boolean.TRUE, p.getEntregaFueraZona());
    }

    @Test
    void paradaExigeRetiradoYEstarEnElLugar() {
        PedidoParada parada = new PedidoParada();
        parada.setId("pa1");
        parada.setPedido(pedido);
        parada.setOrden(1);
        parada.setDireccion("Laprida 500");
        parada.setLat(-26.8250);
        parada.setLng(-65.2050);
        pedido.getParadas().add(parada);
        ParadaEntregadaRequest cerca = new ParadaEntregadaRequest(-26.8251, -65.2050, 10f, Instant.now(), null, null, null);

        assertThrows(BadRequestException.class, () -> service.marcarParadaEntregada("p1", "30111222", "pa1", cerca));

        retirar(retiro(ORIGEN_LAT, ORIGEN_LNG, 10f, null, null, null, Instant.now()));
        ParadaEntregadaRequest lejos = new ParadaEntregadaRequest(-26.8250 + OCHOCIENTOS_M, -65.2050, 10f, Instant.now(), null, null, null);
        assertThrows(BadRequestException.class, () -> service.marcarParadaEntregada("p1", "30111222", "pa1", lejos));

        service.marcarParadaEntregada("p1", "30111222", "pa1", cerca);
        assertNotNull(parada.getEntregadoEn());
        assertNull(parada.getFueraZona());
        assertNotNull(parada.getDistanciaM());
    }

    @Test
    void elAdminNecesitaMotivoYQuedaRegistrado() {
        assertThrows(BadRequestException.class,
                () -> service.finalizarComoAdmin("p1", new FinalizarAdminRequest(null, null, "  "), "admin"));

        // Sin Retirado y sin estar en el lugar: el admin se saltea los controles.
        Pedido p = service.finalizarComoAdmin("p1",
                new FinalizarAdminRequest(null, null, "El cadete se quedó sin batería"), "admin");
        assertEquals("FINALIZADO", p.getEstado().getId());
        assertEquals("admin", p.getFinalizadoPorAdmin());
        assertEquals("El cadete se quedó sin batería", p.getFinalizadoAdminMotivo());
    }

    @Test
    void distanciasLegibles() {
        assertEquals("800 m", PedidoService.formatearDistancia(800));
        assertEquals("1,2 km", PedidoService.formatearDistancia(1234));
    }
}
