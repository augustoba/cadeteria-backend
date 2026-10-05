package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionAlias;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el corazón del diseño de la cache (spec §5-5.4): hit por cuadra+canónica+localidad sin
 * llamar a nadie, aprendizaje de variantes nuevas solo con resultados reales, no duplicar filas
 * de coordenadas cuando otra variante ya cacheó la misma cuadra, y no resolver en silencio una
 * calle que existe en más de una localidad.
 */
class DireccionCacheServiceTest {

    private static final String SMT = "San Miguel de Tucumán";

    private DireccionAliasRepository aliasRepository;
    private CuadraCoordsRepository coordsRepository;
    private ConfiguracionService configuracion;
    private DireccionCacheService service;

    @BeforeEach
    void setUp() {
        aliasRepository = mock(DireccionAliasRepository.class);
        coordsRepository = mock(CuadraCoordsRepository.class);
        configuracion = mock(ConfiguracionService.class);
        // Defaults reales: 30 días, borrado activo, el link de Google Maps no vence.
        when(configuracion.getInt(eq(DireccionCacheService.CONFIG_DIAS_GOOGLE), anyInt())).thenReturn(30);
        when(configuracion.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        service = new DireccionCacheService(aliasRepository, coordsRepository, configuracion);
    }

    @Test
    void esMissSiNoHayAliasGuardado() {
        when(aliasRepository.findByVarianteNorm("av peron")).thenReturn(Optional.empty());

        DireccionCacheService.ResultadoCache resultado = service.buscar("Av. Perón", 1502);

        assertNull(resultado);
        verify(coordsRepository, never()).findByCalleCanonicaAndCuadra(any(), any(Integer.class));
    }

    @Test
    void callesPorPalabrasEnLosDosSentidosSinContarAvenidaNiPasaje() {
        when(aliasRepository.findAll()).thenReturn(List.of(
                alias("avenida mitre", "avenida mitre"), alias("avenida bartolome mitre", "avenida bartolome mitre"),
                alias("bartolome mitre", "bartolome mitre"), alias("pasaje belisario lopez", "pasaje belisario lopez"),
                alias("lopez manan", "lopez manan"), alias("suipacha", "suipacha"),
                alias("batalla de suipacha", "batalla de suipacha"), alias("marcos paz", "marcos paz")));
        when(aliasRepository.findByVarianteNorm(anyString())).thenReturn(Optional.empty());
        when(aliasRepository.findByVarianteNorm("suipacha")).thenReturn(Optional.of(alias("suipacha", "suipacha")));

        // Lo tipeado dentro del nombre; primero la que tiene exactamente esas palabras.
        assertEquals(List.of("avenida mitre", "avenida bartolome mitre", "bartolome mitre"), service.callesPorPalabras("mitre"));
        // El nombre dentro de lo tipeado: la Avenida Mitre también es "av bartolome mitre".
        assertEquals(List.of("avenida bartolome mitre", "bartolome mitre", "avenida mitre"), service.callesPorPalabras("Av. Bartolomé Mitre"));
        // El pasaje se busca por el nombre, y "lopez" trae a todas las que lo llevan.
        assertEquals(List.of("pasaje belisario lopez", "lopez manan"), service.callesPorPalabras("lopez"));
        assertEquals(List.of("pasaje belisario lopez"), service.callesPorPalabras("psje belisario lopez"));
        // Sin la calle a la que ya apunta lo tipeado.
        assertEquals(List.of("batalla de suipacha"), service.callesPorPalabras("Suipacha"));
        // Una palabra corta sola no alcanza: "paz" está en demasiados nombres.
        assertEquals(List.of(), service.callesPorPalabras("paz"));
        assertEquals(List.of(), service.callesPorPalabras("avenida"));
    }

    @Test
    void lasOpcionesTraenTodasLasLocalidadesYSoloConfirmanSiHayUna() {
        when(aliasRepository.findByVarianteNorm("belgrano")).thenReturn(Optional.of(alias("belgrano", "belgrano")));
        CuadraCoords capital = coords("belgrano", SMT, 500, -26.82, -65.21, 3);
        CuadraCoords yerbaBuena = coords("belgrano", "Yerba Buena", 500, -26.81, -65.30, 1);
        when(coordsRepository.findByCalleCanonicaAndCuadra("belgrano", 500)).thenReturn(List.of(capital, yerbaBuena));

        assertEquals(List.of(SMT, "Yerba Buena"),
                service.buscarOpciones("Belgrano", 520).stream().map(DireccionCacheService.ResultadoCache::localidad).toList());
        assertEquals(3, capital.getConfirmaciones());

        when(coordsRepository.findByCalleCanonicaAndCuadra("belgrano", 500)).thenReturn(List.of(capital));
        assertEquals(1, service.buscarOpciones("Belgrano", 520).size());
        assertEquals(4, capital.getConfirmaciones());
    }

    @Test
    void estimaUnaCuadraQueFaltaConLasVecinasDeLaMismaLocalidad() {
        when(coordsRepository.findByCalleCanonica("suipacha")).thenReturn(List.of(
                coords("suipacha", SMT, 600, -26.8200, -65.2140, 1),
                coords("suipacha", SMT, 800, -26.8180, -65.2140, 1),
                coords("suipacha", "Lules", 100, -26.9300, -65.3400, 1)));

        List<DireccionCacheService.ResultadoCache> r = service.estimar("suipacha", 750);

        // Solo San Miguel: la de Lules queda a más de tres cuadras. A la altura 750, entre el 650 y el 850.
        assertEquals(1, r.size());
        assertEquals(SMT, r.get(0).localidad());
        assertEquals(true, r.get(0).approximate());
        assertEquals(-26.8190, r.get(0).lat(), 0.00001);
    }

    @Test
    void conUnaSolaCuadraVecinaUsaEsePuntoYSinNingunaCercaNoEstima() {
        when(coordsRepository.findByCalleCanonica("suipacha")).thenReturn(List.of(coords("suipacha", SMT, 600, -26.8200, -65.2140, 1)));

        assertEquals(-26.8200, service.estimar("suipacha", 750).get(0).lat(), 0.00001);
        assertEquals(List.of(), service.estimar("suipacha", 2500));
    }

    @Test
    void lasParecidasDevuelvenSoloLasDelMejorPuntaje() {
        when(aliasRepository.findAll()).thenReturn(List.of(
                alias("belgrano", "belgrano"), alias("general belgrano", "belgrano"),
                alias("bolivar", "bolivar"), alias("batalla de suipacha", "batalla de suipacha")));

        assertEquals(List.of("belgrano"), service.callesParecidas("belgarno"));
        assertEquals(List.of("bolivar"), service.callesParecidas("Bolibar"));
        assertEquals(List.of(), service.callesParecidas("xyzw"));
    }

    @Test
    void elNombreAnteriorLlevaALaCalleDeHoy() {
        var nombresAnteriores = mock(com.cadeteria.backend.repository.CalleNombreAnteriorRepository.class);
        service = new DireccionCacheService(aliasRepository, coordsRepository, configuracion, nombresAnteriores);
        when(nombresAnteriores.findAll()).thenReturn(List.of(
                nombreAnterior("rivadavia", "virgen de la merced"),
                nombreAnterior("avenida general roca", "avenida nestor kirchner")));
        when(aliasRepository.findByVarianteNorm(anyString())).thenReturn(Optional.empty());

        assertEquals(List.of("virgen de la merced"), service.callesConNombreAnterior("Rivadavia"));
        // Como la escriba: sin "avenida", sin "general", abreviada.
        assertEquals(List.of("avenida nestor kirchner"), service.callesConNombreAnterior("roca"));
        assertEquals(List.of("avenida nestor kirchner"), service.callesConNombreAnterior("Av. Gral. Roca"));
        assertEquals(List.of(), service.callesConNombreAnterior("Gral. Paz"));
    }

    private static com.cadeteria.backend.model.CalleNombreAnterior nombreAnterior(String nombre, String calleDeHoy) {
        var fila = new com.cadeteria.backend.model.CalleNombreAnterior();
        fila.setNombreNorm(nombre);
        fila.setCalleCanonica(calleDeHoy);
        return fila;
    }

    @Test
    void mirarUnaCuadraNoLeSumaConfirmaciones() {
        CuadraCoords coords = coords("batalla de suipacha", SMT, 700, -26.81, -65.21, 4);
        when(coordsRepository.findByCalleCanonicaAndCuadra("batalla de suipacha", 700)).thenReturn(List.of(coords));

        assertNotNull(service.mirarPorCanonica("batalla de suipacha", 750));
        assertEquals(4, coords.getConfirmaciones());
        verify(coordsRepository, never()).save(any());
    }

    @Test
    void esMissSiElAliasExistePeroNoHayCoordenadasDeEsaCuadra() {
        DireccionAlias alias = alias("av peron", "presidente peron");
        when(aliasRepository.findByVarianteNorm("av peron")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("presidente peron", 1500)).thenReturn(List.of());

        assertNull(service.buscar("Av. Perón", 1502));
    }

    @Test
    void esHitYSumaUnaConfirmacionSinLlamarANadieMas() {
        DireccionAlias alias = alias("av peron", "presidente peron");
        CuadraCoords coords = coords("presidente peron", SMT, 1500, -26.81, -65.20, 2);
        when(aliasRepository.findByVarianteNorm("av peron")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("presidente peron", 1500)).thenReturn(List.of(coords));

        DireccionCacheService.ResultadoCache resultado = service.buscar("Av. Perón", 1502);

        assertEquals("presidente peron", resultado.calleCanonica());
        assertEquals(1500, resultado.cuadra());
        assertEquals(3, coords.getConfirmaciones());
        verify(coordsRepository).save(coords);
    }

    @Test
    void esMissSiLaMismaCalleYCuadraEstaCacheadaParaDosLocalidadesDistintas() {
        // "Rivadavia 500" existe tanto en San Miguel de Tucumán como en Lules — sin la localidad
        // en el texto no se puede saber cuál corresponde, así que no se puede resolver en silencio.
        DireccionAlias alias = alias("rivadavia", "rivadavia");
        CuadraCoords enSmt = coords("rivadavia", SMT, 500, -26.81, -65.20, 1);
        CuadraCoords enLules = coords("rivadavia", "Lules", 500, -26.90, -65.35, 1);
        when(aliasRepository.findByVarianteNorm("rivadavia")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("rivadavia", 500)).thenReturn(List.of(enSmt, enLules));

        assertNull(service.buscar("Rivadavia", 500));
    }

    @Test
    void guardarCreaAliasYCoordenadasNuevasEnUnMissCompleto() {
        when(aliasRepository.findByVarianteNorm("av peron")).thenReturn(Optional.empty());
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("presidente peron", SMT, 1500)).thenReturn(Optional.empty());

        service.guardar("Av. Perón", 1502, "Presidente Perón", SMT, -26.81, -65.20, false, "nominatim");

        verify(aliasRepository).save(any(DireccionAlias.class));
        verify(coordsRepository).save(any(CuadraCoords.class));
    }

