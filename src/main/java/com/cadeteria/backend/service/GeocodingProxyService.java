package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proxy server-side del buscador de direcciones — punto único de geocoding para todo lo que
 * lo necesite: la página pública "/pedir" (mejora 2026-09-17, pedida por el dueño: "si hacen
 * muchas búsquedas juntas se puede bloquear?") y, desde la Fase 1 de
 * spec-geocoding-cache.md (2026-09-21), también el panel admin (antes llamaba directo a los
 * proveedores desde el navegador vía `geocoding.service.ts`, con las keys expuestas en el
 * bundle). Pasando la búsqueda por acá, {@link com.cadeteria.backend.config.RateLimitFilter}
 * limita por IP a "/pedir", y {@link DireccionCacheService} evita pagar dos veces el mismo
 * geocode sin importar por qué pantalla entró.
 * <p>
 * Fuentes: Nominatim (siempre, sin key), Geoapify y LocationIQ (si hay key cargada) — Photon
 * quedó afuera (contingencia de bajo impacto según el comentario original del front: "rara vez
 * suma cobertura nueva"), no hacía falta duplicar las 4 fuentes del front en el backend.
 * <p>
 * Geoapify y LocationIQ admiten VARIAS keys (varias cuentas gratuitas propias) separadas por
 * coma o salto de línea en Configuración ("geoapify_keys"/"locationiq_keys") —
 * {@link ApiKeyPoolService} rota a la siguiente apenas una se queda sin cupo (HTTP 429/403). Si
 * no hay ninguna cargada en Configuración, se usa la de application.yml (env var
 * GEOAPIFY_KEY/LOCATIONIQ_KEY) como valor legado.
 */
@Service
public class GeocodingProxyService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingProxyService.class);

    public record GeoAddress(String label, String street, Integer number, String locality,
                              double lat, double lng, boolean approximate, String proveedor) {}

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";
    /** Nominatim exige identificar al cliente (User-Agent propio) — sin esto responde 403 siempre,
     *  ver https://operations.osmfoundation.org/policies/nominatim/. */
    private static final String NOMINATIM_USER_AGENT = "CadeteriaBackend/1.0 (+https://github.com/augustoba/cadeteria-backend)";
    private static final String GEOAPIFY_URL = "https://api.geoapify.com/v1/geocode/search";
    private static final String LOCATIONIQ_URL = "https://us1.locationiq.com/v1/search";
    public static final String PROVEEDOR_GEOAPIFY = "geoapify";
    public static final String PROVEEDOR_LOCATIONIQ = "locationiq";
    public static final String PROVEEDOR_NOMINATIM = "nominatim";
    public static final String PROVEEDOR_CACHE = "cache";
    public static final String CONFIG_GEOAPIFY_KEYS = "geoapify_keys";
    public static final String CONFIG_LOCATIONIQ_KEYS = "locationiq_keys";
    private static final int MAX_INTENTOS_POR_PROVEEDOR = 10;
    /** left,top,right,bottom de la provincia de Tucumán, para sesgar la búsqueda. */
    private static final String TUCUMAN_VIEWBOX = "-66.2,-26.0,-64.4,-28.1";
    private static final Pattern NUMERO_FINAL = Pattern.compile("^(.+?)[\\s,]*(\\d{1,6})\\s*$");

    private final RestClient restClient = RestClient.create();
    private final ApiKeyPoolService apiKeyPool;
    private final DireccionCacheService direccionCache;
    private final String geoapifyKeyLegado;
    private final String locationIqKeyLegado;

    /** Último resultado real de Nominatim — lo lee el panel de salud (ver SaludController), no hace un ping aparte. */
    private volatile boolean nominatimOk = true;

    public GeocodingProxyService(AppProperties props, ApiKeyPoolService apiKeyPool, DireccionCacheService direccionCache) {
        this.apiKeyPool = apiKeyPool;
        this.direccionCache = direccionCache;
        this.geoapifyKeyLegado = props.getMaps().getGeoapifyKey();
        this.locationIqKeyLegado = props.getMaps().getLocationIqKey();
    }

    public boolean isNominatimOk() {
        return nominatimOk;
    }

    public List<GeoAddress> buscar(String textoCrudo) {
        String q = textoCrudo == null ? "" : textoCrudo.trim().replaceAll("\\s+", " ");
        if (q.length() < 4) return List.of();

        Matcher m = NUMERO_FINAL.matcher(q);
        boolean tieneNumero = m.matches();
        String streetPart = tieneNumero ? m.group(1).trim() : q;
        Integer numero = tieneNumero ? Integer.parseInt(m.group(2)) : null;

        if (numero != null) {
            DireccionCacheService.ResultadoCache cacheado = direccionCache.buscar(streetPart, numero);
            if (cacheado != null) {
                String base = cacheado.calleCanonica() + " " + numero;
                String label = cacheado.localidad() != null && !cacheado.localidad().isBlank()
                        ? base + ", " + cacheado.localidad() : base;
                return List.of(new GeoAddress(label, cacheado.calleCanonica(), numero, cacheado.localidad(),
                        cacheado.lat(), cacheado.lng(), cacheado.approximate(), PROVEEDOR_CACHE));
            }
        }

        List<GeoAddress> resultados = new ArrayList<>();
        resultados.addAll(queryNominatim(q, numero));
        resultados.addAll(queryGeoapify(q, numero));
        resultados.addAll(queryLocationIq(q, numero));
        if (numero != null && streetPart.length() >= 3) {
            queryNominatim(streetPart, null).forEach(r -> resultados.add(conNumero(r, numero)));
            queryGeoapify(streetPart, null).forEach(r -> resultados.add(conNumero(r, numero)));
            queryLocationIq(streetPart, null).forEach(r -> resultados.add(conNumero(r, numero)));
        }
        List<GeoAddress> out = dedupe(resultados);
        if (numero != null && !out.isEmpty()) {
            GeoAddress mejor = out.get(0);
            direccionCache.guardar(streetPart, numero, mejor.street(), mejor.locality(),
                    mejor.lat(), mejor.lng(), mejor.approximate(), mejor.proveedor());
        }
        return out;
    }

    public GeoAddress reverse(double lat, double lng) {
        String url = NOMINATIM_REVERSE_URL + "?format=jsonv2&lat=" + lat + "&lon=" + lng
                + "&addressdetails=1&accept-language=es&zoom=18";
        try {
            Map<String, Object> p = restClient.get().uri(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", NOMINATIM_USER_AGENT)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            nominatimOk = true;
            if (p == null || p.containsKey("error")) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> address = (Map<String, Object>) p.get("address");
            if (address == null || blank(address.get("road"))) return null;
            GeoAddress base = desdeNominatim(p, address, null);
            GeoAddress resultado = new GeoAddress(base.label(), base.street(), base.number(), base.locality(), lat, lng, base.approximate(), base.proveedor());
            alimentarCacheSiEsPreciso(resultado);
            return resultado;
        } catch (Exception e) {
            nominatimOk = false;
            log.warn("Fallo el reverse geocoding de {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    /**
     * Un reverse con altura conocida (no aproximado) es un punto CONFIRMADO por GPS real — sirve
     * igual que un geocode normal para alimentar la cache (spec §12, versión simple: sin pedido de
     * por medio no hay variante de cliente que guardar, así que se usa la propia calle canónica
     * como "alias identidad" — quien después escriba el nombre de la calle tal cual pega directo).
     * Lo dispara tanto el reverse del panel ("cadetes libres", pin arrastrado) como
     * {@link MapeoCallesCadetesService}.
     */
    private void alimentarCacheSiEsPreciso(GeoAddress r) {
        if (r.approximate() || r.number() == null) return;
        direccionCache.guardar(r.street(), r.number(), r.street(), r.locality(), r.lat(), r.lng(), false, r.proveedor());
    }

    private List<GeoAddress> queryNominatim(String texto, Integer numeroEsperado) {
        String url = NOMINATIM_URL + "?format=jsonv2&limit=12&countrycodes=ar&addressdetails=1"
                + "&accept-language=es&viewbox=" + TUCUMAN_VIEWBOX
                + "&q=" + encode(texto + ", Tucumán");
        try {
            List<Map<String, Object>> data = restClient.get().uri(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", NOMINATIM_USER_AGENT)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            nominatimOk = true;
            if (data == null) return List.of();
            List<GeoAddress> out = new ArrayList<>();
            for (Map<String, Object> p : data) {
                @SuppressWarnings("unchecked")
                Map<String, Object> address = (Map<String, Object>) p.get("address");
                if (address == null || !"Tucumán".equals(address.get("state")) || blank(address.get("road"))
                        || p.get("lat") == null || p.get("lon") == null) continue;
                out.add(desdeNominatim(p, address, numeroEsperado));
            }
            return out;
        } catch (Exception e) {
            nominatimOk = false;
            log.warn("Fallo la búsqueda en Nominatim de \"{}\": {}", texto, e.getMessage());
            return List.of();
        }
    }

    private List<GeoAddress> queryGeoapify(String texto, Integer numeroEsperado) {
        for (int intento = 0; intento < MAX_INTENTOS_POR_PROVEEDOR; intento++) {
            String key = apiKeyPool.siguienteClave(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, geoapifyKeyLegado);
            if (key == null || key.isBlank()) return List.of();
            String url = GEOAPIFY_URL + "?apiKey=" + key + "&format=json&limit=12&lang=es"
                    + "&filter=countrycode:ar&bias=rect:" + TUCUMAN_VIEWBOX
                    + "&text=" + encode(texto + ", Tucumán");
            try {
                apiKeyPool.registrarUso(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, key);
                Map<String, Object> data = restClient.get().uri(url)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
                if (data == null) return List.of();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
                if (results == null) return List.of();
                List<GeoAddress> out = new ArrayList<>();
                for (Map<String, Object> r : results) {
                    String state = String.valueOf(r.getOrDefault("state", ""));
                    if (!state.toLowerCase().contains("tucum") || blank(r.get("street"))
                            || r.get("lat") == null || r.get("lon") == null) continue;
                    out.add(desdeGeoapify(r, numeroEsperado));
                }
                return out;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 || e.getStatusCode().value() == 403) {
                    apiKeyPool.marcarAgotada(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, key);
                    continue;
                }
                log.warn("Fallo la búsqueda en Geoapify de \"{}\": {}", texto, e.getMessage());
                return List.of();
            } catch (Exception e) {
                log.warn("Fallo la búsqueda en Geoapify de \"{}\": {}", texto, e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    private List<GeoAddress> queryLocationIq(String texto, Integer numeroEsperado) {
        for (int intento = 0; intento < MAX_INTENTOS_POR_PROVEEDOR; intento++) {
            String key = apiKeyPool.siguienteClave(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, locationIqKeyLegado);
            if (key == null || key.isBlank()) return List.of();
            String url = LOCATIONIQ_URL + "?key=" + key + "&format=json&limit=12&countrycodes=ar&addressdetails=1"
                    + "&accept-language=es&viewbox=" + TUCUMAN_VIEWBOX
                    + "&q=" + encode(texto + ", Tucumán");
            try {
                apiKeyPool.registrarUso(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, key);
                List<Map<String, Object>> data = restClient.get().uri(url)
                        .header("Accept", "application/json")
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
                if (data == null) return List.of();
                List<GeoAddress> out = new ArrayList<>();
                for (Map<String, Object> p : data) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> address = (Map<String, Object>) p.get("address");
                    if (address == null || !"Tucumán".equals(address.get("state")) || blank(address.get("road"))
                            || p.get("lat") == null || p.get("lon") == null) continue;
                    out.add(desdeNominatim(p, address, numeroEsperado, PROVEEDOR_LOCATIONIQ));
                }
                return out;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 || e.getStatusCode().value() == 403) {
                    apiKeyPool.marcarAgotada(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, key);
                    continue;
                }
                log.warn("Fallo la búsqueda en LocationIQ de \"{}\": {}", texto, e.getMessage());
                return List.of();
            } catch (Exception e) {
                log.warn("Fallo la búsqueda en LocationIQ de \"{}\": {}", texto, e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    private GeoAddress desdeNominatim(Map<String, Object> p, Map<String, Object> address, Integer numeroEsperado) {
        return desdeNominatim(p, address, numeroEsperado, PROVEEDOR_NOMINATIM);
    }

    /** LocationIQ replica el formato de respuesta de Nominatim, así que reutiliza el mismo parseo (ver front `geocoding.service.ts`). */
    private GeoAddress desdeNominatim(Map<String, Object> p, Map<String, Object> address, Integer numeroEsperado, String proveedor) {
        String street = String.valueOf(address.getOrDefault("road", ""));
        Integer numero = parseNumero(address.get("house_number"), numeroEsperado);
        String locality = limpiarLocalidad(primero(address, "city", "town", "village", "municipality", "suburb", "neighbourhood", "county"));
        String base = street.isBlank() ? String.valueOf(p.getOrDefault("display_name", "")) : street + (numero != null ? " " + numero : "");
        String label = !street.isBlank() && !locality.isBlank() ? base + ", " + locality : base;
        return new GeoAddress(
                label.isBlank() ? "Dirección" : label, street, numero, locality,
                Double.parseDouble(String.valueOf(p.get("lat"))), Double.parseDouble(String.valueOf(p.get("lon"))),
                address.get("house_number") == null, proveedor);
    }

    private GeoAddress desdeGeoapify(Map<String, Object> r, Integer numeroEsperado) {
        String street = String.valueOf(r.getOrDefault("street", ""));
        Integer numero = parseNumero(r.get("housenumber"), numeroEsperado);
        String locality = limpiarLocalidad(primero(r, "city", "suburb", "county"));
        String base = street.isBlank() ? "" : street + (numero != null ? " " + numero : "");
        String label = !street.isBlank() && !locality.isBlank() ? base + ", " + locality : (base.isBlank() ? "Dirección" : base);
        return new GeoAddress(
                label, street, numero, locality,
                Double.parseDouble(String.valueOf(r.get("lat"))), Double.parseDouble(String.valueOf(r.get("lon"))),
                r.get("housenumber") == null, PROVEEDOR_GEOAPIFY);
    }

    private GeoAddress conNumero(GeoAddress r, int numero) {
        String base = r.street() + " " + numero;
        String label = r.locality() != null && !r.locality().isBlank() ? base + ", " + r.locality() : base;
        return new GeoAddress(label, r.street(), numero, r.locality(), r.lat(), r.lng(), true, r.proveedor());
    }

    private List<GeoAddress> dedupe(List<GeoAddress> lista) {
        List<GeoAddress> ordenada = new ArrayList<>(lista);
        ordenada.sort((a, b) -> Boolean.compare(a.approximate(), b.approximate()));
        Set<String> vistos = new LinkedHashSet<>();
        List<GeoAddress> out = new ArrayList<>();
        for (GeoAddress a : ordenada) {
            String clave = a.street().toLowerCase() + "|" + a.locality().toLowerCase();
            if (vistos.add(clave)) out.add(a);
            if (out.size() >= 7) break;
        }
        return out;
    }

    private Integer parseNumero(Object crudo, Integer porDefecto) {
        if (crudo == null) return porDefecto;
        try {
            return Integer.parseInt(String.valueOf(crudo));
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    private String primero(Map<String, Object> m, String... claves) {
        for (String c : claves) {
            Object v = m.get(c);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }

    private String limpiarLocalidad(String s) {
        return s.replaceFirst("(?i)^Municipio de\\s+", "").trim();
    }

    private boolean blank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
