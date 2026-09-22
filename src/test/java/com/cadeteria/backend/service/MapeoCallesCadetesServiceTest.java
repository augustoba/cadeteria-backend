package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.repository.CadeteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El intervalo real (spec-geocoding-cache.md §12) se corre en segundos y tiene un piso duro no
 * configurable — estas pruebas no esperan minutos ni una corrida real cada 1200 seg (sería un test
 * lento y frágil); en cambio verifican la lógica de filtrado y de "no correr dos veces seguidas",
 * que es lo que puede romperse sin que se note.
 */
class MapeoCallesCadetesServiceTest {

    private CadeteRepository cadeteRepository;
    private GeocodingProxyService geocodingProxyService;
    private ConfiguracionService configuracionService;
    private MapeoCallesCadetesService service;

    @BeforeEach
    void setUp() {
        cadeteRepository = mock(CadeteRepository.class);
        geocodingProxyService = mock(GeocodingProxyService.class);
        configuracionService = mock(ConfiguracionService.class);
        service = new MapeoCallesCadetesService(cadeteRepository, geocodingProxyService, configuracionService);
    }

    @Test
    void noHaceNadaSiElIntervaloEsCeroOMenos() {
        when(configuracionService.getInt(MapeoCallesCadetesService.CONFIG_INTERVALO_SEG, 1200)).thenReturn(0);

        service.mapearSiCorresponde();

        verify(cadeteRepository, never()).findAll();
        verify(geocodingProxyService, never()).reverse(anyDouble(), anyDouble());
    }

    @Test
    void primeraCorridaProcesaLosCadetesQueCalifican() {
        when(configuracionService.getInt(MapeoCallesCadetesService.CONFIG_INTERVALO_SEG, 1200)).thenReturn(30);
        Cadete activo = cadete(true, -26.81, -65.20, Instant.now());
        when(cadeteRepository.findAll()).thenReturn(List.of(activo));

        service.mapearSiCorresponde();

        verify(geocodingProxyService, times(1)).reverse(-26.81, -65.20);
    }

    @Test
    void noVuelveACorrerAntesDeQuePaseElIntervalo() {
        when(configuracionService.getInt(MapeoCallesCadetesService.CONFIG_INTERVALO_SEG, 1200)).thenReturn(1200);
        Cadete activo = cadete(true, -26.81, -65.20, Instant.now());
        when(cadeteRepository.findAll()).thenReturn(List.of(activo));

        service.mapearSiCorresponde();
        service.mapearSiCorresponde();

        verify(geocodingProxyService, times(1)).reverse(-26.81, -65.20);
    }

    @Test
    void ignoraCadetesInactivos() {
        when(configuracionService.getInt(MapeoCallesCadetesService.CONFIG_INTERVALO_SEG, 1200)).thenReturn(30);
        Cadete inactivo = cadete(false, -26.81, -65.20, Instant.now());
        when(cadeteRepository.findAll()).thenReturn(List.of(inactivo));

        service.mapearSiCorresponde();

        verify(geocodingProxyService, never()).reverse(anyDouble(), anyDouble());
    }

    @Test
    void ignoraCadetesSinCoordenadas() {
        when(configuracionService.getInt(MapeoCallesCadetesService.CONFIG_INTERVALO_SEG, 1200)).thenReturn(30);
        Cadete sinUbicacion = cadete(true, null, null, Instant.now());
        when(cadeteRepository.findAll()).thenReturn(List.of(sinUbicacion));

        service.mapearSiCorresponde();

        verify(geocodingProxyService, never()).reverse(anyDouble(), anyDouble());
    }

    @Test
    void ignoraCadetesConUbicacionVieja() {
        when(configuracionService.getInt(MapeoCallesCadetesService.CONFIG_INTERVALO_SEG, 1200)).thenReturn(30);
        Cadete desconectado = cadete(true, -26.81, -65.20, Instant.now().minus(1, ChronoUnit.HOURS));
        when(cadeteRepository.findAll()).thenReturn(List.of(desconectado));

        service.mapearSiCorresponde();

        verify(geocodingProxyService, never()).reverse(anyDouble(), anyDouble());
    }

    @Test
    void unFalloDeUnCadeteNoInterrumpeAlResto() {
        when(configuracionService.getInt(MapeoCallesCadetesService.CONFIG_INTERVALO_SEG, 1200)).thenReturn(30);
        Cadete c1 = cadete(true, -26.81, -65.20, Instant.now());
        Cadete c2 = cadete(true, -26.90, -65.30, Instant.now());
        when(cadeteRepository.findAll()).thenReturn(List.of(c1, c2));
        when(geocodingProxyService.reverse(-26.81, -65.20)).thenThrow(new RuntimeException("boom"));

        service.mapearSiCorresponde();

        verify(geocodingProxyService).reverse(-26.90, -65.30);
    }

    private Cadete cadete(boolean activo, Double lat, Double lng, Instant ubicacionActualizadaEn) {
        Cadete c = new Cadete();
        c.setId("cad-" + System.nanoTime());
        c.setActivo(activo);
        c.setLat(lat);
        c.setLng(lng);
        c.setUbicacionActualizadaEn(ubicacionActualizadaEn);
        return c;
    }
}