    @Test
    void guardarNoDuplicaElAliasSiOtraBusquedaYaLoCreo() {
        when(aliasRepository.findByVarianteNorm("av peron")).thenReturn(Optional.of(alias("av peron", "presidente peron")));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("presidente peron", SMT, 1500)).thenReturn(Optional.empty());

        service.guardar("Av. Perón", 1502, "Presidente Perón", SMT, -26.81, -65.20, false, "nominatim");

        verify(aliasRepository, never()).save(any());
    }

    @Test
    void guardarSumaConfirmacionEnVezDeDuplicarLaCuadraYaCacheadaPorOtraVariante() {
        when(aliasRepository.findByVarianteNorm("peron")).thenReturn(Optional.empty());
        CuadraCoords existente = coords("presidente peron", SMT, 1500, -26.81, -65.20, 1);
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("presidente peron", SMT, 1500)).thenReturn(Optional.of(existente));

        service.guardar("Perón", 1502, "Presidente Perón", SMT, -26.81, -65.20, false, "geoapify");

        assertEquals(2, existente.getConfirmaciones());
        verify(coordsRepository).save(existente);
        verify(coordsRepository, times(1)).save(any());
    }

    @Test
    void guardarNoConfundeLaMismaCalleEnDosLocalidadesDistintas() {
        // Ya hay una fila de "Rivadavia 500" en Lules; un geocode nuevo de "Rivadavia 500" en San
        // Miguel de Tucumán tiene que crear SU PROPIA fila, no sumarle confirmación a la de Lules.
        when(aliasRepository.findByVarianteNorm("rivadavia")).thenReturn(Optional.of(alias("rivadavia", "rivadavia")));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("rivadavia", SMT, 500)).thenReturn(Optional.empty());

        service.guardar("Rivadavia", 500, "Rivadavia", SMT, -26.81, -65.20, false, "geoapify");

        verify(coordsRepository).save(any(CuadraCoords.class));
    }

    @Test
    void noGuardaUnaUbicacionAproximada() {
        // Sin la altura exacta el pin queda en cualquier punto de la calle: guardarlo hacía que se
        // devolviera después como única opción, hasta con la localidad equivocada (2026-09-24).
        service.guardar("Colombia", 4695, "Colombia", "Yerba Buena", -26.81, -65.28, true, "geoapify");

        verify(aliasRepository, never()).save(any());
        verify(coordsRepository, never()).save(any());
    }

    @Test
    void noGuardaNadaSiElProveedorNoDevolvioCalleCanonica() {
        service.guardar("Av. Perón", 1502, "  ", SMT, -26.81, -65.20, false, "nominatim");

        verify(aliasRepository, never()).save(any());
        verify(coordsRepository, never()).save(any());
    }

    @Test
    void ignoraUnaUbicacionDeGoogleVencida() {
        DireccionAlias alias = alias("colombia", "colombia");
        CuadraCoords deGoogle = coords("colombia", SMT, 4600, -26.79, -65.25, 1);
        deGoogle.setProveedor("google");
        deGoogle.setCreadaEn(Instant.now().minus(Duration.ofDays(31)));
        when(aliasRepository.findByVarianteNorm("colombia")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("colombia", 4600)).thenReturn(List.of(deGoogle));

        assertNull(service.buscar("Colombia", 4695));
    }

    @Test
    void usaUnaUbicacionDeGoogleDentroDelPlazo() {
        DireccionAlias alias = alias("colombia", "colombia");
        CuadraCoords deGoogle = coords("colombia", SMT, 4600, -26.79, -65.25, 1);
        deGoogle.setProveedor("google");
        deGoogle.setCreadaEn(Instant.now().minus(Duration.ofDays(29)));
        when(aliasRepository.findByVarianteNorm("colombia")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("colombia", 4600)).thenReturn(List.of(deGoogle));

        assertNotNull(service.buscar("Colombia", 4695));
    }

    @Test
    void loDelGeocoderDelTelefonoVenceIgualQueGoogle() {
        DireccionAlias alias = alias("colombia", "colombia");
        CuadraCoords delTelefono = coords("colombia", SMT, 4600, -26.79, -65.25, 1);
        delTelefono.setProveedor(DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER);
        delTelefono.setCreadaEn(Instant.now().minus(Duration.ofDays(31)));
        when(aliasRepository.findByVarianteNorm("colombia")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("colombia", 4600)).thenReturn(List.of(delTelefono));

        assertNull(service.buscar("Colombia", 4695));

        // y con la pausa de dev se sigue usando
        when(configuracion.getBoolean(eq(DireccionCacheService.CONFIG_PAUSAR_BORRADO), anyBoolean())).thenReturn(true);
        assertNotNull(service.buscar("Colombia", 4695));
    }

    @Test
    void elBorradoDiarioIncluyeLoDelGeocoderDelTelefono() {
        service.borrarVencidas();
        verify(coordsRepository).deleteByProveedorInAndCreadaEnBefore(
                org.mockito.ArgumentMatchers.argThat(s -> s.contains("google") && s.contains(DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER)),
                any());
    }

    @Test
    void conElBorradoPausadoUsaUnaUbicacionDeGoogleVencida() {
        when(configuracion.getBoolean(eq(DireccionCacheService.CONFIG_PAUSAR_BORRADO), anyBoolean())).thenReturn(true);
        DireccionAlias alias = alias("colombia", "colombia");
        CuadraCoords deGoogle = coords("colombia", SMT, 4600, -26.79, -65.25, 1);
        deGoogle.setProveedor("google");
        deGoogle.setCreadaEn(Instant.now().minus(Duration.ofDays(90)));
        when(aliasRepository.findByVarianteNorm("colombia")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("colombia", 4600)).thenReturn(List.of(deGoogle));

        assertNotNull(service.buscar("Colombia", 4695));
        assertEquals(0, service.borrarVencidas());
        verify(coordsRepository, never()).deleteByProveedorInAndCreadaEnBefore(any(), any());
    }

    @Test
    void unaAproximadaViejaNoVuelveAmbiguaLaCuadra() {
        // Filas aproximadas guardadas antes del 2026-09-24 (ej. "Colombia 4600" en Yerba Buena)
        // no se usan como respuesta, así que tampoco cuentan como "otra localidad".
        DireccionAlias alias = alias("colombia", "colombia");
        CuadraCoords vieja = coords("colombia", "Yerba Buena", 4600, -26.81, -65.28, 1);
        vieja.setApproximate(true);
        CuadraCoords buena = coords("colombia", SMT, 4600, -26.7954, -65.2568, 1);
        buena.setProveedor(DireccionCacheService.PROVEEDOR_GOOGLE_LINK);
        when(aliasRepository.findByVarianteNorm("colombia")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("colombia", 4600)).thenReturn(List.of(vieja, buena));

        assertEquals(SMT, service.buscar("Colombia", 4695).localidad());
    }

    @Test
    void unPinManualNoVenceNunca() {
        DireccionAlias alias = alias("colombia", "colombia");
        CuadraCoords manual = coords("colombia", SMT, 4600, -26.79, -65.25, 1);
        manual.setProveedor(DireccionCacheService.PROVEEDOR_MANUAL);
        manual.setCreadaEn(Instant.now().minus(Duration.ofDays(400)));
        when(aliasRepository.findByVarianteNorm("colombia")).thenReturn(Optional.of(alias));
        when(coordsRepository.findByCalleCanonicaAndCuadra("colombia", 4600)).thenReturn(List.of(manual));

        assertNotNull(service.buscar("Colombia", 4695));
    }

    @Test
    void unaFuentePropiaPisaLaUbicacionDeGoogleYDejaDeVencer() {
        when(aliasRepository.findByVarianteNorm("colombia")).thenReturn(Optional.of(alias("colombia", "colombia")));
        CuadraCoords deGoogle = coords("colombia", SMT, 4600, -26.79, -65.25, 1);
        deGoogle.setProveedor("google");
        deGoogle.setCreadaEn(Instant.now().minus(Duration.ofDays(20)));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("colombia", SMT, 4600)).thenReturn(Optional.of(deGoogle));

        service.guardar("Colombia", 4695, "Colombia", SMT, -26.7954, -65.2568, false, DireccionCacheService.PROVEEDOR_MANUAL);

        assertEquals(DireccionCacheService.PROVEEDOR_MANUAL, deGoogle.getProveedor());
        assertEquals(-26.7954, deGoogle.getLat());
        assertEquals(2, deGoogle.getConfirmaciones());
    }

    @Test
    void googleNoPisaUnaUbicacionPropia() {
        when(aliasRepository.findByVarianteNorm("colombia")).thenReturn(Optional.of(alias("colombia", "colombia")));
        CuadraCoords propia = coords("colombia", SMT, 4600, -26.79, -65.25, 1);
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("colombia", SMT, 4600)).thenReturn(Optional.of(propia));

        service.guardar("Colombia", 4695, "Colombia", SMT, -26.70, -65.10, false, "google");

        assertEquals("nominatim", propia.getProveedor());
        assertEquals(-26.79, propia.getLat());
    }

    @Test
    void conCeroDiasNoGuardaNadaDeGoogle() {
        when(configuracion.getInt(eq(DireccionCacheService.CONFIG_DIAS_GOOGLE), anyInt())).thenReturn(0);

        service.guardar("Colombia", 4695, "Colombia", SMT, -26.79, -65.25, false, "google");

        verify(aliasRepository, never()).save(any());
        verify(coordsRepository, never()).save(any());
    }

    @Test
    void elBorradoDiarioIncluyeElLinkDeGoogleMapsSoloSiEstaConfigurado() {
        service.borrarVencidas();
        verify(coordsRepository).deleteByProveedorInAndCreadaEnBefore(
                eq(java.util.Set.of("google", "here", DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER)), any());

        when(configuracion.getBoolean(eq(DireccionCacheService.CONFIG_GOOGLE_LINK_VENCE), anyBoolean())).thenReturn(true);
        service.borrarVencidas();
        verify(coordsRepository).deleteByProveedorInAndCreadaEnBefore(
                eq(java.util.Set.of("google", "here", DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER, DireccionCacheService.PROVEEDOR_GOOGLE_LINK)), any());
    }

    // --- Qué fuente pisa a cuál (2026-09-26) ---

    @Test
    void elGpsDelCadeteEnLaPuertaCorrigeLoQueHabiaInterpoladoUnBuscador() {
        CuadraCoords existente = coords("colombia", SMT, 4600, -26.80, -65.25, 1); // nominatim
        when(aliasRepository.findByVarianteNorm(anyString())).thenReturn(Optional.of(alias("colombia", "colombia")));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("colombia", SMT, 4600)).thenReturn(Optional.of(existente));

        service.guardar("Colombia", 4695, "Colombia", SMT, -26.7955, -65.2569, false, DireccionCacheService.PROVEEDOR_CADETE_GPS);

        assertEquals(-26.7955, existente.getLat());
        assertEquals(DireccionCacheService.PROVEEDOR_CADETE_GPS, existente.getProveedor());
        assertEquals(2, existente.getConfirmaciones());
    }

    @Test
    void unBuscadorNoPisaUnPinPuestoAManoPeroElCadeteEnLaPuertaSi() {
        CuadraCoords existente = coords("colombia", SMT, 4600, -26.7955, -65.2569, 1);
        existente.setProveedor(DireccionCacheService.PROVEEDOR_MANUAL);
        when(aliasRepository.findByVarianteNorm(anyString())).thenReturn(Optional.of(alias("colombia", "colombia")));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("colombia", SMT, 4600)).thenReturn(Optional.of(existente));

        service.guardar("Colombia", 4650, "Colombia", SMT, -26.80, -65.25, false, "locationiq");
        assertEquals(-26.7955, existente.getLat());
        assertEquals(DireccionCacheService.PROVEEDOR_MANUAL, existente.getProveedor());

        // 2026-09-28 (3j): el GPS del cadete parado en la puerta le gana al pin (antes era al revés).
        service.guardar("Colombia", 4650, "Colombia", SMT, -26.79, -65.24, false, DireccionCacheService.PROVEEDOR_CADETE_GPS);
        assertEquals(-26.79, existente.getLat());
        assertEquals(DireccionCacheService.PROVEEDOR_CADETE_GPS, existente.getProveedor());
        assertEquals(3, existente.getConfirmaciones());
    }

    @Test
    void elNombreDelProveedorTambienPasaPorLosAlias() {
        // Lista curada: "general lamadrid" (OSM) -> "lamadrid" (como busca la gente).
        when(aliasRepository.findByVarianteNorm("lamadrid")).thenReturn(Optional.of(alias("lamadrid", "lamadrid")));
        when(aliasRepository.findByVarianteNorm("general lamadrid")).thenReturn(Optional.of(alias("general lamadrid", "lamadrid")));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("lamadrid", SMT, 600)).thenReturn(Optional.empty());

        service.guardar("Lamadrid", 650, "General Lamadrid", SMT, -26.83, -65.20, false, "nominatim");

        verify(coordsRepository).save(org.mockito.ArgumentMatchers.<CuadraCoords>argThat(c -> "lamadrid".equals(c.getCalleCanonica())));
        verify(coordsRepository, never()).findByCalleCanonicaAndLocalidadAndCuadra(eq("general lamadrid"), anyString(), anyInt());
    }

    @Test
    void dosPasadasDeLaMismaFuenteSePromedianEnVezDeQuedarseConLaEsquina() {
        CuadraCoords existente = coords("peru", SMT, 3700, -26.8000, -65.2400, 1);
        when(aliasRepository.findByVarianteNorm(anyString())).thenReturn(Optional.of(alias("peru", "peru")));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("peru", SMT, 3700)).thenReturn(Optional.of(existente));

        service.guardar("Perú", 3750, "Perú", SMT, -26.8010, -65.2410, false, "nominatim");

        assertEquals(-26.8005, existente.getLat(), 1e-9);
        assertEquals(-65.2405, existente.getLng(), 1e-9);
        assertEquals(2, existente.getMuestras());
        assertEquals(2, existente.getConfirmaciones());
    }

    @Test
    void loDelTelefonoNoSeMezclaEnUnaFilaQueNoVence() {
        CuadraCoords existente = coords("peru", SMT, 3700, -26.8000, -65.2400, 1);
        when(aliasRepository.findByVarianteNorm(anyString())).thenReturn(Optional.of(alias("peru", "peru")));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("peru", SMT, 3700)).thenReturn(Optional.of(existente));

        service.guardar("Perú", 3750, "Perú", SMT, -26.8010, -65.2410, false, DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER);

        assertEquals(-26.8000, existente.getLat());
        assertEquals("nominatim", existente.getProveedor());
        assertEquals(1, existente.getMuestras());
    }

    @Test
    void mismaCanonicaReconoceNombresDistintosDeLaMismaCalle() {
        when(aliasRepository.findByVarianteNorm("avenida general roca")).thenReturn(Optional.of(alias("avenida general roca", "avenida nestor kirchner")));
        when(aliasRepository.findByVarianteNorm("avenida nestor kirchner")).thenReturn(Optional.of(alias("avenida nestor kirchner", "avenida nestor kirchner")));
        when(aliasRepository.findByVarianteNorm("camino del peru")).thenReturn(Optional.empty());

        assertEquals(true, service.mismaCanonica("Av. General Roca", "Avenida Néstor Kirchner"));
        assertEquals(false, service.mismaCanonica("Camino del Perú", "Avenida Néstor Kirchner"));
    }

    private DireccionAlias alias(String varianteNorm, String calleCanonica) {
        DireccionAlias a = new DireccionAlias();
        a.setId("a1");
        a.setVarianteNorm(varianteNorm);
        a.setLocalidad(SMT);
        a.setCalleCanonica(calleCanonica);
        return a;
    }

    private CuadraCoords coords(String calleCanonica, String localidad, int cuadra, double lat, double lng, int confirmaciones) {
        CuadraCoords c = new CuadraCoords();
        c.setId("c1");
        c.setCalleCanonica(calleCanonica);
        c.setLocalidad(localidad);
        c.setCuadra(cuadra);
        c.setLat(lat);
        c.setLng(lng);
        c.setApproximate(false);
        c.setProveedor("nominatim");
        c.setConfirmaciones(confirmaciones);
        return c;
    }
}
