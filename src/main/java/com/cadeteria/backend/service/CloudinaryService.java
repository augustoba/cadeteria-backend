package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Borrado real de archivos en Cloudinary (mejora 2026-09-23, para RetencionDatosService) —
 * el alta de fotos usa un upload sin firmar desde el navegador (solo necesita cloud_name +
 * upload_preset, ya expuestos en Configuración), pero borrar requiere la Admin API firmada
 * con api_key/api_secret, que son sensibles y no viven en la base. Si no están configurados
 * (todavía no se contrató el plan con esas credenciales), este servicio queda deshabilitado
 * y RetencionDatosService igual limpia la referencia en la base — mismo patrón de
 * degradación intencional que FcmService cuando falta el credencial.
 */
@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final AppProperties props;
    private final ConfiguracionService configuracionService;
    private final RestClient restClient = RestClient.create();
    private boolean habilitado = false;

    public CloudinaryService(AppProperties props, ConfiguracionService configuracionService) {
        this.props = props;
        this.configuracionService = configuracionService;
    }

    @PostConstruct
    void init() {
        String apiKey = props.getCloudinary().getApiKey();
        String apiSecret = props.getCloudinary().getApiSecret();
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            log.warn("app.cloudinary.api-key/api-secret no configurados — borrado de archivos en Cloudinary deshabilitado (solo se limpia la referencia en la base).");
            return;
        }
        habilitado = true;
    }

    /** Para el panel de salud del sistema, si hiciera falta mostrarlo (mismo patrón que FcmService.isHabilitado). */
    public boolean isHabilitado() {
        return habilitado;
    }

    /**
     * Intenta borrar el archivo de Cloudinary a partir de su URL pública — nunca lanza: si
     * no está habilitado, o la URL no es de Cloudinary, o falla la llamada, solo loguea y
     * sigue (la limpieza de la referencia en la base pasa igual, ver RetencionDatosService).
     */
    public void borrarSiCorresponde(String url) {
        if (!habilitado || url == null || url.isBlank()) return;
        String cloudName = configuracionService.getString("cloudinary_cloud_name", "");
        if (cloudName.isBlank()) return;
        String publicId = extraerPublicId(url);
        if (publicId == null) return;
        try {
            long timestampSeg = System.currentTimeMillis() / 1000;
            String firma = firmar("public_id=" + publicId + "&timestamp=" + timestampSeg, props.getCloudinary().getApiSecret());

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("public_id", publicId);
            body.add("api_key", props.getCloudinary().getApiKey());
            body.add("timestamp", String.valueOf(timestampSeg));
            body.add("signature", firma);

            restClient.post()
                    .uri("https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("No se pudo borrar de Cloudinary la URL {}: {}", url, e.getMessage());
        }
    }

    /** "https://res.cloudinary.com/{cloud}/image/upload/v123456/carpeta/nombre.jpg" -> "carpeta/nombre". */
    private static String extraerPublicId(String url) {
        int uploadIdx = url.indexOf("/upload/");
        if (uploadIdx < 0) return null;
        String resto = url.substring(uploadIdx + "/upload/".length());
        if (resto.matches("^v\\d+/.*")) {
            resto = resto.substring(resto.indexOf('/') + 1);
        }
        int lastDot = resto.lastIndexOf('.');
        return lastDot > 0 ? resto.substring(0, lastDot) : resto;
    }

    private static String firmar(String parametros, String apiSecret) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hash = sha1.digest((parametros + apiSecret).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
