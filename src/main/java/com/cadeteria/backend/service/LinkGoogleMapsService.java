package com.cadeteria.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Pegá el link de Google Maps" (2026-09-25): cuando el buscador no encuentra una dirección, el
 * admin/cliente la busca en Google Maps y pega el link; de acá salen las coordenadas para el pin.
 * No usa la API de Google (no gasta cupo): solo lee la URL.
 * <p>
 * Formatos, en orden de preferencia:
 * <ul>
 *   <li>{@code !3d<lat>!4d<lng>} — el lugar marcado (links de /maps/place/).</li>
 *   <li>{@code @<lat>,<lng>} — en Street View es donde está la cámara (en la calle, frente a la
 *       casa); en el mapa común, el centro de la pantalla.</li>
 *   <li>{@code ?q=<lat>,<lng>} y parecidos (ll, query, destination...).</li>
 * </ul>
 * Los links cortos que da "Compartir" en la app del celular ({@code maps.app.goo.gl/...}) no
 * traen coordenadas: se sigue la redirección hasta la URL larga. Solo se siguen dominios de
 * Google — si no, cualquiera podría hacer que el servidor abra un link arbitrario.
 */
@Service
public class LinkGoogleMapsService {

    private static final Logger log = LoggerFactory.getLogger(LinkGoogleMapsService.class);

    /** lat/lng con el pin, o error con el mensaje para mostrar. */
    public record ResultadoLink(Double lat, Double lng, String error) {
        static ResultadoLink ok(double lat, double lng) {
            return new ResultadoLink(lat, lng, null);
        }

        static ResultadoLink error(String mensaje) {
            return new ResultadoLink(null, null, mensaje);
        }
    }

    private static final String NUM = "(-?\\d{1,3}\\.\\d+)";
    private static final Pattern LUGAR = Pattern.compile("!3d" + NUM + "!4d" + NUM);
    private static final Pattern ARROBA = Pattern.compile("@" + NUM + "," + NUM);
    private static final Pattern PARAMETRO = Pattern.compile(
            "[?&](?:q|ll|query|center|destination|daddr|viewpoint)=(?:loc:)?" + NUM + ",\\s*" + NUM);
    private static final Pattern DOMINIO_GOOGLE = Pattern.compile("(?:[a-z0-9-]+\\.)*google\\.com(?:\\.ar)?");
    private static final Pattern DOMINIO_CORTO = Pattern.compile("(?:[a-z0-9-]+\\.)*goo\\.gl");
    private static final int MAX_REDIRECCIONES = 5;

    static final String NO_ES_LINK = "Eso no parece un link de Google Maps.";
    static final String SIN_UBICACION = "Este link no trae la ubicación. Abrí el lugar en Google Maps (o Street View frente a la casa) y copiá o compartí ese link.";
    static final String FUERA_DE_TUCUMAN = "La ubicación de ese link queda fuera de Tucumán.";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ResultadoLink resolver(String link) {
        URI uri = parsear(link);
        if (uri == null) return ResultadoLink.error(NO_ES_LINK);

        for (int i = 0; i <= MAX_REDIRECCIONES; i++) {
            ResultadoLink r = extraer(uri.toString());
            if (r != null) return r;
            // Solo los links cortos se abren; un link largo sin coordenadas no mejora abriéndolo.
            if (!DOMINIO_CORTO.matcher(uri.getHost()).matches() || i == MAX_REDIRECCIONES) break;
            URI siguiente = redireccion(uri);
            if (siguiente == null) break;
            if (!esDeGoogle(siguiente.getHost())) {
                log.warn("El link {} redirige fuera de Google ({}), no se sigue.", uri, siguiente.getHost());
                break;
            }
            uri = siguiente;
        }
        return ResultadoLink.error(SIN_UBICACION);
    }

    /** Coordenadas de una URL ya larga, o null si no trae (sin red, por eso se testea directo). */
    static ResultadoLink extraer(String url) {
        String texto = URLDecoder.decode(url.replace("+", "%2B"), StandardCharsets.UTF_8);
        for (Pattern p : new Pattern[]{LUGAR, ARROBA, PARAMETRO}) {
            Matcher m = p.matcher(texto);
            if (m.find()) {
                double lat = Double.parseDouble(m.group(1));
                double lng = Double.parseDouble(m.group(2));
                // Misma caja que el sesgo de los buscadores (ver GeocodingProxyService).
                if (lat < -28.1 || lat > -26.0 || lng < -66.2 || lng > -64.4) return ResultadoLink.error(FUERA_DE_TUCUMAN);
                return ResultadoLink.ok(lat, lng);
            }
        }
        return null;
    }

    private static URI parsear(String link) {
        if (link == null) return null;
        String t = link.trim();
        // En el celular "Compartir" a veces copia "Nombre del lugar\nhttps://maps.app.goo.gl/..."
        int http = t.indexOf("http");
        if (http < 0) return null;
        t = t.substring(http).split("\\s")[0];
        try {
            URI uri = URI.create(t);
            if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) return null;
            return esDeGoogle(uri.getHost()) ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean esDeGoogle(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return DOMINIO_GOOGLE.matcher(h).matches() || DOMINIO_CORTO.matcher(h).matches();
    }

    private URI redireccion(URI uri) {
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            String location = resp.headers().firstValue("Location").orElse(null);
            return location == null ? null : uri.resolve(location);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("No se pudo abrir el link corto {}: {}", uri, e.getMessage());
            return null;
        }
    }
}
