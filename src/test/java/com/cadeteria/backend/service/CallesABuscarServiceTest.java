package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CalleABuscarDescartada;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.repository.CalleABuscarDescartadaRepository;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cuadras a buscar (2026-10-07): lo que falta y destraba cálculo. Una calle recta de 3 km, con una cuadra cada 100 m. */
class CallesABuscarServiceTest {

    private static final String SMT = "San Miguel de Tucumán";
    private static final double LAT = -26.8182, LNG = -65.2143, M100 = 0.0009;
    private static final String LINK = "https://www.google.com/maps/place/x";

    private final List<CuadraCoords> tabla = new ArrayList<>();
    private final List<CalleABuscarDescartada> descartadas = new ArrayList<>();
    private LinkGoogleMapsService links;
    private CallesABuscarService service;

    @BeforeEach
    void setUp() {
        CuadraCoordsRepository coords = mock(CuadraCoordsRepository.class);
        DireccionAliasRepository alias = mock(DireccionAliasRepository.class);
        CalleABuscarDescartadaRepository descartadasRepo = mock(CalleABuscarDescartadaRepository.class);
        links = mock(LinkGoogleMapsService.class);
        when(alias.findAll()).thenReturn(List.of());
        when(coords.findAll()).thenAnswer(i -> List.copyOf(tabla));
        when(coords.findByCalleCanonica(anyString())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getCalleCanonica().equals(i.getArgument(0))).toList());
        when(coords.save(any(CuadraCoords.class))).thenAnswer(i -> {
            CuadraCoords c = i.getArgument(0);
            if (!tabla.contains(c)) tabla.add(c);
            return c;
        });
        when(descartadasRepo.findAll()).thenAnswer(i -> List.copyOf(descartadas));
        when(descartadasRepo.save(any(CalleABuscarDescartada.class))).thenAnswer(i -> {
            descartadas.add(i.getArgument(0));
            return i.getArgument(0);
        });
        TrazadoCalles trazado = new TrazadoCalles(Map.of("lavalle", List.of(new TrazadoCalles.Tramo(SMT, new double[][]{
                TrazadoCalles.xy(LAT, LNG), TrazadoCalles.xy(LAT + 30.5 * M100, LNG)}))));
        RellenoCallesService relleno = new RellenoCallesService(coords, alias, trazado);
        service = new CallesABuscarService(coords, alias, descartadasRepo, trazado, links, relleno);
    }

    @Test
    void pideLaCuadraDelMedioDeUnHuecoLargo() {
        // De la 0 a la 200 y de la 1500 a la 1700: en el medio faltan 12, demasiado para rellenar solo.
        conocidas(0, 100, 200, 1500, 1600, 1700);

        CallesABuscarService.ABuscar medio = unica("INTERMEDIA");

        assertEquals(800, medio.cuadra());
        assertEquals(11, medio.destraba(), "5 hasta la 800 y 6 después");
        assertEquals(LAT + 8 * M100, medio.lat(), 0.00002);
    }

    @Test
    void pideElFinalCuandoElDibujoSigueDespuesDeLaUltimaConocida() {
        conocidas(2000, 2100, 2200, 2300, 2400);   // el dibujo sigue 6 cuadras y media más

        CallesABuscarService.ABuscar fin = unica("FINAL");

        assertEquals(3000, fin.cuadra());
        assertEquals(5, fin.destraba());
    }

    @Test
    void pideElComienzoCuandoLaCalleSeConoceDesdeMasArriba() {
        conocidas(500, 600, 700);

        CallesABuscarService.ABuscar inicio = unica("INICIO");

        assertEquals(0, inicio.cuadra());
        assertEquals(4, inicio.destraba());
    }

    @Test
    void alPegarElLinkGuardaLaCuadraYElCalculoCompletaElHueco() {
        conocidas(0, 100, 200, 1500, 1600, 1700);
        google(LAT + 8 * M100 + 0.0002, LNG);

        CallesABuscarService.Resultado r = service.cargarConLink("lavalle", SMT, 800, LINK, "admin");

        assertTrue(r.cargada(), r.mensaje());
        CuadraCoords la800 = tabla.stream().filter(c -> c.getCuadra() == 800).findFirst().orElseThrow();
        assertEquals(DireccionCacheService.PROVEEDOR_GOOGLE_LINK, la800.getProveedor());
        assertEquals(6 + 1 + 11, tabla.size(), "las 6 que había, la cargada y las 11 que completó el cálculo");
        assertTrue(service.lista().stream().noneMatch(a -> a.tipo().equals("INTERMEDIA")));
    }

    @Test
    void siGoogleLaUbicaLejosDeDondeDeberiaCaerNoSeGuarda() {
        conocidas(0, 100, 200, 1500, 1600, 1700);
        google(LAT + 14 * M100, LNG);   // a 600 m de donde va la 800

        CallesABuscarService.Resultado r = service.cargarConLink("lavalle", SMT, 800, LINK, "admin");

        assertFalse(r.cargada());
        assertEquals(6, tabla.size());
    }

    @Test
    void siGoogleLeDiceOtroNombreNoSeGuarda() {
        conocidas(0, 100, 200, 1500, 1600, 1700);
        when(links.resolver(anyString())).thenReturn(new LinkGoogleMapsService.ResultadoLink(LAT + 8 * M100, LNG, null, "Belgrano 850"));

        CallesABuscarService.Resultado r = service.cargarConLink("lavalle", SMT, 800, LINK, "admin");

        assertFalse(r.cargada());
        assertTrue(r.mensaje().contains("Belgrano"), r.mensaje());
        assertEquals(6, tabla.size());
    }

    @Test
    void laDescartadaNoSeVuelveAPedir() {
        conocidas(0, 100, 200, 1500, 1600, 1700);

        service.descartar("lavalle", SMT, 800, "admin");

        assertTrue(service.lista().stream().noneMatch(a -> a.cuadra() == 800));
    }

    private CallesABuscarService.ABuscar unica(String tipo) {
        List<CallesABuscarService.ABuscar> deEseTipo = service.lista().stream().filter(a -> a.tipo().equals(tipo)).toList();
        assertEquals(1, deEseTipo.size(), service.lista().toString());
        return deEseTipo.get(0);
    }

    private void google(double lat, double lng) {
        when(links.resolver(anyString())).thenReturn(LinkGoogleMapsService.ResultadoLink.ok(lat, lng));
    }

    private void conocidas(int... cuadras) {
        for (int k : cuadras) {
            CuadraCoords c = new CuadraCoords();
            c.setId("lavalle|" + k);
            c.setCalleCanonica("lavalle");
            c.setLocalidad(SMT);
            c.setCuadra(k);
            c.setLat(LAT + k / 100.0 * M100);
            c.setLng(LNG);
            c.setApproximate(false);
            c.setProveedor("osm");
            c.setConfirmaciones(1);
            tabla.add(c);
        }
    }
}
