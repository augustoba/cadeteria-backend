package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.service.GeocodingProxyService.GeoAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cadeteria.backend.service.DireccionCacheService.PROVEEDOR_CADETE_GPS;
import static com.cadeteria.backend.service.DireccionCacheService.PROVEEDOR_MANUAL;
import static com.cadeteria.backend.service.DireccionCacheService.PROVEEDOR_MANUAL_SIN_CONFIRMAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Carril A del 2026-09-28: el pin del mapa (3j), el reverse que pide el panel (3i) y el buscador
 * que completa el nombre de la calle (3h). Nada sale a internet: los buscadores y el reverse de
 * los proveedores se reemplazan con un spy.
 */
class GeocodingProxyServicePinYBuscadorTest {

    private static final String SMT = "San Miguel de Tucumán";
    /** Colombia 4695 (barrio Tarcos): OSM no tiene nombre en las calles de alrededor. */
    private static final double LAT = -26.8000, LNG = -65.2600;
    /** ~60 m al norte del pin. */
    private static final double LAT_60M = LAT + 0.00054;
    /** ~400 m al norte del pin. */
    private static final double LAT_400M = LAT + 0.0036;

    private DireccionCacheService cache;
    private GeocodingProxyService service;

    @BeforeEach
    void setUp() {
        cache = mock(DireccionCacheService.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        when(config.getInt(eq(GeocodingProxyService.CONFIG_GPS_PRECISION_MAX), anyInt())).thenReturn(50);
        service = spy(new GeocodingProxyService(new AppProperties(), mock(ApiKeyPoolService.class), cache, config));
    }

    // ---- 3j: el pin ----

    @Test
    void pinDelOperadorDondeElMapaDiceOtraCalleSeGuardaSinConfirmar() {
        mapaDice("Camino del Perú", 1600);

        service.aprenderPin("Colombia 4695, San Miguel de Tucumán", LAT, LNG, PROVEEDOR_MANUAL);

        verify(cache).guardar(eq("Colombia"), eq(4695), eq("Colombia"), eq(SMT), eq(LAT), eq(LNG), eq(false),
                eq(PROVEEDOR_MANUAL_SIN_CONFIRMAR));
    }

    @Test
    void pinDelOperadorConfirmadoPorElMapaQuedaComoManual() {
        mapaDice("Colombia", 4695);

        service.aprenderPin("Colombia 4695", LAT, LNG, PROVEEDOR_MANUAL);

        verify(cache).guardar(eq("Colombia"), eq(4695), eq("Colombia"), eq(SMT), eq(LAT), eq(LNG), eq(false), eq(PROVEEDOR_MANUAL));
    }

    @Test
    void pinDelClienteEntraSinConfirmarAunqueElMapaConfirmeLaCalle() {
        mapaDice("Colombia", 4695);

        service.aprenderPinDeCliente("Colombia 4695", LAT, LNG, PROVEEDOR_MANUAL);

        verify(cache).guardar(eq("Colombia"), eq(4695), eq("Colombia"), eq(SMT), eq(LAT), eq(LNG), eq(false),
                eq(PROVEEDOR_MANUAL_SIN_CONFIRMAR));
    }

    @Test
    void elCadeteCercaDelPinLoConfirmaAunqueNiElMapaNiElTelefonoConozcanLaCalle() {
        CuadraCoords pin = fila("colombia", "Yerba Buena", 4600, LAT, LNG, PROVEEDOR_MANUAL_SIN_CONFIRMAR);
        when(cache.filaDe("Colombia", 4695)).thenReturn(pin);
        mapaDice("Camino del Perú", 1600);

        service.aprenderDeCadete("Colombia 4695", LAT, LNG, LAT_60M, LNG, 10f, null, null);

        // Misma fila del pin (su calle y su localidad), con el punto del cadete.
        verify(cache).guardar(eq("Colombia"), eq(4695), eq("colombia"), eq("Yerba Buena"), eq(LAT_60M), eq(LNG), eq(false),
                eq(PROVEEDOR_CADETE_GPS));
    }

    @Test
    void elCadeteLejosDelPinSinConfirmacionDeLaCalleNoLoToca() {
        CuadraCoords pin = fila("colombia", SMT, 4600, LAT, LNG, PROVEEDOR_MANUAL_SIN_CONFIRMAR);
        when(cache.filaDe("Colombia", 4695)).thenReturn(pin);
        mapaDice("Camino del Perú", 1600);

        service.aprenderDeCadete("Colombia 4695", LAT, LNG, LAT_400M, LNG, 10f, null, null);

        verify(cache, never()).guardar(anyString(), anyInt(), anyString(), anyString(), anyDouble(), anyDouble(), anyBoolean(), anyString());
    }

    @Test
    void conPinPrevioYElMapaConfirmandoSeUsaLaLocalidadDelPinNoLaDelMapa() {
        CuadraCoords pin = fila("colombia", "Yerba Buena", 4600, LAT, LNG, PROVEEDOR_MANUAL);
        when(cache.filaDe("Colombia", 4695)).thenReturn(pin);
        mapaDice("Colombia", 4695); // el mapa dice San Miguel de Tucumán

        service.aprenderDeCadete("Colombia 4695", LAT, LNG, LAT_400M, LNG, 10f, null, null);

        verify(cache).guardar(eq("Colombia"), eq(4695), eq("colombia"), eq("Yerba Buena"), eq(LAT_400M), eq(LNG), eq(false),
                eq(PROVEEDOR_CADETE_GPS));
    }

    @Test
    void sinPinPrevioElCadeteAprendeComoSiempre() {
        mapaDice("Colombia", 4695);

        service.aprenderDeCadete("Colombia 4695", LAT, LNG, LAT_60M, LNG, 10f, null, null);

        verify(cache).guardar(eq("Colombia"), eq(4695), eq("Colombia"), eq(SMT), eq(LAT_60M), eq(LNG), eq(false), eq(PROVEEDOR_CADETE_GPS));
    }

    // ---- 3i: el reverse que pide el panel ----

    @Test
    void elReverseDelPanelNoAlimentaLaCache() {
        doReturn(geo("Camino del Perú", 1600, false)).when(service).reverseProveedores(LAT, LNG);

        GeoAddress r = service.reverseParaConsulta(LAT, LNG);

        assertEquals("Camino del Perú", r.street());
        verify(cache, never()).guardar(anyString(), anyInt(), anyString(), anyString(), anyDouble(), anyDouble(), anyBoolean(), anyString());
    }

    @Test
    void elReverseInternoSigueAlimentandoLaCache() {
        doReturn(geo("Camino del Perú", 1600, false)).when(service).reverseProveedores(LAT, LNG);

        service.reverse(LAT, LNG);

        verify(cache).guardar(eq("Camino del Perú"), eq(1600), eq("Camino del Perú"), eq(SMT), eq(LAT), eq(LNG), eq(false), eq("nominatim"));
    }

    @Test
    void elReverseDelPanelContestaConElPuntoPropioMasCercanoSinPreguntarAfuera() {
        when(cache.masCercanaPropia(LAT, LNG, GeocodingProxyService.RADIO_CALLE_PROPIA_M))
                .thenReturn(fila("colombia", SMT, 4600, LAT_60M, LNG, "android_geocoder"));

        GeoAddress r = service.reverseParaConsulta(LAT, LNG);

        assertEquals("Colombia", r.street());
        assertEquals("Colombia al 4600, " + SMT, r.label());
        assertEquals(GeocodingProxyService.PROVEEDOR_CACHE, r.proveedor());
        verify(service, never()).reverseProveedores(anyDouble(), anyDouble());
    }

    // ---- 3h: el buscador completa el nombre ----

    @Test
    void colomCompletaAColombiaYSaleDeLaCache() {
        when(cache.callesQueEmpiezanCon("colom")).thenReturn(List.of("colombia"));
        when(cache.buscarPorCanonica("colombia", 4600))
                .thenReturn(new DireccionCacheService.ResultadoCache("colombia", SMT, 4600, LAT, LNG, false));

        List<GeoAddress> r = service.buscar("colom 4600");

        assertEquals(1, r.size());
        assertEquals("Colombia 4600, " + SMT, r.get(0).label());
        verify(service, never()).buscarAfuera(anyString(), anyString(), any());
    }

    @Test
    void sinLaCuadraAprendidaLeCorrigeElNombreAlBuscadorDeAfuera() {
        when(cache.callesQueEmpiezanCon("colom")).thenReturn(List.of("colombia"));
        doReturn(List.of()).when(service).buscarAfuera(anyString(), anyString(), any());

        service.buscar("colom 4600");

        verify(service).buscarAfuera("Colombia 4600", "Colombia", 4600);
    }

    @Test
    void conVariasCallesPosiblesOfreceLasQueTienenEsaCuadraSinAdivinar() {
        when(cache.callesQueEmpiezanCon("sant")).thenReturn(List.of("santiago", "santa fe", "santa cruz"));
        when(cache.buscarPorCanonica("santiago", 800))
                .thenReturn(new DireccionCacheService.ResultadoCache("santiago", SMT, 800, LAT, LNG, false));
        when(cache.buscarPorCanonica("santa fe", 800))
                .thenReturn(new DireccionCacheService.ResultadoCache("santa fe", SMT, 800, LAT_60M, LNG, false));

        List<GeoAddress> r = service.buscar("sant 800");

        assertEquals(List.of("Santiago 800, " + SMT, "Santa Fe 800, " + SMT), r.stream().map(GeoAddress::label).toList());
        verify(service, never()).buscarAfuera(anyString(), anyString(), any());
    }

    @Test
    void siLaCalleYaEsConocidaNoSeBuscaPorElComienzo() {
        when(cache.conoceCalle("Colombia")).thenReturn(true);
        doReturn(List.of()).when(service).buscarAfuera(anyString(), anyString(), any());

        service.buscar("Colombia 4695");

        verify(cache, never()).callesQueEmpiezanCon(anyString());
        verify(service).buscarAfuera("Colombia 4695", "Colombia", 4695);
    }

    /** Sin altura exacta, así el reverse no alimenta la cache y solo se ve lo que guarda el pin. */
    private void mapaDice(String calle, int altura) {
        doReturn(geo(calle, altura, true)).when(service).reverseProveedores(anyDouble(), anyDouble());
    }

    private static GeoAddress geo(String calle, int altura, boolean aproximada) {
        return new GeoAddress(calle + " " + altura + ", " + SMT, calle, altura, SMT, LAT, LNG, aproximada, "nominatim");
    }

    private static CuadraCoords fila(String canonica, String localidad, int cuadra, double lat, double lng, String proveedor) {
        CuadraCoords c = new CuadraCoords();
        c.setCalleCanonica(canonica);
        c.setLocalidad(localidad);
        c.setCuadra(cuadra);
        c.setLat(lat);
        c.setLng(lng);
        c.setProveedor(proveedor);
        return c;
    }
}
