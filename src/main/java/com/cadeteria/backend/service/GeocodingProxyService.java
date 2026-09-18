package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
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
 * Proxy server-side del buscador de direcciones, SOLO para la página pública "/pedir"
 * (mejora 2026-09-17, pedida por el dueño: "si hacen muchas búsquedas juntas se puede
 * bloquear?"). Antes, esa página llamaba directo a Nominatim/Geoapify desde el navegador
 * del cliente (mismo código que usa el panel admin, `geocoding.service.ts`) — cualquiera
 * podía martillar esas APIs gratuitas (con la key de Geoapify expuesta en el bundle) sin
 * que el backend se enterara. Pasando la búsqueda por acá, {@link com.cadeteria.backend.config.RateLimitFilter}
 * ya la limita por IP igual que al resto de "/api/publico/**".
 * <p>
 * Es una versión más chica que la del panel (que combina 4 fuentes): solo Nominatim
 * (siempre, sin key) y Geoapify (si hay key cargada) — Photon y LocationIQ eran
 * contingencias de bajo impacto según el propio comentario original ("rara vez suma
 * cobertura nueva"), no hacía falta duplicar las 4 en Java para esta página secundaria.
 * El panel admin (nuevo pedido, zonas) sigue con las 4 fuentes de siempre, sin cambios.
 */
@Service
public class GeocodingProxyService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingProxyService.class);

    public record GeoAddress(String label, String street, Integer number, String locality,
                              double lat, double lng, boolean approximate) {}

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";
    private static final String GEOAPIFY_URL = "https://api.geoapify.com/v1/geocode/search";
    /** left,top,right,bottom de la provincia de Tucumán, para sesgar la búsqueda. */
    private static final String TUCUMAN_VIEWBOX = "-66.2,-26.0,-64.4,-28.1";
    private static final Pattern NUMERO_FINAL = Pattern.compile("^(.+?)[\\s,]*(\\d{1,6})\\s*$");

    private final RestClient restClient = RestClient.create();
    private final String geoapifyKey;

    /** Último resultado real de Nominatim — lo lee el panel de salud (ver SaludController), no hace un ping aparte. */
    private volatile boolean nominatimOk = true;

    public GeocodingProxyService(AppProperties props) {
        this.geoapifyKey = props.getMaps().getGeoapifyKey();
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

        List<GeoAddress> resultados = new ArrayList<>();
        resultados.addAll(queryNominatim(q, numero));
        resultados.addAll(queryGeoapify(q, numero));
        if (numero != null && streetPart.length() >= 3) {
            queryNominatim(streetPart, null).forEach(r -> resultados.add(conNumero(r, numero)));
            queryGeoapify(streetPart, null).forEach(r -> resultados.add(conNumero(r, numero)));
        }
        return dedupe(resultados);
    }

    public GeoAddress reverse(double lat, double lng) {
        String url = NOMINATIM_REVERSE_URL + "?format=jsonv2&lat=" + lat + "&lon=" + lng
                + "&addressdetails=1&accept-language=es&zoom=18";
        try {
            Map<String, Object> p = restClient.get().uri(url)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            nominatimOk = true;
            if (p == null || p.containsKey("error")) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> address = (Map<String, Object>) p.get("address");
            if (address == null || blank(address.get("road"))) return null;
            GeoAddress base = desdeNominatim(p, address, null);
            return new GeoAddress(base.label(), base.street(), base.number(), base.locality(), lat, lng, base.approximate());
        } catch (Exception e) {
            nominatimOk = false;
            log.warn("Fallo el reverse geocoding de {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    private List<GeoAddress> queryNominatim(String texto, Integer numeroEsperado) {
        String url = NOMINATIM_URL + "?format=jsonv2&limit=12&countrycodes=ar&addressdetails=1"
                + "&accept-language=es&viewbox=" + TUCUMAN_VIEWBOX
                + "&q=" + encode(texto + ", Tucumán");
        try {
            List<Map<String, Object>> data = restClient.get().uri(url)
                    .header("Accept", "application/json")
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
        if (geoapifyKey == null || geoapifyKey.isBlank()) return List.of();
        String url = GEOAPIFY_URL + "?apiKey=" + geoapifyKey + "&format=json&limit=12&lang=es"
                + "&filter=countrycode:ar&bias=rect:" + TUCUMAN_VIEWBOX
                + "&text=" + encode(texto + ", Tucumán");
        try {
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
        } catch (Exception e) {
            log.warn("Fallo la búsqueda en Geoapify de \"{}\": {}", texto, e.getMessage());
            return List.of();
        }
    }

    private GeoAddress desdeNominatim(Map<String, Object> p, Map<String, Object> address, Integer numeroEsperado) {
        String street = String.valueOf(address.getOrDefault("road", ""));
        Integer numero = parseNumero(address.get("house_number"), numeroEsperado);
        String locality = limpiarLocalidad(primero(address, "city", "town", "village", "municipality", "suburb", "neighbourhood", "county"));
        String base = street.isBlank() ? String.valueOf(p.getOrDefault("display_name", "")) : street + (numero != null ? " " + numero : "");
        String label = !street.isBlank() && !locality.isBlank() ? base + ", " + locality : base;
        return new GeoAddress(
                label.isBlank() ? "Dirección" : label, street, numero, locality,
                Double.parseDouble(String.valueOf(p.get("lat"))), Double.parseDouble(String.valueOf(p.get("lon"))),
                address.get("house_number") == null);
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
                r.get("housenumber") == null);
    }

    private GeoAddress conNumero(GeoAddress r, int numero) {
        String base = r.street() + " " + numero;
        String label = r.locality() != null && !r.locality().isBlank() ? base + ", " + r.locality() : base;
        return new GeoAddress(label, r.street(), numero, r.locality(), r.lat(), r.lng(), true);
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
