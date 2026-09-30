package com.cadeteria.backend.service;

import com.cadeteria.backend.common.TooManyRequestsException;
import com.cadeteria.backend.dto.AvisoCalleDtos.AvisoCalleResponse;
import com.cadeteria.backend.model.AvisoCalle;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.repository.AvisoCalleRepository;
import com.cadeteria.backend.repository.AvisoCalleVotoRepository;
import com.cadeteria.backend.model.AvisoCalleVoto;
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
    private AvisoCalleVotoRepository votoRepo;
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
        votoRepo = mock(AvisoCalleVotoRepository.class);
        service = new AvisoCalleService(repo, votoRepo, cadeteRepo, config, geocoding, publisher);

        avisa = cadete("c-avisa", "LIBRE", LAT, LNG, Instant.now());
        when(cadeteRepo.findByUsername("30111222")).thenReturn(Optional.of(avisa));
        when(repo.save(any(AvisoCalle.class))).thenAnswer(i -> i.getArgument(0));
        when(geocoding.reverseParaConsulta(anyDouble(), anyDouble())).thenReturn(new GeocodingProxyService.GeoAddress(
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
        // 60 minutos, salvo que la medianoche llegue antes (entre las 23 y las 24 el test no depende de la hora).
        assertEquals(AvisoCalleService.hastaFinDelDia(a.getCreadoEn().plus(Duration.ofMinutes(60)), a.getCreadoEn()),
                a.getVenceEn());
        assertFalse(a.getCreadoEn().isBefore(antes));

        ArgumentCaptor<AvisoCalleResponse> panel = ArgumentCaptor.forClass(AvisoCalleResponse.class);
        verify(publisher).publicarAvisoCalleAdmin(panel.capture());
        assertEquals("Calle cortada", panel.getValue().tipoTexto());
        assertEquals("c-avisa", panel.getValue().cadeteNombre());
    }

    @Test
    void alQuePideLeDiceCualesSonSuyos() {
        // Bug 2026-09-29: la app lo recordaba en memoria y, al reiniciarse, preguntaba "¿Sigue ahí?" por los propios.
        AvisoCalle a = new AvisoCalle();
        a.setTipo("CALLE_CORTADA");
        avisa.setUsername("30111222");
        a.setCadete(avisa);
        assertEquals(Boolean.TRUE, AvisoCalleResponse.paraCadete(a, "30111222").mio());
        assertEquals(Boolean.FALSE, AvisoCalleResponse.paraCadete(a, "30999999").mio());
        assertNull(AvisoCalleResponse.paraCadete(a, "30111222").cadeteId(), "igual no se dice quién avisó");
    }

    @Test
    void aLosCadetesNoLesDiceQuienAviso() {
        AvisoCalle a = new AvisoCalle();
        a.setTipo("PIQUETE");
        a.setCadete(avisa);
        assertNull(AvisoCalleResponse.paraCadete(a).cadeteNombre());
        assertNull(AvisoCalleResponse.paraCadete(a).cadeteId());
    }

    /** 2026-09-29 23:40 en Argentina (UTC-3) = 2026-09-30 02:40 UTC; la medianoche es 03:00 UTC. */
    private static final Instant ONCE_Y_CUARENTA = Instant.parse("2026-09-30T02:40:00Z");
    private static final Instant MEDIANOCHE = Instant.parse("2026-09-30T03:00:00Z");

    @Test
    void ningunAvisoPasaDeLaMedianoche() {
        // Pedido del usuario (2026-09-29): al terminar el día nadie anda por ahí; al otro día no sirven.
        assertEquals(MEDIANOCHE, AvisoCalleService.hastaFinDelDia(ONCE_Y_CUARENTA.plus(Duration.ofMinutes(60)), ONCE_Y_CUARENTA));
        assertEquals(MEDIANOCHE, AvisoCalleService.hastaFinDelDia(ONCE_Y_CUARENTA.plus(Duration.ofMinutes(30)), ONCE_Y_CUARENTA));
    }

    @Test
    void dentroDelDiaNoSeToca() {
        Instant tarde = Instant.parse("2026-09-29T18:00:00Z"); // 15:00 en Argentina
        Instant vence = tarde.plus(Duration.ofMinutes(60));
        assertEquals(vence, AvisoCalleService.hastaFinDelDia(vence, tarde));
    }

    @Test
    void conLaCalleDelTelefonoNoSeUsaElMapa() {
        // 2026-09-29: en Colombia 4695 OpenStreetMap decía "Camino del Perú 1600" y el panel (que usa la
        // calle del teléfono) "Colombia al 4600". El aviso ahora dice lo mismo que el panel.
        when(cadeteRepo.findAll()).thenReturn(List.of(avisa));
        AvisoCalle a = service.crear("30111222", "CONTROL", LAT, LNG, "Colombia", 4695);
        assertEquals("Colombia 4600", a.getCalle());
        verify(geocoding, never()).reverseParaConsulta(anyDouble(), anyDouble());
    }

    @Test
    void laCalleDelTelefonoSinAlturaVaSola() {
        when(cadeteRepo.findAll()).thenReturn(List.of(avisa));
        assertEquals("Colombia", service.crear("30111222", "CONTROL", LAT, LNG, "  Colombia ", null).getCalle());
    }

    @Test
    void sinCalleDelTelefonoSeUsaElMapa() {
        when(cadeteRepo.findAll()).thenReturn(List.of(avisa));
        assertEquals("Av. Mate de Luna 2400", service.crear("30111222", "CONTROL", LAT, LNG, " ", null).getCalle());
    }

    @Test
    void sinCalleQuedaNull() {
        when(cadeteRepo.findAll()).thenReturn(List.of(avisa));
        when(geocoding.reverseParaConsulta(anyDouble(), anyDouble())).thenThrow(new RuntimeException("Nominatim caído"));
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

    // ---- "¿Sigue ahí?" (segunda etapa, 2026-09-29) ----

    private AvisoCalle avisoActivo(Instant venceEn) {
        AvisoCalle a = new AvisoCalle();
        a.setId("av1");
        a.setTipo("CONTROL");
        a.setLat(LAT);
        a.setLng(LNG);
        a.setCadete(avisa);
        a.setCreadoEn(Instant.now().minus(Duration.ofMinutes(50)));
        a.setVenceEn(venceEn);
        when(repo.findById("av1")).thenReturn(Optional.of(a));
        return a;
    }

    private Cadete otro(String id, String username) {
        Cadete c = cadete(id, "LIBRE", LAT, LNG, Instant.now());
        when(cadeteRepo.findByUsername(username)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void sigueLoExtiendeTreintaMinutosDesdeAhora() {
        AvisoCalle a = avisoActivo(Instant.now().plus(Duration.ofMinutes(10)));
        otro("c2", "40111222");
        when(cadeteRepo.findAll()).thenReturn(List.of());
        when(votoRepo.findByAvisoIdAndCadeteId("av1", "c2")).thenReturn(Optional.empty());

        service.votar("40111222", "av1", AvisoCalleVoto.SIGUE);

        long minutos = Duration.between(Instant.now(), a.getVenceEn()).toMinutes();
        assertTrue(minutos >= 29 && minutos <= 30, "vence en ~30 min, no en 10: " + minutos);
        assertNull(a.getBajadoEn());
        verify(publisher).publicarAvisoCalleAdmin(any());
    }

    @Test
    void sigueNoAcortaUnAvisoQueVenceMasTarde() {
        Instant vence = Instant.now().plus(Duration.ofMinutes(50));
        AvisoCalle a = avisoActivo(vence);
        otro("c2", "40111222");
        when(cadeteRepo.findAll()).thenReturn(List.of());
        when(votoRepo.findByAvisoIdAndCadeteId("av1", "c2")).thenReturn(Optional.empty());

        service.votar("40111222", "av1", AvisoCalleVoto.SIGUE);
        assertEquals(vence, a.getVenceEn());
    }

    @Test
    void conUnYaNoEstaSigueYConDosDeCadetesDistintosSeBaja() {
        AvisoCalle a = avisoActivo(Instant.now().plus(Duration.ofMinutes(30)));
        otro("c2", "40111222");
        when(cadeteRepo.findAll()).thenReturn(List.of());
        when(votoRepo.findByAvisoIdAndCadeteId(eq("av1"), anyString())).thenReturn(Optional.empty());

        when(votoRepo.countByAvisoIdAndVoto("av1", AvisoCalleVoto.YA_NO_ESTA)).thenReturn(1L);
        service.votar("40111222", "av1", AvisoCalleVoto.YA_NO_ESTA);
        assertNull(a.getBajadoEn(), "con uno solo no se baja");

        when(votoRepo.countByAvisoIdAndVoto("av1", AvisoCalleVoto.YA_NO_ESTA)).thenReturn(2L);
        otro("c3", "40333444");
        service.votar("40333444", "av1", AvisoCalleVoto.YA_NO_ESTA);
        assertNotNull(a.getBajadoEn());
        assertFalse(a.getVenceEn().isAfter(Instant.now()), "bajado = vence ya");
    }

    @Test
    void elQueAvisoNoContestaYUnAvisoVencidoTampoco() {
        avisoActivo(Instant.now().plus(Duration.ofMinutes(30)));
        assertThrows(com.cadeteria.backend.common.BadRequestException.class,
                () -> service.votar("30111222", "av1", AvisoCalleVoto.YA_NO_ESTA));

        avisoActivo(Instant.now().minus(Duration.ofMinutes(1)));
        otro("c2", "40111222");
        assertThrows(com.cadeteria.backend.common.ConflictException.class,
                () -> service.votar("40111222", "av1", AvisoCalleVoto.SIGUE));
    }

    @Test
    void siCambiaDeOpinionSeActualizaElMismoVoto() {
        avisoActivo(Instant.now().plus(Duration.ofMinutes(30)));
        Cadete c2 = otro("c2", "40111222");
        when(cadeteRepo.findAll()).thenReturn(List.of());
        AvisoCalleVoto previo = new AvisoCalleVoto();
        previo.setId("v1");
        previo.setCadete(c2);
        previo.setVoto(AvisoCalleVoto.YA_NO_ESTA);
        when(votoRepo.findByAvisoIdAndCadeteId("av1", "c2")).thenReturn(Optional.of(previo));

        service.votar("40111222", "av1", AvisoCalleVoto.SIGUE);
        assertEquals(AvisoCalleVoto.SIGUE, previo.getVoto());
        verify(votoRepo).save(previo);
    }

    @Test
    void resumenParaLaFicha() {
        when(repo.countByCadeteId("c-avisa")).thenReturn(7L);
        when(votoRepo.avisosDelCadeteMarcadosYaNoEsta("c-avisa")).thenReturn(3L);
        when(repo.countByCadeteIdAndBajadoEnIsNotNull("c-avisa")).thenReturn(1L);
        var r = service.resumenCadete("c-avisa");
        assertEquals(7, r.avisados());
        assertEquals(3, r.marcadosYaNoEsta());
        assertEquals(1, r.bajados());
    }
}
