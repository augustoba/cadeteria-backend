package com.cadeteria.backend.service;

import java.util.List;
import java.util.Set;

/**
 * Qué parte de Configuración es solo del superadmin (2026-09-26). Lo técnico, que puede tirar el
 * servicio o tiene implicancias legales (servidores, Cloudinary, borrado de lo de Google, frecuencias,
 * límites anti-abuso), no lo ve ni lo cambia un admin. Tampoco las API keys que carga el superadmin:
 * el admin tiene su propia lista por proveedor ({@link ApiKeyPoolService#SUFIJO_CLIENTE}) donde puede
 * agregar pero no borrar.
 */
public final class ConfiguracionSistema {

    private ConfiguracionSistema() {
    }

    /** Listas de API keys del sistema, una por proveedor (ver {@link ApiKeyPoolService}). */
    public static final List<String> CLAVES_API_KEYS = List.of(
            GeocodingProxyService.CONFIG_GEOAPIFY_KEYS,
            GeocodingProxyService.CONFIG_LOCATIONIQ_KEYS,
            GeocodingProxyService.CONFIG_GOOGLE_KEYS,
            RutaService.CONFIG_GRAPHHOPPER_KEYS,
            RutaService.CONFIG_ORS_KEYS);

    private static final Set<String> CLAVES_TECNICAS = Set.of(
            "cloudinary_cloud_name", "cloudinary_upload_preset",
            "open_route_service_url",
            DireccionCacheService.CONFIG_PAUSAR_BORRADO, DireccionCacheService.CONFIG_DIAS_GOOGLE,
            DireccionCacheService.CONFIG_GOOGLE_LINK_VENCE,
            "frecuencia_ubicacion_seg", MapeoCallesCadetesService.CONFIG_INTERVALO_SEG,
            "aprender_gps_precision_max_m", "aprender_geocoder_precision_max_m", "reverse_respaldo_max_dia",
            GeocodingProxyService.CONFIG_BUSQUEDA_EXTERNA,
            "version_minima_app", "retencion_imagenes_pedido_dias",
            "rate_limit_publico_max", "rate_limit_publico_ventana_seg",
            "vapid_public_key", "vapid_private_key",
            "proximo_numero_pedido");

    /** True si solo el superadmin la ve y la cambia. */
    public static boolean esDeSistema(String clave) {
        return CLAVES_TECNICAS.contains(clave) || CLAVES_API_KEYS.contains(clave);
    }

    /** True si es la lista de API keys que carga el admin del cliente ("geoapify_keys_cliente"). */
    public static boolean esListaDeKeysDelCliente(String clave) {
        return CLAVES_API_KEYS.stream().anyMatch(k -> ApiKeyPoolService.claveCliente(k).equals(clave));
    }
}
