package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.ApkDescarga;
import com.cadeteria.backend.repository.ApkDescargaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * APK para los cadetes (2026-09-29): se sube desde Configuración (una sola, la nueva reemplaza a la
 * anterior) y se baja con un link de un solo uso que vence. El link apunta al token, no al archivo: baja
 * la APK que esté subida en ese momento.
 */
class ApkServiceTest {

    @TempDir
    Path carpeta;

    private ApkService service;
    private ApkDescargaRepository repo;
    private ConfiguracionService config;
    private final Map<String, ApkDescarga> links = new HashMap<>();
    private final Map<String, String> valores = new HashMap<>();

    @BeforeEach
    void setUp() {
        repo = mock(ApkDescargaRepository.class);
        config = mock(ConfiguracionService.class);
        when(repo.save(any(ApkDescarga.class))).thenAnswer(i -> {
            ApkDescarga d = i.getArgument(0);
            links.put(d.getToken(), d);
            return d;
        });
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(links.get((String) i.getArgument(0))));
        doAnswer(i -> valores.put(i.getArgument(0), i.getArgument(1))).when(config).set(anyString(), anyString());
        when(config.getString(anyString(), anyString())).thenAnswer(i -> valores.getOrDefault(i.getArgument(0), i.getArgument(1)));
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> {
            String v = valores.get((String) i.getArgument(0));
            return v == null ? i.getArgument(1) : Integer.parseInt(v);
        });
        AppProperties props = new AppProperties();
        props.setFrontBaseUrl("https://panel.cadem.com.ar");
        service = new ApkService(repo, config, props, carpeta.toString());
    }

    private static MockMultipartFile apk(String contenido) {
        return new MockMultipartFile("archivo", "app-debug.apk", "application/vnd.android.package-archive", contenido.getBytes());
    }

    @Test
    void subirUnaNuevaReemplazaLaAnterior() throws Exception {
        service.guardar(apk("version-1"), 1, false);
        service.guardar(apk("version-2"), 2, false);
        assertEquals("version-2", Files.readString(service.archivo()));
        assertEquals(1, Files.list(carpeta).count(), "queda una sola APK");
        assertEquals("2", valores.get(ApkService.CLAVE_VERSION));
    }

    @Test
    void obligarAActualizarSubeLaVersionMinima() {
        service.guardar(apk("v3"), 3, true);
        assertEquals("3", valores.get("version_minima_app"));
    }

    @Test
    void soloAceptaApk() {
        MockMultipartFile otro = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", "x".getBytes());
        assertThrows(BadRequestException.class, () -> service.guardar(otro, 1, false));
    }

    @Test
    void sinApkSubidaNoHayLink() {
        assertNull(service.crearLink("c1"));
    }

    @Test
    void elLinkVaAlTokenYBajaLaApkActual() throws Exception {
        service.guardar(apk("vieja"), 1, false);
        ApkService.Link link = service.crearLink("c1");
        assertTrue(link.url().startsWith("https://panel.cadem.com.ar/api/publico/apk/"), link.url());
        service.guardar(apk("nueva"), 2, false);
        String token = link.url().substring(link.url().lastIndexOf('/') + 1);
        assertEquals("nueva", Files.readString(service.descargar(token)), "un link viejo baja la APK nueva");
    }

    @Test
    void despuesDeLaPrimeraDescargaHayQuinceMinutosYDespuesNoVaMas() throws Exception {
        service.guardar(apk("app"), 1, false);
        String token = token(service.crearLink("c1"));
        service.descargar(token);
        assertDoesNotThrow(() -> service.descargar(token), "reintento dentro de los 15 minutos");

        links.get(token).setPrimeraDescargaEn(Instant.now().minus(Duration.ofMinutes(16)));
        assertThrows(ApkService.LinkNoValidoException.class, () -> service.descargar(token));
    }

    @Test
    void vencidoOInexistenteNoBaja() {
        service.guardar(apk("app"), 1, false);
        String token = token(service.crearLink("c1"));
        links.get(token).setVenceEn(Instant.now().minusSeconds(1));
        assertThrows(ApkService.LinkNoValidoException.class, () -> service.descargar(token));
        assertThrows(ApkService.LinkNoValidoException.class, () -> service.descargar("no-existe"));
    }

    private static String token(ApkService.Link link) {
        return link.url().substring(link.url().lastIndexOf('/') + 1);
    }
}
