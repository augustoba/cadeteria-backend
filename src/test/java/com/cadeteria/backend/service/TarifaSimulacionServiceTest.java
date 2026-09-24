package com.cadeteria.backend.service;

import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Simulador del factor de línea recta contra lo cobrado antes (Configuración → Tarifas). */
class TarifaSimulacionServiceTest {

    private PedidoRepository repo;
    private ConfiguracionService config;
    private CotizacionService cotizacion;
    private TarifaSimulacionService service;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        config = mock(ConfiguracionService.class);
        cotizacion = new CotizacionService(config, mock(RutaService.class));
        service = new TarifaSimulacionService(repo, cotizacion, config);
        when(config.getBigDecimal(eq("precio_por_km"), eq(BigDecimal.ZERO))).thenReturn(new BigDecimal("320"));
        when(config.getBigDecimal(eq("precio_base_viaje"), eq(BigDecimal.ZERO))).thenReturn(new BigDecimal("2000"));
        when(config.getBigDecimal(eq("distancia_minima_km"), eq(BigDecimal.valueOf(2)))).thenReturn(BigDecimal.valueOf(3));
        when(config.getBigDecimal(eq("recargo_dinero_transportado_umbral"), eq(BigDecimal.ZERO))).thenReturn(BigDecimal.ZERO);
        when(config.getBigDecimal(eq("factor_linea_recta"), any())).thenReturn(new BigDecimal("1.4"));
    }

    private Pedido pedido(double oLat, double oLng, double dLat, double dLng, BigDecimal precio, String estado) {
        EstadoPedido e = new EstadoPedido();
        e.setId(estado);
        Pedido p = new Pedido();
        p.setEstado(e);
        p.setOrigenLat(oLat);
        p.setOrigenLng(oLng);
        p.setDestinoLat(dLat);
        p.setDestinoLng(dLng);
        p.setPrecio(precio);
        p.setCreadoEn(Instant.now());
        return p;
    }

    /** Precio que se habría cobrado con la fórmula y un factor dado — para fabricar el "historial". */
    private BigDecimal cobradoCon(double factor, double oLat, double oLng, double dLat, double dLng) {
        return cotizacion.precioParaKm(GeocodingService.distanciaKm(oLat, oLng, dLat, dLng) * factor, null);
    }

    @Test
    void sugiereElFactorConElQueSeCobraba() {
        double[][] viajes = {
                {-26.8303, -65.2038, -26.8161, -65.3086},
                {-26.8321, -65.1945, -26.8010, -65.2100},
                {-26.8303, -65.2038, -26.7322, -65.2594},
                {-26.8165, -65.1360, -26.8390, -65.2230},
                {-26.8465, -65.2159, -26.8010, -65.2100},
        };
        List<Pedido> pedidos = new ArrayList<>();
        for (double[] v : viajes) {
            pedidos.add(pedido(v[0], v[1], v[2], v[3], cobradoCon(1.3, v[0], v[1], v[2], v[3]), "FINALIZADO"));
        }
        // uno cancelado no cuenta
        pedidos.add(pedido(-26.83, -65.20, -26.80, -65.25, new BigDecimal("99999"), "CANCELADO"));
        when(repo.findByCreadoEnBetween(any(), any())).thenReturn(pedidos);

        var r = service.simular(1.3, 90);

        assertEquals(5, r.pedidosAnalizados());
        assertEquals(5, r.parecidos());
        assertEquals(1.3, r.factorSugerido(), 0.051);
        assertEquals(5, r.pedidosParaSugerencia());
    }

    @Test
    void conFactorMasAltoLaFormulaSaleMasCara() {
        List<Pedido> pedidos = List.of(pedido(-26.8303, -65.2038, -26.7322, -65.2594,
                cobradoCon(1.2, -26.8303, -65.2038, -26.7322, -65.2594), "FINALIZADO"));
        when(repo.findByCreadoEnBetween(any(), any())).thenReturn(pedidos);

        var r = service.simular(1.6, 30);

        assertEquals(1, r.formulaMasCara());
        assertEquals(1, r.mayoresDiferencias().size());
    }

    @Test
    void losCobradosAlMinimoNoSirvenParaSugerir() {
        List<Pedido> pedidos = List.of(pedido(-26.8241, -65.2065, -26.8175, -65.1974, new BigDecimal("2000"), "FINALIZADO"));
        when(repo.findByCreadoEnBetween(any(), any())).thenReturn(pedidos);

        var r = service.simular(null, 90);

        assertEquals(1.4, r.factor(), 0.001);
        assertNull(r.factorSugerido());
    }
}
