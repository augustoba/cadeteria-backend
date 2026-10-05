package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.PedidoDtos.DireccionFrecuenteResponse;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionClienteOculta;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.DireccionClienteOcultaRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pendientes 3p del 2026-10-05: la sugerencia vieja con el punto malo, las repetidas y la "x". */
class DireccionesClienteServiceTest {

    private static final String TEL = "3815551234";
    /** Punto malo del pedido viejo y el bueno (a unas 6 cuadras). */
    private static final double LAT_MALA = -26.8300, LNG_MALA = -65.2100;
    private static final double LAT_BUENA = -26.8230, LNG_BUENA = -65.2024;
    private static final Instant AYER = Instant.parse("2026-10-04T12:00:00Z");
    private static final Instant HOY = Instant.parse("2026-10-05T12:00:00Z");

    private PedidoRepository pedidos;
    private DireccionCacheService cache;
    private DireccionClienteOcultaRepository ocultas;
    private DireccionesClienteService service;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoRepository.class);
        cache = mock(DireccionCacheService.class);
        ocultas = mock(DireccionClienteOcultaRepository.class);
        when(cache.canonicalizar(anyString())).thenAnswer(i -> i.getArgument(0, String.class).toLowerCase());
        when(ocultas.findByTelefono(TEL)).thenReturn(List.of());
        service = new DireccionesClienteService(pedidos, cache, ocultas);
    }

    @Test
    void laSugerenciaViejaUsaElPuntoValidadoYSeJuntaConLaNueva() {
        // del más nuevo al más viejo, como los devuelve el repositorio
        when(pedidos.ultimosPorTelefonoNormalizado(TEL)).thenReturn(List.of(
                pedido("Corrientes 480, San Miguel de Tucumán", LAT_BUENA, LNG_BUENA, HOY),
                pedido("Corrientes 480", LAT_MALA, LNG_MALA, AYER),
                pedido("Corrientes 480", LAT_MALA, LNG_MALA, AYER)));
        when(cache.filaDe("Corrientes", 480)).thenReturn(fila(DireccionCacheService.PROVEEDOR_GOOGLE_LINK));

        List<DireccionFrecuenteResponse> lista = service.frecuentes("381 555-1234");

        assertEquals(1, lista.size());
        assertEquals(3, lista.get(0).vecesOrigen());
        assertEquals(LAT_BUENA, lista.get(0).lat(), 1e-9);
        assertEquals(LNG_BUENA, lista.get(0).lng(), 1e-9);
        assertEquals("corrientes 480", lista.get(0).clave());
    }

    @Test
    void sinPuntoValidadoQuedaElDelPedidoYLosLugaresLejanosNoSeJuntan() {
        when(pedidos.ultimosPorTelefonoNormalizado(TEL)).thenReturn(List.of(
                pedido("Corrientes 480, Yerba Buena", LAT_BUENA, LNG_BUENA, HOY),
                pedido("Corrientes 480", LAT_MALA, LNG_MALA, AYER)));
        // un buscador gratuito no alcanza para corregir lo que quedó en el pedido
        when(cache.filaDe("Corrientes", 480)).thenReturn(fila("nominatim"));

        List<DireccionFrecuenteResponse> lista = service.frecuentes(TEL);

        assertEquals(2, lista.size());
        assertTrue(lista.stream().anyMatch(d -> d.lat() == LAT_MALA));
    }

    @Test
    void dentroDeLaMismaCuadraValeElPuntoDelPedido() {
        double latPuerta = LAT_BUENA + 0.0005; // ~55 m del punto de la cuadra
        when(pedidos.ultimosPorTelefonoNormalizado(TEL)).thenReturn(List.of(pedido("Corrientes 410", latPuerta, LNG_BUENA, HOY)));
        when(cache.filaDe("Corrientes", 410)).thenReturn(fila(DireccionCacheService.PROVEEDOR_CADETE_GPS));

        assertEquals(latPuerta, service.frecuentes(TEL).get(0).lat(), 1e-9);
    }

    @Test
    void laQuitadaNoSaleHastaQueSeVuelveAPedirAhi() {
        DireccionClienteOculta oculta = new DireccionClienteOculta();
        oculta.setClave("corrientes 480");
        oculta.setOcultaEn(AYER.plusSeconds(60));
        when(ocultas.findByTelefono(TEL)).thenReturn(List.of(oculta));
        when(pedidos.ultimosPorTelefonoNormalizado(TEL)).thenReturn(List.of(pedido("Corrientes 480", LAT_MALA, LNG_MALA, AYER)));
        assertTrue(service.frecuentes(TEL).isEmpty());

        when(pedidos.ultimosPorTelefonoNormalizado(TEL)).thenReturn(List.of(
                pedido("Corrientes 480", LAT_BUENA, LNG_BUENA, HOY),
                pedido("Corrientes 480", LAT_MALA, LNG_MALA, AYER)));
        List<DireccionFrecuenteResponse> lista = service.frecuentes(TEL);
        assertEquals(1, lista.size());
        assertEquals(1, lista.get(0).vecesOrigen());
        assertEquals(LAT_BUENA, lista.get(0).lat(), 1e-9);
    }

    @Test
    void ocultarGuardaPorTelefonoSoloDigitos() {
        when(ocultas.findByTelefonoAndClave(TEL, "corrientes 480")).thenReturn(Optional.empty());

        service.ocultar("381 555-1234", "corrientes 480", "admin");

        verify(ocultas).save(any(DireccionClienteOculta.class));
        verify(ocultas).findByTelefonoAndClave(TEL, "corrientes 480");
    }

    @Test
    void textoSinAlturaSeAgrupaPorElTextoYNoConsultaLaBase() {
        when(pedidos.ultimosPorTelefonoNormalizado(TEL)).thenReturn(List.of(
                pedido("Frente a la plaza", LAT_BUENA, LNG_BUENA, HOY),
                pedido("frente a la plaza", LAT_BUENA, LNG_BUENA, AYER)));

        List<DireccionFrecuenteResponse> lista = service.frecuentes(TEL);

        assertEquals(1, lista.size());
        assertEquals(2, lista.get(0).vecesOrigen());
        verify(cache, org.mockito.Mockito.never()).filaDe(anyString(), anyInt());
    }

    private static Pedido pedido(String origen, double lat, double lng, Instant cuando) {
        Pedido p = new Pedido();
        p.setOrigenDireccion(origen);
        p.setOrigenLat(lat);
        p.setOrigenLng(lng);
        p.setCreadoEn(cuando);
        return p;
    }

    private static CuadraCoords fila(String proveedor) {
        CuadraCoords c = new CuadraCoords();
        c.setProveedor(proveedor);
        c.setLat(LAT_BUENA);
        c.setLng(LNG_BUENA);
        return c;
    }
}
