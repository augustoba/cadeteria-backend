package com.cadeteria.backend.service;

import com.cadeteria.backend.common.TooManyRequestsException;
import com.cadeteria.backend.dto.AvisoCalleDtos.AvisoCalleResponse;
import com.cadeteria.backend.model.AvisoCalle;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.repository.AvisoCalleRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** "Avisos de la calle" (carril C, 2026-09-28): tope por hora, a quién le llega y vencimiento. */
class AvisoCalleServiceTest {

    private static final double LAT = -26.8300, LNG = -65.2000;
    /** 0,0045° de latitud ≈ 500 m; 0,0135° ≈ 1500 m. */
    private static final double QUINIENTOS_M = 0.0045, MIL_QUINIENTOS_M = 0.0135;

    private AvisoCalleRepository repo;
    private CadeteRepository cadeteRepo;
    private WebSocketPublisher publisher;
    private GeocodingProxyService geocoding;
    private AvisoCalleService service;
    private Cadete avisa;

    @BeforeEach
    void setUp() {
        repo = mock(AvisoCalleRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        publisher = mock(WebSocketPublisher.class);
        geocoding = mock(GeocodingProxyService.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        service = new AvisoCalleService(repo, cadeteRepo, config, geocoding, publisher);

        avisa = cadete("c-avisa", "LIBRE", LAT, LNG, Instant.now());
        when(cadeteRepo.findByUsername("30111222")).thenReturn(Optional.of(avisa));
        when(repo.save(any(AvisoCalle.class))).thenAnswer(i -> i.getArgument(0));
        when(geocoding.reverse(anyDouble(), anyDouble())).thenReturn(new GeocodingProxyService.GeoAddress(
                "Av. Mate de Luna 2437, San Miguel de Tucumán", "Av. Mate de Luna", 2437, "San Miguel de Tucumán",
                LAT, LNG, false, "nominatim"));
    }

    private static Cadete cadete(String id, String estado, Double lat, Double lng, Instant ubicacionEn) {
        Cadete c = new Cadete();
        c.setId(id);
        c.setNombre(id);
        c.setActivo(true);
        EstadoCadete e = new EstadoCadete();
        e.setId(estado);
        c.setEstado(e);
        c.setLat(lat);
        c.setLng(lng);
        c.setUbicacionActualizadaEn(ubicacionEn);
        return c;
    }

    @Test
    void llegaSoloALosCercanosConectadosYConPosicionReciente() {
        Instant ahora = Instant.now();
        Cadete cerca = cadete("cerca", "LIBRE", LAT + QUINIENTOS_M, LNG, ahora);
        Cadete ocupadoCerca = cadete("ocupado", "OCUPADO", LAT, LNG + 0.001, ahora);
        Cadete lejos = cadete("lejos", "LIBRE", LAT + MIL_QUINIENTOS_M, LNG, ahora);
        Cadete desconectado = cadete("desc", "DESCONECTADO", LAT, LNG, ahora);
        Cadete posicionVieja = cadete("vieja", "LIBRE", LAT, LNG, ahora.minus(Duration.ofMinutes(15)));
        Cadete sinPosicion = cadete("sinpos", "LIBRE", null, null, null);
        when(cadeteRepo.findAll()).thenReturn(List.of(avisa, cerca, ocupadoCerca, lejos, desconectado, posicionVieja, sinPosicion));

        service.crear("30111222", "CONTROL", LAT, LNG);

        verify(publisher).publicarAvisoCalle(eq("cerca"), any());
        verify(publisher).publicarAvisoCalle(eq("ocupado"), any());
        verify(publisher, times(2)).publicarAvisoCalle(anyString(), any());
        verify(publisher, never()).publicarAvisoCalle(eq("c-avisa"), any());
        verify(publisher).publicarAvisoCalleAdmin(any());
    }

    @Test
    void guardaQuienAvisoLaCalleRedondeadaYVenceALaHora() {
        when(cadeteRepo.findAll()).thenReturn(List.of(avisa));
        Instant antes = Instant.now();
        AvisoCalle a = service.crear("30111222", "CALLE_CORTADA", LAT, LNG);

        assertSame(avisa, a.getCadete());
        assertEquals("Av. Mate de Luna 2400", a.getCalle());
        long minutos = Duration.between(a.getCreadoEn(), a.getVenceEn()).toMinutes();
        assertEquals(60, minutos);
        assertFalse(a.getCreadoEn().isBefore(antes));

        ArgumentCaptor<AvisoCalleResponse> panel = ArgumentCaptor.forClass(AvisoCalleResponse.class);
        verify(publisher).publicarAvisoCalleAdmin(panel.capture());
        assertEquals("Calle cortada", panel.getValue().tipoTexto());
        assertEquals("c-avisa", panel.getValue().cadeteNombre());
    }

    @Test
    void aLosCadetesNoLesDiceQuienAviso() {
        AvisoCalle a = new AvisoCalle();
        a.setTipo("PIQUETE");
        a.setCadete(avisa);
        assertNull(AvisoCalleResponse.paraCadete(a).cadeteNombre());
        assertNull(AvisoCalleResponse.paraCadete(a).cadeteId());
    }

    @Test
    void sinCalleQuedaNull() {
        when(cadeteRepo.findAll()).thenReturn(List.of(avisa));
        when(geocoding.reverse(anyDouble(), anyDouble())).thenThrow(new RuntimeException("Nominatim caído"));
        assertNull(service.crear("30111222", "ACCIDENTE", LAT, LNG).getCalle());
    }

    @Test
    void topeDeCincoPorHora() {
        when(repo.countByCadeteIdAndCreadoEnAfter(eq("c-avisa"), any())).thenReturn(5L);
        TooManyRequestsException e = assertThrows(TooManyRequestsException.class,
                () -> service.crear("30111222", "CONTROL", LAT, LNG));
        assertTrue(e.getMessage().contains("5 avisos"));
        verify(repo, never()).save(any());
        verify(publisher, never()).publicarAvisoCalleAdmin(any());
    }

    @Test
    void activosCercaFiltraPorDistanciaYSoloPideLosNoVencidos() {
        AvisoCalle cerca = new AvisoCalle();
        cerca.setLat(LAT + QUINIENTOS_M);
        cerca.setLng(LNG);
        AvisoCalle lejos = new AvisoCalle();
        lejos.setLat(LAT + MIL_QUINIENTOS_M);
        lejos.setLng(LNG);
        when(repo.findByVenceEnAfterOrderByCreadoEnDesc(any())).thenReturn(List.of(cerca, lejos));

        assertEquals(List.of(cerca), service.activosCerca(LAT, LNG));
        ArgumentCaptor<Instant> corte = ArgumentCaptor.forClass(Instant.class);
        verify(repo).findByVenceEnAfterOrderByCreadoEnDesc(corte.capture());
        assertTrue(Math.abs(Duration.between(corte.getValue(), Instant.now()).toSeconds()) < 5,
                "los vencidos (venceEn antes de ahora) quedan afuera");
    }
}
