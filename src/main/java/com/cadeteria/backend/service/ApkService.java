package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.ApkDescarga;
import com.cadeteria.backend.repository.ApkDescargaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * La APK para los cadetes (2026-09-29). Se sube desde el panel (Configuración → App de cadetes) y se guarda
 * en el disco del backend ({@code app.apk.dir}): hay una sola, la nueva reemplaza a la anterior. No va a
 * Cloudinary: el plan gratis no acepta archivos de más de 10 MB y sus links no vencen.
 * <p>
 * Se baja con links de un solo uso ({@link ApkDescarga}) que apuntan al token, no al archivo: un link
 * mandado antes de subir una versión nueva baja la nueva. La APK igual se puede copiar una vez bajada;
 * lo que de verdad frena que otro trabaje con la cuenta es "un celular por cadete" (CelularCadeteService).
 */
@Service
public class ApkService {

    private static final Logger log = LoggerFactory.getLogger(ApkService.class);

    static final String CLAVE_VERSION = "apk_version";
    static final String CLAVE_SUBIDA_EN = "apk_subida_en";
    static final String CLAVE_TAMANO = "apk_tamano_bytes";
    static final Duration VIGENCIA_LINK = Duration.ofHours(24);
    /** Desde la primera descarga, para reintentar si se cortó (y la descarga que hace Android por su cuenta). */
    static final Duration VENTANA_REINTENTO = Duration.ofMinutes(15);
    private static final String NOMBRE_ARCHIVO = "cadete-app.apk";

    /** Link que no sirve: no existe, venció o ya se usó. */
    public static class LinkNoValidoException extends RuntimeException {
        public LinkNoValidoException() {
            super("Este link de descarga ya se usó o venció. Pedile uno nuevo a la cadetería.");
        }
    }

    public record Link(String url, Instant venceEn) {}

    public record Info(boolean subida, Integer version, Instant subidaEn, Long tamanoBytes) {}

    private final ApkDescargaRepository repo;
    private final ConfiguracionService configuracion;
    private final String frontBaseUrl;
    private final Path carpeta;
    private final SecureRandom random = new SecureRandom();

    public ApkService(ApkDescargaRepository repo, ConfiguracionService configuracion, AppProperties props,
                      @Value("${app.apk.dir:./datos/apk}") String carpeta) {
        this.repo = repo;
        this.configuracion = configuracion;
        this.frontBaseUrl = props.getFrontBaseUrl();
        this.carpeta = Path.of(carpeta);
    }

    Path archivo() {
        return carpeta.resolve(NOMBRE_ARCHIVO);
    }

    /** Guarda la APK nueva (reemplaza la anterior). Con [obligar], sube la versión mínima: las viejas ya no entran. */
    public Info guardar(MultipartFile archivo, Integer version, boolean obligar) {
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase();
        if (archivo.isEmpty() || !nombre.endsWith(".apk")) {
            throw new BadRequestException("Elegí el archivo .apk de la app.");
        }
        if (obligar && version == null) {
            throw new BadRequestException("Para obligar a actualizar hace falta el número de versión.");
        }
        try {
            Files.createDirectories(carpeta);
            // Primero a un temporal y después se reemplaza: una descarga en curso no ve un archivo a medias.
            Path temporal = carpeta.resolve(NOMBRE_ARCHIVO + ".subiendo");
            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, temporal, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporal, archivo(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la APK en " + carpeta, e);
        }
        configuracion.set(CLAVE_SUBIDA_EN, Instant.now().toString());
        configuracion.set(CLAVE_TAMANO, String.valueOf(archivo.getSize()));
        if (version != null) configuracion.set(CLAVE_VERSION, String.valueOf(version));
        if (obligar) configuracion.set("version_minima_app", String.valueOf(version));
        log.info("APK nueva subida (versión {}, {} bytes, obligar a actualizar: {}).", version, archivo.getSize(), obligar);
        return info();
    }

    public Info info() {
        if (!Files.exists(archivo())) return new Info(false, null, null, null);
        int version = configuracion.getInt(CLAVE_VERSION, 0);
        String subida = configuracion.getString(CLAVE_SUBIDA_EN, "");
        String tamano = configuracion.getString(CLAVE_TAMANO, "");
        return new Info(true, version > 0 ? version : null, subida.isBlank() ? null : Instant.parse(subida),
                tamano.isBlank() ? null : Long.parseLong(tamano));
    }

    /** Link nuevo de un solo uso para un cadete; null si todavía no se subió ninguna APK. */
    @Transactional
    public Link crearLink(String cadeteId) {
        if (!Files.exists(archivo())) return null;
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        ApkDescarga d = new ApkDescarga();
        d.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        d.setCadeteId(cadeteId);
        d.setCreadoEn(Instant.now());
        d.setVenceEn(d.getCreadoEn().plus(VIGENCIA_LINK));
        repo.save(d);
        return new Link(frontBaseUrl + "/api/publico/apk/" + d.getToken(), d.getVenceEn());
    }

    /** Si el link sirve (para la página con el botón "Descargar"), sin gastarlo. */
    @Transactional(readOnly = true)
    public boolean linkValido(String token) {
        return buscarValido(token, Instant.now()).isPresent() && Files.exists(archivo());
    }

    /** Gasta el link (con la ventana de reintento) y devuelve el archivo a mandar. */
    @Transactional
    public Path descargar(String token) {
        Instant ahora = Instant.now();
        ApkDescarga d = buscarValido(token, ahora).orElseThrow(LinkNoValidoException::new);
        if (!Files.exists(archivo())) throw new LinkNoValidoException();
        if (d.getPrimeraDescargaEn() == null) d.setPrimeraDescargaEn(ahora);
        d.setDescargas(d.getDescargas() + 1);
        repo.save(d);
        log.info("Descarga de la APK (cadete {}, descarga nº {}).", d.getCadeteId(), d.getDescargas());
        return archivo();
    }

    @Transactional(readOnly = true)
    public Optional<ApkDescarga> ultimoLinkDe(String cadeteId) {
        return repo.findFirstByCadeteIdOrderByCreadoEnDesc(cadeteId);
    }

    private Optional<ApkDescarga> buscarValido(String token, Instant ahora) {
        if (token == null || token.isBlank()) return Optional.empty();
        return repo.findById(token).filter(d -> d.getVenceEn().isAfter(ahora))
                .filter(d -> d.getPrimeraDescargaEn() == null || d.getPrimeraDescargaEn().plus(VENTANA_REINTENTO).isAfter(ahora));
    }
}
