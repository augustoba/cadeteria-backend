package com.cadeteria.backend.controller;

import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.WebPushService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Únicos valores de Configuración que necesita una pantalla pública (ronda 7: el
 * formulario de alta de cadete también sube fotos a Cloudinary) — no son datos
 * secretos, son el cloud name y el preset unsigned que ya se usan igual desde el panel.
 */
@RestController
// Siempre JSON (2026-09-25): con otro Accept (ej. abrir la URL en el navegador) respondía XML, y
// como estas respuestas se cachean, el navegador después le daba ese XML a la página de
// seguimiento — que fallaba con "Algo salió mal" y mostraba "Cadetería" en vez del nombre.
@RequestMapping(value = "/api/publico/configuracion", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConfiguracionPublicaController {

    /** Estos valores casi no cambian (config de cloudinary/marca/vapid) — 5 min de cache evita pegarle a la base en cada carga de pantalla pública. */
    private static final CacheControl CACHE = CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic();

    private final ConfiguracionService service;
    private final WebPushService webPushService;

    public ConfiguracionPublicaController(ConfiguracionService service, WebPushService webPushService) {
        this.service = service;
        this.webPushService = webPushService;
    }

    public record CloudinaryPublicoResponse(String cloudName, String uploadPreset) {}

    @GetMapping("/cloudinary")
    public ResponseEntity<CloudinaryPublicoResponse> cloudinary() {
        var valores = service.findAll();
        return ResponseEntity.ok().cacheControl(CACHE).body(new CloudinaryPublicoResponse(
                valores.getOrDefault("cloudinary_cloud_name", ""),
                valores.getOrDefault("cloudinary_upload_preset", "")));
    }

    public record MarcaPublicaResponse(String nombreCadeteria) {}

    /** Mejora 119 — nombre de la cadetería configurable, mostrado en el header de /seguimiento/:token. */
    @GetMapping("/marca")
    public ResponseEntity<MarcaPublicaResponse> marca() {
        return ResponseEntity.ok().cacheControl(CACHE)
                .body(new MarcaPublicaResponse(service.getString("nombre_cadeteria", "Cadetería")));
    }

    public record VapidPublicKeyResponse(boolean habilitado, String publicKey) {}

    /** Mejora 89 — la clave pública VAPID no es secreta, la necesita el navegador para suscribirse. */
    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidPublicKeyResponse> vapidPublicKey() {
        return ResponseEntity.ok().cacheControl(CACHE)
                .body(new VapidPublicKeyResponse(webPushService.isHabilitado(), webPushService.getPublicKeyBase64Url()));
    }
}
