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
                eq(java.util.Set.of("google", DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER)), any());

        when(configuracion.getBoolean(eq(DireccionCacheService.CONFIG_GOOGLE_LINK_VENCE), anyBoolean())).thenReturn(true);
        service.borrarVencidas();
        verify(coordsRepository).deleteByProveedorInAndCreadaEnBefore(
                eq(java.util.Set.of("google", DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER, DireccionCacheService.PROVEEDOR_GOOGLE_LINK)), any());
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
    void unBuscadorNoPisaUnPinPuestoAMano() {
        CuadraCoords existente = coords("colombia", SMT, 4600, -26.7955, -65.2569, 1);
        existente.setProveedor(DireccionCacheService.PROVEEDOR_MANUAL);
        when(aliasRepository.findByVarianteNorm(anyString())).thenReturn(Optional.of(alias("colombia", "colombia")));
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("colombia", SMT, 4600)).thenReturn(Optional.of(existente));

        service.guardar("Colombia", 4650, "Colombia", SMT, -26.80, -65.25, false, "locationiq");
        service.guardar("Colombia", 4650, "Colombia", SMT, -26.79, -65.24, false, DireccionCacheService.PROVEEDOR_CADETE_GPS);

        assertEquals(-26.7955, existente.getLat());
        assertEquals(DireccionCacheService.PROVEEDOR_MANUAL, existente.getProveedor());
        assertEquals(3, existente.getConfirmaciones());
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
