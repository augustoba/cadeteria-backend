package com.cadeteria.backend.controller;

import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.WebPushService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Únicos valores de Configuración que necesita una pantalla pública (ronda 7: el
 * formulario de alta de cadete también sube fotos a Cloudinary) — no son datos
 * secretos, son el cloud name y el preset unsigned que ya se usan igual desde el panel.
 */
@RestController
@RequestMapping("/api/publico/configuracion")
public class ConfiguracionPublicaController {

    private final ConfiguracionService service;
    private final WebPushService webPushService;

    public ConfiguracionPublicaController(ConfiguracionService service, WebPushService webPushService) {
        this.service = service;
        this.webPushService = webPushService;
    }

    public record CloudinaryPublicoResponse(String cloudName, String uploadPreset) {}

    @GetMapping("/cloudinary")
    public CloudinaryPublicoResponse cloudinary() {
        var valores = service.findAll();
        return new CloudinaryPublicoResponse(
                valores.getOrDefault("cloudinary_cloud_name", ""),
                valores.getOrDefault("cloudinary_upload_preset", ""));
    }

    public record MarcaPublicaResponse(String nombreCadeteria) {}

    /** Mejora 119 — nombre de la cadetería configurable, mostrado en el header de /seguimiento/:token. */
    @GetMapping("/marca")
    public MarcaPublicaResponse marca() {
        return new MarcaPublicaResponse(service.getString("nombre_cadeteria", "Cadetería"));
    }

    public record VapidPublicKeyResponse(boolean habilitado, String publicKey) {}

    /** Mejora 89 — la clave pública VAPID no es secreta, la necesita el navegador para suscribirse. */
    @GetMapping("/vapid-public-key")
    public VapidPublicKeyResponse vapidPublicKey() {
        return new VapidPublicKeyResponse(webPushService.isHabilitado(), webPushService.getPublicKeyBase64Url());
    }
}
