package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CalleDuda;
import com.cadeteria.backend.model.CalleNombreAnterior;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.repository.CalleDudaRepository;
import com.cadeteria.backend.repository.CalleNombreAnteriorRepository;
import com.cadeteria.backend.repository.CalleUnionRepository;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Calles a revisar (2026-10-07). Las dos tablas viven en listas; la unión de nombres es la de verdad. */
class CallesARevisarServiceTest {

    private static final String SMT = "San Miguel de Tucumán", YB = "Yerba Buena", TAFI = "Tafí Viejo";
    private static final double LAT = -26.8182, LNG = -65.2143;
    /** Un grado de latitud son ~111 km: 0,0009 son ~100 m. */
    private static final double M100 = 0.0009;
    private static final String LINK = "https://www.google.com/maps/place/x";

    private final List<CuadraCoords> tabla = new ArrayList<>();
    private final List<CalleDuda> dudas = new ArrayList<>();
    private final List<CalleNombreAnterior> nombresAnteriores = new ArrayList<>();
    private LinkGoogleMapsService links;
    private CallesARevisarService service;

    @BeforeEach
    void setUp() {
        CuadraCoordsRepository coords = mock(CuadraCoordsRepository.class);
        DireccionAliasRepository alias = mock(DireccionAliasRepository.class);
        CalleDudaRepository dudasRepo = mock(CalleDudaRepository.class);
        links = mock(LinkGoogleMapsService.class);
        when(coords.findAll()).thenAnswer(i -> List.copyOf(tabla));
        when(coords.findByCalleCanonica(anyString())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getCalleCanonica().equals(i.getArgument(0))).toList());
        when(coords.findByCalleCanonicaAndLocalidadAndCuadra(anyString(), anyString(), anyInt())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getCalleCanonica().equals(i.getArgument(0)) && c.getLocalidad().equals(i.getArgument(1))
                        && c.getCuadra() == (int) i.getArgument(2)).findFirst());
        when(coords.findByLatBetweenAndLngBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        doAnswer(i -> tabla.remove((CuadraCoords) i.getArgument(0))).when(coords).delete(any());
        when(alias.findByVarianteNorm(anyString())).thenReturn(Optional.empty());
        when(alias.findByCalleCanonica(anyString())).thenReturn(List.of());
        when(dudasRepo.findAll()).thenAnswer(i -> List.copyOf(dudas));
        when(dudasRepo.findByEstado(anyString())).thenAnswer(i ->
                dudas.stream().filter(d -> d.getEstado().equals(i.getArgument(0))).toList());
        when(dudasRepo.findById(anyString())).thenAnswer(i ->
                dudas.stream().filter(d -> d.getId().equals(i.getArgument(0))).findFirst());
        when(dudasRepo.save(any(CalleDuda.class))).thenAnswer(i -> {
            CalleDuda d = i.getArgument(0);
            if (!dudas.contains(d)) dudas.add(d);
            return d;
        });
        UnionCallesService union = new UnionCallesService(coords, alias, mock(CalleUnionRepository.class));
        CalleNombreAnteriorRepository anteriores = mock(CalleNombreAnteriorRepository.class);
        when(anteriores.findAll()).thenAnswer(i -> List.copyOf(nombresAnteriores));
        service = new CallesARevisarService(coords, dudasRepo, union, links, anteriores);
    }

    // --- qué entra a la lista ---

    @Test
    void anotaDosNombresDeLaMismaFormaEnLaMismaLocalidad() {
        // Pasaje Bascary 3200 y Bascari: se escriben casi igual y no hay cómo compararlas por el lugar.
        fila("bascari", SMT, 2500, LAT, LNG, "osm");
        fila("bascari", SMT, 2600, LAT + M100, LNG, "osm");
        fila("pasaje bascary", SMT, 3200, LAT + 5 * M100, LNG, "osm");

        assertEquals(1, service.actualizar());

        CalleDuda d = dudas.get(0);
        assertEquals(CallesARevisarService.TIPO_NOMBRE, d.getTipo());
        assertEquals("pasaje bascary", d.getCalleCanonica());
        assertEquals("bascari", d.getOtraCalle(), "queda el nombre con más cuadras");
        assertEquals(3200, d.getCuadra());
        assertEquals(CallesARevisarService.PENDIENTE, d.getEstado());
    }

    @Test
    void laMismaFormaEnOtraLocalidadYLejosNoEsUnaDuda() {
        // "Avenida Sarmiento" de San Miguel y "Sarmiento" de Yerba Buena: 7 km, otra calle.
        fila("sarmiento", YB, 100, LAT, LNG, "osm");
        fila("avenida sarmiento", SMT, 100, LAT + 70 * M100, LNG, "osm");

        assertEquals(0, service.actualizar());
    }

    @Test
    void anotaLaCuadraQueNoCaeEntreSusDosVecinas() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        fila("lavalle", SMT, 200, LAT + M100, LNG + 0.004, "nominatim");   // 400 m al costado
        fila("lavalle", SMT, 300, LAT + 2 * M100, LNG, "osm");

        assertEquals(1, service.actualizar());

        CalleDuda d = dudas.get(0);
        assertEquals(CallesARevisarService.TIPO_UBICACION, d.getTipo());
        assertEquals(200, d.getCuadra());
        assertTrue(d.getMotivo().contains("entre la 100 y la 300"), d.getMotivo());
    }

    @Test
    void anotaLaPuntaDeLaCalleQueQuedaDemasiadoLejosDeSuVecina() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "osm");   // 1 km para una cuadra

        assertEquals(2, service.actualizar(), "las dos son punta y ninguna sabe cuál está mal");
        assertTrue(dudas.get(0).getMotivo().contains("punta"), dudas.get(0).getMotivo());
    }

    @Test
    void loQueYaSeResolvioNoSeVuelveAPreguntar() {
        fila("bascari", SMT, 2500, LAT, LNG, "osm");
        fila("pasaje bascary", SMT, 3200, LAT + 5 * M100, LNG, "osm");
        service.actualizar();
        service.marcar(dudas.get(0).getId(), "DISTINTAS", "admin");

        assertEquals(0, service.actualizar());
        assertEquals(1, dudas.size());
        assertTrue(service.pendientes().isEmpty());
    }

    @Test
    void laListaVaOrdenadaPorUso() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm").setConfirmaciones(2);
        fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "osm").setConfirmaciones(40);
        service.actualizar();

        List<CallesARevisarService.DudaVista> lista = service.pendientes();

        assertEquals(200, lista.get(0).cuadra());
        assertEquals(40, lista.get(0).usos());
        // Para el mapa chico: el punto de la cuadra y los de las cuadras con las que se compara.
        assertEquals(LAT + 10 * M100, lista.get(0).lat());
        assertEquals(100, lista.get(0).cercanas().get(0).cuadra());
    }

    @Test
    void conElDibujoAnotaLaCuadraLejosDeSuCalleYAlCorregirlaElCalculoCompletaElHueco() {
        CuadraCoordsRepository coords = mock(CuadraCoordsRepository.class);
        when(coords.findAll()).thenAnswer(i -> List.copyOf(tabla));
        when(coords.findByCalleCanonica(anyString())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getCalleCanonica().equals(i.getArgument(0))).toList());
        when(coords.findByCalleCanonicaAndLocalidadAndCuadra(anyString(), anyString(), anyInt())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getCalleCanonica().equals(i.getArgument(0)) && c.getLocalidad().equals(i.getArgument(1))
                        && c.getCuadra() == (int) i.getArgument(2)).findFirst());
        when(coords.save(any(CuadraCoords.class))).thenAnswer(i -> {
            if (!tabla.contains((CuadraCoords) i.getArgument(0))) tabla.add(i.getArgument(0));
            return i.getArgument(0);
        });
        DireccionAliasRepository alias = mock(DireccionAliasRepository.class);
        when(alias.findAll()).thenReturn(List.of());
        CalleDudaRepository dudasRepo = mock(CalleDudaRepository.class);
        when(dudasRepo.findAll()).thenAnswer(i -> List.copyOf(dudas));
        when(dudasRepo.findByEstado(anyString())).thenAnswer(i -> dudas.stream().filter(d -> d.getEstado().equals(i.getArgument(0))).toList());
        when(dudasRepo.findById(anyString())).thenAnswer(i -> dudas.stream().filter(d -> d.getId().equals(i.getArgument(0))).findFirst());
        when(dudasRepo.save(any(CalleDuda.class))).thenAnswer(i -> {
            if (!dudas.contains((CalleDuda) i.getArgument(0))) dudas.add(i.getArgument(0));
            return i.getArgument(0);
        });
        CalleNombreAnteriorRepository anteriores = mock(CalleNombreAnteriorRepository.class);
        when(anteriores.findAll()).thenReturn(List.of());
        // "lavalle" dibujada recta hacia el norte; la 500 quedó 300 m al costado.
        TrazadoCalles trazado = new TrazadoCalles(java.util.Map.of("lavalle", List.of(new TrazadoCalles.Tramo(SMT, new double[][]{
                TrazadoCalles.xy(LAT, LNG), TrazadoCalles.xy(LAT + 20 * M100, LNG)}))));
        CallesARevisarService conDibujo = new CallesARevisarService(coords, dudasRepo,
                new UnionCallesService(coords, alias, mock(CalleUnionRepository.class)), links, anteriores, new RellenoCallesService(coords, alias, trazado));
        fila("lavalle", SMT, 100, LAT + M100, LNG, "osm");
        CuadraCoords lejos = fila("lavalle", SMT, 500, LAT + 5 * M100, LNG + 0.003, "nominatim");

        conDibujo.actualizar();

        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 500).findFirst().orElseThrow();
        assertTrue(d.getMotivo().contains("del dibujo de la calle"), d.getMotivo());

        google(LAT + 5 * M100, LNG);
        assertEquals(CallesARevisarService.CORREGIDA, conDibujo.resolverConLink(d.getId(), LINK, "admin").estado());
        assertEquals(LNG, lejos.getLng());
        // Con la 500 en su lugar, el cálculo completa la 200, la 300 y la 400.
        assertEquals(5, tabla.size());
        assertTrue(tabla.stream().filter(c -> c.getCuadra() == 300).allMatch(c -> c.getProveedor().equals("osm_relleno")));
    }

    // --- pegar el link de Google Maps ---

    @Test
    void siGoogleLaUbicaSobreLaOtraCalleLasUne() {
        fila("manuel alberti", SMT, 400, LAT, LNG, "nominatim");
        fila("alberti manuel m", SMT, 400, LAT + 0.0006, LNG, "android_geocoder");
        service.actualizar();
        google(LAT + 0.0002, LNG);   // a ~20 m de "Manuel Alberti 400"

        CallesARevisarService.Resultado r = service.resolverConLink(dudas.get(0).getId(), LINK, "admin");

        assertEquals(CallesARevisarService.UNIDA, r.estado());
        assertEquals(1, tabla.size());
        CuadraCoords queda = tabla.get(0);
        assertEquals("manuel alberti", queda.getCalleCanonica());
        // El punto de Google queda como el bueno de esa cuadra.
        assertEquals(DireccionCacheService.PROVEEDOR_GOOGLE_LINK, queda.getProveedor());
        assertEquals(LAT + 0.0002, queda.getLat());
        assertEquals("admin", dudas.get(0).getResueltaPor());
    }

    @Test
    void alUnirSoloPasanLasCuadrasDeEsaZona() {
        // "Avenida Camino del Perú" también existe en Tafí Viejo, a 7 km: esa queda como está.
        fila("camino del peru", YB, 900, LAT - M100, LNG, "osm");
        fila("camino del peru", YB, 1000, LAT, LNG, "osm");
        fila("camino del peru", YB, 1100, LAT + M100, LNG, "osm");
        fila("avenida camino del peru", YB, 1000, LAT + 0.0011, LNG, "android_geocoder");
        fila("avenida camino del peru", TAFI, 600, LAT + 70 * M100, LNG, "osm");
        service.actualizar();
        google(LAT + 0.0001, LNG);

        service.resolverConLink(dudas.get(0).getId(), LINK, "admin");

        assertEquals(4, tabla.size());
        assertTrue(tabla.stream().anyMatch(c -> c.getCalleCanonica().equals("avenida camino del peru") && c.getLocalidad().equals(TAFI)));
        assertTrue(tabla.stream().anyMatch(c -> c.getCalleCanonica().equals("camino del peru") && c.getLocalidad().equals(YB)));
    }

    @Test
    void alUnirUnaCalleLargaPasaEnteraAunqueLaOtraTengaUnaSolaCuadraEnLaPunta() {
        // "Boulevard 9 de Julio" de Yerba Buena va de la 300 a la 2100 y "9 de Julio" ahí tiene solo la 2100:
        // la 300 queda a 1800 m de ella, pero es la misma calle y pasa con las demás. La de Tafí Viejo no.
        for (int i = 0; i <= 20; i++) fila("9 de julio", SMT, i * 100, LAT + 0.3 + i * M100, LNG, "osm");
        fila("9 de julio", YB, 2100, LAT, LNG, "osm");
        for (int c = 300; c <= 2100; c += 100) fila("boulevard 9 de julio", YB, c, LAT + (2100 - c) / 100 * M100, LNG + 0.0002, "osm");
        fila("boulevard 9 de julio", TAFI, 300, LAT + 70 * M100, LNG, "osm");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> CallesARevisarService.TIPO_NOMBRE.equals(x.getTipo())).findFirst().orElseThrow();
        google(LAT, LNG);

        assertEquals(CallesARevisarService.UNIDA, service.resolverConLink(d.getId(), LINK, "admin").estado());

        assertEquals(19, tabla.stream().filter(c -> c.getCalleCanonica().equals("9 de julio") && c.getLocalidad().equals(YB)).count());
        assertEquals(List.of(TAFI), tabla.stream().filter(c -> c.getCalleCanonica().equals("boulevard 9 de julio")).map(CuadraCoords::getLocalidad).toList());
    }

    @Test
    void siGoogleLaUbicaLejosDeLaOtraYCercaDeLaPropiaSonDistintas() {
        fila("roca", TAFI, 300, LAT, LNG, "osm");
        fila("avenida roca", TAFI, 300, LAT + 10 * M100, LNG, "osm");
        fila("avenida roca", TAFI, 400, LAT + 11 * M100, LNG, "osm");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getTipo().equals(CallesARevisarService.TIPO_NOMBRE)).findFirst().orElseThrow();
        google(LAT + 0.0003, LNG);   // a ~30 m de "Roca 300" y a 1 km de "Avenida Roca"

        CallesARevisarService.Resultado r = service.resolverConLink(d.getId(), LINK, "admin");

        assertEquals(CallesARevisarService.DISTINTAS, r.estado());
        assertEquals(3, tabla.size(), "no se une nada");
        assertEquals(CallesARevisarService.DISTINTAS, d.getEstado());
    }

    @Test
    void siGoogleCaeEnElMedioNoDecideYLaDudaSigue() {
        fila("manuel alberti", SMT, 400, LAT, LNG, "nominatim");
        fila("alberti manuel m", SMT, 400, LAT + 4 * M100, LNG, "android_geocoder");
        service.actualizar();
        google(LAT + 2 * M100, LNG);   // a 200 m de las dos

        CallesARevisarService.Resultado r = service.resolverConLink(dudas.get(0).getId(), LINK, "admin");

        assertEquals(CallesARevisarService.PENDIENTE, r.estado());
        assertEquals(2, tabla.size());
        // El punto de Google queda anotado para mostrarlo en el mapa.
        assertEquals(LAT + 2 * M100, dudas.get(0).getLinkLat());
        // Y el motivo también, para poder revisar después por qué no se decidió sola.
        assertEquals(r.mensaje(), dudas.get(0).getResultado());
        assertTrue(r.mensaje().startsWith("No alcanza para decidir"));
    }

    @Test
    void unLinkQueNoSirveNoCambiaNada() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "osm");
        service.actualizar();
        when(links.resolver(anyString())).thenReturn(LinkGoogleMapsService.ResultadoLink.error("Eso no parece un link de Google Maps."));

        CallesARevisarService.Resultado r = service.resolverConLink(dudas.get(0).getId(), "hola", "admin");

        assertEquals(CallesARevisarService.PENDIENTE, r.estado());
        assertEquals("Eso no parece un link de Google Maps.", r.mensaje());
    }

    @Test
    void elLinkCorrigeLaCuadraMalUbicadaYSacaDeLaListaALaVecinaQueYaCierra() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        CuadraCoords mala = fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "nominatim");
        service.actualizar();
        assertEquals(2, service.pendientes().size());
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();
        google(LAT + M100, LNG);   // donde va: a una cuadra de la 100

        CallesARevisarService.Resultado r = service.resolverConLink(d.getId(), LINK, "admin");

        assertEquals(CallesARevisarService.CORREGIDA, r.estado());
        assertEquals(LAT + M100, mala.getLat());
        assertEquals(DireccionCacheService.PROVEEDOR_GOOGLE_LINK, mala.getProveedor());
        assertTrue(service.pendientes().isEmpty(), "la 100 era dudosa solo por la 200");
    }

    @Test
    void siGoogleCoincideConElPuntoQueHabiaEstaBien() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "osm");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();
        google(LAT + 10 * M100 + 0.0002, LNG);

        assertEquals(CallesARevisarService.ESTA_BIEN, service.resolverConLink(d.getId(), LINK, "admin").estado());
    }

    @Test
    void elLinkNoPisaLoQueElCadeteMarcoEnLaPuerta() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        CuadraCoords delCadete = fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, DireccionCacheService.PROVEEDOR_CADETE_GPS);
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();
        google(LAT + M100, LNG);

        CallesARevisarService.Resultado r = service.resolverConLink(d.getId(), LINK, "admin");

        assertEquals(CallesARevisarService.PENDIENTE, r.estado());
        assertEquals(LAT + 10 * M100, delCadete.getLat());
    }

    @Test
    void siGoogleLaUbicaLejosDeTodaLaCalleNoSeLeCree() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        CuadraCoords mala = fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "nominatim");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();
        google(LAT - 50 * M100, LNG);   // a 5 km: Google encontró otra cosa

        assertEquals(CallesARevisarService.PENDIENTE, service.resolverConLink(d.getId(), LINK, "admin").estado());
        assertEquals(LAT + 10 * M100, mala.getLat());
    }

    // --- cuándo no se le cree a Google (2026-10-07, por Rivadavia: Google numera mal después de Sarmiento) ---

    @Test
    void siGoogleCaeSobreOtraAlturaDeLaMismaCalleNoSeAplica() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        CuadraCoords mala = fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "nominatim");
        fila("lavalle", SMT, 600, LAT + 5 * M100, LNG, "osm");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();
        google(LAT + 5 * M100 + 0.0002, LNG);   // encima de la 600

        CallesARevisarService.Resultado r = service.resolverConLink(d.getId(), LINK, "admin");

        assertEquals(CallesARevisarService.PENDIENTE, r.estado());
        assertTrue(r.mensaje().contains("600"), r.mensaje());
        assertEquals(LAT + 10 * M100, mala.getLat());
    }

    @Test
    void siConElPuntoDeGoogleSigueSinCerrarConLasVecinasNoSeAplicaSolo() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        CuadraCoords mala = fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "nominatim");
        fila("lavalle", SMT, 300, LAT + 2 * M100, LNG, "osm");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();
        google(LAT + M100, LNG + 0.004);   // 400 m al costado de donde iría

        CallesARevisarService.Resultado r = service.resolverConLink(d.getId(), LINK, "admin");

        assertEquals(CallesARevisarService.PENDIENTE, r.estado());
        assertEquals(LAT + 10 * M100, mala.getLat());
        assertEquals(LNG, mala.getLng());
        assertEquals(r.mensaje(), d.getResultado());

        // La persona mira el mapa y decide usar igual el punto de Google.
        assertEquals(CallesARevisarService.CORREGIDA, service.marcar(d.getId(), "USAR_GOOGLE", "admin").estado());
        assertEquals(LAT + M100, mala.getLat());
        assertEquals(LNG + 0.004, mala.getLng());
    }

    @Test
    void siGoogleLeDiceOtroNombreAEsaDireccionNoDecide() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        CuadraCoords mala = fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "nominatim");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();
        when(links.resolver(anyString())).thenReturn(new LinkGoogleMapsService.ResultadoLink(LAT + M100, LNG, null, "Virgen de la Merced 250"));

        CallesARevisarService.Resultado r = service.resolverConLink(d.getId(), LINK, "admin");

        assertEquals(CallesARevisarService.PENDIENTE, r.estado());
        assertTrue(r.mensaje().contains("Virgen de la Merced"), r.mensaje());
        assertEquals(LAT + 10 * M100, mala.getLat());
    }

    @Test
    void elMismoNombreEscritoComoLoPoneGoogleNoFrena() {
        fila("avenida lavalle", SMT, 100, LAT, LNG, "osm");
        fila("avenida lavalle", SMT, 200, LAT + 10 * M100, LNG, "nominatim");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();
        when(links.resolver(anyString())).thenReturn(new LinkGoogleMapsService.ResultadoLink(LAT + M100, LNG, null, "Av. Lavalle 250"));

        assertEquals(CallesARevisarService.CORREGIDA, service.resolverConLink(d.getId(), LINK, "admin").estado());
    }

    @Test
    void elNombreDeGoogleEscritoDeOtraManeraEsLaMismaCalle() {
        // Casos de la revisión del 2026-10-07: frenaban links buenos.
        assertEquals(null, CallesARevisarService.otroNombreSegunGoogle("24 de setiembre", "Av. 24 de Septiembre 1250"));
        assertEquals(null, CallesARevisarService.otroNombreSegunGoogle("primero de mayo", "1 de Mayo 550"));
        assertEquals(null, CallesARevisarService.otroNombreSegunGoogle("primero de mayo", "1° de Mayo 550"));
        assertEquals(null, CallesARevisarService.otroNombreSegunGoogle("lamadrid", "La Madrid 1050"));
        assertEquals(null, CallesARevisarService.otroNombreSegunGoogle("avenida ejercito del norte", "Av. Ejército del Nte. 2450"));
    }

    @Test
    void elNombreDeGoogleQueEsOtraCalleSigueFrenando() {
        assertEquals("Virgen de la Merced", CallesARevisarService.otroNombreSegunGoogle("rivadavia", "Virgen de la Merced 250"));
        assertEquals("Av. Juan Benjamín Terán", CallesARevisarService.otroNombreSegunGoogle("juan bautista teran", "Av. Juan Benjamín Terán 450"));
        // Un número distinto es otra calle, aunque el resto sea igual.
        assertEquals("Diagonal 2", CallesARevisarService.otroNombreSegunGoogle("diagonal 1", "Diagonal 2 300"));
        assertEquals("25 de Mayo", CallesARevisarService.otroNombreSegunGoogle("primero de mayo", "25 de Mayo 550"));
    }

    // --- calles que cambiaron de nombre ---

    @Test
    void anotaLaCuadraRepetidaConElNombreAnteriorYNoLaJuzgaPorUbicacion() {
        // Rivadavia se llama Virgen de la Merced hasta Sarmiento: "Rivadavia 500" es la misma cuadra con el nombre viejo.
        nombreAnterior("rivadavia", "virgen de la merced");
        fila("virgen de la merced", SMT, 500, LAT, LNG, "osm");
        fila("rivadavia", SMT, 500, LAT + 0.0003, LNG, "osm");
        fila("rivadavia", SMT, 600, LAT + 10 * M100, LNG, "osm");   // sin el nombre viejo, las dos serían "punta"

        assertEquals(1, service.actualizar());

        CalleDuda d = dudas.get(0);
        assertEquals(CallesARevisarService.TIPO_NOMBRE_VIEJO, d.getTipo());
        assertEquals("rivadavia", d.getCalleCanonica());
        assertEquals(500, d.getCuadra());
        assertEquals("virgen de la merced", d.getOtraCalle());
    }

    @Test
    void elNombreAnteriorLejosDeLaCalleDeHoyNoEsUnaDuda() {
        // Después de Sarmiento la calle sigue siendo Rivadavia.
        nombreAnterior("rivadavia", "virgen de la merced");
        fila("virgen de la merced", SMT, 900, LAT, LNG, "osm");
        fila("rivadavia", SMT, 1000, LAT + M100, LNG, "osm");
        fila("rivadavia", SMT, 1100, LAT + 2 * M100, LNG, "osm");

        assertEquals(0, service.actualizar());
    }

    @Test
    void quitarLaRepetidaDejaLaCuadraConElNombreDeHoy() {
        nombreAnterior("rivadavia", "virgen de la merced");
        fila("virgen de la merced", SMT, 500, LAT, LNG, "osm").setConfirmaciones(2);
        fila("rivadavia", SMT, 500, LAT + 0.0003, LNG, "osm").setConfirmaciones(3);
        service.actualizar();

        CallesARevisarService.Resultado r = service.marcar(dudas.get(0).getId(), "QUITAR", "admin");

        assertEquals(CallesARevisarService.QUITADA, r.estado());
        assertEquals(1, tabla.size());
        assertEquals("virgen de la merced", tabla.get(0).getCalleCanonica());
        assertEquals(5, tabla.get(0).getConfirmaciones());
    }

    // --- elegir a mano ---

    @Test
    void marcarQueNoExisteBorraLaCuadra() {
        fila("lavalle", SMT, 100, LAT, LNG, "osm");
        fila("lavalle", SMT, 200, LAT + 10 * M100, LNG, "osm");
        service.actualizar();
        CalleDuda d = dudas.stream().filter(x -> x.getCuadra() == 200).findFirst().orElseThrow();

        assertEquals(CallesARevisarService.NO_EXISTE, service.marcar(d.getId(), "NO_EXISTE", "admin").estado());
        assertFalse(tabla.stream().anyMatch(c -> c.getCuadra() == 200));
    }

    @Test
    void marcarQueSonLaMismaLasUne() {
        fila("bascari", SMT, 2500, LAT, LNG, "osm");
        fila("bascari", SMT, 2600, LAT + M100, LNG, "osm");
        fila("pasaje bascary", SMT, 3200, LAT + 5 * M100, LNG, "osm");
        service.actualizar();

        assertEquals(CallesARevisarService.UNIDA, service.marcar(dudas.get(0).getId(), "MISMA", "admin").estado());
        assertTrue(tabla.stream().allMatch(c -> c.getCalleCanonica().equals("bascari")));
    }

    private void nombreAnterior(String viejo, String deHoy) {
        CalleNombreAnterior n = new CalleNombreAnterior();
        n.setId(viejo + "|" + deHoy);
        n.setNombreNorm(viejo);
        n.setCalleCanonica(deHoy);
        nombresAnteriores.add(n);
    }

    private void google(double lat, double lng) {
        when(links.resolver(anyString())).thenReturn(LinkGoogleMapsService.ResultadoLink.ok(lat, lng));
    }

    private CuadraCoords fila(String calle, String localidad, int cuadra, double lat, double lng, String proveedor) {
        CuadraCoords c = new CuadraCoords();
        c.setId(calle + "|" + localidad + "|" + cuadra);
        c.setCalleCanonica(calle);
        c.setLocalidad(localidad);
        c.setCuadra(cuadra);
        c.setLat(lat);
        c.setLng(lng);
        c.setApproximate(false);
        c.setProveedor(proveedor);
        c.setConfirmaciones(1);
        tabla.add(c);
        return c;
    }
}
